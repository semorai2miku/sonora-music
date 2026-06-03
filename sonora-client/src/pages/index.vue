<script setup lang="ts">
import { albumNew, artistList, banner, topPlaylist, recommendSongs as getRecommendSongs } from '@/api'
import { useI18n } from 'vue-i18n'
import { Swiper, SwiperSlide } from 'swiper/vue'
import { Navigation, Pagination, Autoplay } from 'swiper/modules'
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

const PLAYLIST_SOURCE_LIMIT = 24
const SONG_SOURCE_LIMIT = 24
const ARTIST_SOURCE_LIMIT = 18
const ALBUM_SOURCE_LIMIT = 12
const MAX_VISIBLE_RECOMMEND_SONGS = 10

type HomeArtistArea = -1 | 7 | 96 | 8 | 16 | 0
type HomeAlbumArea = 'ALL' | 'ZH' | 'EA' | 'JP' | 'KR'

const artistAreaOptions: Array<{ key: HomeArtistArea; labelKey: string }> = [
  { key: -1, labelKey: 'artists.areas.all' },
  { key: 7, labelKey: 'artists.areas.zh' },
  { key: 96, labelKey: 'artists.areas.ea' },
  { key: 8, labelKey: 'artists.areas.jp' },
  { key: 16, labelKey: 'artists.areas.kr' },
  { key: 0, labelKey: 'artists.areas.other' },
]

const albumAreaOptions: Array<{ key: HomeAlbumArea; labelKey: string }> = [
  { key: 'ALL', labelKey: 'newAlbums.areas.all' },
  { key: 'ZH', labelKey: 'newAlbums.areas.zh' },
  { key: 'EA', labelKey: 'newAlbums.areas.ea' },
  { key: 'JP', labelKey: 'newAlbums.areas.jp' },
  { key: 'KR', labelKey: 'newAlbums.areas.kr' },
]

const state = reactive({
  banners: [] as BannerData[],
  playlistPool: [] as PlaylistData[],
  songPool: [] as SongData[],
  featuredArtists: [] as ArtistData[],
  featuredAlbums: [] as AlbumData[],
  recommendPlaylists: [] as PlaylistData[],
  recommendSongs: [] as SongData[],
  isLoading: true,
  artistLoading: false,
  albumLoading: false,
  swiper: null as SwiperClass | null,
  artistSwiper: null as SwiperClass | null,
  windowWidth: typeof window === 'undefined' ? 1280 : window.innerWidth,
  artistArea: -1 as HomeArtistArea,
  albumArea: 'ALL' as HomeAlbumArea,
})

const { banners, recommendPlaylists, recommendSongs, featuredArtists, featuredAlbums, isLoading } =
  toRefs(state)

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

const refreshPlaylists = () => {
  state.recommendPlaylists = shuffleList(state.playlistPool).slice(0, playlistDisplayCount.value)
}

const refreshSongs = () => {
  state.recommendSongs = shuffleList(state.songPool).slice(0, songDisplayCount.value)
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
}

const handleResize = () => {
  state.windowWidth = window.innerWidth
}

const loadFeaturedArtists = async () => {
  try {
    state.artistLoading = true
    const res = await artistList({
      limit: ARTIST_SOURCE_LIMIT,
      offset: 0,
      ...(state.artistArea !== -1 ? { area: state.artistArea } : {}),
    })
    state.featuredArtists = transformArtists(res as Record<string, unknown>, ARTIST_SOURCE_LIMIT)
  } finally {
    state.artistLoading = false
  }
}

const loadFeaturedAlbums = async () => {
  try {
    state.albumLoading = true
    const res = await albumNew({
      area: state.albumArea,
      limit: ALBUM_SOURCE_LIMIT,
      offset: 0,
    })
    state.featuredAlbums = transformAlbums(res as Record<string, unknown>, ALBUM_SOURCE_LIMIT)
  } finally {
    state.albumLoading = false
  }
}

const loadData = async () => {
  state.isLoading = true
  try {
    const [b, p, s, artistsRes, albumsRes] = await Promise.allSettled([
      banner({ type: 0 }),
      topPlaylist({ order: 'hot', limit: PLAYLIST_SOURCE_LIMIT }),
      getRecommendSongs(),
      artistList({ limit: ARTIST_SOURCE_LIMIT, offset: 0 }),
      albumNew({ area: state.albumArea, limit: ALBUM_SOURCE_LIMIT, offset: 0 }),
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
        ? transformSongs(s.value as Record<string, unknown>, SONG_SOURCE_LIMIT)
        : []
    state.featuredArtists =
      artistsRes.status === 'fulfilled'
        ? transformArtists(artistsRes.value as Record<string, unknown>, ARTIST_SOURCE_LIMIT)
        : []
    state.featuredAlbums =
      albumsRes.status === 'fulfilled'
        ? transformAlbums(albumsRes.value as Record<string, unknown>, ALBUM_SOURCE_LIMIT)
        : []
    refreshPlaylists()
    refreshSongs()
  } finally {
    state.isLoading = false
  }
}

const onSwiper = (sw: SwiperClass) => {
  state.swiper = sw
}

const onArtistSwiper = (sw: SwiperClass) => {
  state.artistSwiper = sw
}

const slideArtistPrev = () => {
  state.artistSwiper?.slidePrev()
}

const slideArtistNext = () => {
  state.artistSwiper?.slideNext()
}

const swiperModules = [Navigation, Pagination, Autoplay]

onMounted(() => {
  window.addEventListener('resize', handleResize)
  loadData()
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
})

watch([playlistDisplayCount, songDisplayCount], () => {
  syncResponsiveRecommendations()
})

watch(
  () => state.artistArea,
  () => {
    if (!state.isLoading) {
      loadFeaturedArtists()
    }
  }
)

watch(
  () => state.albumArea,
  () => {
    if (!state.isLoading) {
      loadFeaturedAlbums()
    }
  }
)
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
            :loop="true"
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
          <div class="home-pagination mt-4 flex justify-center gap-1.5"></div>
        </section>

        <section
          v-if="featuredArtists.length || state.artistLoading"
          v-scroll-in="{ direction: 'up', delay: 0.08 }"
        >
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
              <div class="flex flex-wrap gap-2">
                <button
                  v-for="option in artistAreaOptions"
                  :key="option.key"
                  type="button"
                  class="home-filter-chip"
                  :class="{ 'home-filter-chip--active': state.artistArea === option.key }"
                  @click="state.artistArea = option.key"
                >
                  {{ t(option.labelKey) }}
                </button>
              </div>
            </div>
            <div class="flex items-center gap-2">
              <button class="home-nav-button" type="button" @click="slideArtistPrev">
                <span class="icon-[mdi--chevron-left] h-4 w-4" />
              </button>
              <button class="home-nav-button" type="button" @click="slideArtistNext">
                <span class="icon-[mdi--chevron-right] h-4 w-4" />
              </button>
              <router-link to="/artists" class="home-action-button">
                <span class="icon-[mdi--dots-horizontal-circle-outline] h-4 w-4" />
                {{ t('common.more') }}
              </router-link>
            </div>
          </div>

          <div class="artist-swiper-shell overflow-hidden rounded-2xl">
            <Swiper
              v-if="featuredArtists.length"
              @swiper="onArtistSwiper"
              :modules="[Navigation]"
              :slides-per-view="1.15"
              :space-between="16"
              :breakpoints="{
                640: { slidesPerView: 2.1 },
                900: { slidesPerView: 3.1 },
                1200: { slidesPerView: 4.1 },
                1500: { slidesPerView: 5.1 },
              }"
            >
              <SwiperSlide v-for="artist in featuredArtists" :key="artist.id">
                <router-link :to="`/artist/${artist.id}`" class="artist-focus-card group">
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
              </SwiperSlide>
            </Swiper>
            <div
              v-else
              class="flex min-h-[170px] items-center justify-center text-sm text-[var(--glass-text-muted)]"
            >
              {{ t('common.noData') }}
            </div>
          </div>
        </section>

        <section
          v-if="featuredAlbums.length || state.albumLoading"
          v-scroll-in="{ direction: 'up', delay: 0.1 }"
        >
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
              <div class="flex flex-wrap gap-2">
                <button
                  v-for="option in albumAreaOptions"
                  :key="option.key"
                  type="button"
                  class="home-filter-chip"
                  :class="{ 'home-filter-chip--active': state.albumArea === option.key }"
                  @click="state.albumArea = option.key"
                >
                  {{ t(option.labelKey) }}
                </button>
              </div>
            </div>
            <router-link to="/new-albums" class="home-action-button">
              <span class="icon-[mdi--dots-horizontal-circle-outline] h-4 w-4" />
              {{ t('common.more') }}
            </router-link>
          </div>

          <div class="album-grid grid gap-4">
            <router-link
              v-for="album in featuredAlbums"
              :key="album.id"
              :to="`/album/${album.id}`"
              class="album-spotlight-card group"
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
            <router-link
              v-for="item in recommendPlaylists"
              :key="item.id"
              :to="`/playlist/${item.id}`"
              class="group playlist-card stagger-item"
            >
              <div class="playlist-card__cover">
                <LazyImage
                  :src="item.coverImgUrl"
                  :alt="item.name"
                  img-class="h-full w-full object-cover transition-transform duration-300 group-hover:scale-[1.03]"
                />
              </div>
              <div class="playlist-card__body">
                <p
                  class="line-clamp-2 text-sm leading-5 font-semibold text-[var(--glass-text-primary)]"
                >
                  {{ item.name }}
                </p>
              </div>
            </router-link>
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

.home-nav-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 2.25rem;
  height: 2.25rem;
  border-radius: 9999px;
  border: 1px solid var(--glass-border-default);
  background: var(--glass-bg-card);
  color: var(--glass-text-secondary);
  transition:
    color 0.2s ease,
    border-color 0.2s ease,
    background 0.2s ease,
    transform 0.2s ease;
}

.home-nav-button:hover {
  color: var(--glass-text-primary);
  background: var(--glass-interactive-hover-muted);
  border-color: var(--glass-border-strong);
  transform: translateY(-1px);
}

.home-filter-chip {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 0.45rem 0.85rem;
  border-radius: 9999px;
  border: 1px solid var(--glass-border-default);
  background: var(--glass-bg-card);
  color: var(--glass-text-secondary);
  font-size: 0.8125rem;
  font-weight: 500;
  transition:
    color 0.2s ease,
    background 0.2s ease,
    border-color 0.2s ease;
}

.home-filter-chip:hover,
.home-filter-chip--active {
  color: var(--glass-text-primary);
  border-color: rgba(31, 124, 255, 0.3);
  background: rgba(31, 124, 255, 0.12);
}

.artist-swiper-shell {
  border: 1px solid var(--glass-border-default);
  background: var(--glass-bg-card);
  padding: 1rem;
}

.artist-focus-card {
  display: flex;
  align-items: center;
  gap: 1rem;
  min-height: 9.5rem;
  height: 100%;
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

.album-grid {
  grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
}

.album-spotlight-card {
  display: block;
  overflow: hidden;
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
  .home-action-button {
    padding: 0.45rem 0.7rem;
    font-size: 0.75rem;
  }
}
</style>
