<script setup lang="ts">
import { banner, clientAlbums, clientArtists, clientRecommendPlaylists, clientSongs } from '@/api'
import { usePlayActions } from '@/composables/usePlayActions'
import { useI18n } from 'vue-i18n'
import { Swiper, SwiperSlide } from 'swiper/vue'
import { Autoplay, Pagination } from 'swiper/modules'
import type SwiperClass from 'swiper'
import 'swiper/css'
import 'swiper/css/pagination'
import {
  transformAlbums,
  transformArtists,
  transformBanners,
  transformPlaylists,
  transformSongs,
  type AlbumData,
  type ArtistData,
  type BannerData,
  type PlaylistData,
  type SongData,
} from '@/utils/transformers'

const { t } = useI18n()
const router = useRouter()
const PLAYLIST_SOURCE_LIMIT = 12
const SONG_POOL_LIMIT = 60
const ARTIST_POOL_LIMIT = 120
const ALBUM_POOL_LIMIT = 120
const ARTIST_SOURCE_LIMIT = 10
const ALBUM_SOURCE_LIMIT = 8
const MAX_VISIBLE_RECOMMEND_SONGS = 6
const { playPlaylist: playPlaylistAction } = usePlayActions()

interface HomeState {
  banners: BannerData[]
  playlistPool: PlaylistData[]
  songPool: SongData[]
  artistPool: ArtistData[]
  albumPool: AlbumData[]
  artists: ArtistData[]
  albums: AlbumData[]
  playlists: PlaylistData[]
  songs: SongData[]
  playingPlaylistId: number | string | null
  isLoading: boolean
}

const state = reactive<HomeState>({
  banners: [],
  playlistPool: [],
  songPool: [],
  artistPool: [],
  albumPool: [],
  artists: [],
  albums: [],
  playlists: [],
  songs: [],
  playingPlaylistId: null,
  isLoading: true,
})

const { banners, playlists, songs, artists, albums, isLoading } = toRefs(state)
let bannerSwiper: SwiperClass | null = null

const syncBannerSwiper = async (restartAutoplay = false) => {
  await nextTick()
  if (!bannerSwiper || bannerSwiper.destroyed) return
  bannerSwiper.update()
  if (restartAutoplay && banners.value.length > 1) {
    bannerSwiper.autoplay?.start()
  }
}

const shuffleList = <T,>(items: T[]) => {
  const list = [...items]
  for (let i = list.length - 1; i > 0; i -= 1) {
    const j = Math.floor(Math.random() * (i + 1))
    ;[list[i], list[j]] = [list[j], list[i]]
  }
  return list
}

const refreshPlaylists = () => {
  state.playlists = shuffleList(state.playlistPool).slice(0, MAX_VISIBLE_RECOMMEND_SONGS)
}

const refreshSongs = () => {
  state.songs = shuffleList(state.songPool).slice(0, MAX_VISIBLE_RECOMMEND_SONGS)
}

const refreshArtists = () => {
  state.artists = shuffleList(state.artistPool).slice(0, ARTIST_SOURCE_LIMIT)
}

const refreshAlbums = () => {
  state.albums = shuffleList(state.albumPool).slice(0, ALBUM_SOURCE_LIMIT)
}

const applyArtists = () => {
  refreshArtists()
}

const applyAlbums = () => {
  refreshAlbums()
}

const loadHomeData = async () => {
  state.isLoading = true
  try {
    const [b, p, s, artistRes, albumRes] = await Promise.allSettled([
      banner({ type: 2 }),
      clientRecommendPlaylists({ limit: PLAYLIST_SOURCE_LIMIT }),
      clientSongs({ limit: SONG_POOL_LIMIT, sort: 'id_desc' }),
      clientArtists({ limit: ARTIST_POOL_LIMIT }),
      clientAlbums({ limit: ALBUM_POOL_LIMIT }),
    ])

    state.banners =
      b.status === 'fulfilled' ? transformBanners(b.value as Record<string, unknown>, 5) : []
    state.playlistPool =
      p.status === 'fulfilled'
        ? transformPlaylists(
            p.value as Record<string, unknown>,
            PLAYLIST_SOURCE_LIMIT,
            t('home.playlistFallback')
          )
        : []
    state.songPool =
      s.status === 'fulfilled'
        ? transformSongs({ songs: s.value?.data || [] } as Record<string, unknown>, SONG_POOL_LIMIT)
        : []
    state.artistPool =
      artistRes.status === 'fulfilled'
        ? transformArtists({ artists: artistRes.value?.data || [] } as Record<string, unknown>)
        : []
    state.albumPool =
      albumRes.status === 'fulfilled'
        ? transformAlbums({ albums: albumRes.value?.data || [] } as Record<string, unknown>)
        : []
    refreshPlaylists()
    refreshSongs()
    applyArtists()
    applyAlbums()
  } finally {
    state.isLoading = false
  }
}

const playPlaylist = async (playlistId: number | string) => {
  if (!playlistId || state.playingPlaylistId === playlistId) return
  state.playingPlaylistId = playlistId
  try {
    await playPlaylistAction(playlistId)
  } finally {
    state.playingPlaylistId = null
  }
}

const onSwiper = (sw: SwiperClass) => {
  bannerSwiper = sw
  void syncBannerSwiper(true)
}

onMounted(loadHomeData)

onBeforeUnmount(() => {
  bannerSwiper = null
})

onActivated(() => {
  void syncBannerSwiper(true)
})

onDeactivated(() => {
  bannerSwiper?.autoplay?.stop()
})

const swiperModules = [Autoplay, Pagination]

</script>

<template>
  <div class="flex-1 overflow-hidden">
    <div class="h-full overflow-auto pb-4">
      <template v-if="isLoading">
        <MobileHomeSkeleton />
      </template>
      <template v-else>
        <section v-if="banners.length" class="mb-6 px-4 pt-2">
          <Swiper
            @swiper="onSwiper"
            :modules="swiperModules"
            :slides-per-view="1"
            :space-between="12"
            :loop="false"
            :rewind="banners.length > 1"
            :autoplay="{ delay: 4000, disableOnInteraction: false }"
            :pagination="{ clickable: true }"
            class="home-swiper h-44 overflow-hidden rounded-2xl"
          >
            <SwiperSlide v-for="(item, i) in banners" :key="i" class="rounded-2xl">
              <a
                :href="item.url || undefined"
                :target="item.url ? '_blank' : undefined"
                class="relative block h-full w-full overflow-hidden rounded-2xl border border-[var(--glass-border-default)] bg-[var(--glass-bg-card)]"
              >
                <LazyImage
                  :src="item.coverImgUrl"
                  alt="banner"
                  imgClass="h-full w-full object-cover"
                />
                <div
                  class="absolute inset-0 bg-linear-to-t from-black/60 via-black/10 to-transparent"
                ></div>
                <div v-if="item.title" class="absolute right-3 bottom-3 left-3">
                  <span
                    class="text-primary inline-flex items-center gap-1.5 rounded-full border border-white/15 bg-black/20 px-3 py-1.5 text-xs font-medium"
                  >
                    <span class="icon-[mdi--fire] h-3.5 w-3.5 text-orange-300"></span>
                    {{ item.title }}
                  </span>
                </div>
              </a>
            </SwiperSlide>
          </Swiper>
        </section>

        <section class="mb-6 px-4">
          <div class="mb-4 flex items-center justify-between">
            <h2 class="section-title">
              <span class="mobile-section-icon">
                <span class="icon-[mdi--playlist-star] h-4 w-4"></span>
              </span>
              {{ t('home.recommendPlaylists') }}
            </h2>
            <div class="flex items-center gap-2">
              <button class="mobile-action-button" type="button" @click="refreshPlaylists">
                <span class="icon-[mdi--refresh] h-3.5 w-3.5"></span>
                {{ t('common.refresh') }}
              </button>
              <router-link to="/playlists" class="mobile-action-button">
                <span class="icon-[mdi--dots-horizontal-circle-outline] h-3.5 w-3.5"></span>
                {{ t('common.more') }}
              </router-link>
            </div>
          </div>
          <div class="grid grid-cols-3 gap-3">
            <div
              v-for="pl in playlists"
              :key="pl.id"
              class="group cursor-pointer"
              role="link"
              tabindex="0"
              @click="router.push(`/playlist/${pl.id}`)"
              @keydown.enter="router.push(`/playlist/${pl.id}`)"
            >
              <div class="playlist-cover relative mb-2 aspect-square overflow-hidden rounded-xl">
                <LazyImage
                  :src="pl.coverImgUrl"
                  :alt="pl.name"
                  imgClass="h-full w-full object-cover transition-transform duration-300 group-active:scale-105"
                />
                <div
                  class="absolute inset-0 flex items-center justify-center bg-black/0 opacity-0 transition-all group-active:bg-black/35 group-active:opacity-100"
                >
                  <button
                    type="button"
                    class="playlist-play-button"
                    :disabled="state.playingPlaylistId === pl.id"
                    @click.stop.prevent="playPlaylist(pl.id)"
                  >
                    <span
                      class="icon-[mdi--play] h-4.5 w-4.5 text-white"
                    ></span>
                  </button>
                </div>
              </div>
              <p class="playlist-name line-clamp-2 text-xs leading-tight">{{ pl.name }}</p>
            </div>
          </div>
        </section>

        <section class="mb-6 px-4">
          <div class="mb-4 flex items-center justify-between">
            <h2 class="section-title">
              <span class="mobile-section-icon">
                <span class="icon-[mdi--account-star-outline] h-4 w-4"></span>
              </span>
              {{ t('home.followArtists') }}
            </h2>
            <div class="flex items-center gap-2">
              <button class="mobile-action-button" type="button" @click="refreshArtists">
                <span class="icon-[mdi--refresh] h-3.5 w-3.5"></span>
                {{ t('common.refresh') }}
              </button>
              <router-link to="/artists" class="mobile-action-button">
                <span class="icon-[mdi--dots-horizontal-circle-outline] h-3.5 w-3.5"></span>
                {{ t('common.more') }}
              </router-link>
            </div>
          </div>
          <div class="mobile-artist-strip">
            <router-link
              v-for="artist in artists"
              :key="artist.id"
              :to="`/artist/${artist.id}`"
              class="mobile-artist-card"
            >
              <div class="mobile-artist-card__avatar">
                <LazyImage
                  :src="artist.picUrl"
                  :alt="artist.name"
                  imgClass="h-full w-full object-cover"
                />
              </div>
              <div class="min-w-0 flex-1">
                <p class="truncate text-sm font-medium text-[var(--glass-text-primary)]">
                  {{ artist.name }}
                </p>
                <p class="mt-1 text-[11px] text-[var(--glass-text-muted)]">
                  {{ artist.musicSize || 0 }} {{ t('artistPage.stats.songs') }}
                </p>
              </div>
            </router-link>
          </div>
        </section>

        <section class="mb-6 px-4">
          <div class="mb-4 flex items-center justify-between">
            <h2 class="section-title">
              <span class="mobile-section-icon">
                <span class="icon-[mdi--album] h-4 w-4"></span>
              </span>
              {{ t('home.latestAlbums') }}
            </h2>
            <div class="flex items-center gap-2">
              <button class="mobile-action-button" type="button" @click="refreshAlbums">
                <span class="icon-[mdi--refresh] h-3.5 w-3.5"></span>
                {{ t('common.refresh') }}
              </button>
              <router-link to="/new-albums" class="mobile-action-button">
                <span class="icon-[mdi--dots-horizontal-circle-outline] h-3.5 w-3.5"></span>
                {{ t('common.more') }}
              </router-link>
            </div>
          </div>
          <div class="grid grid-cols-2 gap-3">
            <router-link
              v-for="album in albums"
              :key="album.id"
              :to="`/album/${album.id}`"
              class="mobile-album-card"
            >
              <div class="mobile-album-card__cover">
                <LazyImage
                  :src="album.picUrl"
                  :alt="album.name"
                  imgClass="h-full w-full object-cover"
                />
              </div>
              <div class="px-1 pt-2">
                <p class="truncate text-xs font-medium text-[var(--glass-text-primary)]">
                  {{ album.name }}
                </p>
                <p class="mt-1 truncate text-[11px] text-[var(--glass-text-muted)]">
                  {{ album.artist || t('player.unknownArtist') }}
                </p>
              </div>
            </router-link>
          </div>
        </section>

        <section class="mb-6 px-4">
          <div class="mb-4 flex items-center justify-between">
            <h2 class="section-title">
              <span class="mobile-section-icon">
                <span class="icon-[mdi--music-note-plus] h-4 w-4"></span>
              </span>
              {{ t('home.recommendSongs') }}
            </h2>
            <div class="flex items-center gap-2">
              <button class="mobile-action-button" type="button" @click="refreshSongs">
                <span class="icon-[mdi--refresh] h-3.5 w-3.5"></span>
                {{ t('common.refresh') }}
              </button>
              <router-link to="/library" class="mobile-action-button">
                <span class="icon-[mdi--dots-horizontal-circle-outline] h-3.5 w-3.5"></span>
                {{ t('common.more') }}
              </router-link>
            </div>
          </div>
          <div class="space-y-2.5">
            <router-link
              v-for="song in songs"
              :key="song.id"
              :to="`/song/${song.id}`"
              class="mobile-song-row"
            >
              <div class="mobile-song-row__cover">
                <LazyImage
                  :src="song.cover"
                  :alt="song.name"
                  imgClass="h-full w-full object-cover"
                />
              </div>
              <div class="min-w-0 flex-1">
                <p class="truncate text-sm font-medium text-[var(--glass-text-primary)]">
                  {{ song.name }}
                </p>
                <p class="mt-1 truncate text-xs text-[var(--glass-text-muted)]">
                  {{ song.artist || t('player.unknownArtist') }}
                </p>
              </div>
            </router-link>
          </div>
        </section>

        <section
          v-if="!banners.length && !artists.length && !albums.length && !playlists.length && !songs.length"
          class="mx-4 rounded-2xl border border-[var(--glass-border-default)] bg-[var(--glass-bg-card)] px-5 py-8 text-center"
        >
          <div
            class="mx-auto mb-3 flex h-11 w-11 items-center justify-center rounded-2xl bg-[var(--glass-interactive-bg)]"
          >
            <span class="icon-[mdi--music-note-outline] h-5 w-5 text-[var(--sonora-blue)]" />
          </div>
          <p class="text-sm font-semibold text-[var(--glass-text-primary)]">首页内容暂时为空</p>
          <p class="mt-2 text-xs leading-5 text-[var(--glass-text-muted)]">
            请先在管理端启用轮播图，或新增公开歌单与可播放歌曲。
          </p>
        </section>
      </template>
    </div>
  </div>
</template>

<style scoped>
.home-swiper :deep(.swiper-pagination) {
  bottom: 12px;
}

.home-swiper :deep(.swiper-pagination-bullet) {
  width: 6px;
  height: 6px;
  background: rgba(255, 255, 255, 0.4);
  opacity: 1;
  transition: all 0.3s;
}

.home-swiper :deep(.swiper-pagination-bullet-active) {
  background: white;
  width: 20px;
  border-radius: 3px;
}

.section-title {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-size: 1rem;
  font-weight: 700;
  color: var(--glass-text-primary);
}

.mobile-section-icon {
  display: inline-flex;
  height: 1.75rem;
  width: 1.75rem;
  align-items: center;
  justify-content: center;
  border-radius: 0.65rem;
  background: var(--glass-bg-subtle);
  border: 1px solid var(--glass-border-default);
  box-shadow: none;
}

.mobile-section-icon :deep(span) {
  color: var(--glass-text-primary);
}

.view-more-link {
  display: flex;
  align-items: center;
  gap: 0.125rem;
  font-size: 0.75rem;
  color: var(--glass-text-primary);
  opacity: 0.5;
  transition: opacity 0.2s;
}

.view-more-link:active {
  opacity: 0.7;
}

.mobile-action-button {
  display: inline-flex;
  align-items: center;
  gap: 0.25rem;
  border: 1px solid var(--glass-border-default);
  border-radius: 9999px;
  background: var(--glass-bg-card);
  color: var(--glass-text-secondary);
  padding: 0.4rem 0.625rem;
  font-size: 0.6875rem;
  font-weight: 500;
}

.mobile-artist-strip {
  display: grid;
  gap: 0.75rem;
}

.mobile-artist-card {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  border: 1px solid var(--glass-border-default);
  border-radius: 1rem;
  background: var(--glass-bg-card);
  padding: 0.75rem;
}

.mobile-artist-card__avatar {
  width: 3.5rem;
  height: 3.5rem;
  flex-shrink: 0;
  overflow: hidden;
  border-radius: 9999px;
  border: 1px solid var(--glass-border-subtle);
  background: var(--glass-bg-subtle);
}

.mobile-album-card__cover {
  aspect-ratio: 1 / 1;
  overflow: hidden;
  border-radius: 0.95rem;
  border: 1px solid var(--glass-border-subtle);
  background: var(--glass-bg-subtle);
}

.playlist-cover {
  border: 1px solid var(--glass-border-subtle);
  background: var(--glass-bg-subtle);
}

.playlist-name {
  color: var(--glass-text-primary);
  opacity: 0.8;
}

.playlist-play-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 2.25rem;
  height: 2.25rem;
  border: 0;
  border-radius: 9999px;
  background: rgba(15, 23, 42, 0.82);
  box-shadow: 0 10px 22px rgba(15, 23, 42, 0.28);
}

.mobile-song-row {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  border: 1px solid var(--glass-border-default);
  border-radius: 1rem;
  background: var(--glass-bg-card);
  padding: 0.75rem;
}

.mobile-song-row__cover {
  height: 3rem;
  width: 3rem;
  flex-shrink: 0;
  overflow: hidden;
  border-radius: 0.875rem;
  border: 1px solid var(--glass-border-subtle);
  background: var(--glass-bg-subtle);
}
</style>
