<script setup lang="ts">
import { useAudioStore } from '@/stores/modules/audio'
import { storeToRefs } from 'pinia'
import { useAudio } from '@/composables/useAudio'
import type { Song as StoreSong } from '@/stores/interface'
import MobileSongList from '@/components/Mobile/MobileSongList.vue'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
const { setPlaylist, play } = useAudio()

const audioStore = useAudioStore()
const { audio } = storeToRefs(audioStore)
const recentSongs = computed<StoreSong[]>(() => audio.value?.playHistory || [])

const playAllRecent = () => {
  if (!recentSongs.value.length) return
  const list = recentSongs.value
  setPlaylist(list, 0)
  play(list[0], 0)
}
</script>

<template>
  <div class="flex flex-1 flex-col overflow-hidden">
    <div class="shrink-0 px-4 pt-1 pb-3">
      <div class="glass-card p-4">
        <div class="mb-3">
          <h1 class="text-primary text-xl font-semibold">{{ t('recent.title') }}</h1>
          <p class="text-primary/55 mt-2 text-sm">{{ t('recent.subtitle') }}</p>
        </div>
        <div class="flex items-center justify-between gap-3">
          <div class="text-primary/55 flex items-center gap-2 text-xs">
            <span class="icon-[mdi--history] h-4 w-4"></span>
            <span>{{ t('commonUnits.songsShort', { count: recentSongs.length }) }}</span>
          </div>
          <button
            class="play-all-button flex items-center gap-1.5 rounded-full px-4 py-2 text-xs font-medium text-primary shadow-lg transition-all duration-200 active:scale-95"
            :disabled="!recentSongs.length"
            @click="playAllRecent"
          >
            <span class="icon-[mdi--play] h-4 w-4" />
            {{ t('actions.playAll') }}
          </button>
        </div>
      </div>
    </div>

    <div class="flex-1 overflow-auto px-4 pb-6">
      <div
        v-if="!recentSongs.length"
        class="flex h-full flex-col items-center justify-center py-16 text-center"
      >
        <div class="empty-icon mb-4 flex h-24 w-24 items-center justify-center rounded-full">
          <span class="icon-[mdi--history] empty-icon-inner h-12 w-12" />
        </div>
        <p class="empty-title mb-2 text-sm font-medium">{{ t('recent.empty') }}</p>
      </div>
      <MobileSongList
        v-else
        :songs="recentSongs"
        variant="compact"
        :show-index="true"
        context="generic"
      />
    </div>
  </div>
</template>

<style scoped>
.play-all-button {
  background: linear-gradient(to right, #1f7cff, #4da3ff);
  box-shadow: 0 4px 15px rgba(31, 124, 255, 0.3);
}

.play-all-button:active {
  box-shadow: 0 2px 8px rgba(31, 124, 255, 0.3);
}

.empty-icon {
  background: linear-gradient(to bottom right, rgba(31, 124, 255, 0.15), rgba(77, 163, 255, 0.15));
}

:root.dark .empty-icon,
html.dark .empty-icon {
  background: linear-gradient(to bottom right, rgba(31, 124, 255, 0.2), rgba(77, 163, 255, 0.2));
}

.empty-icon-inner {
  color: var(--glass-text-primary);
  opacity: 0.4;
}

.empty-title {
  color: var(--glass-text-primary);
  opacity: 0.7;
}

.empty-hint {
  color: var(--glass-text-primary);
  opacity: 0.4;
}
</style>
