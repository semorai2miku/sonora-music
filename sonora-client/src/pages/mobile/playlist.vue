<script setup lang="ts">
import {
  clientPlaylistDetail,
  collectPlaylist,
  myPlaylistDetail,
  myPlaylists,
  playlistDetail,
  playlistTrackAll,
  uncollectPlaylist,
  updateMyPlaylist,
} from '@/api'
import Button from '@/components/Ui/Button.vue'
import LazyImage from '@/components/Ui/LazyImage.vue'
import { usePlayActions } from '@/composables/usePlayActions'
import { useUserStore } from '@/stores/modules/user'
import { withImageParam } from '@/utils/media'
import { formatCount } from '@/utils/time'
import {
  transformClientPlaylistDetail,
  transformPlaylistDetail,
  transformSongs,
  type SongData,
} from '@/utils/transformers'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
const userStore = useUserStore()
const route = useRoute()
const playlistId = computed(() => Number(route.params.id))
const { playAll: playAllAction, shufflePlay: shufflePlayAction } = usePlayActions()
const PLAYLIST_SONG_PAGE_SIZE = 60

type PlaylistInfo = {
  name: string
  description: string
  creator: string
  creatorAvatar: string
  createTime: string
  songCount: number
  playCount: number | string
  likes: number | string
  category: string
  coverImgUrl: string
}

const state = reactive({
  info: {} as PlaylistInfo,
  songs: [] as SongData[],
  songPage: 1,
  loading: true,
  collected: false,
  isLocalPlaylist: false,
  showFullDesc: false,
  myPlaylist: null as null | { id: number | string; type?: string; status?: number; subscribed?: boolean },
  actionLoading: false,
})

const isSubscribedPlaylist = computed(() => Boolean(state.myPlaylist?.subscribed))
const isOwnedNormalPlaylist = computed(
  () => state.myPlaylist?.type === 'normal' && !isSubscribedPlaylist.value
)
const isPublishedPlaylist = computed(() => Number(state.myPlaylist?.status || 0) === 1)
const showCoverCollectButton = computed(
  () => state.isLocalPlaylist && !isOwnedNormalPlaylist.value && state.myPlaylist?.type !== 'liked'
)
const showUtilityActionButton = computed(
  () => (state.isLocalPlaylist && state.myPlaylist?.type !== 'liked') || isOwnedNormalPlaylist.value
)
const visibleSongs = computed(() => state.songs.slice(0, state.songPage * PLAYLIST_SONG_PAGE_SIZE))
const hasMoreSongs = computed(() => visibleSongs.value.length < state.songs.length)
const currentCategory = computed(() => {
  if (state.myPlaylist?.type === 'liked' && !isSubscribedPlaylist.value) {
    return t('playlist.likedBadge')
  }
  if (isOwnedNormalPlaylist.value) {
    return isPublishedPlaylist.value ? t('playlist.visibility.public') : t('playlist.visibility.private')
  }
  return state.info.category || t('home.playlistFallback')
})

const loadMyPlaylistMeta = async (id: number) => {
  if (!userStore.isLoggedIn) {
    state.myPlaylist = null
    state.collected = false
    return null
  }
  try {
    const res = await myPlaylists()
    const found = (res?.data || []).find(item => Number(item.id) === id) || null
    state.myPlaylist = found
    state.collected = Boolean(found?.subscribed)
    return found && !found.subscribed ? found : null
  } catch {
    state.myPlaylist = null
    state.collected = false
    return null
  }
}

const loadPublicPlaylist = async (id: number) => {
  state.isLocalPlaylist = false
  try {
    const res = await clientPlaylistDetail(id)
    if (res?.code === 200 && res.data) {
      const detail = transformClientPlaylistDetail(
        res as Record<string, unknown>,
        t('home.playlistFallback')
      )
      if (detail) {
        state.info = {
          ...detail,
          playCount: formatCount(Number(detail.playCount || 0)),
          likes: formatCount(Number(detail.likes || 0)),
        }
      }
      state.isLocalPlaylist = true
      state.collected = Boolean(res.data.subscribed || state.myPlaylist?.subscribed)
      state.songPage = 1
      state.songs = transformSongs(res as Record<string, unknown>, 300)
      return
    }
  } catch {}

  const [detailRes, tracksRes] = await Promise.all([
    playlistDetail({ id }),
    playlistTrackAll({ id, limit: 100 }),
  ])

  const detail = transformPlaylistDetail(detailRes as Record<string, unknown>, t('home.playlistFallback'))
  if (detail) {
    state.info = {
      ...detail,
      playCount: formatCount(detail.playCount as number),
      likes: formatCount(detail.likes as number),
    }
  }

  state.songPage = 1
  state.songs = transformSongs(tracksRes as Record<string, unknown>)
}

const loadOwnedPlaylist = async (id: number) => {
  state.isLocalPlaylist = true
  const res = await myPlaylistDetail(id)
  const detail = transformClientPlaylistDetail(
    res as Record<string, unknown>,
    currentCategory.value || t('home.playlistFallback')
  )
  if (detail) {
    state.info = {
      ...detail,
      playCount: formatCount(Number(detail.playCount || 0)),
      likes: formatCount(Number(detail.likes || 0)),
    }
  }

  state.songPage = 1
  state.songs = transformSongs(res as Record<string, unknown>, 300)
}

const load = async (id: number) => {
  state.loading = true
  try {
    const mine = await loadMyPlaylistMeta(id)
    if (mine) {
      await loadOwnedPlaylist(id)
    } else {
      await loadPublicPlaylist(id)
    }
  } finally {
    state.loading = false
  }
}

const playAll = () => playAllAction(state.songs)
const shufflePlay = () => shufflePlayAction(state.songs)
const loadMoreSongs = () => {
  if (!hasMoreSongs.value) return
  state.songPage += 1
}

const toggleCollect = async () => {
  if (!userStore.isAuthenticated) {
    if (userStore.isLoggedIn) userStore.logout()
    return
  }
  if (!state.isLocalPlaylist || isOwnedNormalPlaylist.value || state.myPlaylist?.type === 'liked') {
    return
  }
  state.actionLoading = true
  try {
    const res = state.collected
      ? await uncollectPlaylist(playlistId.value)
      : await collectPlaylist(playlistId.value)
    if (res?.code !== 200 || !res.data) return
    state.collected = !state.collected
    state.info.likes = formatCount(Number(res.data.collectCount || 0))
    state.myPlaylist = state.collected ? { ...res.data, subscribed: true } : null
    window.dispatchEvent(new CustomEvent('sonora:playlists-updated'))
  } finally {
    state.actionLoading = false
  }
}

const togglePublish = async () => {
  if (!isOwnedNormalPlaylist.value) return
  state.actionLoading = true
  try {
    const res = await updateMyPlaylist(playlistId.value, {
      status: isPublishedPlaylist.value ? 0 : 1,
    })
    if (res?.code === 200 && res.data) {
      state.myPlaylist = res.data
      state.info.category =
        Number(res.data.status || 0) === 1
          ? t('playlist.visibility.public')
          : t('playlist.visibility.private')
      window.dispatchEvent(new CustomEvent('sonora:playlists-updated'))
    }
  } finally {
    state.actionLoading = false
  }
}

const handleUtilityAction = () => {
  if (isOwnedNormalPlaylist.value) {
    void togglePublish()
    return
  }
  void toggleCollect()
}

watch(
  playlistId,
  idNum => {
    if (!Number.isNaN(idNum) && idNum > 0) {
      state.loading = true
      state.collected = false
      state.isLocalPlaylist = false
      state.songPage = 1
      state.showFullDesc = false
      void load(idNum)
    }
  },
  { immediate: true }
)

watch(() => userStore.isLoggedIn, () => void load(playlistId.value))
</script>

<template>
  <div class="playlist-page flex flex-1 flex-col overflow-hidden">
    <div v-if="state.loading" class="flex-1 overflow-auto px-4 py-6">
      <PageSkeleton :sections="['hero', 'list']" :list-count="8" />
    </div>
    <template v-else>
      <div class="header-section relative">
        <div class="header-bg absolute inset-0 overflow-hidden">
          <LazyImage
            v-if="state.info.coverImgUrl"
            :src="withImageParam(state.info.coverImgUrl, '400y400')"
            :alt="$t('components.songList.coverAlt')"
            imgClass="h-full w-full object-cover scale-110"
          />
          <div class="header-overlay absolute inset-0"></div>
        </div>

        <div class="header-content relative z-10 px-4 pt-4 pb-6">
          <div class="mb-3">
            <span class="rounded-full bg-black/20 px-3 py-1 text-[11px] text-accent/90">
              {{ currentCategory }}
            </span>
          </div>

          <div class="flex gap-4">
            <div class="cover-wrapper relative shrink-0">
              <LazyImage
                v-if="state.info.coverImgUrl"
                :src="withImageParam(state.info.coverImgUrl, '300y300')"
                :alt="$t('components.songList.coverAlt')"
                imgClass="cover-image h-32 w-32 rounded-2xl object-cover"
              />
              <button
                type="button"
                class="absolute inset-0 flex items-center justify-center rounded-2xl bg-black/10"
                :disabled="state.actionLoading || !state.songs.length"
                @click.stop.prevent="playAll"
              >
                <span
                  class="flex h-12 w-12 items-center justify-center rounded-full bg-black/45 text-white shadow-lg backdrop-blur-sm"
                  :class="{ 'opacity-70': state.actionLoading || !state.songs.length }"
                >
                  <span class="icon-[mdi--play] h-6 w-6"></span>
                </span>
              </button>
              <button
                v-if="showCoverCollectButton"
                type="button"
                class="absolute top-2 right-2 flex h-9 w-9 items-center justify-center rounded-full border border-white/10 bg-black/45 text-white shadow-lg backdrop-blur-sm"
                :disabled="state.actionLoading"
                @click.stop="toggleCollect"
              >
                <span
                  :class="state.collected ? 'icon-[mdi--heart]' : 'icon-[mdi--heart-outline]'"
                  class="h-4.5 w-4.5"
                ></span>
              </button>
              <div
                class="play-count-badge absolute -right-1 -bottom-1 flex items-center gap-1 rounded-full px-2 py-0.5 text-[10px] font-medium text-accent"
              >
                <span class="icon-[mdi--play] h-3 w-3"></span>
                {{ state.info.playCount }}
              </div>
            </div>

            <div class="flex min-w-0 flex-1 flex-col justify-between py-1">
              <div>
                <h1 class="mb-2 line-clamp-2 text-lg leading-tight font-bold text-accent">
                  {{ state.info.name }}
                </h1>
                <div class="creator-info flex items-center gap-2">
                  <img
                    v-if="state.info.creatorAvatar"
                    :src="withImageParam(state.info.creatorAvatar, '50y50')"
                    alt=""
                    class="h-5 w-5 rounded-full"
                  />
                  <span class="text-xs text-accent/70">{{ state.info.creator }}</span>
                </div>
              </div>
              <div class="mt-2 flex items-center gap-3 text-[11px] text-accent/60">
                <span class="flex items-center gap-1">
                  <span class="icon-[mdi--music-note] h-3.5 w-3.5"></span>
                  {{ $t('commonUnits.songsShort', { count: state.info.songCount }) }}
                </span>
                <span class="flex items-center gap-1">
                  <span class="icon-[mdi--heart] h-3.5 w-3.5"></span>
                  {{ state.info.likes }}
                </span>
              </div>
            </div>
          </div>

          <div
            v-if="state.info.description"
            class="desc-section mt-4"
            @click="state.showFullDesc = !state.showFullDesc"
          >
            <p
              class="text-xs leading-relaxed text-accent/60"
              :class="state.showFullDesc ? '' : 'line-clamp-2'"
            >
              {{ state.info.description }}
            </p>
            <span class="mt-1 inline-flex items-center text-[10px] text-accent/60">
              {{ state.showFullDesc ? $t('common.collapse') : $t('common.expand') }}
              <span
                :class="state.showFullDesc ? 'icon-[mdi--chevron-up]' : 'icon-[mdi--chevron-down]'"
                class="h-3 w-3"
              ></span>
            </span>
          </div>
        </div>
      </div>

      <div class="action-bar flex items-center gap-3 px-4 py-3">
        <Button
          variant="gradient"
          size="md"
          rounded="full"
          class="play-all-btn flex flex-1 items-center justify-center gap-2 py-2.5 text-sm font-medium"
          icon="icon-[mdi--play-circle]"
          icon-class="h-5 w-5"
          @click="playAll"
        >
          {{ t('actions.playAll') }}
        </Button>
        <Button
          variant="glass"
          size="md"
          rounded="full"
          class="shuffle-btn flex flex-1 items-center justify-center gap-2 py-2.5 text-sm font-medium"
          icon="icon-[mdi--shuffle-variant]"
          icon-class="h-5 w-5"
          @click="shufflePlay"
        >
          {{ t('actions.shufflePlay') }}
        </Button>
        <Button
          v-if="showUtilityActionButton"
          variant="glass"
          size="icon-lg"
          rounded="full"
          class="collect-btn"
          :class="
            isOwnedNormalPlaylist
              ? isPublishedPlaylist
                ? 'collected'
                : ''
              : state.collected
                ? 'collected'
                : ''
          "
          :icon="
            isOwnedNormalPlaylist
              ? isPublishedPlaylist
                ? 'icon-[mdi--earth-off]'
                : 'icon-[mdi--earth]'
              : state.collected
                ? 'icon-[mdi--heart]'
                : 'icon-[mdi--heart-outline]'
          "
          icon-class="h-5 w-5"
          :disabled="state.actionLoading"
          @click="handleUtilityAction"
        />
      </div>

      <div class="flex-1 overflow-auto px-4 pb-6">
        <section>
          <MobileSongList :songs="visibleSongs" :show-index="true" />
          <div v-if="hasMoreSongs" class="mt-4 text-center">
            <button class="glass-button rounded-full px-5 py-2 text-sm" @click="loadMoreSongs">
              {{ t('playlists.loadMore') }}
            </button>
          </div>
        </section>
      </div>
    </template>
  </div>
</template>

<style scoped>
.header-bg::after {
  content: '';
  position: absolute;
  inset: 0;
  backdrop-filter: blur(40px) saturate(1.5);
  -webkit-backdrop-filter: blur(40px) saturate(1.5);
}

.header-overlay {
  background: linear-gradient(
    to bottom,
    rgba(0, 0, 0, 0.3) 0%,
    rgba(0, 0, 0, 0.5) 50%,
    var(--glass-bg-solid) 100%
  );
}

.cover-wrapper {
  filter: drop-shadow(0 8px 24px rgba(0, 0, 0, 0.4));
}

.cover-image {
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.3);
}

.play-count-badge {
  background: linear-gradient(135deg, rgba(0, 0, 0, 0.6), rgba(0, 0, 0, 0.4));
  backdrop-filter: blur(8px);
  -webkit-backdrop-filter: blur(8px);
}

.play-all-btn {
  background: linear-gradient(135deg, #1f7cff, #4da3ff);
  box-shadow: 0 4px 16px rgba(31, 124, 255, 0.35);
  transition: all 0.3s ease;
}

.play-all-btn:active {
  transform: scale(0.97);
  box-shadow: 0 2px 8px rgba(31, 124, 255, 0.3);
}

.shuffle-btn,
.collect-btn {
  background: var(--glass-card-bg);
  color: var(--glass-text-primary);
  border: 1px solid var(--glass-border-default);
  transition: all 0.3s ease;
}

.shuffle-btn:active,
.collect-btn:active {
  transform: scale(0.97);
}

.collect-btn.collected {
  background: linear-gradient(135deg, rgba(31, 124, 255, 0.2), rgba(77, 163, 255, 0.2));
  border-color: rgba(31, 124, 255, 0.3);
  color: #1f7cff;
}

.desc-section {
  cursor: pointer;
}
</style>
