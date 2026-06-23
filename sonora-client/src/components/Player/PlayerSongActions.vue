<script setup lang="ts">
import LoginDialog from '@/components/Auth/LoginDialog.vue'
import SaveToPlaylistDialog from '@/components/Playlist/SaveToPlaylistDialog.vue'
import Button from '@/components/Ui/Button.vue'
import { likedSongIds, likeSong, unlikeSong } from '@/api'
import { useAudio } from '@/composables/useAudio'
import { useUserStore } from '@/stores/modules/user'

const props = withDefaults(
  defineProps<{
    size?: 'sm' | 'md'
    action?: 'like' | 'save' | 'both'
  }>(),
  {
    size: 'sm',
    action: 'both',
  }
)

const { currentSong, playlist } = useAudio()
const userStore = useUserStore()

const showLogin = ref(false)
const showSaveToPlaylist = ref(false)
const isLiked = ref(false)
const isSubmitting = ref(false)

const showLike = computed(() => props.action === 'like' || props.action === 'both')
const showSave = computed(() => props.action === 'save' || props.action === 'both')

const buttonClass = computed(() =>
  props.size === 'md'
    ? 'player-song-action-button h-12 w-12 justify-center'
    : 'player-song-action-button h-9 w-9 justify-center'
)

const iconClass = computed(() => (props.size === 'md' ? 'h-6 w-6' : 'h-4.5 w-4.5'))

const applyLikedState = (liked: boolean) => {
  isLiked.value = liked
  if (!currentSong.value?.id) return
  currentSong.value.liked = liked
  playlist.value.forEach(song => {
    if (String(song.id) === String(currentSong.value?.id)) {
      song.liked = liked
    }
  })
}

const ensureAuthenticated = () => {
  if (userStore.isAuthenticated) return true
  if (userStore.isLoggedIn) userStore.logout()
  showLogin.value = true
  return false
}

const refreshLikedState = async () => {
  if (!currentSong.value?.id) {
    isLiked.value = false
    return
  }
  if (!userStore.isAuthenticated) {
    applyLikedState(false)
    return
  }
  try {
    const res = await likedSongIds()
    const ids = new Set((res?.data || []).map(id => String(id)))
    applyLikedState(ids.has(String(currentSong.value.id)))
  } catch {}
}

const toggleLike = async () => {
  if (!currentSong.value?.id || isSubmitting.value) return
  if (!ensureAuthenticated()) return

  const nextLiked = !isLiked.value
  applyLikedState(nextLiked)
  isSubmitting.value = true
  try {
    const res = nextLiked ? await likeSong(currentSong.value.id) : await unlikeSong(currentSong.value.id)
    if (res?.code !== 200) {
      throw new Error(res?.message || '操作失败')
    }
    window.dispatchEvent(new CustomEvent('sonora:playlists-updated'))
  } catch (error: any) {
    applyLikedState(!nextLiked)
    if (error?.response?.status === 401) {
      showLogin.value = true
    }
  } finally {
    isSubmitting.value = false
  }
}

const openSaveToPlaylist = () => {
  if (!currentSong.value?.id) return
  if (!ensureAuthenticated()) return
  showSaveToPlaylist.value = true
}

watch(
  () => [currentSong.value?.id, userStore.isAuthenticated],
  refreshLikedState,
  { immediate: true }
)
</script>

<template>
  <div class="flex items-center gap-2">
    <div v-if="showLike" class="flex">
      <Button
        variant="ghost"
        size="none"
        rounded="full"
        :class="[
          buttonClass,
          'items-center justify-center',
          isLiked ? 'player-song-action-button--liked' : '',
          isSubmitting ? 'scale-95 opacity-80' : '',
        ]"
        :disabled="!currentSong?.id || isSubmitting"
        @click="toggleLike"
      >
        <span
          :class="[
            isLiked ? 'icon-[mdi--heart]' : 'icon-[mdi--heart-outline]',
            iconClass,
            'transition-all duration-200',
            isSubmitting ? 'scale-90' : '',
          ]"
        />
      </Button>
    </div>

    <div v-if="showSave" class="flex">
      <Button
        variant="ghost"
        size="none"
        rounded="full"
        :class="[
          buttonClass,
          'items-center justify-center',
        ]"
        :disabled="!currentSong?.id"
        @click="openSaveToPlaylist"
      >
        <span :class="['icon-[mdi--playlist-plus]', iconClass]" />
      </Button>
    </div>
  </div>

  <LoginDialog v-if="showLogin" @close="showLogin = false" @success="refreshLikedState" />
  <SaveToPlaylistDialog
    v-if="showSaveToPlaylist && currentSong?.id"
    :song-id="currentSong.id"
    :song-name="currentSong.name"
    @close="showSaveToPlaylist = false"
  />
</template>

<style scoped>
.player-song-action-button {
  color: var(--glass-text-secondary);
  background: transparent;
  transition:
    color 0.2s ease,
    background 0.2s ease,
    transform 0.2s ease;
}

.player-song-action-button:hover:not(:disabled) {
  color: var(--glass-text-primary);
  background: rgba(255, 255, 255, 0.08);
  transform: scale(1.08);
}

.player-song-action-button:active:not(:disabled) {
  transform: scale(0.95);
}

.player-song-action-button--liked {
  color: var(--sonora-blue-soft);
  background: transparent;
}
</style>
