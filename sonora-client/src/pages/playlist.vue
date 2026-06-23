<script setup lang="ts">
import {
  clientPlaylistDetail,
  collectPlaylist,
  deleteMyPlaylist,
  myPlaylistDetail,
  myPlaylists,
  pinMyPlaylist,
  playlistDetail,
  playlistTrackAll,
  removeSongFromMyPlaylist,
  uncollectPlaylist,
  updateMyPlaylist,
  uploadMyPlaylistCover,
  type ClientPlaylist,
} from '@/api'
import LoginDialog from '@/components/Auth/LoginDialog.vue'
import Button from '@/components/Ui/Button.vue'
import LazyImage from '@/components/Ui/LazyImage.vue'
import { usePlayActions } from '@/composables/usePlayActions'
import { useUserStore } from '@/stores/modules/user'
import { withImageParam } from '@/utils/media'
import { formatCount } from '@/utils/time'
import {
  transformClientPlaylistDetail,
  transformPlaylistDetail,
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
const uploadInputRef = ref<HTMLInputElement | null>(null)

type PlaylistInfo = {
  name: string
  description: string
  creator: string
  creatorAvatar: string
  createTime: string
  songCount: number
  playCount: number
  likes: string
  category: string
  coverImgUrl: string
}

interface PlaylistState {
  playlistInfo: PlaylistInfo
  isCollected: boolean
  songs: SongData[]
  songPage: number
  isPageLoading: boolean
  myPlaylist: ClientPlaylist | null
  isLocalPlaylist: boolean
  showEditPlaylist: boolean
  playlistActionLoading: boolean
  coverUploading: boolean
  playlistError: string
  showLogin: boolean
  editForm: {
    name: string
    description: string
    status: number
    cover: string
    coverDirty: boolean
    coverFileName: string
  }
}

const PLAYLIST_SONG_PAGE_SIZE = 80

const state = reactive<PlaylistState>({
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
    coverImgUrl: '/default-cover.svg',
  },
  isCollected: false,
  songs: [],
  songPage: 1,
  isPageLoading: true,
  myPlaylist: null,
  isLocalPlaylist: false,
  showEditPlaylist: false,
  playlistActionLoading: false,
  coverUploading: false,
  playlistError: '',
  showLogin: false,
  editForm: {
    name: '',
    description: '',
    status: 0,
    cover: '',
    coverDirty: false,
    coverFileName: '',
  },
})

const {
  playlistInfo,
  isPageLoading,
  showEditPlaylist,
  playlistActionLoading,
  coverUploading,
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
const showUtilityActionButton = computed(
  () => (state.isLocalPlaylist && !isLikedPlaylist.value) || isOwnedNormalPlaylist.value
)
const visibleSongs = computed(() => state.songs.slice(0, state.songPage * PLAYLIST_SONG_PAGE_SIZE))
const hasMoreSongs = computed(() => visibleSongs.value.length < state.songs.length)
const editCoverPreview = computed(() => state.editForm.cover || state.playlistInfo.coverImgUrl)

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

const editStatusOptions = [
  { value: 0, label: 'playlist.visibility.private' },
  { value: 1, label: 'playlist.visibility.public' },
]

const dispatchPlaylistsUpdated = () => {
  window.dispatchEvent(new CustomEvent('sonora:playlists-updated'))
}

const ensureAuthenticated = () => {
  if (userStore.isAuthenticated) return true
  if (userStore.isLoggedIn) userStore.logout()
  state.showLogin = true
  return false
}

const syncPlaylistInfoFromMine = (playlist: ClientPlaylist) => {
  state.playlistInfo = {
    ...state.playlistInfo,
    name: playlist.name || state.playlistInfo.name,
    description: playlist.description || '',
    coverImgUrl: playlist.cover || state.playlistInfo.coverImgUrl,
    songCount: playlist.songCount ?? state.playlistInfo.songCount,
    playCount: Number(playlist.playCount ?? state.playlistInfo.playCount),
    likes: String(playlist.collectCount ?? state.playlistInfo.likes),
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
          coverImgUrl: detail.coverImgUrl,
        }
      }
      state.isLocalPlaylist = true
      state.isCollected = Boolean(res.data.subscribed || state.myPlaylist?.subscribed)
      state.songPage = 1
      state.songs = transformSongs(res as Record<string, unknown>, 500)
      return
    }
  } catch {}

  const [detailRes, tracksRes] = await Promise.all([
    playlistDetail({ id }),
    playlistTrackAll({ id, limit: 200 }),
  ])

  const detail = transformPlaylistDetail(
    detailRes as Record<string, unknown>,
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
      coverImgUrl: detail.coverImgUrl,
    }
  }

  state.songPage = 1
  state.songs = transformSongs(res as Record<string, unknown>, 500)
}

const loadPlaylistPage = async (id: number) => {
  if (Number.isNaN(id) || id <= 0) return
  state.isPageLoading = true
  state.playlistError = ''
  try {
    const mine = await loadMyPlaylistMeta(id)
    if (mine) {
      await loadOwnedPlaylist(id)
    } else {
      await loadPublicPlaylist(id)
    }
  } finally {
    state.isPageLoading = false
  }
}

const refreshPlaylist = async () => {
  await loadPlaylistPage(currentPlaylistId.value)
}

const playAll = () => playAllAction(state.songs)
const shufflePlay = () => shufflePlayAction(state.songs)

const loadMoreSongs = () => {
  if (!hasMoreSongs.value) return
  state.songPage += 1
}

const toggleCollect = async () => {
  if (!ensureAuthenticated()) return
  if (!state.isLocalPlaylist || isOwnedPlaylist.value) return
  state.playlistActionLoading = true
  state.playlistError = ''
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

const openEditPlaylist = () => {
  if (!isOwnedNormalPlaylist.value) return
  state.playlistError = ''
  state.editForm = {
    name: state.myPlaylist?.name || state.playlistInfo.name || '',
    description: state.myPlaylist?.description || state.playlistInfo.description || '',
    status: Number(state.myPlaylist?.status || 0),
    cover: '',
    coverDirty: false,
    coverFileName: '',
  }
  state.showEditPlaylist = true
}

const triggerCoverUpload = () => {
  if (!isOwnedNormalPlaylist.value || state.coverUploading) return
  uploadInputRef.value?.click()
}

const onCoverFileChange = async (event: Event) => {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file || !isOwnedNormalPlaylist.value) return
  if (!file.type.startsWith('image/')) {
    state.playlistError = t('playlist.messages.invalidCover')
    return
  }
  if (file.size > 5 * 1024 * 1024) {
    state.playlistError = t('playlist.messages.coverTooLarge')
    return
  }

  const formData = new FormData()
  formData.append('file', file)

  state.coverUploading = true
  state.playlistError = ''
  try {
    const res = await uploadMyPlaylistCover(currentPlaylistId.value, formData)
    if (res?.code !== 200 || !res.data?.url) {
      throw new Error(res?.message || t('playlist.messages.coverUploadFailed'))
    }
    state.editForm.cover = res.data.url
    state.editForm.coverDirty = true
    state.editForm.coverFileName = file.name
  } catch (error: any) {
    state.playlistError =
      error?.response?.data?.message || error?.message || t('playlist.messages.coverUploadFailed')
  } finally {
    state.coverUploading = false
  }
}

const resetCoverToAuto = () => {
  state.editForm.cover = ''
  state.editForm.coverDirty = true
  state.editForm.coverFileName = ''
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
    const payload: Record<string, unknown> = {
      name,
      description: state.editForm.description.trim(),
      status: state.editForm.status,
    }
    if (state.editForm.coverDirty) {
      payload.cover = state.editForm.cover.trim()
    }
    const res = await updateMyPlaylist(currentPlaylistId.value, payload)
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
  } finally {
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
  } finally {
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
    router.push('/playlists')
  } finally {
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
  () => Number(route.params.id),
  id => {
    state.isCollected = false
    state.isLocalPlaylist = false
    state.songPage = 1
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
      <div class="flex flex-col gap-4">
        <div class="relative">
          <div class="absolute inset-0 overflow-hidden rounded-3xl">
            <img
              :src="withImageParam(playlistInfo.coverImgUrl, '1000y1000')"
              class="h-full w-full scale-150 object-cover opacity-30 blur-3xl"
            />
            <div class="absolute inset-0 bg-linear-to-b from-transparent via-transparent to-black/35" />
          </div>

          <div class="relative z-10 overflow-hidden rounded-3xl">
            <div class="glass-container">
              <div class="flex flex-col gap-8 p-6 lg:flex-row lg:gap-10">
                <div class="group relative mx-auto w-56 shrink-0 lg:mx-0 lg:w-72">
                  <div class="aspect-square overflow-hidden rounded-3xl shadow-2xl ring-1 ring-glass">
                    <LazyImage
                      :src="withImageParam(playlistInfo.coverImgUrl, '500y500')"
                      :alt="$t('components.songList.coverAlt')"
                      imgClass="h-full w-full object-cover transition-transform duration-500 group-hover:scale-105"
                      wrapperClass="h-full w-full"
                    />
                  </div>
                  <button
                    type="button"
                    class="absolute inset-0 flex items-center justify-center rounded-3xl bg-black/10 opacity-0 transition-opacity group-hover:opacity-100"
                    :disabled="!state.songs.length"
                    @click.stop.prevent="playAll"
                  >
                    <span class="sonora-cover-play-button sonora-cover-play-button--lg">
                      <span class="icon-[mdi--play] h-8 w-8"></span>
                    </span>
                  </button>
                  <button
                    v-if="showCoverCollectButton"
                    type="button"
                    class="sonora-cover-action-button absolute top-4 right-4 h-11 w-11"
                    :class="state.isCollected ? 'sonora-cover-action-button--active' : ''"
                    :disabled="playlistActionLoading"
                    :aria-pressed="state.isCollected"
                    @click.stop="toggleCollect"
                  >
                    <span
                      :class="state.isCollected ? 'icon-[mdi--heart]' : 'icon-[mdi--heart-outline]'"
                      class="h-5 w-5"
                    />
                  </button>
                </div>

                <div class="flex min-w-0 flex-1 flex-col justify-center">
                  <div class="mb-4 flex flex-wrap items-center gap-3">
                    <span class="rounded-full px-3 py-1 text-xs font-medium" :class="statusBadgeClass">
                      {{ currentPlaylistCategory }}
                    </span>
                  </div>

                  <h1
                    class="text-primary mb-4 line-clamp-2 text-2xl leading-tight font-bold lg:text-4xl xl:text-5xl"
                  >
                    {{ playlistInfo.name }}
                  </h1>

                  <div class="mb-5 flex flex-wrap items-center gap-3">
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
                    class="text-primary/70 mb-6 line-clamp-3 text-sm leading-relaxed lg:text-base"
                    :title="playlistInfo.description"
                  >
                    {{ playlistInfo.description }}
                  </p>

                  <div class="mb-6 flex flex-wrap items-center gap-6">
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

                  <div class="flex flex-wrap items-center gap-3">
                    <Button
                      variant="solid"
                      size="md"
                      rounded="lg"
                      class="gap-2 px-8 py-3"
                      :disabled="!state.songs.length"
                      @click="playAll"
                    >
                      <span class="icon-[mdi--play] h-5 w-5"></span>
                      {{ $t('actions.playAll') }}
                    </Button>
                    <Button
                      variant="soft"
                      size="md"
                      rounded="lg"
                      class="gap-2 px-6 py-3"
                      :disabled="!state.songs.length"
                      @click="shufflePlay"
                    >
                      <span class="icon-[mdi--shuffle] h-5 w-5"></span>
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

        <section class="h-full overflow-hidden">
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
      </div>
    </template>

    <div
      v-if="showEditPlaylist"
      class="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4 backdrop-blur-sm"
      @click.self="showEditPlaylist = false"
    >
      <div class="glass-container-strong w-full max-w-xl p-6">
        <div class="mb-5 flex items-center justify-between">
          <h3 class="text-primary text-xl font-semibold">{{ $t('playlist.edit.title') }}</h3>
          <Button
            variant="soft"
            size="icon-sm"
            rounded="full"
            icon="icon-[mdi--close]"
            icon-class="h-4 w-4"
            :disabled="playlistActionLoading || coverUploading"
            @click="showEditPlaylist = false"
          />
        </div>

        <div class="space-y-5">
          <div class="grid gap-5 lg:grid-cols-[180px,1fr]">
            <div>
              <label class="text-primary/60 mb-2 block text-xs font-medium">
                {{ $t('playlist.edit.cover') }}
              </label>
              <div class="glass-card overflow-hidden rounded-2xl border border-glass">
                <div class="aspect-square">
                  <img
                    :src="withImageParam(editCoverPreview, '400y400')"
                    class="h-full w-full object-cover"
                  />
                </div>
              </div>
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
                  {{ $t('playlist.edit.description') }}
                </label>
                <textarea
                  v-model="editForm.description"
                  rows="5"
                  maxlength="2000"
                  class="text-primary glass-card w-full resize-none rounded-xl border border-glass px-4 py-3 text-sm outline-none placeholder:text-white/30 focus:border-pink-400/50"
                  :placeholder="$t('playlist.edit.descriptionPlaceholder')"
                />
              </div>
            </div>
          </div>

          <div class="space-y-3">
            <div class="flex flex-wrap items-center gap-3">
              <input
                ref="uploadInputRef"
                class="hidden"
                type="file"
                accept="image/*"
                @change="onCoverFileChange"
              />
              <Button
                variant="soft"
                size="md"
                rounded="full"
                :loading="coverUploading"
                :disabled="coverUploading || playlistActionLoading"
                icon="icon-[mdi--image-plus]"
                icon-class="mr-2 h-5 w-5"
                @click="triggerCoverUpload"
              >
                {{ $t('playlist.edit.uploadCover') }}
              </Button>
              <Button
                variant="ghost"
                size="md"
                rounded="full"
                :disabled="coverUploading || playlistActionLoading"
                icon="icon-[mdi--image-refresh-outline]"
                icon-class="mr-2 h-5 w-5"
                @click="resetCoverToAuto"
              >
                {{ $t('playlist.edit.resetCover') }}
              </Button>
            </div>

            <p class="text-primary/55 text-xs leading-5">
              {{ $t('playlist.edit.coverHint') }}
            </p>
            <p v-if="editForm.coverFileName" class="text-primary/60 text-xs">
              {{ editForm.coverFileName }}
            </p>
            <p
              v-else-if="editForm.coverDirty && !editForm.cover"
              class="text-amber-200/90 text-xs"
            >
              {{ $t('playlist.edit.coverAutoPending') }}
            </p>
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
            :disabled="playlistActionLoading || coverUploading"
            @click="showEditPlaylist = false"
          >
            {{ $t('playlist.edit.cancel') }}
          </Button>
          <Button
            variant="solid"
            size="md"
            rounded="full"
            :loading="playlistActionLoading"
            :disabled="playlistActionLoading || coverUploading"
            icon="icon-[mdi--content-save-outline]"
            @click="submitEditPlaylist"
          >
            {{ $t('playlist.edit.save') }}
          </Button>
        </div>
      </div>
    </div>

    <LoginDialog v-if="state.showLogin" @close="state.showLogin = false" />
  </div>
</template>
