<script setup lang="ts">
import { likedSongs } from '@/api'
import LazyImage from '@/components/Ui/LazyImage.vue'
import { useAudio } from '@/composables/useAudio'
import { useUserStore } from '@/stores/modules/user'
import type { Song } from '@/stores/interface'
import { transformSong } from '@/utils/transformers'
import { withImageParam } from '@/utils/media'
import { useI18n } from 'vue-i18n'

const userStore = useUserStore()
const { setPlaylist, play } = useAudio()
const songs = ref<Song[]>([])
const loading = ref(false)
const { t } = useI18n()

const loadLikedSongs = async () => {
  if (!userStore.isLoggedIn) {
    songs.value = []
    return
  }
  loading.value = true
  try {
    const res = await likedSongs()
    if (res?.code === 401) {
      userStore.logout()
      songs.value = []
      return
    }
    songs.value = (res?.data || []).map(item => ({ ...transformSong(item), liked: true }))
  } finally {
    loading.value = false
  }
}

const playSong = (song: Song, index: number) => {
  setPlaylist(songs.value, index)
  play(song, index)
}

onMounted(loadLikedSongs)
watch(() => userStore.isLoggedIn, loadLikedSongs)
</script>

<template>
  <div class="flex-1 overflow-auto px-3 pb-6">
    <div class="mb-3 px-1">
      <h1 class="text-primary text-xl font-bold">{{ $t('playlist.likedBadge') }}</h1>
      <p class="text-primary/60 mt-1 text-xs">
        {{ userStore.isLoggedIn ? t('likes.count', { count: songs.length }) : t('likes.loginHint') }}
      </p>
    </div>

    <div v-if="loading" class="py-10 text-center text-primary/70">{{ $t('common.loading') }}...</div>
    <template v-else-if="!songs.length">
      <div class="py-10 text-center text-primary/70">{{ $t('likes.empty') }}</div>
    </template>
    <div v-else class="space-y-3">
      <div
        v-for="(song, index) in songs"
        :key="song.id"
        class="glass-card flex items-center gap-3 p-3"
        @click="playSong(song, index)"
      >
        <div class="h-12 w-12 shrink-0 overflow-hidden rounded-lg">
          <LazyImage
            v-if="song.cover"
            :src="withImageParam(song.cover, '200y200')"
            alt="cover"
            imgClass="h-full w-full object-cover"
          />
          <div v-else class="glass-button flex h-full w-full items-center justify-center rounded-lg">
            <span class="icon-[mdi--music-note] h-5 w-5"></span>
          </div>
        </div>
        <div class="min-w-0 flex-1">
          <p class="text-primary truncate text-sm font-medium">{{ song.name }}</p>
          <p class="text-primary/70 truncate text-xs">{{ song.artist }}</p>
        </div>
        <span class="icon-[mdi--heart] h-5 w-5 text-pink-400"></span>
      </div>
    </div>
  </div>
</template>
