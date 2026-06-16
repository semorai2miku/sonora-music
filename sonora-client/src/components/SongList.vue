<script setup lang="ts">
import { useAudio } from '@/composables/useAudio'
import { useSharedElement } from '@/composables/useSharedElement'
import type { Song } from '@/stores/interface'
import { formatDuration } from '@/utils/time'
import { RouterLink, useRouter } from 'vue-router'
import LazyImage from '@/components/Ui/LazyImage.vue'
import Button from '@/components/Ui/Button.vue'
import { useI18n } from 'vue-i18n'
import { likedSongIds, likeSong, unlikeSong } from '@/api'
import { useUserStore } from '@/stores/modules/user'
import LoginDialog from '@/components/Auth/LoginDialog.vue'
import SaveToPlaylistDialog from '@/components/Playlist/SaveToPlaylistDialog.vue'
import { withImageParam } from '@/utils/media'

interface Props {
  songs: Song[]
  currentPlayingIndex?: number
  showHeader?: boolean
  showControls?: boolean
  showSaveToPlaylist?: boolean
  emptyMessage?: string
  loading?: boolean
  allowRemove?: boolean
}

interface Emits {
  (e: 'play', song: Song, index: number): void
  (e: 'like', song: Song, index: number): void
  (e: 'more', song: Song, index: number): void
  (e: 'sort'): void
  (e: 'filter'): void
  (e: 'mv', song: Song, index: number): void
  (e: 'remove', song: Song, index: number): void
}

const props = withDefaults(defineProps<Props>(), {
  currentPlayingIndex: -1,
  showHeader: true,
  showControls: true,
  showSaveToPlaylist: true,
  emptyMessage: '',
  loading: false,
  allowRemove: false,
})

const emit = defineEmits<Emits>()
const router = useRouter()
const { setPlaylist, play, currentSong, isPlaying } = useAudio()
const { t } = useI18n()
const { flyTo, createRipple } = useSharedElement()
const userStore = useUserStore()
const showLogin = ref(false)
const showSaveToPlaylist = ref(false)
const playlistTargetSong = ref<Song | null>(null)
const likedState = reactive<Record<string, boolean>>({})
const likedUpdating = reactive<Record<string, boolean>>({})
const likedActionAt = reactive<Record<string, number>>({})

const songKey = (song: Song) => String(song.id ?? '')

const isSongLiked = (song: Song) => {
  const key = songKey(song)
  if (key && key in likedState) return likedState[key]
  return Boolean(song.liked)
}

const setSongLiked = (song: Song, liked: boolean) => {
  const key = songKey(song)
  if (key) likedState[key] = liked
  song.liked = liked
}

const ensureAuthenticated = () => {
  if (userStore.isAuthenticated) return true
  if (userStore.isLoggedIn) userStore.logout()
  showLogin.value = true
  return false
}

// 歌曲封面飞行动画
const playSongWithAnimation = async (song: Song, index: number, event?: MouseEvent) => {
  try {
    // 获取源封面元素和目标元素
    const sourceCover = document.getElementById(`song-cover-${song.id}`)
    const targetCover = document.getElementById('footer-cover')

    // 如果有点击事件，添加涟漪效果
    if (event && sourceCover) {
      const container = sourceCover.closest('.song-item') as HTMLElement
      if (container) {
        createRipple(event, container, 'rgba(31, 124, 255, 0.3)')
      }
    }

    // 如果能获取到元素，执行飞行动画
    if (sourceCover && targetCover && song.cover) {
      // 先设置播放列表
      setPlaylist(props.songs, index)

      // 执行抛物线飞行动画
      await flyTo(sourceCover, targetCover, withImageParam(song.cover, '128x128'), {
        duration: 0.55,
        ease: 'power2.out',
        borderRadius: { from: '8px', to: '8px' },
        scale: { from: 1, to: 1 },
        onComplete: () => {
          // 动画完成后播放
          play(props.songs[index], index)
        },
      })
    } else {
      // 降级：直接播放
      setPlaylist(props.songs, index)
      play(props.songs[index], index)
    }

    emit('play', song, index)
  } catch {}
}

const playSong = async (song: Song, index: number) => {
  await playSongWithAnimation(song, index)
}

// eslint-disable-next-line @typescript-eslint/no-unused-vars
const _playSong = playSong // 保留以备将来使用

const isCurrent = (s: Song) => {
  const cur = currentSong.value
  if (!cur) return false
  return String(s.id ?? '') === String(cur.id ?? '')
}

const openMV = (song: Song, index: number) => {
  const id = song.mvId || song.id
  if (id) {
    router.push(`/mv-player/${id}`)
  } else {
    emit('mv', song, index)
  }
}

const removeSong = (song: Song, index: number) => {
  emit('remove', song, index)
}

const refreshLikedStates = async () => {
  if (!props.songs?.length) return
  if (!userStore.isAuthenticated) {
    if (userStore.isLoggedIn) userStore.logout()
    props.songs.forEach(song => setSongLiked(song, false))
    return
  }
  try {
    const res = await likedSongIds()
    if (res?.code === 401) {
      showLogin.value = true
      return
    }
    const ids = new Set((res?.data || []).map(id => String(id)))
    props.songs.forEach(song => {
      setSongLiked(song, ids.has(String(song.id)))
    })
  } catch {}
}

const toggleLike = async (song: Song, index: number) => {
  if (!ensureAuthenticated()) return
  if (!song.id) return
  const key = songKey(song)
  const now = Date.now()
  if (key && now - (likedActionAt[key] || 0) < 350) return
  if (key) likedActionAt[key] = now
  if (key && likedUpdating[key]) return
  if (key) likedUpdating[key] = true
  const nextLiked = !isSongLiked(song)
  setSongLiked(song, nextLiked)
  try {
    let res
    if (nextLiked) {
      res = await likeSong(song.id)
    } else {
      res = await unlikeSong(song.id)
    }
    if (res?.code === 401) {
      showLogin.value = true
      throw new Error(res?.message || '请先登录')
    }
    if (res?.code !== 200) throw new Error(res?.message || '操作失败')
    window.dispatchEvent(new CustomEvent('sonora:playlists-updated'))
    emit('like', song, index)
  } catch (error: any) {
    setSongLiked(song, !nextLiked)
    if (error?.response?.status === 401) {
      showLogin.value = true
    }
    console.error('Failed to toggle like state in SongList:', error)
  } finally {
    if (key) likedUpdating[key] = false
  }
}

const openSaveToPlaylist = (song: Song) => {
  if (!ensureAuthenticated()) return
  if (!song.id) return
  playlistTargetSong.value = song
  showSaveToPlaylist.value = true
}

watch(
  () => [props.songs?.map(song => song.id).join(','), userStore.isAuthenticated],
  refreshLikedStates,
  { immediate: true }
)
</script>

<style scoped>
/* 歌曲项悬停效果 - 移除位移，改用更平滑的背景过渡 */
.song-item {
  transition: all 0.2s ease;
}

/* 响应式调整 */
@media (max-width: 768px) {
  .song-item {
    flex-direction: column;
    align-items: flex-start;
    gap: 0.5rem;
  }

  .song-item .w-12,
  .song-item .w-24,
  .song-item .w-20 {
    width: auto;
  }
}
</style>
<template>
  <div class="flex h-full flex-col overflow-hidden">
    <div class="glass-card flex flex-1 flex-col overflow-hidden p-2">
      <!-- 列表头部 -->
      <div
        v-if="showHeader"
        class="text-primary/60 mb-2 hidden items-center border-b border-white/5 py-3 text-xs font-medium tracking-wider uppercase md:flex"
      >
        <div class="w-14 text-center">#</div>
        <div class="grid min-w-0 flex-1 grid-cols-12 items-center gap-4 px-4">
          <div class="col-span-4">{{ t('components.songList.headers.song') }}</div>
          <div class="col-span-3 hidden md:block">
            {{ t('components.songList.headers.artist') }}
          </div>
          <div class="col-span-2 hidden text-center md:block">
            {{ t('components.songList.headers.album') }}
          </div>
          <div class="col-span-1 text-right">{{ t('components.songList.headers.duration') }}</div>
          <div class="col-span-2 text-center">{{ t('components.songList.headers.actions') }}</div>
        </div>
      </div>

      <!-- Loading 骨架屏 -->
      <div
        v-if="loading"
        class="custom-scrollbar h-full space-y-1 overflow-x-hidden overflow-y-auto pr-2"
      >
        <div v-for="i in 12" :key="i" class="flex items-center rounded-xl p-2">
          <div class="flex w-14 shrink-0 items-center justify-center">
            <div class="h-4 w-4 animate-pulse rounded bg-white/10"></div>
          </div>
          <div class="grid min-w-0 flex-1 grid-cols-12 items-center gap-4">
            <div class="col-span-4 flex items-center space-x-4">
              <div class="h-12 w-12 shrink-0 animate-pulse rounded-lg bg-white/10"></div>
              <div class="min-w-0 flex-1 space-y-2">
                <div class="h-4 w-3/4 animate-pulse rounded bg-white/10"></div>
                <div class="h-3 w-1/2 animate-pulse rounded bg-white/10 md:hidden"></div>
              </div>
            </div>
            <div class="col-span-3 hidden md:block">
              <div class="h-4 w-2/3 animate-pulse rounded bg-white/10"></div>
            </div>
            <div class="col-span-2 hidden md:block">
              <div class="mx-auto h-4 w-1/2 animate-pulse rounded bg-white/10"></div>
            </div>
            <div class="col-span-1 flex justify-end">
              <div class="h-4 w-10 animate-pulse rounded bg-white/10"></div>
            </div>
            <div class="col-span-2 flex justify-center gap-1">
              <div class="h-8 w-8 animate-pulse rounded-full bg-white/10"></div>
              <div class="h-8 w-8 animate-pulse rounded-full bg-white/10"></div>
            </div>
          </div>
        </div>
      </div>

      <!-- 歌曲列表 -->
      <div
        v-else-if="songs.length > 0"
        class="custom-scrollbar h-full space-y-1 overflow-x-hidden overflow-y-auto pr-2"
      >
        <div
          v-for="(song, index) in songs"
          :key="song.id || index"
          class="song-item group flex cursor-pointer items-center rounded-xl p-2 hover:bg-white/10"
          :class="isCurrent(song) ? 'bg-white/10' : ''"
          @dblclick="e => playSongWithAnimation(song, index, e)"
        >
          <!-- 序号/播放状态 -->
          <div class="flex w-14 shrink-0 items-center justify-center text-center">
            <span
              v-if="!isCurrent(song)"
              class="text-primary/60 text-sm font-medium group-hover:hidden"
            >
              {{ index + 1 }}
            </span>
            <div v-if="isCurrent(song)" class="playing-icon">
              <span class="bar" :class="{ animate: isPlaying }"></span>
              <span class="bar" :class="{ animate: isPlaying }"></span>
              <span class="bar" :class="{ animate: isPlaying }"></span>
            </div>
            <Button
              v-if="!isCurrent(song)"
              variant="text"
              size="none"
              class="hidden! transition-colors group-hover:block! hover:text-pink-400"
              @click.stop="(e: MouseEvent) => playSongWithAnimation(song, index, e)"
            >
              <span class="icon-[mdi--play] h-6 w-6"></span>
            </Button>
          </div>

          <div class="grid min-w-0 flex-1 grid-cols-12 items-center gap-4">
            <div class="col-span-4 flex items-center space-x-4">
              <div
                :id="`song-cover-${song.id}`"
                class="relative h-12 w-12 shrink-0 overflow-hidden rounded-lg shadow-md transition-shadow group-hover:shadow-lg"
              >
                <LazyImage
                  :src="withImageParam(song.cover || '', '90y90')"
                  :alt="t('components.songList.coverAlt')"
                  imgClass="h-full w-full object-cover transition-transform duration-500 group-hover:scale-110"
                  wrapperClass="h-full w-full"
                />
                <div
                  class="absolute inset-0 flex items-center justify-center bg-black/20 opacity-0 transition-opacity duration-300 group-hover:opacity-100"
                ></div>
              </div>
              <div class="min-w-0 flex-1">
                <RouterLink
                  v-if="song.id"
                  :to="`/song/${song.id}`"
                  :title="song.name"
                  class="text-primary block truncate text-base font-medium transition-colors group-hover:text-pink-300 hover:text-pink-300"
                  @click.stop
                >
                  {{ song.name }}
                </RouterLink>
                <h3
                  v-else
                  :title="song.name"
                  class="text-primary truncate text-base font-medium transition-colors group-hover:text-pink-300"
                >
                  {{ song.name }}
                </h3>
                <div class="mt-0.5 flex items-center gap-2 md:hidden">
                  <span class="text-primary/60 truncate text-xs">{{ song.artist }}</span>
                </div>
              </div>
            </div>

            <div class="col-span-3 hidden overflow-hidden md:block">
              <template v-if="song.artists && song.artists.length > 0">
                <span class="text-primary/80 truncate text-sm">
                  <template v-for="(ar, idx) in song.artists" :key="ar.id">
                    <RouterLink
                      :to="`/artist/${ar.id}`"
                      :title="ar.name"
                      class="transition-colors hover:text-pink-400"
                    >
                      {{ ar.name }}
                    </RouterLink>
                    <span v-if="idx < song.artists.length - 1" class="text-primary/50"> / </span>
                  </template>
                </span>
              </template>
              <template v-else-if="song.artistId">
                <RouterLink
                  :to="`/artist/${song.artistId}`"
                  :title="song.artist"
                  class="text-primary/80 truncate text-sm transition-colors hover:text-pink-400"
                >
                  {{ song.artist }}
                </RouterLink>
              </template>
              <span v-else :title="song.artist" class="text-primary/80 truncate text-sm">
                {{ song.artist }}
              </span>
            </div>

            <div class="col-span-2 hidden overflow-hidden text-center md:block">
              <RouterLink
                v-if="song.albumId"
                :to="`/album/${song.albumId}`"
                :title="song.album || '-'"
                class="text-primary/60 hover:text-primary truncate text-sm transition-colors"
              >
                {{ song.album || '-' }}
              </RouterLink>
              <span v-else :title="song.album || '-'" class="text-primary/60 truncate text-sm">
                {{ song.album || '-' }}
              </span>
            </div>
            <div class="col-span-1 flex items-center justify-end">
              <span class="text-primary/50 hidden font-mono text-sm md:inline-block">{{
                formatDuration(song.duration)
              }}</span>
            </div>
            <!-- 操控按钮 -->
            <div
              class="col-span-2 flex items-center justify-center gap-1 opacity-80 transition-opacity duration-200 group-hover:opacity-100"
            >
              <button
                v-if="song.id"
                type="button"
                class="text-primary/70 hover:text-primary inline-flex h-9 w-9 items-center justify-center rounded-full bg-transparent transition-all duration-300 hover:bg-hover-glass"
                :class="isSongLiked(song) ? 'text-pink-400' : ''"
                :title="isSongLiked(song) ? '取消喜欢' : '喜欢'"
                @click.stop.prevent="toggleLike(song, index)"
              >
                <span
                  class="h-5 w-5"
                  :class="isSongLiked(song) ? 'icon-[mdi--heart]' : 'icon-[mdi--heart-outline]'"
                ></span>
              </button>
              <button
                v-if="song.id"
                type="button"
                class="text-primary/70 hover:text-primary inline-flex h-9 w-9 items-center justify-center rounded-full bg-transparent transition-all duration-300 hover:bg-hover-glass"
                title="收藏到歌单"
                @click.stop.prevent="openSaveToPlaylist(song)"
              >
                <span class="icon-[mdi--playlist-plus] h-5 w-5"></span>
              </button>
              <button
                v-if="allowRemove && song.id"
                type="button"
                class="inline-flex h-9 w-9 items-center justify-center rounded-full bg-transparent text-red-300 transition-all duration-300 hover:bg-hover-glass"
                title="从歌单移除"
                @click.stop.prevent="removeSong(song, index)"
              >
                <span class="icon-[mdi--playlist-remove] h-5 w-5"></span>
              </button>
              <button
                v-if="song.mvId"
                type="button"
                class="text-primary/70 hover:text-primary inline-flex h-9 w-9 items-center justify-center rounded-full bg-transparent transition-all duration-300 hover:bg-hover-glass"
                :title="t('common.playMV')"
                @click.stop.prevent="openMV(song, index)"
              >
                <span class="icon-[mdi--movie-open-play] h-5 w-5"></span>
              </button>
              <button
                v-if="song.id"
                type="button"
                class="text-primary/70 hover:text-primary inline-flex h-9 w-9 items-center justify-center rounded-full bg-transparent transition-all duration-300 hover:bg-hover-glass"
                :title="t('common.detail')"
                @click.stop.prevent="router.push(`/song/${song.id}`)"
              >
                <span class="icon-[mdi--information-outline] h-5 w-5"></span>
              </button>
            </div>
          </div>
        </div>
      </div>

      <!-- 空状态 -->
      <div
        v-if="!loading && (!songs || songs.length === 0)"
        class="flex h-full flex-col items-center justify-center py-12 text-center"
      >
        <div class="mb-6 rounded-full bg-white/5 p-6">
          <span class="icon-[mdi--music-note-off] text-primary/20 h-12 w-12"></span>
        </div>
        <p class="text-primary/60 text-lg font-medium">
          {{ emptyMessage || t('components.songList.empty') }}
        </p>
      </div>
    </div>
  </div>
  <LoginDialog v-if="showLogin" @close="showLogin = false" @success="refreshLikedStates" />
  <SaveToPlaylistDialog
    v-if="showSaveToPlaylist && playlistTargetSong?.id"
    :song-id="playlistTargetSong.id"
    :song-name="playlistTargetSong.name"
    @close="showSaveToPlaylist = false"
  />
</template>
