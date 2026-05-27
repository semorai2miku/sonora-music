<script setup lang="ts">
import { likedSongs } from '@/api'
import Button from '@/components/Ui/Button.vue'
import { useAudio } from '@/composables/useAudio'
import { useUserStore } from '@/stores/modules/user'
import type { Song } from '@/stores/interface'
import { transformSong } from '@/utils/transformers'

const userStore = useUserStore()
const { setPlaylist, play } = useAudio()

const state = reactive({
  isPageLoading: false,
  songs: [] as Song[],
})

const loadLikedSongs = async () => {
  if (!userStore.isLoggedIn) {
    state.songs = []
    return
  }
  state.isPageLoading = true
  try {
    const res = await likedSongs()
    state.songs = (res?.data || []).map(item => ({
      ...transformSong(item),
      liked: true,
    }))
  } finally {
    state.isPageLoading = false
  }
}

const playAll = () => {
  if (!state.songs.length) return
  setPlaylist(state.songs, 0)
  play(state.songs[0], 0)
}

onMounted(loadLikedSongs)
watch(() => userStore.isLoggedIn, loadLikedSongs)
</script>

<template>
  <div class="relative flex-1 overflow-hidden">
    <div class="absolute inset-0 -z-10">
      <div class="glow top-16 left-24 bg-pink-500/30"></div>
      <div class="glow right-24 bottom-20 bg-purple-500/30"></div>
      <div class="glow top-1/2 right-10 bg-blue-500/25"></div>
    </div>

    <div class="h-full overflow-auto p-6">
      <PageSkeleton v-if="state.isPageLoading" :sections="['list']" :list-count="12" />
      <template v-else>
        <div class="mb-8">
          <div class="glass-container relative overflow-hidden rounded-3xl p-6">
            <div class="shimmer absolute inset-0"></div>
            <div class="relative z-10 flex flex-col gap-6 md:flex-row md:items-center md:justify-between">
              <div>
                <h1 class="text-primary text-3xl font-bold">我喜欢的音乐</h1>
                <p class="text-primary/70 mt-1 text-sm">
                  {{ userStore.isLoggedIn ? `共 ${state.songs.length} 首歌曲` : '登录后同步你点红心收藏的歌曲' }}
                </p>
              </div>
              <Button
                variant="solid"
                size="md"
                icon="icon-[mdi--play]"
                :disabled="!state.songs.length"
                @click="playAll"
              >
                播放全部
              </Button>
            </div>
          </div>
        </div>

        <SongList
          :songs="state.songs"
          :loading="state.isPageLoading"
          :showHeader="true"
          emptyMessage="暂无喜欢的歌曲"
          @like="loadLikedSongs"
        />
      </template>
    </div>
  </div>
</template>

<style scoped>
.glow {
  position: absolute;
  width: 22rem;
  height: 22rem;
  border-radius: 9999px;
  filter: blur(80px);
  animation: float 10s ease-in-out infinite;
}
.shimmer {
  background: linear-gradient(
    120deg,
    rgba(255, 255, 255, 0.06),
    rgba(255, 255, 255, 0.02) 30%,
    rgba(255, 255, 255, 0.06)
  );
  mask-image: linear-gradient(180deg, transparent, black 20%, black 80%, transparent);
  animation: shimmer 5s linear infinite;
}
@keyframes shimmer {
  0% {
    transform: translateX(-30%);
  }
  100% {
    transform: translateX(30%);
  }
}
@keyframes float {
  0%,
  100% {
    transform: translateY(0);
  }
  50% {
    transform: translateY(-12px);
  }
}
</style>
