<script setup lang="ts">
import { clientPublicPlaylists, collectPlaylist, uncollectPlaylist } from '@/api'
import LoginDialog from '@/components/Auth/LoginDialog.vue'
import HeroCard from '@/components/Ui/HeroCard.vue'
import { usePlayActions } from '@/composables/usePlayActions'
import { useUserStore } from '@/stores/modules/user'
import { useI18n } from 'vue-i18n'
import { transformPlaylists, type PlaylistData } from '@/utils/transformers'

const { t } = useI18n()
const { playPlaylist: playPlaylistAction } = usePlayActions()
const userStore = useUserStore()

const state = reactive({
  playlists: [] as PlaylistData[],
  isLoading: false,
  pageNum: 1,
  pageSize: 24,
  total: 0,
  hasMore: true,
  playingPlaylistId: null as number | string | null,
  collectingPlaylistId: null as number | string | null,
  showLogin: false,
})

const loadPlaylists = async (reset = false) => {
  if (reset) {
    state.pageNum = 1
    state.playlists = []
    state.total = 0
    state.hasMore = true
  }
  if (!state.hasMore) return

  state.isLoading = true
  try {
    const res = await clientPublicPlaylists({
      pageNum: state.pageNum,
      pageSize: state.pageSize,
    })
    const rows = transformPlaylists(
      res as Record<string, unknown>,
      undefined,
      String(t('home.playlistFallback'))
    )
    state.playlists = reset ? rows : [...state.playlists, ...rows]
    state.total = Number(res?.data?.total || 0)
    state.hasMore = state.playlists.length < state.total
    if (rows.length) {
      state.pageNum += 1
    }
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

const ensureAuthenticated = () => {
  if (userStore.isAuthenticated) return true
  if (userStore.isLoggedIn) userStore.logout()
  state.showLogin = true
  return false
}

const isOwnedPlaylist = (playlist: PlaylistData) => {
  if (!userStore.profile?.userId || !playlist.creatorId) return false
  return String(playlist.creatorId) === String(userStore.profile.userId)
}

const toggleCollect = async (playlist: PlaylistData) => {
  if (!playlist.id || state.collectingPlaylistId === playlist.id || isOwnedPlaylist(playlist)) return
  if (!ensureAuthenticated()) return

  state.collectingPlaylistId = playlist.id
  try {
    const res = playlist.subscribed
      ? await uncollectPlaylist(playlist.id)
      : await collectPlaylist(playlist.id)
    if (res?.code !== 200) {
      throw new Error(res?.message || t('playlist.messages.actionFailed'))
    }
    playlist.subscribed = !playlist.subscribed
    window.dispatchEvent(new CustomEvent('sonora:playlists-updated'))
  } finally {
    state.collectingPlaylistId = null
  }
}

onMounted(() => {
  loadPlaylists(true)
})

onActivated(() => {
  void loadPlaylists(true)
})
</script>

<template>
  <div class="flex flex-1 flex-col overflow-hidden p-4">
    <section class="glass-card mb-4 shrink-0 p-5">
      <div class="flex flex-wrap items-center justify-between gap-4">
        <div>
          <h1 class="text-primary text-2xl font-bold">{{ $t('playlists.title') }}</h1>
          <p class="text-primary/60 mt-2 text-sm">{{ $t('playlists.subtitle') }}</p>
        </div>
      </div>
    </section>

    <div class="custom-scrollbar min-h-0 flex-1 overflow-y-auto">
      <PageSkeleton v-if="state.isLoading && !state.playlists.length" :sections="['grid']" />
      <div
        v-else
        class="grid grid-cols-2 gap-4 pr-1 sm:grid-cols-3 lg:grid-cols-4 xl:grid-cols-5 2xl:grid-cols-6"
      >
        <HeroCard
          v-for="item in state.playlists"
          :key="item.id"
          :id="item.id"
          :cover-url="item.coverImgUrl"
          :title="item.name"
          :play-count="item.playCount"
          :track-count="item.trackCount"
          :to="`/playlist/${item.id}`"
          :enable-tilt="false"
          :playable="true"
          :collectible="!isOwnedPlaylist(item)"
          :collected="Boolean(item.subscribed)"
          :collect-disabled="state.collectingPlaylistId === item.id"
          @play="playPlaylist"
          @collect="toggleCollect(item)"
        />
      </div>

      <div v-if="state.hasMore && state.playlists.length" class="mt-6 pb-2 text-center">
        <button
          :disabled="state.isLoading"
          class="glass-button px-6 py-2 text-sm"
          @click="loadPlaylists(false)"
        >
          <span v-if="state.isLoading" class="icon-[mdi--loading] mr-2 h-4 w-4 animate-spin" />
          {{ state.isLoading ? t('common.loading') : t('playlists.loadMore') }}
        </button>
      </div>
    </div>
    <LoginDialog v-if="state.showLogin" @close="state.showLogin = false" />
  </div>
</template>
