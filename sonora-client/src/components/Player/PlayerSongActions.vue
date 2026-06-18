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
    ? 'h-10 w-10 rounded-full'
    : 'h-9 w-9 rounded-full'
)

const iconClass = computed(() => (props.size === 'md' ? 'h-5 w-5' : 'h-4.5 w-4.5'))

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
    <div v-if="showLike" class="group relative flex">
      <Button
        variant="ghost"
        size="none"
        :class="[
          buttonClass,
          'items-center justify-center transition-all duration-200',
          isLiked ? 'bg-pink-500/10 text-pink-400' : 'text-primary/70',
          isSubmitting ? 'scale-95 opacity-80' : '',
        ]"
        :disabled="!currentSong?.id || isSubmitting"
        @click="toggleLike"
      >
        <span
          :class="[
            isLiked ? 'icon-[mdi--heart] text-pink-400' : 'icon-[mdi--heart-outline] text-primary/70',
            iconClass,
            'transition-all duration-200',
            isSubmitting ? 'scale-90' : '',
          ]"
        />
      </Button>
      <span class="player-action-tooltip">
        {{ $t(isLiked ? 'common.unlike' : 'common.like') }}
      </span>
    </div>

    <div v-if="showSave" class="group relative flex">
      <Button
        variant="ghost"
        size="none"
        :class="[
          buttonClass,
          'items-center justify-center text-primary/70 transition-all duration-200',
        ]"
        :disabled="!currentSong?.id"
        @click="openSaveToPlaylist"
      >
        <span :class="['icon-[mdi--playlist-plus]', iconClass, 'text-primary/70']" />
      </Button>
      <span class="player-action-tooltip">
        {{ $t('playlist.dialog.title') }}
      </span>
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
.player-action-tooltip {
  position: absolute;
  left: 50%;
  bottom: calc(100% + 0.5rem);
  transform: translateX(-50%) translateY(4px);
  pointer-events: none;
  white-space: nowrap;
  border-radius: 0.625rem;
  border: 1px solid rgba(255, 255, 255, 0.12);
  background: rgba(15, 23, 42, 0.84);
  padding: 0.35rem 0.55rem;
  color: #fff;
  font-size: 0.75rem;
  line-height: 1;
  opacity: 0;
  box-shadow: 0 12px 28px rgba(15, 23, 42, 0.25);
  backdrop-filter: blur(10px);
  transition:
    opacity 0.18s ease,
    transform 0.18s ease;
}

.group:hover .player-action-tooltip {
  opacity: 1;
  transform: translateX(-50%) translateY(0);
}
</style>
