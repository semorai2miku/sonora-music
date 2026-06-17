<script setup lang="ts">
import { banner, clientAlbums, clientArtists, clientRecommendPlaylists, clientSongs } from '@/api'
import { usePlayActions } from '@/composables/usePlayActions'
import { useI18n } from 'vue-i18n'
import { Swiper, SwiperSlide } from 'swiper/vue'
import { Pagination, Autoplay } from 'swiper/modules'
import type SwiperClass from 'swiper'
import 'swiper/css'
import 'swiper/css/navigation'
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

const PLAYLIST_SOURCE_LIMIT = 24
const SONG_POOL_LIMIT = 120
const ARTIST_POOL_LIMIT = 200
const ALBUM_POOL_LIMIT = 200
const MAX_VISIBLE_RECOMMEND_SONGS = 10
const { playPlaylist: playPlaylistAction } = usePlayActions()

const state = reactive({
  banners: [] as BannerData[],
  playlistPool: [] as PlaylistData[],
  songPool: [] as SongData[],
  artistPool: [] as ArtistData[],
  albumPool: [] as AlbumData[],
  featuredArtists: [] as ArtistData[],
  featuredAlbums: [] as AlbumData[],
  recommendPlaylists: [] as PlaylistData[],
  recommendSongs: [] as SongData[],
  playingPlaylistId: null as number | string | null,
  isLoading: true,
  windowWidth: typeof window === 'undefined' ? 1280 : window.innerWidth,
})

const { banners, recommendPlaylists, recommendSongs, featuredArtists, featuredAlbums, isLoading } =
  toRefs(state)
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

const playlistDisplayCount = computed(() => {
  const width = state.windowWidth
  if (width >= 1600) return 8
  if (width >= 1360) return 7
  if (width >= 1120) return 6
  if (width >= 900) return 5
  if (width >= 640) return 4
  return 3
})

const songDisplayCount = computed(() => {
  const width = state.windowWidth
  if (width >= 1600) return MAX_VISIBLE_RECOMMEND_SONGS
  if (width >= 1120) return 8
  if (width >= 768) return 6
  return 4
})

const artistDisplayCount = computed(() => {
  const width = state.windowWidth
  if (width >= 1600) return 6
  if (width >= 1320) return 5
  if (width >= 1080) return 4
  if (width >= 768) return 3
  return 2
})

const albumDisplayCount = computed(() => {
  const width = state.windowWidth
  if (width >= 1600) return 6
  if (width >= 1320) return 5
  if (width >= 1080) return 4
  if (width >= 768) return 3
  return 2
})

const artistGridStyle = computed(() => ({
  gridTemplateColumns: `repeat(${artistDisplayCount.value}, minmax(0, 1fr))`,
}))

const albumGridStyle = computed(() => ({
  gridTemplateColumns: `repeat(${albumDisplayCount.value}, minmax(0, 1fr))`,
}))

const refreshPlaylists = () => {
  state.recommendPlaylists = shuffleList(state.playlistPool).slice(0, playlistDisplayCount.value)
}

const refreshSongs = () => {
  state.recommendSongs = shuffleList(state.songPool).slice(0, songDisplayCount.value)
}

const refreshArtists = () => {
  state.featuredArtists = shuffleList(state.artistPool).slice(0, artistDisplayCount.value)
}

const refreshAlbums = () => {
  state.featuredAlbums = shuffleList(state.albumPool).slice(0, albumDisplayCount.value)
}

const syncResponsiveRecommendations = () => {
  if (state.playlistPool.length) {
    const currentIds = new Set(state.recommendPlaylists.map(item => item.id))
    const next = state.playlistPool
      .filter(item => currentIds.has(item.id))
      .slice(0, playlistDisplayCount.value)
    state.recommendPlaylists =
      next.length === playlistDisplayCount.value
        ? next
        : shuffleList(state.playlistPool).slice(0, playlistDisplayCount.value)
  }

  if (state.songPool.length) {
    const currentIds = new Set(state.recommendSongs.map(item => item.id))
    const next = state.songPool
      .filter(item => currentIds.has(item.id))
      .slice(0, songDisplayCount.value)
    state.recommendSongs =
      next.length === songDisplayCount.value
        ? next
        : shuffleList(state.songPool).slice(0, songDisplayCount.value)
  }

  if (state.artistPool.length) {
    const currentIds = new Set(state.featuredArtists.map(item => item.id))
    const next = state.artistPool
      .filter(item => currentIds.has(item.id))
      .slice(0, artistDisplayCount.value)
    state.featuredArtists =
      next.length === artistDisplayCount.value
        ? next
        : shuffleList(state.artistPool).slice(0, artistDisplayCount.value)
  }

  if (state.albumPool.length) {
    const currentIds = new Set(state.featuredAlbums.map(item => item.id))
    const next = state.albumPool
      .filter(item => currentIds.has(item.id))
      .slice(0, albumDisplayCount.value)
    state.featuredAlbums =
      next.length === albumDisplayCount.value
        ? next
        : shuffleList(state.albumPool).slice(0, albumDisplayCount.value)
  }
}

const handleResize = () => {
  state.windowWidth = window.innerWidth
}

const applyFeaturedArtists = () => {
  refreshArtists()
}

const applyFeaturedAlbums = () => {
  refreshAlbums()
}

const loadData = async () => {
  state.isLoading = true
  try {
    const [b, p, s, artistsRes, albumsRes] = await Promise.allSettled([
      banner({ type: 0 }),
      clientRecommendPlaylists({ limit: PLAYLIST_SOURCE_LIMIT }),
      clientSongs({ limit: SONG_POOL_LIMIT, sort: 'id_desc' }),
      clientArtists({ limit: ARTIST_POOL_LIMIT }),
      clientAlbums({ limit: ALBUM_POOL_LIMIT }),
    ])

    state.banners =
      b.status === 'fulfilled' ? transformBanners(b.value as Record<string, unknown>, 6) : []
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
      artistsRes.status === 'fulfilled'
        ? transformArtists({ artists: artistsRes.value?.data || [] } as Record<string, unknown>)
        : []
    state.albumPool =
      albumsRes.status === 'fulfilled'
        ? transformAlbums({ albums: albumsRes.value?.data || [] } as Record<string, unknown>)
        : []
    refreshPlaylists()
    refreshSongs()
    applyFeaturedArtists()
    applyFeaturedAlbums()
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

const slideBannerPrev = () => {
  bannerSwiper?.slidePrev()
}

const slideBannerNext = () => {
  bannerSwiper?.slideNext()
}

const swiperModules = [Pagination, Autoplay]

onMounted(() => {
  window.addEventListener('resize', handleResize)
  void loadData()
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  bannerSwiper = null
})

watch([playlistDisplayCount, songDisplayCount, artistDisplayCount, albumDisplayCount], () => {
  syncResponsiveRecommendations()
})

onActivated(() => {
  void syncBannerSwiper(true)
})

onDeactivated(() => {
  bannerSwiper?.autoplay?.stop()
})

</script>

<template>
  <div class="flex-1 overflow-hidden">
    <div class="custom-scrollbar h-full overflow-y-auto">
      <HomeSkeleton v-if="isLoading" />
      <div v-else class="space-y-8 p-5 pb-8 lg:p-6">
        <!-- ═══════ Banner 轮播 ═══════ -->
        <section
          v-if="banners.length"
          v-scroll-in="{ direction: 'up', duration: 0.8 }"
          class="relative"
        >
          <Swiper
            @swiper="onSwiper"
            :modules="swiperModules"
            :slides-per-view="1"
            :space-between="16"
            :centered-slides="true"
            :loop="false"
            :rewind="banners.length > 1"
            :autoplay="{ delay: 5000, disableOnInteraction: false }"
            :pagination="{ clickable: true, el: '.home-pagination' }"
            :breakpoints="{
              640: { slidesPerView: 1.15 },
              1024: { slidesPerView: 1.4 },
              1280: { slidesPerView: 1.7 },
            }"
            class="overflow-hidden rounded-2xl"
          >
            <SwiperSlide v-for="(item, idx) in banners" :key="idx">
              <a
                :href="item.url || undefined"
                :target="item.url ? '_blank' : undefined"
                class="group relative block aspect-[18/7] overflow-hidden rounded-2xl border border-[var(--glass-border-default)] bg-[var(--glass-bg-card)]"
              >
                <LazyImage
                  :src="item.coverImgUrl"
                  alt="banner"
                  img-class="h-full w-full object-cover transition-transform duration-700 ease-out group-hover:scale-[1.03]"
                />
                <div
                  class="absolute inset-0 bg-linear-to-t from-black/50 via-black/10 to-transparent"
                />
                <div class="absolute right-0 bottom-0 left-0 p-5 lg:p-7">
                  <span
                    v-if="item.title"
                    class="mb-2.5 inline-flex items-center gap-1.5 rounded-full border border-white/20 bg-black/20 px-3 py-1 text-[11px] font-medium tracking-wide text-white/90"
                  >
                    <span class="icon-[mdi--fire] h-3 w-3 text-orange-400" />
                    {{ item.title }}
                  </span>
                  <h3
                    class="line-clamp-2 text-lg font-bold tracking-tight text-white drop-shadow-lg lg:text-xl"
                  >
                    {{ item.description }}
                  </h3>
                </div>
              </a>
            </SwiperSlide>
          </Swiper>
          <button
            v-if="banners.length > 1"
            type="button"
            class="home-banner-nav home-banner-nav--prev"
            @click="slideBannerPrev"
          >
            <span class="icon-[mdi--chevron-left] h-5 w-5" />
          </button>
          <button
            v-if="banners.length > 1"
            type="button"
            class="home-banner-nav home-banner-nav--next"
            @click="slideBannerNext"
          >
            <span class="icon-[mdi--chevron-right] h-5 w-5" />
          </button>
          <div class="home-pagination mt-4 flex justify-center gap-1.5"></div>
        </section>

        <!-- ═══════ 推荐歌单 ═══════ -->
        <section v-if="recommendPlaylists.length" v-scroll-in="{ direction: 'up', delay: 0.1 }">
          <div class="mb-5 flex items-center justify-between gap-4">
            <h2
              class="text-primary flex items-center gap-2.5 text-lg font-bold tracking-tight lg:text-xl"
            >
              <span class="section-icon-badge">
                <span class="icon-[mdi--playlist-star] h-5 w-5"></span>
              </span>
              {{ t('home.recommendPlaylists') }}
            </h2>
            <div class="flex items-center gap-2">
              <button class="home-action-button" type="button" @click="refreshPlaylists">
                <span class="icon-[mdi--refresh] h-4 w-4" />
                {{ t('common.refresh') }}
              </button>
              <router-link to="/playlists" class="home-action-button">
                <span class="icon-[mdi--dots-horizontal-circle-outline] h-4 w-4" />
                {{ t('common.more') }}
              </router-link>
            </div>
          </div>
          <div v-scroll-in="{ stagger: true, staggerDelay: 0.04 }" class="playlist-grid grid gap-4">
            <div
              v-for="item in recommendPlaylists"
              :key="item.id"
              class="group playlist-card stagger-item cursor-pointer"
              role="link"
              tabindex="0"
              @click="router.push(`/playlist/${item.id}`)"
              @keydown.enter="router.push(`/playlist/${item.id}`)"
            >
              <div class="playlist-card__cover relative">
                <LazyImage
                  :src="item.coverImgUrl"
                  :alt="item.name"
                  img-class="h-full w-full object-cover transition-transform duration-300 group-hover:scale-[1.03]"
                />
                <div
                  class="absolute inset-0 flex items-center justify-center bg-black/0 opacity-0 transition-all group-hover:bg-black/40 group-hover:opacity-100"
                >
                  <button
                    type="button"
                    class="album-play-button"
                    :disabled="state.playingPlaylistId === item.id"
                    @click.stop.prevent="playPlaylist(item.id)"
                  >
                    <span
                      class="icon-[mdi--play] h-6 w-6 text-white"
                    ></span>
                  </button>
                </div>
              </div>
              <div class="playlist-card__body">
                <p
                  class="line-clamp-2 text-sm leading-5 font-semibold text-[var(--glass-text-primary)]"
                >
                  {{ item.name }}
                </p>
                <p
                  v-if="item.creator"
                  class="mt-1 truncate text-xs text-[var(--glass-text-muted)]"
                >
                  {{ item.creator }}
                </p>
              </div>
            </div>
          </div>
        </section>

        <!-- ═══════ 推荐歌曲 ═══════ -->
        <section v-if="recommendSongs.length" v-scroll-in="{ direction: 'up', delay: 0.1 }">
          <div class="mb-5 flex items-center justify-between gap-4">
            <h2
              class="text-primary flex items-center gap-2.5 text-lg font-bold tracking-tight lg:text-xl"
            >
              <span class="section-icon-badge">
                <span class="icon-[mdi--music-note-plus] h-5 w-5"></span>
              </span>
              {{ t('home.recommendSongs') }}
            </h2>
            <div class="flex items-center gap-2">
              <button class="home-action-button" type="button" @click="refreshSongs">
                <span class="icon-[mdi--refresh] h-4 w-4" />
                {{ t('common.refresh') }}
              </button>
              <router-link to="/library" class="home-action-button">
                <span class="icon-[mdi--dots-horizontal-circle-outline] h-4 w-4" />
                {{ t('common.more') }}
              </router-link>
            </div>
          </div>
          <div class="songs-container overflow-hidden rounded-2xl">
            <div
              v-scroll-in="{ stagger: true, staggerDelay: 0.03, distance: 20 }"
              class="grid grid-cols-1 md:grid-cols-2"
            >
              <router-link
                v-for="song in recommendSongs"
                :key="song.id"
                :to="`/song/${song.id}`"
                class="song-row stagger-item song-divider"
              >
                <div class="song-row__cover">
                  <LazyImage
                    :src="song.cover"
                    :alt="song.name"
                    img-class="h-full w-full object-cover"
                  />
                </div>
                <div class="min-w-0 flex-1">
                  <p
                    class="truncate text-sm font-semibold text-[var(--glass-text-primary)] lg:text-[15px]"
                  >
                    {{ song.name }}
                  </p>
                  <p class="mt-1 truncate text-xs text-[var(--glass-text-muted)] lg:text-[13px]">
                    {{ song.artist || t('player.unknownArtist') }}
                  </p>
                </div>
              </router-link>
            </div>
          </div>
        </section>

        <section v-if="featuredArtists.length" v-scroll-in="{ direction: 'up', delay: 0.08 }">
          <div class="mb-5 flex flex-wrap items-center justify-between gap-4">
            <div class="flex min-w-0 flex-1 flex-col gap-3">
              <div class="flex items-center gap-2.5">
                <h2
                  class="text-primary flex items-center gap-2.5 text-lg font-bold tracking-tight lg:text-xl"
                >
                  <span class="section-icon-badge">
                    <span class="icon-[mdi--account-star-outline] h-5 w-5"></span>
                  </span>
                  {{ t('home.followArtists') }}
                </h2>
              </div>
            </div>
            <div class="flex items-center gap-2">
              <button class="home-action-button" type="button" @click="refreshArtists">
                <span class="icon-[mdi--refresh] h-4 w-4" />
                {{ t('common.refresh') }}
              </button>
              <router-link to="/artists" class="home-action-button">
                <span class="icon-[mdi--dots-horizontal-circle-outline] h-4 w-4" />
                {{ t('common.more') }}
              </router-link>
            </div>
          </div>

          <div
            v-scroll-in="{ stagger: true, staggerDelay: 0.04 }"
            class="artist-grid grid gap-4"
            :style="artistGridStyle"
          >
            <router-link
              v-for="artist in featuredArtists"
              :key="artist.id"
              :to="`/artist/${artist.id}`"
              class="artist-focus-card group stagger-item"
            >
              <div class="artist-focus-card__avatar">
                <LazyImage
                  :src="artist.picUrl"
                  :alt="artist.name"
                  img-class="h-full w-full object-cover transition-transform duration-300 group-hover:scale-105"
                />
              </div>
              <div class="min-w-0 flex-1">
                <p class="truncate text-base font-semibold text-[var(--glass-text-primary)]">
                  {{ artist.name }}
                </p>
                <p
                  v-if="artist.alias?.length"
                  class="mt-1 truncate text-xs text-[var(--glass-text-muted)]"
                >
                  {{ artist.alias.join(' / ') }}
                </p>
                <div class="mt-3 flex items-center gap-3 text-xs text-[var(--glass-text-muted)]">
                  <span>{{ artist.musicSize || 0 }} {{ t('artistPage.stats.songs') }}</span>
                  <span>{{ artist.albumSize || 0 }} {{ t('artists.albums') }}</span>
                </div>
              </div>
            </router-link>
          </div>
        </section>

        <section v-if="featuredAlbums.length" v-scroll-in="{ direction: 'up', delay: 0.1 }">
          <div class="mb-5 flex flex-wrap items-center justify-between gap-4">
            <div class="flex min-w-0 flex-1 flex-col gap-3">
              <h2
                class="text-primary flex items-center gap-2.5 text-lg font-bold tracking-tight lg:text-xl"
              >
                <span class="section-icon-badge">
                  <span class="icon-[mdi--album] h-5 w-5"></span>
                </span>
                {{ t('home.latestAlbums') }}
              </h2>
            </div>
            <div class="flex items-center gap-2">
              <button class="home-action-button" type="button" @click="refreshAlbums">
                <span class="icon-[mdi--refresh] h-4 w-4" />
                {{ t('common.refresh') }}
              </button>
              <router-link to="/new-albums" class="home-action-button">
                <span class="icon-[mdi--dots-horizontal-circle-outline] h-4 w-4" />
                {{ t('common.more') }}
              </router-link>
            </div>
          </div>

          <div
            v-scroll-in="{ stagger: true, staggerDelay: 0.04 }"
            class="album-grid grid gap-4"
            :style="albumGridStyle"
          >
            <router-link
              v-for="album in featuredAlbums"
              :key="album.id"
              :to="`/album/${album.id}`"
              class="album-spotlight-card group stagger-item"
            >
              <div class="album-spotlight-card__cover">
                <LazyImage
                  :src="album.picUrl"
                  :alt="album.name"
                  img-class="h-full w-full object-cover transition-transform duration-300 group-hover:scale-[1.04]"
                />
              </div>
              <div class="album-spotlight-card__body">
                <p class="truncate text-sm font-semibold text-[var(--glass-text-primary)]">
                  {{ album.name }}
                </p>
                <p class="mt-1 truncate text-xs text-[var(--glass-text-muted)]">
                  {{ album.artist || t('player.unknownArtist') }}
                </p>
                <p class="mt-2 text-xs text-[var(--glass-text-muted)]">
                  {{ album.publishTime || '-' }}
                </p>
              </div>
            </router-link>
          </div>
        </section>

        <section
          v-if="
            !banners.length &&
            !featuredArtists.length &&
            !featuredAlbums.length &&
            !recommendPlaylists.length &&
            !recommendSongs.length
          "
          class="rounded-2xl border border-[var(--glass-border-default)] bg-[var(--glass-bg-card)] p-10 text-center"
        >
          <div
            class="mx-auto mb-4 flex h-12 w-12 items-center justify-center rounded-2xl bg-[var(--glass-interactive-bg)]"
          >
            <span class="icon-[mdi--music-note-outline] h-6 w-6 text-[var(--sonora-blue)]" />
          </div>
          <p class="text-base font-semibold text-[var(--glass-text-primary)]">首页内容暂时为空</p>
          <p class="mt-2 text-sm text-[var(--glass-text-muted)]">
            请先在管理端启用轮播图，或新增公开歌单与可播放歌曲。
          </p>
        </section>
      </div>
    </div>
  </div>
</template>

<style scoped>
@reference "../style/tailwind.css";

/* ── 轮播图分页指示器 ── */
.home-pagination :deep(.swiper-pagination-bullet) {
  width: 6px;
  height: 6px;
  background: var(--glass-text-primary);
  opacity: 0.2;
  border-radius: 9999px;
  transition: all 0.4s cubic-bezier(0.4, 0, 0.2, 1);
}

.home-pagination :deep(.swiper-pagination-bullet-active) {
  width: 20px;
  opacity: 0.6;
  border-radius: 3px;
}

.home-banner-nav {
  position: absolute;
  top: 50%;
  z-index: 10;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 2.75rem;
  height: 2.75rem;
  border-radius: 9999px;
  border: 1px solid rgba(255, 255, 255, 0.16);
  background: rgba(15, 23, 42, 0.38);
  color: white;
  backdrop-filter: blur(14px);
  transform: translateY(-50%);
  transition:
    transform 0.2s ease,
    background 0.2s ease,
    border-color 0.2s ease;
}

.home-banner-nav:hover {
  background: rgba(15, 23, 42, 0.56);
  border-color: rgba(255, 255, 255, 0.26);
}

.home-banner-nav:active {
  transform: translateY(-50%) scale(0.96);
}

.home-banner-nav--prev {
  left: 1rem;
}

.home-banner-nav--next {
  right: 1rem;
}

/* ── 歌曲列表容器 ── */
.songs-container {
  background: var(--glass-bg-card);
  border: 1px solid var(--glass-border-subtle);
}

.home-action-button {
  display: inline-flex;
  min-width: 0;
  align-items: center;
  gap: 0.375rem;
  border: 1px solid var(--glass-border-default);
  border-radius: 9999px;
  background: var(--glass-bg-card);
  color: var(--glass-text-secondary);
  padding: 0.5rem 0.75rem;
  font-size: 0.8125rem;
  font-weight: 500;
  transition:
    color 0.2s ease,
    border-color 0.2s ease,
    background 0.2s ease;
}

.home-action-button:hover {
  color: var(--glass-text-primary);
  background: var(--glass-interactive-hover-muted);
  border-color: var(--glass-border-strong);
}

.artist-grid,
.album-grid {
  align-items: stretch;
}

.artist-focus-card {
  display: flex;
  align-items: center;
  gap: 1rem;
  min-height: 9.5rem;
  height: 100%;
  min-width: 0;
  border-radius: 1rem;
  border: 1px solid var(--glass-border-subtle);
  background: var(--glass-bg-elevated);
  padding: 1rem;
  transition:
    transform 0.2s ease,
    border-color 0.2s ease,
    background 0.2s ease;
}

.artist-focus-card:hover {
  transform: translateY(-2px);
  border-color: var(--glass-border-strong);
  background: var(--glass-interactive-hover-muted);
}

.artist-focus-card__avatar {
  width: 5.5rem;
  height: 5.5rem;
  flex-shrink: 0;
  overflow: hidden;
  border-radius: 9999px;
  border: 1px solid var(--glass-border-subtle);
  background: var(--glass-bg-subtle);
}

.album-spotlight-card {
  display: block;
  overflow: hidden;
  min-width: 0;
  border-radius: 1rem;
  border: 1px solid var(--glass-border-default);
  background: var(--glass-bg-card);
  transition:
    transform 0.2s ease,
    border-color 0.2s ease,
    background 0.2s ease;
}

.album-spotlight-card:hover {
  transform: translateY(-2px);
  border-color: var(--glass-border-strong);
  background: var(--glass-bg-elevated);
}

.album-spotlight-card__cover {
  aspect-ratio: 1 / 1;
  overflow: hidden;
  border-bottom: 1px solid var(--glass-border-subtle);
  background: var(--glass-bg-subtle);
}

.album-spotlight-card__body {
  padding: 0.9rem 0.95rem 1rem;
}

.playlist-card {
  display: block;
  overflow: hidden;
  border: 1px solid var(--glass-border-default);
  border-radius: 1rem;
  background: var(--glass-bg-card);
  transition:
    border-color 0.2s ease,
    background 0.2s ease,
    transform 0.2s ease;
}

.playlist-card:hover {
  transform: translateY(-2px);
  border-color: var(--glass-border-strong);
  background: var(--glass-bg-elevated);
}

.playlist-card__cover {
  aspect-ratio: 1 / 1;
  overflow: hidden;
  background: var(--glass-bg-subtle);
}

.playlist-card__body {
  padding: 0.875rem 0.875rem 1rem;
}

.playlist-grid {
  grid-template-columns: repeat(auto-fill, minmax(138px, 1fr));
}

.album-play-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 3rem;
  height: 3rem;
  border: 0;
  border-radius: 9999px;
  background: rgba(15, 23, 42, 0.82);
  box-shadow: 0 14px 28px rgba(15, 23, 42, 0.28);
  transition:
    transform 0.2s ease,
    background 0.2s ease;
}

.album-play-button:hover {
  transform: scale(1.06);
  background: rgba(31, 124, 255, 0.92);
}

.section-icon-badge {
  display: inline-flex;
  height: 2rem;
  width: 2rem;
  align-items: center;
  justify-content: center;
  border-radius: 0.75rem;
  background: var(--glass-bg-subtle);
  border: 1px solid var(--glass-border-default);
  box-shadow: none;
}

.section-icon-badge :deep(span) {
  color: var(--glass-text-primary);
}

/* ── 歌曲分隔线 ── */
.song-divider {
  border-bottom: 1px solid var(--glass-border-subtle);
}

.song-row {
  display: flex;
  align-items: center;
  gap: 0.875rem;
  padding: 1rem;
  transition: background 0.2s ease;
}

.song-row:hover {
  background: var(--glass-interactive-hover-muted);
}

.song-row__cover {
  height: 3.25rem;
  width: 3.25rem;
  flex-shrink: 0;
  overflow: hidden;
  border-radius: 0.875rem;
  background: var(--glass-bg-subtle);
  border: 1px solid var(--glass-border-subtle);
}

@media (min-width: 768px) {
  .song-divider:nth-last-child(1),
  .song-divider:nth-last-child(2):nth-child(odd) {
    border-bottom: none;
  }
}

@media (max-width: 767px) {
  .home-banner-nav {
    width: 2.35rem;
    height: 2.35rem;
  }

  .home-banner-nav--prev {
    left: 0.75rem;
  }

  .home-banner-nav--next {
    right: 0.75rem;
  }

  .home-action-button {
    padding: 0.45rem 0.7rem;
    font-size: 0.75rem;
  }
}
</style>
