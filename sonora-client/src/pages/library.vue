<script setup lang="ts">
import { topSong } from '@/api'
import SongList from '@/components/SongList.vue'
import Button from '@/components/Ui/Button.vue'
import { useAudio } from '@/composables/useAudio'
import type { Song as StoreSong } from '@/stores/interface'
import { transformTopSongs, type SongData } from '@/utils/transformers'

const state = reactive({
  songs: [] as SongData[],
  isLoading: false,
})

const { setPlaylist, play } = useAudio()

const loadSongs = async () => {
  state.isLoading = true
  try {
    const res = await topSong({ type: 0 })
    state.songs = transformTopSongs(res as Record<string, unknown>)
  } finally {
    state.isLoading = false
  }
}

const playAll = () => {
  if (!state.songs.length) return
  const list = state.songs as unknown as StoreSong[]
  setPlaylist(list, 0)
  play(list[0], 0)
}

onMounted(loadSongs)
</script>

<template>
  <div class="flex flex-1 flex-col overflow-hidden p-4">
    <section class="glass-card mb-4 shrink-0 p-5">
      <div class="flex flex-wrap items-center justify-between gap-4">
        <div>
          <h1 class="text-primary text-2xl font-bold">{{ $t('library.title') }}</h1>
          <p class="text-primary/60 mt-2 text-sm">{{ $t('library.subtitle') }}</p>
        </div>
        <Button
          variant="solid"
          size="md"
          rounded="full"
          class="px-6"
          :disabled="!state.songs.length"
          @click="playAll"
        >
          <span class="icon-[mdi--play] mr-2 h-4.5 w-4.5"></span>
          {{ $t('actions.playAll') }}
        </Button>
      </div>
    </section>

    <div class="min-h-0 flex-1 overflow-hidden">
      <SongList
        :songs="state.songs as unknown as StoreSong[]"
        :loading="state.isLoading"
        :show-header="true"
        :show-controls="true"
        :empty-message="$t('library.empty')"
      />
    </div>
  </div>
</template>
