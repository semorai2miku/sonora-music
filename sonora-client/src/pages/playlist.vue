<script setup lang="ts">
import {
  deleteMyPlaylist,
  myPlaylists,
  pinMyPlaylist,
  playlistDetail,
  playlistTrackAll,
  removeSongFromMyPlaylist,
  updateMyPlaylist,
  commentNew,
  search,
  type ClientPlaylist,
} from '@/api'
import { usePlayActions } from '@/composables/usePlayActions'
import { PlaylistInfo, PlaylistSong, CommentItem } from '@/typings'
import LazyImage from '@/components/Ui/LazyImage.vue'
import Button from '@/components/Ui/Button.vue'
import TabGroup from '@/components/Ui/TabGroup.vue'
import { formatCount } from '@/utils/time'
import { useI18n } from 'vue-i18n'
import { useUserStore } from '@/stores/modules/user'
import { withImageParam } from '@/utils/media'
import {
  transformPlaylistDetail,
  transformSongs,
  transformSearchPlaylists,
  type SongData,
  type PlaylistData,
} from '@/utils/transformers'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
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
  newComment: string
  comments: CommentItem[]
  isPageLoading: boolean
  similarPlaylists: SimilarPlaylist[]
  myPlaylist: ClientPlaylist | null
  showEditPlaylist: boolean
  playlistActionLoading: boolean
  playlistError: string
  editForm: {
    name: string
    cover: string
    description: string
  }
}

const state = reactive<PlaylistState>({
  activeTab: 'songs',
  playlistInfo: {} as PlaylistInfo,
  isCollected: false,
  songs: [],
  newComment: '',
  comments: [],
  isPageLoading: true,
  similarPlaylists: [],
  myPlaylist: null,
  showEditPlaylist: false,
  playlistActionLoading: false,
  playlistError: '',
  editForm: {
    name: '',
    cover: '',
    description: '',
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
const { playAll: playAllAction, shufflePlay: shufflePlayAction } = usePlayActions()
const { t } = useI18n()

const gradients: string[] = ['from-purple-500 to-pink-500']
const emojis: string[] = ['🎵', '🎶', '♪', '♫', '🎼']

const pickGradient = (): string => gradients[Math.floor(Math.random() * gradients.length)]

const isOwnedNormalPlaylist = computed(() => state.myPlaylist?.type === 'normal')
const isPinnedPlaylist = computed(() => Number(state.myPlaylist?.pinned || 0) === 1)

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
  }
}

const loadMyPlaylistMeta = async (id: number) => {
  if (!userStore.isLoggedIn || Number.isNaN(id) || id <= 0) {
    state.myPlaylist = null
    return
  }
  try {
    const res = await myPlaylists()
    const found = (res?.data || []).find(playlist => Number(playlist.id) === id) || null
    state.myPlaylist = found
    if (found) syncPlaylistInfoFromMine(found)
  } catch {
    state.myPlaylist = null
  }
}

const loadPlaylist = async (id: number) => {
  try {
    const [detailRes, tracksRes] = await Promise.all([
      playlistDetail({ id }),
      playlistTrackAll({ id, limit: 100 }),
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
        playCount: detail.playCount as number,
        likes: String(detail.likes),
        category: detail.category,
        emoji: state.playlistInfo.emoji,
        gradient: pickGradient(),
        coverImgUrl: detail.coverImgUrl,
      }
    }

    state.songs = transformSongs(tracksRes as Record<string, unknown>, 100)
  } catch {
  } finally {
    state.isPageLoading = false
  }
}

const loadComments = async (id: number) => {
  try {
    const res = await commentNew({ id, type: 2, sortType: 1, pageNo: 1, pageSize: 10 })
    const list = (res as any)?.data?.comments || (res as any)?.comments || []
    if (Array.isArray(list)) {
      state.comments = list.map((c: any, i: number) => ({
        username: c?.user?.nickname || t('comments.user'),
        avatarGradient: gradients[i % gradients.length],
        time: c?.time ? new Date(c.time).toLocaleString() : '',
        content: c?.content || '',
        likes: c?.likedCount || 0,
        avatarUrl: c?.user?.avatarUrl || '',
        replies: (c?.beReplied || []).map((r: any) => ({
          username: r?.user?.nickname || t('comments.user'),
          avatarUrl: r?.user?.avatarUrl || '',
          avatarGradient: gradients[(i + 1) % gradients.length],
          time: '',
          content: r?.content || '',
        })),
      }))
    }
  } catch {}
}

const loadSimilarPlaylists = async (name: string) => {
  try {
    const res = await search({ keywords: name, type: 1000 })
    const { playlists } = transformSearchPlaylists(res as Record<string, unknown>, 12)
    state.similarPlaylists = playlists.map(pl => ({
      id: pl.id,
      name: pl.name,
      coverImgUrl: pl.coverImgUrl,
      trackCount: pl.trackCount,
      playCount: pl.playCount,
    }))
  } catch {}
}

// 初始化加载播放列表
onMounted(() => {
  const idNum = currentPlaylistId.value
  if (!Number.isNaN(idNum) && idNum > 0) {
    state.isPageLoading = true
    loadPlaylist(idNum)
    loadComments(idNum)
    loadMyPlaylistMeta(idNum)
  }
})

// 监听路由参数变化，加载新的播放列表
watch(
  () => Number(route.params.id),
  idNum => {
    if (!Number.isNaN(idNum) && idNum > 0) {
      state.isPageLoading = true
      loadPlaylist(idNum)
      loadComments(idNum)
      loadMyPlaylistMeta(idNum)
    }
  }
)

watch(
  () => userStore.isLoggedIn,
  () => loadMyPlaylistMeta(currentPlaylistId.value)
)

watch(
  () => state.playlistInfo.name,
  name => {
    if (name) loadSimilarPlaylists(name)
  }
)

const submitComment = () => {
  if (!state.newComment.trim()) return
  const comment = {
    username: t('common.me'),
    avatar: t('common.me'),
    avatarGradient: 'from-pink-400 to-purple-500',
    time: t('common.justNow'),
    content: state.newComment,
    likes: 0,
    avatarUrl: '',
    replies: [],
  }
  state.comments.unshift(comment)
  state.newComment = ''
}

const playAll = () => playAllAction(state.songs)

const shufflePlay = () => shufflePlayAction(state.songs)

const toggleCollect = () => {
  state.isCollected = !state.isCollected
}

const sharePlaylist = async () => {
  const url = location.origin + location.pathname + `#/playlist/${currentPlaylistId.value}`
  const title = String((state.playlistInfo as any)?.name || t('home.playlistFallback'))
  const text = String((state.playlistInfo as any)?.description || '')
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
    cover: state.myPlaylist?.cover || state.playlistInfo.coverImgUrl || '',
    description: state.myPlaylist?.description || state.playlistInfo.description || '',
  }
  state.showEditPlaylist = true
}

const submitEditPlaylist = async () => {
  if (!isOwnedNormalPlaylist.value) return
  const name = state.editForm.name.trim()
  if (!name) {
    state.playlistError = '请输入歌单名称'
    return
  }
  state.playlistActionLoading = true
  state.playlistError = ''
  try {
    const res = await updateMyPlaylist(currentPlaylistId.value, {
      name,
      cover: state.editForm.cover.trim(),
      description: state.editForm.description.trim(),
    })
    if (res?.code !== 200 || !res.data) throw new Error(res?.message || '保存失败')
    state.myPlaylist = res.data
    syncPlaylistInfoFromMine(res.data)
    dispatchPlaylistsUpdated()
    state.showEditPlaylist = false
  } catch (error: any) {
    state.playlistError = error?.response?.data?.message || error?.message || '保存失败'
  } finally {
    state.playlistActionLoading = false
  }
}

const togglePinPlaylist = async () => {
  if (!isOwnedNormalPlaylist.value) return
  state.playlistActionLoading = true
  try {
    const res = await pinMyPlaylist(currentPlaylistId.value, !isPinnedPlaylist.value)
    if (res?.code !== 200 || !res.data) throw new Error(res?.message || '操作失败')
    state.myPlaylist = res.data
    dispatchPlaylistsUpdated()
  } catch {
  } finally {
    state.playlistActionLoading = false
  }
}

const removeCurrentPlaylist = async () => {
  if (!isOwnedNormalPlaylist.value) return
  if (!window.confirm('删除后歌单内歌曲关系会被清空，确定删除这个歌单吗？')) return
  state.playlistActionLoading = true
  try {
    const res = await deleteMyPlaylist(currentPlaylistId.value)
    if (res?.code !== 200) throw new Error(res?.message || '删除失败')
    dispatchPlaylistsUpdated()
    router.push('/')
  } catch {
  } finally {
    state.playlistActionLoading = false
  }
}

const removeSongFromCurrentPlaylist = async (song: SongData, index: number) => {
  if (!isOwnedNormalPlaylist.value || !song.id) return
  try {
    const res = await removeSongFromMyPlaylist(currentPlaylistId.value, song.id)
    if (res?.code !== 200) throw new Error(res?.message || '移除失败')
    state.songs.splice(index, 1)
    state.playlistInfo.songCount = state.songs.length
    dispatchPlaylistsUpdated()
  } catch {}
}

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
</script>

<template>
  <div class="w-full p-4 overflow-x-hidden">
    <PageSkeleton v-if="isPageLoading" :sections="['hero', 'list']" :list-count="12" />
    <template v-else>
      <div class="flex flex-col gap-3">
        <!-- 头部区域 -->
        <div class="relative">
          <!-- 背景模糊图片 -->
          <div class="absolute inset-0 overflow-hidden rounded-3xl">
            <img
              :src="withImageParam(playlistInfo.coverImgUrl, '100y100')"
              class="h-full w-full scale-150 object-cover opacity-30 blur-3xl"
            />
            <div
              class="to-overlay absolute inset-0 bg-linear-to-b from-transparent via-transparent"
            ></div>
          </div>

          <!-- 内容区域 -->
          <div class="relative z-10 overflow-hidden rounded-3xl">
            <div class="glass-container">
              <div class="flex flex-col gap-6 p-6 lg:flex-row lg:gap-10">
                <!-- 封面 -->
                <div class="group relative mx-auto w-56 shrink-0 lg:mx-0 lg:w-72">
                  <div
                    class="aspect-square overflow-hidden rounded-3xl shadow-2xl ring-1 ring-glass"
                  >
                    <LazyImage
                      :src="withImageParam(playlistInfo.coverImgUrl, '400y400')"
                      :alt="$t('components.songList.coverAlt')"
                      imgClass="h-full w-full object-cover transition-transform duration-700 group-hover:scale-110"
                      wrapperClass="h-full w-full"
                    />
                  </div>
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

                <!-- 信息区域 -->
                <div class="flex min-w-0 flex-1 flex-col justify-center text-center lg:text-left">
                  <!-- 标题 -->
                  <h1
                    class="text-primary mb-4 line-clamp-2 text-xl leading-tight font-bold lg:text-4xl xl:text-5xl"
                  >
                    {{ playlistInfo.name }}
                  </h1>

                  <!-- 创建者信息 -->
                  <div class="mb-5 flex items-center justify-center gap-3 lg:justify-start">
                    <img
                      v-if="playlistInfo.creatorAvatar"
                      :src="withImageParam(playlistInfo.creatorAvatar, '80y80')"
                      class="h-8 w-8 rounded-full ring-2 ring-glass"
                    />
                    <span class="text-primary font-medium">{{ playlistInfo.creator }}</span>
                    <span class="text-primary/40">•</span>
                    <span class="text-primary/60 text-sm">{{ playlistInfo.createTime }}</span>
                    <span class="text-primary/40">•</span>
                    <!-- 分类标签 -->
                    <span
                      class="glass-button accent-gradient px-4 py-1.5 text-xs font-medium text-white"
                    >
                      {{ playlistInfo.category }}
                    </span>
                  </div>

                  <!-- 描述 -->
                  <p
                    v-if="playlistInfo.description"
                    class="text-primary/70 mb-6 line-clamp-2 text-sm leading-relaxed lg:text-base"
                    :title="playlistInfo.description"
                  >
                    {{ playlistInfo.description }}
                  </p>

                  <!-- 统计信息 -->
                  <div
                    class="mb-6 flex flex-wrap items-center justify-center gap-6 lg:justify-start"
                  >
                    <div class="flex items-center gap-2">
                      <span class="icon-[mdi--music-note] text-primary/60 h-5 w-5"></span>
                      <span class="text-primary font-medium">
                        {{ $t('commonUnits.songsShort', playlistInfo.songCount) }}
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

                  <!-- 操作按钮 -->
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
                    <div class="flex items-center gap-3">
                      <Button
                        variant="soft"
                        size="icon-md"
                        rounded="full"
                        class="h-11 w-11"
                        :class="{
                          'bg-red-500/20 text-red-400 hover:bg-red-500/30': state.isCollected,
                        }"
                        @click="toggleCollect"
                        :title="state.isCollected ? $t('common.uncollect') : $t('common.collect')"
                      >
                        <span
                          :class="
                            state.isCollected ? 'icon-[mdi--heart]' : 'icon-[mdi--heart-outline]'
                          "
                          class="h-5 w-5"
                        ></span>
                      </Button>
                      <Button
                        variant="soft"
                        size="icon-md"
                        rounded="full"
                        class="h-11 w-11"
                        @click="sharePlaylist"
                        :title="$t('common.share')"
                      >
                        <span class="icon-[mdi--share-variant] h-5 w-5"></span>
                      </Button>
                    </div>
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
                        编辑歌单
                      </Button>
                      <Button
                        variant="soft"
                        size="icon-md"
                        rounded="full"
                        class="h-11 w-11"
                        :class="isPinnedPlaylist ? 'text-pink-300' : ''"
                        :icon="isPinnedPlaylist ? 'icon-[mdi--pin-off]' : 'icon-[mdi--pin]'"
                        icon-class="h-5 w-5"
                        :title="isPinnedPlaylist ? '取消置顶' : '置顶歌单'"
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
                        title="删除歌单"
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

        <!-- 主内容区域 -->
        <!-- Tabs 工具栏 -->
        <div class="flex items-center justify-between">
          <TabGroup v-model="activeTab" :tabs="tabsWithCount" class="w-full" size="md" />
        </div>

        <!-- 歌曲列表 -->
        <section v-show="activeTab === 'songs'" class="h-full overflow-hidden">
          <SongList
            :songs="songs"
            :show-header="true"
            :allow-remove="isOwnedNormalPlaylist"
            @remove="removeSongFromCurrentPlaylist"
          />
        </section>

        <!-- 评论区 -->
        <section v-show="activeTab === 'comments'" class="animate-fade-in">
          <div class="glass-card overflow-hidden">
            <!-- 发表评论 -->
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

            <!-- 评论列表 -->
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

                    <!-- 回复列表 -->
                    <div
                      v-if="comment.replies?.length"
                      class="glass-card mt-4 space-y-3 rounded-xl p-4"
                    >
                      <div v-for="(reply, ri) in comment.replies" :key="ri" class="flex gap-3">
                        <img
                          v-if="reply.avatarUrl"
                          :src="withImageParam(reply.avatarUrl, '80y80')"
                          class="h-8 w-8 shrink-0 rounded-full ring-1 ring-glass"
                        />
                        <div class="min-w-0 flex-1">
                          <span class="text-primary text-xs font-semibold">{{
                            reply.username
                          }}</span>
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

            <!-- 空状态 -->
            <div v-else class="flex flex-col items-center justify-center py-20">
              <span class="icon-[mdi--comment-off-outline] text-primary/20 mb-4 h-16 w-16"></span>
              <p class="text-primary/50 text-sm font-medium">{{ $t('comments.empty') }}</p>
            </div>

            <!-- 加载更多 -->
            <div
              v-if="comments.length >= 10"
              class="border-t border-glass p-5 text-center"
            >
              <Button
                variant="ghost"
                size="sm"
                class="text-primary/60 hover:text-primary text-sm font-medium transition-colors"
              >
                {{ $t('comments.loadMore') }}
              </Button>
            </div>
          </div>
        </section>

        <!-- 相似歌单 -->
        <section v-show="activeTab === 'similar'" class="animate-fade-in">
          <div
            v-if="similarPlaylists.length"
            class="grid grid-cols-2 gap-4 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 xl:grid-cols-6"
          >
            <router-link
              v-for="pl in similarPlaylists"
              :key="pl.id"
              :to="`/playlist/${pl.id}`"
              class="group"
            >
              <div
                class="glass-card overflow-hidden transition-all duration-300 hover:scale-105 hover:shadow-xl"
              >
                <div class="relative aspect-square overflow-hidden">
                  <LazyImage
                    :src="withImageParam(pl.coverImgUrl, '300y300')"
                    :alt="pl.name"
                    imgClass="h-full w-full object-cover transition-transform duration-700 group-hover:scale-110"
                    wrapperClass="h-full w-full"
                  />
                  <div
                    class="absolute inset-0 flex items-center justify-center bg-black/50 opacity-0 backdrop-blur-sm transition-opacity duration-300 group-hover:opacity-100"
                  >
                    <div
                      class="accent-gradient flex h-14 w-14 items-center justify-center rounded-full shadow-2xl"
                    >
                      <span class="icon-[mdi--play] h-7 w-7 text-white"></span>
                    </div>
                  </div>
                  <div
                    v-if="pl.playCount"
                    class="absolute top-2 right-2 flex items-center gap-1 rounded-full bg-black/70 px-2.5 py-1 text-xs font-medium text-white backdrop-blur-md"
                  >
                    <span class="icon-[mdi--play] h-3.5 w-3.5"></span>
                    {{ formatCount(pl.playCount) }}
                  </div>
                </div>
                <div class="p-3">
                  <p
                    class="text-primary mb-1.5 line-clamp-2 text-sm leading-snug font-semibold transition-colors group-hover:text-pink-400"
                  >
                    {{ pl.name }}
                  </p>
                  <p class="text-primary/60 text-xs">
                    {{ $t('commonUnits.songsShort', pl.trackCount) }}
                    <span v-if="pl.creator"> • {{ pl.creator.nickname }}</span>
                  </p>
                </div>
              </div>
            </router-link>
          </div>
          <div
            v-else
            class="glass-card flex flex-col items-center justify-center py-20 text-center"
          >
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
          <h3 class="text-primary text-xl font-semibold">编辑歌单</h3>
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
            <label class="text-primary/60 mb-2 block text-xs font-medium">歌单名称</label>
            <input
              v-model="editForm.name"
              type="text"
              maxlength="80"
              class="text-primary glass-card w-full rounded-xl border border-glass px-4 py-3 text-sm outline-none placeholder:text-white/30 focus:border-pink-400/50"
              placeholder="请输入歌单名称"
            />
          </div>
          <div>
            <label class="text-primary/60 mb-2 block text-xs font-medium">封面地址</label>
            <input
              v-model="editForm.cover"
              type="text"
              class="text-primary glass-card w-full rounded-xl border border-glass px-4 py-3 text-sm outline-none placeholder:text-white/30 focus:border-pink-400/50"
              placeholder="留空则使用默认封面"
            />
          </div>
          <div>
            <label class="text-primary/60 mb-2 block text-xs font-medium">简介</label>
            <textarea
              v-model="editForm.description"
              rows="3"
              class="text-primary glass-card w-full resize-none rounded-xl border border-glass px-4 py-3 text-sm outline-none placeholder:text-white/30 focus:border-pink-400/50"
              placeholder="写点歌单简介"
            />
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
            取消
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
            保存
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
