<script setup lang="ts">
import { banner, topPlaylist, recommendSongs } from '@/api'
import { useI18n } from 'vue-i18n'
import { Swiper, SwiperSlide } from 'swiper/vue'
import { Autoplay, Pagination } from 'swiper/modules'
import 'swiper/css'
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

interface HomeState {
  banners: BannerData[]
  playlistPool: PlaylistData[]
  songPool: SongData[]
  playlists: PlaylistData[]
  songs: SongData[]
  isLoading: boolean
}

const state = reactive<HomeState>({
  banners: [],
  playlistPool: [],
  songPool: [],
  playlists: [],
  songs: [],
  isLoading: true,
})

const { banners, playlists, songs, isLoading } = toRefs(state)

const shuffleList = <T,>(items: T[]) => {
  const list = [...items]
  for (let i = list.length - 1; i > 0; i -= 1) {
    const j = Math.floor(Math.random() * (i + 1))
    ;[list[i], list[j]] = [list[j], list[i]]
  }
  return list
}

const refreshPlaylists = () => {
  state.playlists = shuffleList(state.playlistPool).slice(0, 6)
}

const refreshSongs = () => {
  state.songs = shuffleList(state.songPool).slice(0, 6)
}

const loadHomeData = async () => {
  state.isLoading = true
  try {
    const [b, p, s] = await Promise.allSettled([
      banner({ type: 2 }),
      topPlaylist({ order: 'hot', limit: 12 }),
      recommendSongs(),
    ])

    state.banners =
      b.status === 'fulfilled' ? transformBanners(b.value as Record<string, unknown>, 5) : []
    state.playlistPool =
      p.status === 'fulfilled'
        ? transformPlaylists(p.value as Record<string, unknown>, 12, t('home.playlistFallback'))
        : []
    state.songPool =
      s.status === 'fulfilled' ? transformSongs(s.value as Record<string, unknown>, 12) : []
    refreshPlaylists()
    refreshSongs()
  } finally {
    state.isLoading = false
  }
}

onMounted(loadHomeData)

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
            :modules="swiperModules"
            :slides-per-view="1"
            :space-between="12"
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
                <span class="icon-[mdi--playlist-star] text-primary h-4 w-4"></span>
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
            <router-link
              v-for="pl in playlists"
              :key="pl.id"
              :to="`/playlist/${pl.id}`"
              class="group"
            >
              <div class="playlist-cover relative mb-2 aspect-square overflow-hidden rounded-xl">
                <LazyImage
                  :src="pl.coverImgUrl"
                  :alt="pl.name"
                  imgClass="h-full w-full object-cover transition-transform duration-300 group-active:scale-105"
                />
              </div>
              <p class="playlist-name line-clamp-2 text-xs leading-tight">{{ pl.name }}</p>
            </router-link>
          </div>
        </section>

        <section class="mb-6 px-4">
          <div class="mb-4 flex items-center justify-between">
            <h2 class="section-title">
              <span class="mobile-section-icon">
                <span class="icon-[mdi--music-note-plus] text-primary h-4 w-4"></span>
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
                <LazyImage :src="song.cover" :alt="song.name" imgClass="h-full w-full object-cover" />
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
          v-if="!banners.length && !playlists.length && !songs.length"
          class="mx-4 rounded-2xl border border-[var(--glass-border-default)] bg-[var(--glass-bg-card)] px-5 py-8 text-center"
        >
          <div class="mx-auto mb-3 flex h-11 w-11 items-center justify-center rounded-2xl bg-[var(--glass-interactive-bg)]">
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
  background: var(--glass-interactive-bg);
  border: 1px solid var(--glass-border-default);
  box-shadow: none;
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

.playlist-cover {
  border: 1px solid var(--glass-border-subtle);
  background: var(--glass-bg-subtle);
}

.playlist-name {
  color: var(--glass-text-primary);
  opacity: 0.8;
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
