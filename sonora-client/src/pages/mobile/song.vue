<script setup lang="ts">
import LoginDialog from '@/components/Auth/LoginDialog.vue'
import SaveToPlaylistDialog from '@/components/Playlist/SaveToPlaylistDialog.vue'
import { likedSongIds, likeSong, lyric, songDetail, unlikeSong } from '@/api'
import { useAudio } from '@/composables/useAudio'
import { useUserStore } from '@/stores/modules/user'
import { withImageParam } from '@/utils/media'
import { transformSongs } from '@/utils/transformers'

const route = useRoute()
const userStore = useUserStore()
const showLogin = ref(false)
const showSaveToPlaylist = ref(false)
const router = useRouter()

const state = reactive({
  id: String(route.params.id || ''),
  info: null as Record<string, any> | null,
  name: '',
  artist: '',
  album: '',
  artistId: 0 as number | string,
  albumId: 0 as number | string,
  duration: 0,
  cover: '',
  lrc: [] as Array<{ time: number; text: string }>,
  loading: true,
  liked: false,
})

const ensureAuthenticated = () => {
  if (userStore.isAuthenticated) return true
  if (userStore.isLoggedIn) userStore.logout()
  showLogin.value = true
  return false
}

const { play } = useAudio()

const artistEntries = computed(() => {
  const artists = Array.isArray(state.info?.artists)
    ? state.info.artists
    : Array.isArray((state.info as any)?.ar)
      ? (state.info as any).ar
      : []

  return artists
    .filter((artist: any) => artist?.name)
    .map((artist: any) => ({
      id: artist.id || '',
      name: artist.name,
    }))
})

const parseLrc = (raw: string) => {
  const lines = raw.split(/\r?\n/)
  const result: Array<{ time: number; text: string }> = []
  for (const line of lines) {
    const m = line.match(/\[(\d{2}):(\d{2})\.(\d{2,3})\](.*)/)
    if (!m) continue
    const min = parseInt(m[1])
    const sec = parseInt(m[2])
    const ms = parseInt(m[3].slice(0, 2))
    const time = min * 60 + sec + ms / 100
    result.push({ time, text: m[4].trim() })
  }
  return result
}

const load = async (id: string) => {
  try {
    const [detailRes, lrcRes] = await Promise.all([songDetail({ ids: id }), lyric({ id })])
    const song = transformSongs(detailRes as Record<string, unknown>, 1)[0]
    const rawSong =
      (detailRes as any)?.songs?.[0] ||
      (detailRes as any)?.data?.songs?.[0] ||
      (detailRes as any)?.data?.[0]
    if (song) {
      state.info = rawSong ? { ...rawSong, ...song } : song
      state.name = song.name || ''
      state.artist = song.artist || ''
      state.album = song.album || ''
      state.artistId = song.artistId || song.artists?.[0]?.id || 0
      state.albumId = song.albumId || 0
      state.duration = song.duration ?? 0
      state.cover = song.cover || ''
    }
    const raw = (lrcRes as any)?.lrc?.lyric || (lrcRes as any)?.lyric || ''
    state.lrc = raw ? parseLrc(raw) : []
    await refreshLikeState()
  } finally {
    state.loading = false
  }
}

onMounted(() => {
  if (state.id) {
    state.loading = true
    load(state.id)
  }
})

const playCurrent = () => {
  const s = {
    id: state.id,
    name: state.name,
    artist: state.artist,
    album: state.album,
    duration: state.duration || 0,
    artistId: state.artistId,
    artists: artistEntries.value,
    albumId: state.albumId,
    cover: state.cover,
    liked: state.liked,
  }
  play(s, 0)
}

const refreshLikeState = async () => {
  if (!state.id) {
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
    state.liked = (res?.data || []).map(String).includes(String(state.id))
  } catch {}
}

const toggleLike = async () => {
  if (!ensureAuthenticated()) return
  const nextLiked = !state.liked
  state.liked = nextLiked
  try {
    const res = nextLiked ? await likeSong(state.id) : await unlikeSong(state.id)
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
    console.error('Failed to toggle like state on mobile song page:', error)
  }
}

const openSaveToPlaylist = () => {
  if (!state.id) return
  if (!ensureAuthenticated()) return
  showSaveToPlaylist.value = true
}

watch(() => userStore.isAuthenticated, refreshLikeState)
</script>

<template>
  <div class="flex-1 overflow-auto px-3 pb-6">
    <div v-if="state.loading" class="py-6">
      <PageSkeleton :sections="['hero', 'list']" :list-count="6" />
    </div>
    <template v-else>
      <section class="mb-4">
        <div class="flex items-center gap-3">
          <div class="h-16 w-16 shrink-0 overflow-hidden rounded-xl">
            <img
              v-if="state.cover"
              :src="withImageParam(state.cover, '300y300')"
              alt="cover"
              class="h-full w-full object-cover"
            />
            <div
              v-else
              class="flex h-full w-full items-center justify-center rounded-xl bg-white/10"
            >
              🎵
            </div>
          </div>
          <div class="min-w-0 flex-1">
            <h1 class="text-primary truncate text-lg font-bold">{{ state.name }}</h1>
            <div class="mt-1 flex flex-wrap items-center gap-x-1.5 gap-y-1 text-xs text-purple-300">
              <template v-for="(artist, index) in artistEntries" :key="`${artist.id}-${artist.name}-${index}`">
                <button
                  type="button"
                  class="truncate transition-colors hover:text-pink-300"
                  @click="artist.id && router.push(`/artist/${artist.id}`)"
                >
                  {{ artist.name }}
                </button>
                <span v-if="index < artistEntries.length - 1" class="text-purple-400">/</span>
              </template>
              <span v-if="!artistEntries.length" class="truncate">{{ state.artist }}</span>
            </div>
            <button
              type="button"
              class="truncate text-[11px] text-purple-400 transition-colors hover:text-pink-300"
              @click="state.albumId && router.push(`/album/${state.albumId}`)"
            >
              {{ state.album }}
            </button>
          </div>
          <div class="flex items-center gap-2">
            <button
              class="glass-button text-primary rounded-full bg-linear-to-r from-pink-500 to-purple-600 px-3 py-2 text-sm"
              @click="playCurrent"
            >
              <span class="icon-[mdi--play] h-4 w-4"></span>
            </button>
            <button
              class="glass-button text-primary rounded-full px-3 py-2 text-sm"
              @click="toggleLike"
            >
              <span
                :class="state.liked ? 'icon-[mdi--heart]' : 'icon-[mdi--heart-outline]'"
                class="h-4 w-4"
              ></span>
            </button>
            <button
              class="glass-button text-primary rounded-full px-3 py-2 text-sm"
              @click="openSaveToPlaylist"
            >
              <span class="icon-[mdi--playlist-plus] h-4 w-4"></span>
            </button>
          </div>
        </div>
      </section>

      <section>
        <h3 class="text-primary mb-2 text-sm font-semibold">{{ $t('song.lyrics') }}</h3>
        <div class="glass-card max-h-64 overflow-auto p-3">
          <p v-for="(l, i) in state.lrc" :key="i" class="text-primary/80 mb-1 text-sm">
            {{ l.text }}
          </p>
          <p v-if="!state.lrc.length" class="text-primary/60 text-sm">{{ $t('song.noLyrics') }}</p>
        </div>
      </section>
    </template>
    <LoginDialog v-if="showLogin" @close="showLogin = false" @success="refreshLikeState" />
    <SaveToPlaylistDialog
      v-if="showSaveToPlaylist && state.id"
      :song-id="state.id"
      :song-name="state.name"
      @close="showSaveToPlaylist = false"
    />
  </div>
</template>
