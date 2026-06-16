<script setup lang="ts">
import { clientPublicPlaylists } from '@/api'
import HeroCard from '@/components/Ui/HeroCard.vue'
import { usePlayActions } from '@/composables/usePlayActions'
import { useI18n } from 'vue-i18n'
import { transformPlaylists, type PlaylistData } from '@/utils/transformers'

const { t } = useI18n()
const { playPlaylist: playPlaylistAction } = usePlayActions()

const state = reactive({
  playlists: [] as PlaylistData[],
  isLoading: false,
  pageNum: 1,
  pageSize: 24,
  total: 0,
  hasMore: true,
  playingPlaylistId: null as number | string | null,
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

onMounted(() => loadPlaylists(true))
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
          @play="playPlaylist"
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
  </div>
</template>
