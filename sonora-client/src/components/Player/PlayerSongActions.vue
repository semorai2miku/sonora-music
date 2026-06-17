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
  }>(),
  {
    size: 'sm',
  }
)

const { currentSong, playlist } = useAudio()
const userStore = useUserStore()

const showLogin = ref(false)
const showSaveToPlaylist = ref(false)
const isLiked = ref(false)
const isSubmitting = ref(false)

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
    <Button
      variant="ghost"
      size="none"
      :class="buttonClass"
      :disabled="!currentSong?.id || isSubmitting"
      :title="$t(isLiked ? 'common.unlike' : 'common.like')"
      @click="toggleLike"
    >
      <span
        :class="[
          isLiked ? 'icon-[mdi--heart] text-pink-400' : 'icon-[mdi--heart-outline] text-primary/70',
          iconClass,
        ]"
      />
    </Button>

    <Button
      variant="ghost"
      size="none"
      :class="buttonClass"
      :disabled="!currentSong?.id"
      :title="$t('playlist.dialog.title')"
      @click="openSaveToPlaylist"
    >
      <span :class="['icon-[mdi--playlist-plus]', iconClass, 'text-primary/70']" />
    </Button>
  </div>

  <LoginDialog v-if="showLogin" @close="showLogin = false" @success="refreshLikedState" />
  <SaveToPlaylistDialog
    v-if="showSaveToPlaylist && currentSong?.id"
    :song-id="currentSong.id"
    :song-name="currentSong.name"
    @close="showSaveToPlaylist = false"
  />
</template>
