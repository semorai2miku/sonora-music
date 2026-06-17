<script setup lang="ts">
import { useRoute, useRouter } from 'vue-router'
import { useLyrics } from '@/composables/useLyrics'
import SongCommentsDialog from '@/components/Comments/SongCommentsDialog.vue'
import LoginDialog from '@/components/Auth/LoginDialog.vue'
import SaveToPlaylistDialog from '@/components/Playlist/SaveToPlaylistDialog.vue'
import { likedSongIds, likeSong, search, songDetail, unlikeSong } from '@/api'
import { useAudio } from '@/composables/useAudio'
import { usePlayActions } from '@/composables/usePlayActions'
import { useUserStore } from '@/stores/modules/user'
import { formatDuration } from '@/utils/time'
import LazyImage from '@/components/Ui/LazyImage.vue'
import { withImageParam } from '@/utils/media'
import {
  transformSearchSongs,
  transformSearchPlaylists,
  transformSongs,
  type SongData,
  type PlaylistData,
} from '@/utils/transformers'

const route = useRoute()
const router = useRouter()
const songId = computed(() => route.params.id as string | number)
const { mergedLines, fetchLyrics } = useLyrics()
const { play, setPlaylist, currentSong, isPlaying } = useAudio()
const { playPlaylist: playPlaylistAction } = usePlayActions()
const userStore = useUserStore()
const showComments = ref(false)
const showLogin = ref(false)
const showSaveToPlaylist = ref(false)

const state = reactive({
  info: null as any,
  similarSongs: [] as SongData[],
  similarPlaylists: [] as PlaylistData[],
  liked: false,
  playingPlaylistId: null as number | string | null,
})

const ensureAuthenticated = () => {
  if (userStore.isAuthenticated) return true
  if (userStore.isLoggedIn) userStore.logout()
  showLogin.value = true
  return false
}

onMounted(() => fetchLyrics(songId.value))
watch(
  () => route.params.id,
  id => fetchLyrics(id as any)
)

const artistName = computed(() => {
  if (!state.info) return ''
  return Array.isArray(state.info?.ar)
    ? state.info.ar.map((a: any) => a.name).join(' / ')
    : state.info?.artists?.map((a: any) => a.name).join(' / ')
})

const artistEntries = computed(() => {
  const source = Array.isArray(state.info?.artists)
    ? state.info.artists
    : Array.isArray(state.info?.ar)
      ? state.info.ar
      : []

  return source
    .filter((artist: any) => artist?.name)
    .map((artist: any) => ({
      id: artist.id || '',
      name: artist.name,
    }))
})

const albumName = computed(() => state.info?.al?.name || state.info?.album?.name || '')
const albumCover = computed(() => state.info?.cover || state.info?.al?.picUrl || state.info?.album?.picUrl || '')
const duration = computed(() => state.info?.dt || state.info?.duration || 0)

const playCurrent = () => {
  if (!state.info) return
  const song = {
    id: state.info.id,
    name: state.info.name,
    artist: artistName.value,
    artistId: artistEntries.value[0]?.id || 0,
    artists: artistEntries.value,
    album: albumName.value,
    albumId: state.info?.albumId || state.info?.al?.id || state.info?.album?.id || 0,
    duration: duration.value,
    cover: albumCover.value,
    liked: state.liked,
  }
  setPlaylist([song], 0)
  play(song, 0)
}

const isCurrent = computed(() => {
  if (!currentSong.value || !state.info) return false
  return String(state.info.id) === String(currentSong.value.id)
})

const loadInfo = async () => {
  try {
    const res: any = await songDetail({ ids: String(songId.value) })
    const song = transformSongs(res as Record<string, unknown>, 1)[0]
    const rawSong = Array.isArray(res?.songs) ? res.songs[0] : res?.songs
    state.info = rawSong ? { ...rawSong, ...song } : song
    const artistNameVal = Array.isArray(song?.ar)
      ? song.ar.map((a: any) => a.name).join(' / ')
      : song?.artists?.map((a: any) => a.name).join(' / ')

    // 搜索相似歌曲
    const resSimSongs = await search({ keywords: artistNameVal || song?.name || '', type: 1 })
    const { songs } = transformSearchSongs(resSimSongs as Record<string, unknown>, 12)
    state.similarSongs = songs

    // 搜索相关歌单
    const resSimPls = await search({ keywords: song?.name || '', type: 1000 })
    const { playlists } = transformSearchPlaylists(resSimPls as Record<string, unknown>, 6)
    state.similarPlaylists = playlists
  } catch {}
}

onMounted(() => loadInfo())
watch(songId, () => loadInfo())

const refreshLikeState = async () => {
  if (!state.info?.id) {
    state.liked = false
    return
  }
  if (!userStore.isAuthenticated) {
    if (userStore.isLoggedIn) userStore.logout()
    state.liked = false
    return
  }
  try {
    const res = await likedSongIds()
    if (res?.code === 401) {
      state.liked = false
      return
    }
    state.liked = (res?.data || []).map(String).includes(String(state.info.id))
  } catch {}
}

const toggleLike = async () => {
  if (!state.info?.id) return
  if (!ensureAuthenticated()) return
  const nextLiked = !state.liked
  state.liked = nextLiked
  try {
    const res = nextLiked ? await likeSong(state.info.id) : await unlikeSong(state.info.id)
    if (res?.code === 401) {
      showLogin.value = true
      throw new Error(res?.message || '请先登录')
    }
    if (res?.code !== 200) throw new Error(res?.message || '操作失败')
    window.dispatchEvent(new CustomEvent('sonora:playlists-updated'))
  } catch (error: any) {
    state.liked = !nextLiked
    if (error?.response?.status === 401) {
      showLogin.value = true
    }
    console.error('Failed to toggle like state on song page:', error)
  }
}

const openSaveToPlaylist = () => {
  if (!state.info?.id) return
  if (!ensureAuthenticated()) return
  showSaveToPlaylist.value = true
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

watch(() => [state.info?.id, userStore.isAuthenticated], refreshLikeState)
</script>

<template>
  <div class="text-primary flex-1 overflow-hidden">
    <div class="h-full overflow-auto">
      <div class="p-6">
        <div v-if="state.info" class="mb-8 flex gap-8">
          <div class="group relative h-72 w-72 shrink-0 overflow-hidden rounded-2xl shadow-2xl">
            <LazyImage
              :src="withImageParam(albumCover, '300y300')"
              alt="封面"
              imgClass="h-full w-full object-cover transition-transform duration-500 group-hover:scale-105"
              wrapperClass="h-full w-full"
            />
            <div
              class="absolute inset-0 flex items-center justify-center bg-black/40 opacity-0 transition-opacity duration-300 group-hover:opacity-100"
            >
              <button
                class="flex h-16 w-16 items-center justify-center rounded-full bg-pink-500/90 text-white shadow-lg transition-transform hover:scale-110"
                @click="playCurrent"
              >
                <span v-if="isCurrent && isPlaying" class="icon-[mdi--pause] h-8 w-8"></span>
                <span v-else class="icon-[mdi--play] h-8 w-8"></span>
              </button>
            </div>
          </div>

          <div class="flex min-w-0 flex-1 flex-col justify-center">
            <h1 class="mb-4 text-4xl leading-tight font-bold">{{ state.info.name }}</h1>

            <div class="mb-6 flex flex-wrap items-center gap-3">
              <div
                class="glass-button inline-flex flex-wrap items-center gap-2 px-4 py-2 text-sm"
              >
                <span class="icon-[mdi--account-music] h-5 w-5"></span>
                <template v-for="(artist, index) in artistEntries" :key="`${artist.id}-${artist.name}-${index}`">
                  <button
                    type="button"
                    class="transition-colors hover:text-pink-300"
                    @click="artist.id && router.push(`/artist/${artist.id}`)"
                  >
                    {{ artist.name }}
                  </button>
                  <span v-if="index < artistEntries.length - 1" class="text-primary/40">/</span>
                </template>
                <span v-if="!artistEntries.length">{{ artistName }}</span>
              </div>
              <button
                v-if="state.info?.al?.id || state.info?.album?.id"
                class="glass-button inline-flex items-center gap-2 px-4 py-2 text-sm transition-colors hover:bg-white/20"
                @click="router.push(`/album/${state.info?.al?.id || state.info?.album?.id}`)"
              >
                <span class="icon-[mdi--album] h-5 w-5"></span>
                {{ albumName }}
              </button>
              <span class="text-primary/60 text-sm">{{ formatDuration(duration) }}</span>
            </div>

            <div class="flex items-center gap-3">
              <button
                class="inline-flex items-center gap-2 rounded-full bg-pink-500 px-6 py-3 font-medium text-white shadow-lg transition-all hover:bg-pink-600 hover:shadow-xl"
                @click="playCurrent"
              >
                <span v-if="isCurrent && isPlaying" class="icon-[mdi--pause] h-5 w-5"></span>
                <span v-else class="icon-[mdi--play] h-5 w-5"></span>
                {{ isCurrent && isPlaying ? '暂停' : '播放' }}
              </button>
              <button
                class="glass-button inline-flex items-center gap-2 px-5 py-3 transition-colors hover:bg-white/20"
                :class="state.liked ? 'text-pink-300' : ''"
                @click="toggleLike"
              >
                <span :class="state.liked ? 'icon-[mdi--heart]' : 'icon-[mdi--heart-outline]'" class="h-5 w-5"></span>
                {{ state.liked ? '已喜欢' : '喜欢' }}
              </button>
              <button
                class="glass-button inline-flex items-center gap-2 px-5 py-3 transition-colors hover:bg-white/20"
                @click="openSaveToPlaylist"
              >
                <span class="icon-[mdi--playlist-plus] h-5 w-5"></span>
                收藏到歌单
              </button>
              <button
                class="glass-button inline-flex items-center gap-2 px-5 py-3 transition-colors hover:bg-white/20"
                @click="showComments = true"
              >
                <span class="icon-[mdi--comment-outline] h-5 w-5"></span>
                评论
              </button>
            </div>
          </div>
        </div>

        <div class="grid grid-cols-1 gap-8 lg:grid-cols-3">
          <div class="lg:col-span-2">
            <div class="glass-card overflow-hidden rounded-2xl">
              <div class="border-b border-white/10 px-6 py-4">
                <h2 class="text-lg font-semibold">歌词</h2>
              </div>
              <div class="custom-scrollbar overflow-y-auto p-6">
                <div v-if="mergedLines.length" class="space-y-3">
                  <div
                    v-for="(m, i) in mergedLines"
                    :key="i"
                    class="rounded-xl p-3 transition-colors hover:bg-white/5"
                  >
                    <p class="text-primary text-base leading-relaxed">{{ m.ori }}</p>
                    <p v-if="m.tran" class="text-primary/70 mt-1 text-sm">{{ m.tran }}</p>
                    <p v-if="m.roma" class="text-primary/50 mt-1 text-xs italic">{{ m.roma }}</p>
                  </div>
                </div>
                <div v-else class="flex flex-col items-center justify-center py-12 text-center">
                  <span
                    class="icon-[mdi--text-box-remove-outline] text-primary/30 mb-3 h-12 w-12"
                  ></span>
                  <p class="text-primary/50">暂无歌词</p>
                </div>
              </div>
            </div>
          </div>

          <div class="space-y-6">
            <div v-if="state.similarSongs.length" class="glass-card overflow-hidden rounded-2xl">
              <div class="border-b border-white/10 px-6 py-4">
                <h2 class="text-lg font-semibold">相似歌曲</h2>
              </div>
              <div class="custom-scrollbar max-h-[400px] overflow-y-auto">
                <div
                  v-for="(song, index) in state.similarSongs"
                  :key="song.id"
                  class="group flex cursor-pointer items-center gap-3 px-4 py-3 transition-colors hover:bg-white/10"
                  @click="router.push(`/song/${song.id}`)"
                >
                  <span class="text-primary/40 w-6 text-center text-sm">{{ index + 1 }}</span>
                  <div class="min-w-0 flex-1">
                    <p class="text-primary truncate text-sm font-medium group-hover:text-pink-300">
                      {{ song.name }}
                    </p>
                    <p class="text-primary/60 truncate text-xs">{{ song.artist }}</p>
                  </div>
                  <span class="text-primary/40 text-xs">{{ formatDuration(song.duration) }}</span>
                </div>
              </div>
            </div>

            <div
              v-if="state.similarPlaylists.length"
              class="glass-card overflow-hidden rounded-2xl"
            >
              <div class="border-b border-white/10 px-6 py-4">
                <h2 class="text-lg font-semibold">相关歌单</h2>
              </div>
              <div class="grid grid-cols-2 gap-3 p-4">
                <div
                  v-for="pl in state.similarPlaylists"
                  :key="pl.id"
                  class="group cursor-pointer overflow-hidden rounded-xl"
                  role="link"
                  tabindex="0"
                  @click="router.push(`/playlist/${pl.id}`)"
                  @keydown.enter="router.push(`/playlist/${pl.id}`)"
                >
                  <div class="relative aspect-square overflow-hidden rounded-lg">
                    <img
                      :src="withImageParam(pl.coverImgUrl, '200y200')"
                      class="h-full w-full object-cover transition-transform duration-300 group-hover:scale-110"
                    />
                    <div
                      class="absolute inset-0 bg-linear-to-t from-black/60 to-transparent opacity-0 transition-opacity group-hover:opacity-100"
                    ></div>
                    <button
                      type="button"
                      class="absolute inset-0 flex items-center justify-center opacity-0 transition-opacity group-hover:opacity-100"
                      :disabled="state.playingPlaylistId === pl.id"
                      @click.stop.prevent="playPlaylist(pl.id)"
                    >
                      <span class="flex h-12 w-12 items-center justify-center rounded-full bg-pink-500/90 text-white shadow-lg">
                        <span class="icon-[mdi--play] h-6 w-6"></span>
                      </span>
                    </button>
                  </div>
                  <p
                    class="text-primary mt-2 truncate text-xs font-medium group-hover:text-pink-300"
                  >
                    {{ pl.name }}
                  </p>
                  <p class="text-primary/50 truncate text-xs">{{ pl.trackCount }} 首</p>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
    <SongCommentsDialog v-model:show="showComments" :song-id="songId" />
    <LoginDialog v-if="showLogin" @close="showLogin = false" @success="refreshLikeState" />
    <SaveToPlaylistDialog
      v-if="showSaveToPlaylist && state.info?.id"
      :song-id="state.info.id"
      :song-name="state.info.name"
      @close="showSaveToPlaylist = false"
    />
  </div>
</template>
