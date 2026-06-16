<script setup lang="ts">
import {
  clientPlaylistDetail,
  commentNew,
  collectPlaylist,
  deleteMyPlaylist,
  myPlaylistDetail,
  myPlaylists,
  pinMyPlaylist,
  playlistDetail,
  playlistTrackAll,
  removeSongFromMyPlaylist,
  search,
  uncollectPlaylist,
  updateMyPlaylist,
  type ClientPlaylist,
} from '@/api'
import Button from '@/components/Ui/Button.vue'
import LazyImage from '@/components/Ui/LazyImage.vue'
import TabGroup from '@/components/Ui/TabGroup.vue'
import { usePlayActions } from '@/composables/usePlayActions'
import { useUserStore } from '@/stores/modules/user'
import { CommentItem, PlaylistInfo } from '@/typings'
import { withImageParam } from '@/utils/media'
import { formatCount } from '@/utils/time'
import {
  transformClientPlaylistDetail,
  transformPlaylistDetail,
  transformSearchPlaylists,
  transformSongs,
  type SongData,
} from '@/utils/transformers'
import { useI18n } from 'vue-i18n'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const { t } = useI18n()
const { playAll: playAllAction, shufflePlay: shufflePlayAction } = usePlayActions()

const currentPlaylistId = computed(() => Number(route.params.id))

interface SimilarPlaylist {
  id: number | string
  name: string
  coverImgUrl: string
  trackCount?: number
  playCount?: number
  creator?: { nickname: string }
}

interface PlaylistState {
  activeTab: 'songs' | 'comments' | 'similar'
  playlistInfo: PlaylistInfo
  isCollected: boolean
  songs: SongData[]
  songPage: number
  newComment: string
  comments: CommentItem[]
  isPageLoading: boolean
  similarPlaylists: SimilarPlaylist[]
  myPlaylist: ClientPlaylist | null
  isLocalPlaylist: boolean
  showEditPlaylist: boolean
  playlistActionLoading: boolean
  playlistError: string
  editForm: {
    name: string
    cover: string
    description: string
    status: number
  }
}

const gradients = ['from-purple-500 to-pink-500']
const PLAYLIST_SONG_PAGE_SIZE = 80

const state = reactive<PlaylistState>({
  activeTab: 'songs',
  playlistInfo: {
    name: '',
    description: '',
    creator: '',
    creatorAvatar: '',
    createTime: '',
    songCount: 0,
    playCount: 0,
    likes: '0',
    category: '',
    gradient: gradients[0],
    coverImgUrl: '/default-cover.svg',
  },
  isCollected: false,
  songs: [],
  songPage: 1,
  newComment: '',
  comments: [],
  isPageLoading: true,
  similarPlaylists: [],
  myPlaylist: null,
  isLocalPlaylist: false,
  showEditPlaylist: false,
  playlistActionLoading: false,
  playlistError: '',
  editForm: {
    name: '',
    cover: '',
    description: '',
    status: 0,
  },
})

const {
  activeTab,
  playlistInfo,
  songs,
  newComment,
  comments,
  isPageLoading,
  similarPlaylists,
  myPlaylist,
  showEditPlaylist,
  playlistActionLoading,
  playlistError,
  editForm,
} = toRefs(state)

const isSubscribedPlaylist = computed(() => Boolean(state.myPlaylist?.subscribed))
const isOwnedPlaylist = computed(() => Boolean(state.myPlaylist && !state.myPlaylist.subscribed))
const isOwnedNormalPlaylist = computed(
  () => state.myPlaylist?.type === 'normal' && !isSubscribedPlaylist.value
)
const isLikedPlaylist = computed(
  () => state.myPlaylist?.type === 'liked' && !isSubscribedPlaylist.value
)
const isPinnedPlaylist = computed(() => Number(state.myPlaylist?.pinned || 0) === 1)
const isPublishedPlaylist = computed(() => Number(state.myPlaylist?.status || 0) === 1)
const showCoverCollectButton = computed(
  () => state.isLocalPlaylist && !isOwnedPlaylist.value && !isLikedPlaylist.value
)
const showUtilityActionButton = computed(() => isOwnedNormalPlaylist.value)
const visibleSongs = computed(() => state.songs.slice(0, state.songPage * PLAYLIST_SONG_PAGE_SIZE))
const hasMoreSongs = computed(() => visibleSongs.value.length < state.songs.length)

const currentPlaylistCategory = computed(() => {
  if (isLikedPlaylist.value) return t('playlist.likedBadge')
  if (isOwnedNormalPlaylist.value) {
    return isPublishedPlaylist.value
      ? t('playlist.visibility.public')
      : t('playlist.visibility.private')
  }
  return state.playlistInfo.category || t('home.playlistFallback')
})

const statusBadgeClass = computed(() => {
  if (isLikedPlaylist.value) return 'bg-pink-500/20 text-pink-200'
  if (isPublishedPlaylist.value) return 'bg-emerald-500/20 text-emerald-200'
  return 'bg-white/10 text-white/80'
})

const utilityButtonIcon = computed(() => {
  if (isOwnedNormalPlaylist.value) {
    return isPublishedPlaylist.value ? 'icon-[mdi--earth-off]' : 'icon-[mdi--earth]'
  }
  return state.isCollected ? 'icon-[mdi--heart]' : 'icon-[mdi--heart-outline]'
})

const utilityButtonLabel = computed(() => {
  if (isOwnedNormalPlaylist.value) {
    return isPublishedPlaylist.value
      ? t('playlist.actions.makePrivate')
      : t('playlist.actions.publish')
  }
  return state.isCollected ? t('common.uncollect') : t('common.collect')
})

const utilityButtonClass = computed(() => {
  if (isOwnedNormalPlaylist.value) {
    return isPublishedPlaylist.value ? 'bg-sky-500/20 text-sky-300 hover:bg-sky-500/30' : ''
  }
  return state.isCollected ? 'bg-red-500/20 text-red-400 hover:bg-red-500/30' : ''
})

const tabs = [
  { key: 'songs', labelKey: 'playlist.tabs.songs', icon: 'icon-[mdi--music-note]' },
  { key: 'comments', labelKey: 'playlist.tabs.comments', icon: 'icon-[mdi--comment-text]' },
  { key: 'similar', labelKey: 'playlist.tabs.similar', icon: 'icon-[mdi--playlist-music]' },
] as const

const tabsWithCount = computed(() =>
  tabs.map(tab => ({
    ...tab,
    count:
      tab.key === 'songs'
        ? songs.value.length
        : tab.key === 'comments'
          ? comments.value.length
          : similarPlaylists.value.length,
  }))
)

const editStatusOptions = [
  { value: 0, label: 'playlist.visibility.private' },
  { value: 1, label: 'playlist.visibility.public' },
]

const pickGradient = () => gradients[Math.floor(Math.random() * gradients.length)]

const dispatchPlaylistsUpdated = () => {
  window.dispatchEvent(new CustomEvent('sonora:playlists-updated'))
}

const syncPlaylistInfoFromMine = (playlist: ClientPlaylist) => {
  state.playlistInfo = {
    ...state.playlistInfo,
    name: playlist.name || state.playlistInfo.name,
    description: playlist.description || '',
    coverImgUrl: playlist.cover || state.playlistInfo.coverImgUrl,
    songCount: playlist.songCount ?? state.playlistInfo.songCount,
    category:
      playlist.type === 'liked'
        ? t('playlist.likedBadge')
        : Number(playlist.status || 0) === 1
          ? t('playlist.visibility.public')
          : t('playlist.visibility.private'),
  }
}

const loadMyPlaylistMeta = async (id: number) => {
  if (!userStore.isLoggedIn || Number.isNaN(id) || id <= 0) {
    state.myPlaylist = null
    state.isCollected = false
    return null
  }
  try {
    const res = await myPlaylists()
    const found = (res?.data || []).find(playlist => Number(playlist.id) === id) || null
    state.myPlaylist = found
    state.isCollected = Boolean(found?.subscribed)
    if (found && !found.subscribed) syncPlaylistInfoFromMine(found)
    return found && !found.subscribed ? found : null
  } catch {
    state.myPlaylist = null
    state.isCollected = false
    return null
  }
}

const loadPublicPlaylist = async (id: number) => {
  state.isLocalPlaylist = false
  try {
    const res = await clientPlaylistDetail(id)
    if (res?.code === 200 && res.data) {
      const detail = transformClientPlaylistDetail(
        res as Record<string, unknown>,
        t('home.playlistFallback')
      )
      if (detail) {
        state.playlistInfo = {
          name: detail.name,
          description: detail.description,
          creator: detail.creator,
          creatorAvatar: detail.creatorAvatar,
          createTime: detail.createTime,
          songCount: detail.songCount,
          playCount: Number(detail.playCount || 0),
          likes: String(detail.likes || 0),
          category: detail.category,
          gradient: pickGradient(),
          coverImgUrl: detail.coverImgUrl,
        }
      }
      state.isLocalPlaylist = true
      state.isCollected = Boolean(res.data.subscribed || state.myPlaylist?.subscribed)
      state.songs = transformSongs(res as Record<string, unknown>, 500)
      return
    }
  } catch {}

  const [detailRes, tracksRes] = await Promise.all([
    playlistDetail({ id }),
    playlistTrackAll({ id, limit: 200 }),
  ])

  const detail = transformPlaylistDetail(detailRes as Record<string, unknown>, t('home.playlistFallback'))
  if (detail) {
    state.playlistInfo = {
      name: detail.name,
      description: detail.description,
      creator: detail.creator,
      creatorAvatar: detail.creatorAvatar,
      createTime: detail.createTime,
      songCount: detail.songCount,
      playCount: Number(detail.playCount || 0),
      likes: String(detail.likes || 0),
      category: detail.category,
      gradient: pickGradient(),
      coverImgUrl: detail.coverImgUrl,
    }
  }

  state.songPage = 1
  state.songs = transformSongs(tracksRes as Record<string, unknown>, 200)
}

const loadOwnedPlaylist = async (id: number) => {
  state.isLocalPlaylist = true
  const res = await myPlaylistDetail(id)
  const detail = transformClientPlaylistDetail(
    res as Record<string, unknown>,
    currentPlaylistCategory.value
  )
  if (detail) {
    state.playlistInfo = {
      name: detail.name,
      description: detail.description,
      creator: detail.creator || t('common.me'),
      creatorAvatar: detail.creatorAvatar,
      createTime: detail.createTime,
      songCount: detail.songCount,
      playCount: Number(detail.playCount || 0),
      likes: String(detail.likes || 0),
      category: detail.category,
      gradient: pickGradient(),
      coverImgUrl: detail.coverImgUrl,
    }
  }

  state.songPage = 1
  state.songs = transformSongs(res as Record<string, unknown>, 500)
}

const loadComments = async (id: number) => {
  try {
    const res = await commentNew({ id, type: 2, sortType: 1, pageNo: 1, pageSize: 10 })
    const list = (res as any)?.data?.comments || (res as any)?.comments || []
    if (!Array.isArray(list)) {
      state.comments = []
      return
    }
    state.comments = list.map((c: any, index: number) => ({
      username: c?.user?.nickname || t('comments.user'),
      avatarGradient: gradients[index % gradients.length],
      time: c?.time ? new Date(c.time).toLocaleString() : '',
      content: c?.content || '',
      likes: c?.likedCount || 0,
      avatarUrl: c?.user?.avatarUrl || '',
      replies: (c?.beReplied || []).map((reply: any) => ({
        username: reply?.user?.nickname || t('comments.user'),
        avatarUrl: reply?.user?.avatarUrl || '',
        avatarGradient: gradients[(index + 1) % gradients.length],
        time: '',
        content: reply?.content || '',
      })),
    }))
  } catch {
    state.comments = []
  }
}

const loadSimilarPlaylists = async (name: string) => {
  if (!name) {
    state.similarPlaylists = []
    return
  }
  try {
    const res = await search({ keywords: name, type: 1000 })
    const { playlists } = transformSearchPlaylists(res as Record<string, unknown>, 12)
    state.similarPlaylists = playlists.map(playlist => ({
      id: playlist.id,
      name: playlist.name,
      coverImgUrl: playlist.coverImgUrl,
      trackCount: playlist.trackCount,
      playCount: playlist.playCount,
    }))
  } catch {
    state.similarPlaylists = []
  }
}

const loadPlaylistPage = async (id: number) => {
  if (Number.isNaN(id) || id <= 0) return
  state.isPageLoading = true
  state.playlistError = ''
  try {
    const mine = await loadMyPlaylistMeta(id)
    await Promise.allSettled([
      mine ? loadOwnedPlaylist(id) : loadPublicPlaylist(id),
      loadComments(id),
    ])
  } finally {
    state.isPageLoading = false
  }
}

const refreshPlaylist = async () => {
  await loadPlaylistPage(currentPlaylistId.value)
}

const submitComment = () => {
  if (!state.newComment.trim()) return
  state.comments.unshift({
    username: t('common.me'),
    avatarUrl: '',
    avatarGradient: 'from-pink-400 to-purple-500',
    time: t('common.justNow'),
    content: state.newComment.trim(),
    likes: 0,
    replies: [],
  })
  state.newComment = ''
}

const playAll = () => playAllAction(state.songs)
const shufflePlay = () => shufflePlayAction(state.songs)
const loadMoreSongs = () => {
  if (!hasMoreSongs.value) return
  state.songPage += 1
}

const toggleCollect = async () => {
  if (!userStore.isAuthenticated) {
    if (userStore.isLoggedIn) userStore.logout()
    return
  }
  if (!state.isLocalPlaylist || isOwnedPlaylist.value) return
  state.playlistActionLoading = true
  try {
    const res = state.isCollected
      ? await uncollectPlaylist(currentPlaylistId.value)
      : await collectPlaylist(currentPlaylistId.value)
    if (res?.code !== 200 || !res.data) {
      throw new Error(res?.message || t('playlist.messages.actionFailed'))
    }
    state.isCollected = !state.isCollected
    state.playlistInfo.likes = String(res.data.collectCount ?? state.playlistInfo.likes)
    state.myPlaylist = state.isCollected ? { ...res.data, subscribed: true } : null
    dispatchPlaylistsUpdated()
  } catch (error: any) {
    state.playlistError =
      error?.response?.data?.message || error?.message || t('playlist.messages.actionFailed')
  } finally {
    state.playlistActionLoading = false
  }
}

const handleUtilityAction = () => {
  if (isOwnedNormalPlaylist.value) {
    void togglePublishPlaylist()
    return
  }
  void toggleCollect()
}

const sharePlaylist = async () => {
  const url = `${location.origin}${location.pathname}#/playlist/${currentPlaylistId.value}`
  const title = state.playlistInfo.name || t('home.playlistFallback')
  const text = state.playlistInfo.description || ''
  try {
    if (navigator.share) {
      await navigator.share({ title, text, url })
    } else {
      await navigator.clipboard.writeText(url)
    }
  } catch {}
}

const openEditPlaylist = () => {
  if (!isOwnedNormalPlaylist.value) return
  state.playlistError = ''
  state.editForm = {
    name: state.myPlaylist?.name || state.playlistInfo.name || '',
    cover: state.myPlaylist?.cover || '',
    description: state.myPlaylist?.description || state.playlistInfo.description || '',
    status: Number(state.myPlaylist?.status || 0),
  }
  state.showEditPlaylist = true
}

const submitEditPlaylist = async () => {
  if (!isOwnedNormalPlaylist.value) return
  const name = state.editForm.name.trim()
  if (!name) {
    state.playlistError = t('playlist.messages.nameRequired')
    return
  }

  state.playlistActionLoading = true
  state.playlistError = ''
  try {
    const res = await updateMyPlaylist(currentPlaylistId.value, {
      name,
      cover: state.editForm.cover.trim(),
      description: state.editForm.description.trim(),
      status: state.editForm.status,
    })
    if (res?.code !== 200 || !res.data) {
      throw new Error(res?.message || t('playlist.messages.saveFailed'))
    }
    state.myPlaylist = res.data
    syncPlaylistInfoFromMine(res.data)
    dispatchPlaylistsUpdated()
    state.showEditPlaylist = false
  } catch (error: any) {
    state.playlistError =
      error?.response?.data?.message || error?.message || t('playlist.messages.saveFailed')
  } finally {
    state.playlistActionLoading = false
  }
}

const togglePinPlaylist = async () => {
  if (!isOwnedNormalPlaylist.value) return
  state.playlistActionLoading = true
  try {
    const res = await pinMyPlaylist(currentPlaylistId.value, !isPinnedPlaylist.value)
    if (res?.code !== 200 || !res.data) {
      throw new Error(res?.message || t('playlist.messages.actionFailed'))
    }
    state.myPlaylist = res.data
    syncPlaylistInfoFromMine(res.data)
    dispatchPlaylistsUpdated()
  } catch {} finally {
    state.playlistActionLoading = false
  }
}

const togglePublishPlaylist = async () => {
  if (!isOwnedNormalPlaylist.value) return
  state.playlistActionLoading = true
  try {
    const res = await updateMyPlaylist(currentPlaylistId.value, {
      status: isPublishedPlaylist.value ? 0 : 1,
    })
    if (res?.code !== 200 || !res.data) {
      throw new Error(res?.message || t('playlist.messages.actionFailed'))
    }
    state.myPlaylist = res.data
    syncPlaylistInfoFromMine(res.data)
    dispatchPlaylistsUpdated()
  } catch {} finally {
    state.playlistActionLoading = false
  }
}

const removeCurrentPlaylist = async () => {
  if (!isOwnedNormalPlaylist.value) return
  if (!window.confirm(t('playlist.messages.deleteConfirm'))) return

  state.playlistActionLoading = true
  try {
    const res = await deleteMyPlaylist(currentPlaylistId.value)
    if (res?.code !== 200) {
      throw new Error(res?.message || t('playlist.messages.deleteFailed'))
    }
    dispatchPlaylistsUpdated()
    router.push('/my-music')
  } catch {} finally {
    state.playlistActionLoading = false
  }
}

const removeSongFromCurrentPlaylist = async (song: SongData, index: number) => {
  if (!isOwnedNormalPlaylist.value || !song.id) return
  try {
    const res = await removeSongFromMyPlaylist(currentPlaylistId.value, song.id)
    if (res?.code !== 200) {
      throw new Error(res?.message || t('playlist.messages.removeSongFailed'))
    }
    state.songs.splice(index, 1)
    state.playlistInfo.songCount = state.songs.length
    dispatchPlaylistsUpdated()
    if (index === 0) await refreshPlaylist()
  } catch {}
}

const handleSongLikeChanged = async () => {
  if (isOwnedPlaylist.value) {
    await refreshPlaylist()
  }
}

watch(
  () => state.playlistInfo.name,
  name => {
    if (name) loadSimilarPlaylists(name)
  }
)

watch(
  () => Number(route.params.id),
  id => {
    state.activeTab = 'songs'
    state.isCollected = false
    state.isLocalPlaylist = false
    state.songPage = 1
    state.comments = []
    state.similarPlaylists = []
    state.songs = []
    void loadPlaylistPage(id)
  },
  { immediate: true }
)

watch(
  () => userStore.isLoggedIn,
  () => {
    void loadPlaylistPage(currentPlaylistId.value)
  }
)
</script>

<template>
  <div class="w-full overflow-x-hidden p-4">
    <PageSkeleton v-if="isPageLoading" :sections="['hero', 'list']" :list-count="12" />
    <template v-else>
      <div class="flex flex-col gap-3">
        <div class="relative">
          <div class="absolute inset-0 overflow-hidden rounded-3xl">
            <img
              :src="withImageParam(playlistInfo.coverImgUrl, '100y100')"
              class="h-full w-full scale-150 object-cover opacity-30 blur-3xl"
            />
            <div class="to-overlay absolute inset-0 bg-linear-to-b from-transparent via-transparent" />
          </div>

          <div class="relative z-10 overflow-hidden rounded-3xl">
            <div class="glass-container">
              <div class="flex flex-col gap-6 p-6 lg:flex-row lg:gap-10">
                <div class="group relative mx-auto w-56 shrink-0 lg:mx-0 lg:w-72">
                  <div class="aspect-square overflow-hidden rounded-3xl shadow-2xl ring-1 ring-glass">
                    <LazyImage
                      :src="withImageParam(playlistInfo.coverImgUrl, '400y400')"
                      :alt="$t('components.songList.coverAlt')"
                      imgClass="h-full w-full object-cover transition-transform duration-700 group-hover:scale-110"
                      wrapperClass="h-full w-full"
                    />
                  </div>
                  <button
                    v-if="showCoverCollectButton"
                    type="button"
                    class="absolute top-4 right-4 flex h-12 w-12 items-center justify-center rounded-full border border-white/12 bg-black/45 text-white shadow-lg backdrop-blur-md transition-all hover:scale-105 hover:bg-black/60"
                    :disabled="playlistActionLoading"
                    @click.stop="toggleCollect"
                  >
                    <span
                      :class="state.isCollected ? 'icon-[mdi--heart]' : 'icon-[mdi--heart-outline]'"
                      class="h-6 w-6"
                    ></span>
                  </button>
                  <Button
                    variant="ghost"
                    size="none"
                    class="absolute! inset-0 flex items-center justify-center rounded-3xl opacity-0 transition-all duration-300 group-hover:opacity-100"
                    @click="playAll"
                  >
                    <div
                      class="flex h-20 w-20 items-center justify-center rounded-full bg-linear-to-r from-pink-500 to-purple-600 text-white shadow-2xl transition-transform hover:scale-110"
                    >
                      <span class="icon-[mdi--play] h-10 w-10"></span>
                    </div>
                  </Button>
                </div>

                <div class="flex min-w-0 flex-1 flex-col justify-center text-center lg:text-left">
                  <div class="mb-4 flex flex-wrap items-center justify-center gap-3 lg:justify-start">
                    <span
                      class="inline-flex items-center rounded-full px-3 py-1 text-xs font-medium"
                      :class="statusBadgeClass"
                    >
                      {{ currentPlaylistCategory }}
                    </span>
                  </div>

                  <h1
                    class="text-primary mb-4 line-clamp-2 text-xl leading-tight font-bold lg:text-4xl xl:text-5xl"
                  >
                    {{ playlistInfo.name }}
                  </h1>

                  <div class="mb-5 flex flex-wrap items-center justify-center gap-3 lg:justify-start">
                    <img
                      v-if="playlistInfo.creatorAvatar"
                      :src="withImageParam(playlistInfo.creatorAvatar, '80y80')"
                      class="h-8 w-8 rounded-full ring-2 ring-glass"
                    />
                    <span class="text-primary font-medium">{{ playlistInfo.creator }}</span>
                    <template v-if="playlistInfo.createTime">
                      <span class="text-primary/40">•</span>
                      <span class="text-primary/60 text-sm">{{ playlistInfo.createTime }}</span>
                    </template>
                  </div>

                  <p
                    v-if="playlistInfo.description"
                    class="text-primary/70 mb-6 line-clamp-2 text-sm leading-relaxed lg:text-base"
                    :title="playlistInfo.description"
                  >
                    {{ playlistInfo.description }}
                  </p>

                  <div class="mb-6 flex flex-wrap items-center justify-center gap-6 lg:justify-start">
                    <div class="flex items-center gap-2">
                      <span class="icon-[mdi--music-note] text-primary/60 h-5 w-5"></span>
                      <span class="text-primary font-medium">
                        {{ $t('commonUnits.songsShort', { count: playlistInfo.songCount }) }}
                      </span>
                    </div>
                    <div class="flex items-center gap-2">
                      <span class="icon-[mdi--play-circle-outline] text-primary/60 h-5 w-5"></span>
                      <span class="text-primary font-medium">
                        {{ formatCount(playlistInfo.playCount || 0) }}
                        {{ $t('common.stats.plays') }}
                      </span>
                    </div>
                    <div class="flex items-center gap-2">
                      <span class="icon-[mdi--heart] h-5 w-5 text-red-400"></span>
                      <span class="text-primary font-medium">
                        {{ formatCount(Number(playlistInfo.likes) || 0) }}
                        {{ $t('common.stats.favorites') }}
                      </span>
                    </div>
                  </div>

                  <div class="flex flex-wrap items-center justify-center gap-3 lg:justify-start">
                    <Button
                      variant="solid"
                      size="md"
                      rounded="full"
                      class="px-8 py-3 shadow-lg shadow-pink-500/30 hover:shadow-xl hover:shadow-pink-500/40"
                      @click="playAll"
                    >
                      <span class="icon-[mdi--play] mr-2 h-5 w-5"></span>
                      {{ $t('actions.playAll') }}
                    </Button>
                    <Button
                      variant="soft"
                      size="md"
                      rounded="full"
                      class="px-6 py-3"
                      @click="shufflePlay"
                    >
                      <span class="icon-[mdi--shuffle] mr-2 h-5 w-5"></span>
                      {{ $t('actions.shufflePlay') }}
                    </Button>
                    <Button
                      v-if="showUtilityActionButton"
                      variant="soft"
                      size="md"
                      rounded="full"
                      class="px-6 py-3"
                      :class="utilityButtonClass"
                      :disabled="playlistActionLoading"
                      @click="handleUtilityAction"
                    >
                      <span :class="utilityButtonIcon" class="mr-2 h-5 w-5"></span>
                      {{ utilityButtonLabel }}
                    </Button>
                    <Button
                      variant="soft"
                      size="icon-md"
                      rounded="full"
                      class="h-11 w-11"
                      :title="$t('common.share')"
                      @click="sharePlaylist"
                    >
                      <span class="icon-[mdi--share-variant] h-5 w-5"></span>
                    </Button>

                    <div v-if="isOwnedNormalPlaylist" class="flex items-center gap-3">
                      <Button
                        variant="soft"
                        size="md"
                        rounded="full"
                        class="px-5 py-3"
                        icon="icon-[mdi--playlist-edit]"
                        icon-class="mr-2 h-5 w-5"
                        :disabled="playlistActionLoading"
                        @click="openEditPlaylist"
                      >
                        {{ $t('playlist.actions.edit') }}
                      </Button>
                      <Button
                        variant="soft"
                        size="icon-md"
                        rounded="full"
                        class="h-11 w-11"
                        :class="isPinnedPlaylist ? 'text-pink-300' : ''"
                        :icon="isPinnedPlaylist ? 'icon-[mdi--pin-off]' : 'icon-[mdi--pin]'"
                        icon-class="h-5 w-5"
                        :title="
                          isPinnedPlaylist
                            ? $t('playlist.actions.unpin')
                            : $t('playlist.actions.pin')
                        "
                        :disabled="playlistActionLoading"
                        @click="togglePinPlaylist"
                      />
                      <Button
                        variant="soft"
                        size="icon-md"
                        rounded="full"
                        class="h-11 w-11 text-red-300"
                        icon="icon-[mdi--delete-outline]"
                        icon-class="h-5 w-5"
                        :title="$t('playlist.actions.delete')"
                        :disabled="playlistActionLoading"
                        @click="removeCurrentPlaylist"
                      />
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>

        <div class="flex items-center justify-between">
          <TabGroup v-model="activeTab" :tabs="tabsWithCount" class="w-full" size="md" />
        </div>

        <section v-show="activeTab === 'songs'" class="h-full overflow-hidden">
          <SongList
            :songs="visibleSongs"
            :show-header="true"
            :allow-remove="isOwnedNormalPlaylist"
            @remove="removeSongFromCurrentPlaylist"
            @like="handleSongLikeChanged"
          />
          <div v-if="hasMoreSongs" class="mt-4 text-center">
            <button class="glass-button px-6 py-2 text-sm" @click="loadMoreSongs">
              {{ $t('playlists.loadMore') }}
            </button>
          </div>
        </section>

        <section v-show="activeTab === 'comments'" class="animate-fade-in">
          <div class="glass-card overflow-hidden">
            <div class="border-b border-glass p-6">
              <div class="flex gap-4">
                <div
                  class="accent-gradient flex h-11 w-11 shrink-0 items-center justify-center rounded-full font-semibold text-white shadow-md"
                >
                  {{ $t('common.me') }}
                </div>
                <div class="flex-1">
                  <textarea
                    v-model="newComment"
                    :placeholder="$t('comments.placeholder')"
                    class="text-primary glass-card placeholder-glass-50 w-full resize-none rounded-xl border border-glass p-4 text-sm transition-all focus:border-pink-400/50 focus:ring-2 focus:ring-pink-400/20 focus:outline-none"
                    rows="3"
                  ></textarea>
                  <div class="mt-3 flex items-center justify-end">
                    <Button
                      variant="gradient"
                      size="md"
                      rounded="full"
                      class="px-6 py-2.5 shadow-lg transition-all hover:shadow-xl"
                      :disabled="!newComment.trim()"
                      @click="submitComment"
                    >
                      {{ $t('comments.publish') }}
                    </Button>
                  </div>
                </div>
              </div>
            </div>

            <div v-if="comments.length" class="divide-y divide-glass">
              <div
                v-for="(comment, index) in comments"
                :key="index"
                class="hover:bg-hover-glass p-6 transition-colors"
              >
                <div class="flex gap-4">
                  <img
                    v-if="comment.avatarUrl"
                    :src="withImageParam(comment.avatarUrl, '100y100')"
                    class="h-11 w-11 shrink-0 rounded-full ring-2 ring-glass"
                  />
                  <div
                    v-else
                    class="flex h-11 w-11 shrink-0 items-center justify-center rounded-full bg-linear-to-br text-sm font-semibold text-white"
                    :class="comment.avatarGradient"
                  >
                    {{ comment.username.charAt(0) }}
                  </div>

                  <div class="min-w-0 flex-1">
                    <div class="mb-2 flex items-center gap-3">
                      <span class="text-primary text-sm font-semibold">{{ comment.username }}</span>
                      <span class="text-primary/50 text-xs">{{ comment.time }}</span>
                    </div>

                    <p class="text-primary/80 mb-4 text-sm leading-relaxed">
                      {{ comment.content }}
                    </p>

                    <div class="flex items-center gap-5 text-xs">
                      <Button
                        variant="ghost"
                        size="none"
                        class="text-primary/60 hover:text-primary flex items-center gap-1.5 transition-colors"
                        icon="icon-[mdi--thumb-up-outline]"
                        icon-class="h-4 w-4"
                      >
                        <span class="font-medium">{{ comment.likes || '' }}</span>
                      </Button>
                      <Button
                        variant="ghost"
                        size="none"
                        class="text-primary/60 hover:text-primary flex items-center gap-1.5 transition-colors"
                        icon="icon-[mdi--reply]"
                        icon-class="h-4 w-4"
                      >
                        <span class="font-medium">{{ $t('comments.reply') }}</span>
                      </Button>
                    </div>

                    <div v-if="comment.replies?.length" class="glass-card mt-4 space-y-3 rounded-xl p-4">
                      <div v-for="(reply, ri) in comment.replies" :key="ri" class="flex gap-3">
                        <img
                          v-if="reply.avatarUrl"
                          :src="withImageParam(reply.avatarUrl, '80y80')"
                          class="h-8 w-8 shrink-0 rounded-full ring-1 ring-glass"
                        />
                        <div class="min-w-0 flex-1">
                          <span class="text-primary text-xs font-semibold">{{ reply.username }}</span>
                          <p class="text-primary/70 mt-1 text-xs leading-relaxed">
                            {{ reply.content }}
                          </p>
                        </div>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </div>

            <div v-else class="flex flex-col items-center justify-center py-20">
              <span class="icon-[mdi--comment-off-outline] text-primary/20 mb-4 h-16 w-16"></span>
              <p class="text-primary/50 text-sm font-medium">{{ $t('comments.empty') }}</p>
            </div>
          </div>
        </section>

        <section v-show="activeTab === 'similar'" class="animate-fade-in">
          <div
            v-if="similarPlaylists.length"
            class="grid grid-cols-2 gap-4 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 xl:grid-cols-6"
          >
            <router-link
              v-for="playlist in similarPlaylists"
              :key="playlist.id"
              :to="`/playlist/${playlist.id}`"
              class="group"
            >
              <div class="glass-card overflow-hidden transition-all duration-300 hover:scale-105 hover:shadow-xl">
                <div class="relative aspect-square overflow-hidden">
                  <LazyImage
                    :src="withImageParam(playlist.coverImgUrl, '300y300')"
                    :alt="playlist.name"
                    imgClass="h-full w-full object-cover transition-transform duration-700 group-hover:scale-110"
                    wrapperClass="h-full w-full"
                  />
                  <div
                    class="absolute inset-0 flex items-center justify-center bg-black/50 opacity-0 backdrop-blur-sm transition-opacity duration-300 group-hover:opacity-100"
                  >
                    <div class="accent-gradient flex h-14 w-14 items-center justify-center rounded-full shadow-2xl">
                      <span class="icon-[mdi--play] h-7 w-7 text-white"></span>
                    </div>
                  </div>
                  <div
                    v-if="playlist.playCount"
                    class="absolute top-2 right-2 flex items-center gap-1 rounded-full bg-black/70 px-2.5 py-1 text-xs font-medium text-white backdrop-blur-md"
                  >
                    <span class="icon-[mdi--play] h-3.5 w-3.5"></span>
                    {{ formatCount(playlist.playCount) }}
                  </div>
                </div>
                <div class="p-3">
                  <p class="text-primary mb-1.5 line-clamp-2 text-sm leading-snug font-semibold transition-colors group-hover:text-pink-400">
                    {{ playlist.name }}
                  </p>
                  <p class="text-primary/60 text-xs">
                    {{ $t('commonUnits.songsShort', { count: playlist.trackCount || 0 }) }}
                    <span v-if="playlist.creator"> • {{ playlist.creator.nickname }}</span>
                  </p>
                </div>
              </div>
            </router-link>
          </div>
          <div v-else class="glass-card flex flex-col items-center justify-center py-20 text-center">
            <span class="icon-[mdi--playlist-remove] text-primary/20 mb-5 h-20 w-20"></span>
            <p class="text-primary/50 text-base font-medium">{{ $t('playlist.similarEmpty') }}</p>
          </div>
        </section>
      </div>
    </template>

    <div
      v-if="showEditPlaylist"
      class="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4 backdrop-blur-sm"
      @click.self="showEditPlaylist = false"
    >
      <div class="glass-container-strong w-full max-w-md p-6">
        <div class="mb-5 flex items-center justify-between">
          <h3 class="text-primary text-xl font-semibold">{{ $t('playlist.edit.title') }}</h3>
          <Button
            variant="soft"
            size="icon-sm"
            rounded="full"
            icon="icon-[mdi--close]"
            icon-class="h-4 w-4"
            :disabled="playlistActionLoading"
            @click="showEditPlaylist = false"
          />
        </div>

        <div class="space-y-4">
          <div>
            <label class="text-primary/60 mb-2 block text-xs font-medium">
              {{ $t('playlist.edit.name') }}
            </label>
            <input
              v-model="editForm.name"
              type="text"
              maxlength="80"
              class="text-primary glass-card w-full rounded-xl border border-glass px-4 py-3 text-sm outline-none placeholder:text-white/30 focus:border-pink-400/50"
              :placeholder="$t('playlist.edit.namePlaceholder')"
            />
          </div>

          <div>
            <label class="text-primary/60 mb-2 block text-xs font-medium">
              {{ $t('playlist.edit.cover') }}
            </label>
            <input
              v-model="editForm.cover"
              type="text"
              class="text-primary glass-card w-full rounded-xl border border-glass px-4 py-3 text-sm outline-none placeholder:text-white/30 focus:border-pink-400/50"
              :placeholder="$t('playlist.edit.coverPlaceholder')"
            />
          </div>

          <div>
            <label class="text-primary/60 mb-2 block text-xs font-medium">
              {{ $t('playlist.edit.description') }}
            </label>
            <textarea
              v-model="editForm.description"
              rows="4"
              maxlength="2000"
              class="text-primary glass-card w-full resize-none rounded-xl border border-glass px-4 py-3 text-sm outline-none placeholder:text-white/30 focus:border-pink-400/50"
              :placeholder="$t('playlist.edit.descriptionPlaceholder')"
            />
          </div>

          <div>
            <label class="text-primary/60 mb-2 block text-xs font-medium">
              {{ $t('playlist.edit.visibility') }}
            </label>
            <div class="grid grid-cols-2 gap-2">
              <button
                v-for="option in editStatusOptions"
                :key="option.value"
                type="button"
                class="rounded-xl border px-4 py-3 text-sm transition-colors"
                :class="
                  editForm.status === option.value
                    ? 'border-sky-400/60 bg-sky-500/20 text-sky-200'
                    : 'border-glass bg-white/5 text-primary/70 hover:bg-white/10'
                "
                @click="editForm.status = option.value"
              >
                {{ $t(option.label) }}
              </button>
            </div>
          </div>
        </div>

        <p v-if="playlistError" class="mt-4 text-sm text-red-300">{{ playlistError }}</p>

        <div class="mt-6 flex justify-end gap-3">
          <Button
            variant="ghost"
            size="md"
            rounded="full"
            :disabled="playlistActionLoading"
            @click="showEditPlaylist = false"
          >
            {{ $t('playlist.edit.cancel') }}
          </Button>
          <Button
            variant="solid"
            size="md"
            rounded="full"
            :loading="playlistActionLoading"
            :disabled="playlistActionLoading"
            icon="icon-[mdi--content-save-outline]"
            @click="submitEditPlaylist"
          >
            {{ $t('playlist.edit.save') }}
          </Button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.animate-fade-in {
  animation: fadeIn 0.4s cubic-bezier(0.4, 0, 0.2, 1);
}

@keyframes fadeIn {
  from {
    opacity: 0;
    transform: translateY(12px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}
</style>
