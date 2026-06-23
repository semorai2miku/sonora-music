<script setup lang="ts">
import { useAudioStore } from '@/stores/modules/audio'
import { useAudio } from '@/composables/useAudio'
import type { Song as StoreSong } from '@/stores/interface'
import SongList from '@/components/SongList.vue'
import Button from '@/components/Ui/Button.vue'

const audioStore = useAudioStore()
const { audio } = storeToRefs(audioStore)

const { setPlaylist, play } = useAudio()

const recentList = computed(() => audio.value?.playHistory || [])

const playAll = () => {
  if (!recentList.value.length) return
  const list: StoreSong[] = recentList.value
  setPlaylist(list, 0)
  play(list[0], 0)
}
</script>

<template>
  <div class="flex flex-1 flex-col overflow-hidden p-4">
    <section class="glass-card sonora-page-hero mb-6 shrink-0 p-6 md:p-8">
      <div class="flex flex-col items-start gap-4 md:flex-row md:items-center md:justify-between">
        <div>
          <h1 class="mb-2 text-3xl font-bold text-primary md:text-4xl">
            {{ $t('recent.title') }}
          </h1>
          <p class="text-sm text-primary/70 md:text-base">{{ $t('recent.subtitle') }}</p>
          <div class="mt-4 flex items-center gap-4 text-primary/60">
            <div class="flex items-center gap-2">
              <span class="icon-[mdi--music-note] h-5 w-5"></span>
              <span class="text-sm font-medium">
                {{ $t('commonUnits.songsShort', { count: recentList.length }) }}
              </span>
            </div>
            <div v-if="recentList.length > 0" class="flex items-center gap-2">
              <span class="icon-[mdi--history] h-5 w-5"></span>
              <span class="text-sm font-medium">{{ $t('recent.playHistory') }}</span>
            </div>
          </div>
        </div>
        <div class="flex items-center gap-3">
          <Button
            variant="solid"
            size="md"
            rounded="full"
            class="px-6 py-3"
            :disabled="!recentList.length"
            @click="playAll"
          >
            <span class="icon-[mdi--play] mr-2 h-5 w-5"></span>
            {{ $t('actions.playAll') }}
          </Button>
        </div>
      </div>
    </section>

    <!-- 歌曲列表 -->
    <div class="flex-1 overflow-hidden">
      <SongList
        :songs="recentList"
        :show-header="true"
        :show-controls="true"
        :empty-message="$t('recent.empty')"
      />
    </div>
  </div>
</template>
