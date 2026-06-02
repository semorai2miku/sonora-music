<script setup lang="ts">
import { banner, topPlaylist, recommendSongs as getRecommendSongs } from '@/api'
import { useI18n } from 'vue-i18n'
import { Swiper, SwiperSlide } from 'swiper/vue'
import { Navigation, Pagination, Autoplay } from 'swiper/modules'
import type SwiperClass from 'swiper'
import 'swiper/css'
import 'swiper/css/navigation'
import 'swiper/css/pagination'
import {
  transformBanners,
  transformPlaylists,
  transformSongs,
  type BannerData,
  type PlaylistData,
  type SongData,
} from '@/utils/transformers'

const { t } = useI18n()

const PLAYLIST_SOURCE_LIMIT = 24
const SONG_SOURCE_LIMIT = 24

const state = reactive({
  banners: [] as BannerData[],
  playlistPool: [] as PlaylistData[],
  songPool: [] as SongData[],
  recommendPlaylists: [] as PlaylistData[],
  recommendSongs: [] as SongData[],
  isLoading: true,
  swiper: null as SwiperClass | null,
  windowWidth: typeof window === 'undefined' ? 1280 : window.innerWidth,
})

const { banners, recommendPlaylists, recommendSongs, isLoading } = toRefs(state)

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
  if (width >= 1600) return 10
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
    const next = state.playlistPool.filter(item => currentIds.has(item.id)).slice(0, playlistDisplayCount.value)
    state.recommendPlaylists =
      next.length === playlistDisplayCount.value
        ? next
        : shuffleList(state.playlistPool).slice(0, playlistDisplayCount.value)
  }

  if (state.songPool.length) {
    const currentIds = new Set(state.recommendSongs.map(item => item.id))
    const next = state.songPool.filter(item => currentIds.has(item.id)).slice(0, songDisplayCount.value)
    state.recommendSongs =
      next.length === songDisplayCount.value
        ? next
        : shuffleList(state.songPool).slice(0, songDisplayCount.value)
  }
}

const handleResize = () => {
  state.windowWidth = window.innerWidth
}

const loadData = async () => {
  state.isLoading = true
  try {
    const [b, p, s] = await Promise.allSettled([
      banner({ type: 0 }),
      topPlaylist({ order: 'hot', limit: PLAYLIST_SOURCE_LIMIT }),
      getRecommendSongs(),
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
    refreshPlaylists()
    refreshSongs()
  } finally {
    state.isLoading = false
  }
}

const onSwiper = (sw: SwiperClass) => {
  state.swiper = sw
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
</script>

<template>
  <div class="flex-1 overflow-hidden">
    <div class="custom-scrollbar h-full overflow-y-auto">
      <HomeSkeleton v-if="isLoading" />
      <div v-else class="space-y-8 p-5 pb-8 lg:p-6">
        <!-- ═══════ Banner 轮播 ═══════ -->
        <section v-if="banners.length" v-scroll-in="{ direction: 'up', duration: 0.8 }" class="relative">
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
                <div class="absolute inset-0 bg-linear-to-t from-black/50 via-black/10 to-transparent" />
                <div class="absolute right-0 bottom-0 left-0 p-5 lg:p-7">
                  <span
                    v-if="item.title"
                    class="mb-2.5 inline-flex items-center gap-1.5 rounded-full border border-white/20 bg-black/20 px-3 py-1 text-[11px] font-medium tracking-wide text-white/90"
                  >
                    <span class="icon-[mdi--fire] h-3 w-3 text-orange-400" />
                    {{ item.title }}
                  </span>
                  <h3 class="line-clamp-2 text-lg font-bold tracking-tight text-white drop-shadow-lg lg:text-xl">
                    {{ item.description }}
                  </h3>
                </div>
              </a>
            </SwiperSlide>
          </Swiper>
          <div class="home-pagination mt-4 flex justify-center gap-1.5"></div>
        </section>

        <!-- ═══════ 推荐歌单 ═══════ -->
        <section v-if="recommendPlaylists.length" v-scroll-in="{ direction: 'up', delay: 0.1 }">
          <div class="mb-5 flex items-center justify-between gap-4">
            <h2 class="text-primary flex items-center gap-2.5 text-lg font-bold tracking-tight lg:text-xl">
              <span class="section-icon-badge">
                <span class="icon-[mdi--playlist-star] h-5 w-5 text-white"></span>
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
          <div
            v-scroll-in="{ stagger: true, staggerDelay: 0.04 }"
            class="playlist-grid grid gap-4"
          >
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
                <p class="line-clamp-2 text-sm font-semibold leading-5 text-[var(--glass-text-primary)]">
                  {{ item.name }}
                </p>
              </div>
            </router-link>
          </div>
        </section>

        <!-- ═══════ 推荐歌曲 ═══════ -->
        <section v-if="recommendSongs.length" v-scroll-in="{ direction: 'up', delay: 0.1 }">
          <div class="mb-5 flex items-center justify-between gap-4">
            <h2 class="text-primary flex items-center gap-2.5 text-lg font-bold tracking-tight lg:text-xl">
              <span class="section-icon-badge">
                <span class="icon-[mdi--music-note-plus] h-5 w-5 text-white"></span>
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
                  <LazyImage :src="song.cover" :alt="song.name" img-class="h-full w-full object-cover" />
                </div>
                <div class="min-w-0 flex-1">
                  <p class="truncate text-sm font-semibold text-[var(--glass-text-primary)] lg:text-[15px]">
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
          v-if="!banners.length && !recommendPlaylists.length && !recommendSongs.length"
          class="rounded-2xl border border-[var(--glass-border-default)] bg-[var(--glass-bg-card)] p-10 text-center"
        >
          <div class="mx-auto mb-4 flex h-12 w-12 items-center justify-center rounded-2xl bg-[var(--glass-interactive-bg)]">
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
  background: var(--glass-interactive-bg);
  border: 1px solid var(--glass-border-default);
  box-shadow: none;
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
