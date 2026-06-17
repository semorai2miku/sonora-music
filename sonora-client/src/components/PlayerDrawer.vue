<script setup lang="ts">
/**
 * PlayerDrawer - 全屏播放器抽屉
 * 包含黑胶封面、歌词滚动、背景过渡、
 * 共享元素过渡动画、歌词拖动跳转等功能
 */
import { gsap } from 'gsap'
import { useAudio } from '@/composables/useAudio'
import { useLyrics } from '@/composables/useLyrics'
import { useLyricsScroll } from '@/composables/useLyricsScroll'
import { useGradientBackground } from '@/composables/useGradientBackground'
import { useI18n } from 'vue-i18n'
import { useLyricsDrag } from '@/composables/useLyricsDrag'
import { useDrawerTransition } from '@/composables/useDrawerTransition'
import PlayerSongActions from '@/components/Player/PlayerSongActions.vue'
import VinylDisc from '@/components/Player/VinylDisc.vue'
import type { Artist as SongArtist } from '@/stores/interface'

const { t } = useI18n()
const router = useRouter()
const globalStore = useGlobalStore()
// ═══ 主题切换 ═══

/** 当前主题对应图标 */
const themeIcon = computed(() => {
  switch (globalStore.theme) {
    case 'light':
      return 'icon-[mdi--white-balance-sunny]'
    case 'dark':
      return 'icon-[mdi--moon-waning-crescent]'
    default:
      return 'icon-[mdi--theme-light-dark]'
  }
})

/** 循环切换主题：light → dark → system */
const cycleTheme = () => {
  const order: Array<'light' | 'dark' | 'system'> = ['light', 'dark', 'system']
  const idx = order.indexOf(globalStore.theme)
  globalStore.setTheme(order[(idx + 1) % 3])
}

/** 抽屉开关（双向绑定） */
const isOpen = defineModel<boolean>()

// ═══ 模板引用 ═══
const drawerRef = useTemplateRef('drawerRef')
const lyricsRef = useTemplateRef('lyricsRef')
const bgARef = useTemplateRef('bgARef')
const bgBRef = useTemplateRef('bgBRef')
const lyricsContainerRef = ref<HTMLElement | null>(null)
const vinylDiscRef = ref<InstanceType<typeof VinylDisc> | null>(null)

// ═══ 组合式函数 ═══

// 音频播放器
const {
  currentSong,
  isPlaying,
  isLoading,
  currentTime,
  playMode,
  togglePlay,
  next,
  previous,
  setCurrentTime,
  formattedCurrentTime,
  formattedDuration,
  togglePlayMode,
} = useAudio()

// 歌词
const {
  lyricsTrans,
  lyricsRoma,
  showTrans,
  showRoma,
  activeSingleLyrics,
  activeTimeline,
  timeForIndex,
  fetchLyrics,
} = useLyrics()

// 歌词滚动
const {
  currentIndex: currentLyricIndex,
  positioned: lyricsPositioned,
  autoScroll,
  scale: lyricsScale,
  updateCurrentLyric,
  scrollToCurrentLyric,
  toggleAutoScroll,
  resetLyrics,
  increaseScale,
  decreaseScale,
} = useLyricsScroll({
  lyricsRef,
  timeline: activeTimeline,
  currentTime,
})

// 歌词拖动
const {
  isDragging: lyricsDragging,
  previewIndex: dragPreviewIndex,
  previewInfo: dragPreviewInfo,
  onDragStart: handleLyricsDragStart,
} = useLyricsDrag({
  lyricsRef,
  lyricsContainerRef,
  activeSingleLyrics,
  timeForIndex,
  setCurrentTime,
  currentLyricIndex,
  autoScroll,
  scrollToCurrentLyric,
  toggleAutoScroll,
  formattedDuration,
  showTrans,
})

// 背景渐变
const {
  useCoverBg,
  bgAStyle,
  bgBStyle,
  activeGradient,
  stopBackgroundBreathing,
  setBackgroundGradient,
} = useGradientBackground({
  bgARef,
  bgBRef,
  isPlaying,
  isOpen: isOpen as Ref<boolean>,
})

// 抽屉过渡动画
const {
  isRendered,
  open: openDrawer,
  close: closeDrawer,
} = useDrawerTransition({
  drawerRef,
  vinylDiscRef,
  currentSong,
  isPlaying,
})

// 本地 UI 状态
const state = reactive({
  /** 播放列表弹出框 */
  isRecentOpen: false,
  /** 移动端歌词视图 */
  showMobileLyrics: false,
  /** 滚轮预览歌词索引 */
  wheelPreviewIndex: -1,
  /** 是否显示滚轮预览线 */
  wheelPreviewVisible: false,
})

const { isRecentOpen, showMobileLyrics } = toRefs(state)
let wheelPreviewTimer: ReturnType<typeof setTimeout> | null = null

const playerArtists = computed<SongArtist[]>(() => {
  const artists = currentSong.value?.artists
  if (Array.isArray(artists) && artists.length > 0) {
    return artists
      .filter(artist => artist?.name)
      .map(artist => ({ id: artist.id, name: artist.name }))
  }

  if (currentSong.value?.artistId && currentSong.value?.artist) {
    return [{ id: currentSong.value.artistId, name: currentSong.value.artist }]
  }

  return (currentSong.value?.artist || '')
    .split('/')
    .map(name => name.trim())
    .filter(Boolean)
    .map(name => ({ id: '', name }))
})

const wheelPreviewTimeLabel = computed(() => {
  if (state.wheelPreviewIndex < 0) return ''
  const time = timeForIndex(state.wheelPreviewIndex) ?? 0
  const minutes = Math.floor(time / 60)
  const seconds = Math.floor(time % 60)
  return `${minutes}:${seconds.toString().padStart(2, '0')}`
})

/** 可视化器渐变色：从背景主色调提取并适配当前主题 */
const visualizerGradient = computed(() => {
  const gradient = activeGradient.value
  if (gradient.length === 0) {
    return ['#08111c', '#1f7cff', '#4da3ff']
  }
  const colors = gradient.map(color => {
    const match = color.match(/rgba?\(([^)]+)\)/)
    if (match) {
      const values = match[1].split(',').slice(0, 3)
      return `rgb(${values.join(',')})`
    }
    return color
  })
  return adaptColorsForTheme(colors)
})

/** 播放模式对应图标 */
const playModeIconClass = computed(() => {
  switch (playMode.value) {
    case 'single':
      return 'icon-[mdi--repeat-once]'
    case 'random':
      return 'icon-[mdi--shuffle]'
    case 'list':
    default:
      return 'icon-[mdi--repeat]'
  }
})

/** 切换歌词选项（翻译/罗马音），切换后重新定位 */
const toggleLyricsOption = async (option: 'trans' | 'roma') => {
  if (option === 'trans') showTrans.value = !showTrans.value
  else showRoma.value = !showRoma.value
  await nextTick()
  lyricsPositioned.value = false
  updateCurrentLyric(true)
}

/** 点击封面切换播放/暂停（加载中忽略） */
const handleAlbumCoverClick = () => {
  if (!isLoading.value) {
    togglePlay()
  }
}

const collapsePlayerAndNavigate = (path: string) => {
  state.showMobileLyrics = false
  isOpen.value = false
  router.push(path)
}

const handleLyricsPreviewClick = () => {
  if (state.wheelPreviewVisible && !lyricsDragging.value) {
    applyWheelPreview()
  }
}

const scrollLyricsToIndex = (index: number, instant = false) => {
  if (!lyricsRef.value || !lyricsContainerRef.value || index < 0) return
  const lyricsContainer = lyricsRef.value
  const targetElement = lyricsContainer.children[index] as HTMLElement | undefined
  if (!targetElement) return

  const containerHeight = lyricsContainerRef.value.clientHeight
  const targetScrollTop =
    targetElement.offsetTop - containerHeight / 2 + targetElement.clientHeight / 2

  if (instant) {
    gsap.set(lyricsContainer, { y: -targetScrollTop })
    return
  }

  gsap.to(lyricsContainer, {
    y: -targetScrollTop,
    duration: 0.35,
    ease: 'power2.out',
  })
}

const resetWheelPreview = (restoreAutoScroll = true) => {
  state.wheelPreviewIndex = -1
  state.wheelPreviewVisible = false
  if (wheelPreviewTimer) {
    clearTimeout(wheelPreviewTimer)
    wheelPreviewTimer = null
  }
  if (restoreAutoScroll) {
    autoScroll.value = true
    updateCurrentLyric(true)
  }
}

const scheduleWheelPreviewReset = () => {
  if (wheelPreviewTimer) {
    clearTimeout(wheelPreviewTimer)
  }
  wheelPreviewTimer = setTimeout(() => {
    resetWheelPreview(true)
  }, 8000)
}

const handleLyricsWheel = (event: WheelEvent) => {
  if (!activeSingleLyrics.value.length || lyricsDragging.value) return

  event.preventDefault()

  const direction = event.deltaY > 0 ? 1 : -1
  const baseIndex = state.wheelPreviewVisible ? state.wheelPreviewIndex : currentLyricIndex.value
  const nextIndex = Math.max(
    0,
    Math.min(activeSingleLyrics.value.length - 1, baseIndex + direction)
  )

  autoScroll.value = false
  state.wheelPreviewIndex = nextIndex
  state.wheelPreviewVisible = true
  scrollLyricsToIndex(nextIndex)
  scheduleWheelPreviewReset()
}

const applyWheelPreview = () => {
  if (!state.wheelPreviewVisible || state.wheelPreviewIndex < 0) return
  const targetTime = timeForIndex(state.wheelPreviewIndex) ?? 0
  setCurrentTime(targetTime)
  currentLyricIndex.value = state.wheelPreviewIndex
  resetWheelPreview(false)
  autoScroll.value = true
  scrollToCurrentLyric()
}

// ═══ Watchers ═══

/** 抽屉开关：打开时初始化动画和背景，关闭时清理 */
watch(
  () => isOpen.value,
  async newVal => {
    if (newVal) {
      isRendered.value = true
      await nextTick()
      openDrawer()
      lyricsPositioned.value = false
      updateCurrentLyric(true)
      setBackgroundGradient(currentSong.value?.cover)
    } else {
      closeDrawer()
      stopBackgroundBreathing()
    }
  }
)

/** 播放状态变化：控制背景呼吸动画 */
watch(
  isPlaying,
  playing => {
    if (!playing) {
      stopBackgroundBreathing()
    }
  },
  { immediate: true }
)

watch(currentTime, () => {
  updateCurrentLyric()
})

/** 切歌时：加载歌词、重置滚动、更新背景、翻转封面 */
watch(
  currentSong,
  async (s, oldSong) => {
    resetWheelPreview(false)
    await fetchLyrics(s?.id)
    resetLyrics()
    await nextTick()
    updateCurrentLyric(true)
    setBackgroundGradient(s?.cover, 0)
  },
  { immediate: true }
)

onMounted(() => {
  if (drawerRef.value) {
    gsap.set(drawerRef.value as any, { display: 'none' })
  }
})

onUnmounted(() => {
  stopBackgroundBreathing()
  if (wheelPreviewTimer) {
    clearTimeout(wheelPreviewTimer)
  }
})
</script>

<template>
  <div
    v-if="isRendered"
    ref="drawerRef"
    class="bg-overlay/95 absolute inset-0 z-50 flex backdrop-blur-md backdrop-filter"
  >
    <div v-show="useCoverBg" class="absolute inset-0 -z-10 overflow-hidden">
      <div ref="bgARef" class="bg-layer absolute inset-0 opacity-0" :style="bgAStyle"></div>
      <div ref="bgBRef" class="bg-layer absolute inset-0 opacity-0" :style="bgBStyle"></div>
      <div class="bg-overlay/40 absolute inset-0"></div>
      <!-- 暗角 -->
      <div class="vignette pointer-events-none absolute inset-0"></div>
    </div>

    <div
      class="absolute top-0 right-0 left-0 z-10 flex items-center justify-between p-4 lg:px-6 lg:pt-5"
    >
      <div class="flex items-center gap-2">
        <div class="glass-toolbar flex items-center gap-0.5 rounded-xl p-1">
          <Button
            variant="ghost"
            size="icon-sm"
            rounded="lg"
            :title="t('player.fontDec')"
            @click="decreaseScale()"
            icon="icon-[mdi--format-font-size-decrease]"
            icon-class="h-4 w-4"
          />
          <Button
            variant="ghost"
            size="icon-sm"
            rounded="lg"
            :title="t('player.fontInc')"
            @click="increaseScale()"
            icon="icon-[mdi--format-font-size-increase]"
            icon-class="h-4 w-4"
          />
          <div class="mx-0.5 h-4 w-px bg-white/8"></div>
          <Button
            variant="ghost"
            size="icon-sm"
            rounded="lg"
            :class="{ 'text-primary bg-white/12 ring-1 ring-white/15': autoScroll }"
            :title="t('player.autoCenter')"
            @click="toggleAutoScroll"
          >
            <span
              :class="autoScroll ? 'icon-[mdi--autorenew]' : 'icon-[mdi--pause]'"
              class="h-4 w-4"
            ></span>
          </Button>
        </div>
      </div>

      <div class="flex items-center gap-2">
        <div
          v-if="lyricsTrans.length || lyricsRoma.length"
          class="glass-toolbar flex items-center gap-1 rounded-xl p-1"
        >
          <Button
            v-if="lyricsTrans.length"
            variant="ghost"
            size="sm"
            rounded="lg"
            class="gap-1.5 text-xs"
            :class="{ 'text-primary bg-white/12 ring-1 ring-white/15': showTrans }"
            @click="toggleLyricsOption('trans')"
          >
            <span class="icon-[mdi--translate] h-3.5 w-3.5" />
            <span>{{ t('player.translate') }}</span>
          </Button>
          <Button
            v-if="lyricsRoma.length"
            variant="ghost"
            size="sm"
            rounded="lg"
            class="gap-1.5 text-xs"
            :class="{ 'text-primary bg-white/12 ring-1 ring-white/15': showRoma }"
            @click="toggleLyricsOption('roma')"
          >
            <span class="icon-[mdi--alphabetical-variant] h-3.5 w-3.5"></span>
            <span>{{ t('player.roma') }}</span>
          </Button>
        </div>

        <div class="glass-toolbar flex items-center gap-0.5 rounded-xl p-1">
          <Button
            variant="ghost"
            size="icon-sm"
            rounded="lg"
            class="lg:hidden"
            @click="showMobileLyrics = !showMobileLyrics"
            :icon="showMobileLyrics ? 'icon-[mdi--album]' : 'icon-[mdi--text-box-outline]'"
            icon-class="h-4 w-4"
          />

          <Button
            variant="ghost"
            size="icon-sm"
            rounded="lg"
            :class="{ 'bg-white/12 text-yellow-300/80': !useCoverBg }"
            @click="useCoverBg = !useCoverBg"
            :title="t('player.toggleBg')"
          >
            <span
              :class="[
                useCoverBg ? 'icon-[mdi--image-multiple-outline]' : 'icon-[mdi--palette-swatch]',
                'h-4 w-4',
              ]"
            ></span>
          </Button>

          <Button
            variant="ghost"
            size="icon-sm"
            rounded="lg"
            @click="cycleTheme"
            :title="t('components.settings.themeMode')"
          >
            <span :class="[themeIcon, 'h-4 w-4']"></span>
          </Button>

          <div class="mx-0.5 h-4 w-px bg-white/8"></div>

          <Button
            variant="ghost"
            size="icon-sm"
            rounded="lg"
            @click="isOpen = false"
            icon="icon-[mdi--chevron-down]"
            icon-class="h-5 w-5"
          />
        </div>
      </div>
    </div>

    <div
      class="player-left-panel flex w-full flex-col items-center justify-center px-4 pt-20 pb-8 lg:w-1/2 lg:px-8 lg:pt-24 lg:pb-12"
      :class="{ 'hidden lg:flex': state.showMobileLyrics }"
    >
      <!-- 专辑封面区域 -->
      <div class="mb-4 flex flex-col items-center lg:mb-6">
        <VinylDisc
          ref="vinylDiscRef"
          :cover="currentSong?.cover"
          :is-playing="isPlaying"
          :is-loading="isLoading"
          size="lg"
          class="album-cover mb-6"
          @click="handleAlbumCoverClick"
        />
      </div>

      <!-- 歌曲信息（两种模式共享） -->
      <div class="song-info mb-4 text-center lg:mb-6">
        <button
          v-if="currentSong?.id"
          type="button"
          class="player-title-link text-primary mb-1 block cursor-pointer border-none bg-transparent p-0 line-clamp-1 text-2xl font-bold sm:text-3xl lg:text-[2.75rem]"
          @click="collapsePlayerAndNavigate(`/song/${currentSong.id}`)"
        >
          {{ currentSong?.name || t('player.unknownSong') }}
        </button>
        <h2
          v-else
          class="song-title text-primary mb-1 line-clamp-1 text-2xl font-bold sm:text-3xl lg:text-[2.75rem]"
        >
          {{ currentSong?.name || t('player.unknownSong') }}
        </h2>
        <div class="player-meta-group mt-2 space-y-1.5">
          <div class="player-meta-row text-sm sm:text-base lg:text-lg">
            <span class="player-meta-icon icon-[mdi--account-music-outline]" aria-hidden="true"></span>
            <div class="flex min-w-0 flex-wrap items-center justify-center gap-x-2 gap-y-1">
              <template
                v-for="(artist, index) in playerArtists"
                :key="`${artist.id}-${artist.name}-${index}`"
              >
                <button
                  v-if="artist.id"
                  type="button"
                  class="player-meta-link"
                  @click="collapsePlayerAndNavigate(`/artist/${artist.id}`)"
                >
                  {{ artist.name }}
                </button>
                <span v-else class="player-meta-text">{{ artist.name }}</span>
                <span v-if="index < playerArtists.length - 1" class="text-primary/35">/</span>
              </template>
            </div>
          </div>

          <div v-if="currentSong?.album" class="player-meta-row text-xs sm:text-sm">
            <span class="player-meta-icon icon-[mdi--album]" aria-hidden="true"></span>
            <button
              v-if="currentSong?.albumId"
              type="button"
              class="player-meta-link"
              @click="collapsePlayerAndNavigate(`/album/${currentSong.albumId}`)"
            >
              {{ currentSong.album }}
            </button>
            <p v-else class="player-meta-text truncate">
              {{ currentSong.album }}
            </p>
          </div>
        </div>
      </div>

      <div v-if="currentSong" class="mb-5 w-full max-w-xl px-4">
        <MusicProgress :color="visualizerGradient" />
        <div class="mt-1.5 flex justify-between">
          <span class="text-primary/45 text-[11px] tabular-nums">{{
            isLoading ? t('player.loading') : formattedCurrentTime
          }}</span>
          <span class="text-primary/45 text-[11px] tabular-nums">{{ formattedDuration }}</span>
        </div>
      </div>

      <div class="controls-row mb-5 flex items-center gap-3 sm:gap-5 lg:mb-6">
        <Button
          variant="ghost"
          rounded="full"
          size="none"
          class="ctrl-btn h-10 w-10 justify-center"
          :class="{ 'bg-sky-500/15 text-sky-400!': playMode !== 'list' }"
          @click="togglePlayMode"
        >
          <span :class="playModeIconClass" class="h-5 w-5" />
        </Button>

        <Button
          variant="ghost"
          rounded="full"
          size="none"
          class="ctrl-btn h-12 w-12 justify-center"
          @click="previous"
          icon="icon-[mdi--skip-previous]"
          icon-class="h-6 w-6"
        />

        <Button
          variant="gradient"
          rounded="full"
          size="none"
          class="main-play-btn h-[72px] w-[72px] justify-center"
          :loading="isLoading"
          :pulse="true"
          :press3d="true"
          @click="togglePlay"
        >
          <span
            v-if="!isLoading"
            :class="!isPlaying ? 'icon-[mdi--play]' : 'icon-[mdi--pause]'"
            class="h-8 w-8"
          ></span>
        </Button>

        <Button
          variant="ghost"
          rounded="full"
          size="none"
          class="ctrl-btn h-12 w-12 justify-center"
          @click="next"
          icon="icon-[mdi--skip-next]"
          icon-class="h-6 w-6"
        />

        <PlaylistBubble
          v-model:show="isRecentOpen"
          placement="top-left"
          :offset-x="8"
          :offset-y="10"
        >
          <template #trigger>
            <Button
              variant="ghost"
              rounded="full"
              size="none"
              class="ctrl-btn h-10 w-10 justify-center"
              icon="icon-[mdi--playlist-music]"
              icon-class="h-5 w-5"
            />
          </template>
        </PlaylistBubble>
      </div>

      <div class="flex w-full max-w-sm items-center justify-between gap-4 px-4">
        <PlayerSongActions size="md" />
        <div class="volume-control flex items-center gap-2">
          <VolumeControl />
        </div>
      </div>
    </div>

    <div
      class="player-right-panel hidden w-1/2 flex-col px-6 pt-20 pb-8 lg:flex lg:px-8 lg:pt-24 lg:pb-12"
      :class="{ 'flex! w-full': state.showMobileLyrics }"
    >
      <div
        ref="lyricsContainerRef"
        class="lyrics-container relative h-full flex-1 overflow-hidden"
        :class="{ 'cursor-grabbing': lyricsDragging, 'cursor-grab': !lyricsDragging }"
        @click="handleLyricsPreviewClick"
        @wheel.prevent="handleLyricsWheel"
      >
        <div
          ref="lyricsRef"
          class="lyrics-scroll relative z-20 h-full select-none"
          :style="{ fontSize: lyricsScale + 'rem' }"
          @mousedown="handleLyricsDragStart"
          @touchstart="handleLyricsDragStart"
        >
          <div
            v-for="(line, index) in activeSingleLyrics"
            :key="index"
            class="lyric-line text-center transition-all duration-500"
            :class="{
              current: index === (lyricsDragging ? dragPreviewIndex : currentLyricIndex),
              'text-primary/40': index !== (lyricsDragging ? dragPreviewIndex : currentLyricIndex),
            }"
          >
            <p class="lyric-text pointer-events-none">{{ line.ori }}</p>
            <p v-if="showTrans && line.tran" class="lyric-sub pointer-events-none">
              {{ line.tran }}
            </p>
            <p v-if="showRoma && line.roma" class="lyric-sub pointer-events-none">
              {{ line.roma }}
            </p>
          </div>
          <div class="h-64"></div>
        </div>

        <!-- 歌词中心指示线 -->
        <button
          v-if="state.wheelPreviewVisible && !lyricsDragging"
          type="button"
          class="wheel-preview-line absolute top-1/2 right-0 left-0 z-30 flex -translate-y-1/2 items-center gap-4 px-8"
          @click.stop="applyWheelPreview"
        >
          <span class="wheel-preview-time shrink-0 rounded-full px-3 py-1 text-xs font-semibold">
            {{ wheelPreviewTimeLabel }}
          </span>
          <span class="h-px flex-1 bg-linear-to-r from-transparent via-white/55 to-transparent"></span>
          <span class="wheel-preview-dot h-2.5 w-2.5 shrink-0 rounded-full"></span>
        </button>

        <!-- 拖动时显示的时间和歌词提示 -->
        <Transition name="fade-scale">
          <div
            v-if="lyricsDragging && dragPreviewInfo"
            class="drag-preview pointer-events-none absolute top-1/2 left-1/2 z-50 -translate-x-1/2 -translate-y-1/2 overflow-hidden rounded-2xl border border-white/10 bg-black/85 px-6 py-4 shadow-2xl backdrop-blur-xl"
          >
            <div class="mb-3 flex items-center justify-center gap-3">
              <span class="text-primary text-2xl font-bold tabular-nums">{{
                dragPreviewInfo.time
              }}</span>
              <span class="text-primary/25">/</span>
              <span class="text-lg text-white/40 tabular-nums">{{
                dragPreviewInfo.totalDuration
              }}</span>
            </div>
            <div class="max-w-md text-center">
              <p class="text-primary mb-1.5 text-base leading-relaxed font-medium">
                {{ dragPreviewInfo.lyric.ori }}
              </p>
              <p
                v-if="dragPreviewInfo.showTrans && dragPreviewInfo.lyric.tran"
                class="text-primary/50 text-sm"
              >
                {{ dragPreviewInfo.lyric.tran }}
              </p>
            </div>
          </div>
        </Transition>
      </div>
    </div>
  </div>
</template>

<style scoped>
@reference "../style/tailwind.css";
.bg-layer {
  transform: scale(1.5);
  filter: blur(48px) saturate(1.3);
  transition: filter 0.3s ease;
  will-change: transform, opacity;
}

/* 暗角效果 */
.vignette {
  background: radial-gradient(ellipse at center, transparent 50%, rgba(0, 0, 0, 0.45) 100%);
}

.glass-toolbar {
  background: rgba(255, 255, 255, 0.06);
  backdrop-filter: blur(16px) saturate(1.4);
  border: 1px solid rgba(255, 255, 255, 0.08);
  box-shadow:
    0 1px 3px rgba(0, 0, 0, 0.2),
    inset 0 1px 0 rgba(255, 255, 255, 0.05);
}

.player-title-link,
.player-meta-link {
  position: relative;
  display: inline-block;
  border: none;
  background: transparent;
  padding: 0;
  border-radius: 0.25rem;
  color: inherit;
  cursor: pointer;
  font: inherit;
  line-height: inherit;
  transition: color 0.2s ease;
}

.player-title-link:hover,
.player-meta-link:hover {
  color: rgb(147 197 253 / 1);
}

.player-title-link::after,
.player-meta-link::after {
  content: '';
  position: absolute;
  left: 0;
  bottom: -0.16em;
  width: 100%;
  height: 2px;
  border-radius: 9999px;
  background: linear-gradient(90deg, rgba(96, 165, 250, 0.2), rgba(59, 130, 246, 0.95));
  transform: scaleX(0);
  transform-origin: center;
  transition: transform 0.18s ease;
}

.player-title-link:hover::after,
.player-meta-link:hover::after {
  transform: scaleX(1);
}

.player-meta-group {
  display: flex;
  flex-direction: column;
  align-items: center;
}

.player-meta-row {
  display: inline-flex;
  max-width: min(100%, 42rem);
  align-items: center;
  justify-content: center;
  gap: 0.5rem;
  color: var(--glass-text-secondary);
}

.player-meta-icon {
  flex-shrink: 0;
  width: 1rem;
  height: 1rem;
  color: var(--glass-text-muted);
}

.player-meta-text {
  min-width: 0;
  color: var(--glass-text-secondary);
}

/* 控制按钮 hover 效果 */
.ctrl-btn {
  transition: all 0.25s cubic-bezier(0.4, 0, 0.2, 1);
}
.ctrl-btn:hover {
  background: rgba(255, 255, 255, 0.08);
  transform: scale(1.08);
}
.ctrl-btn:active {
  transform: scale(0.95);
}

/* 主播放按钮光环 */
.main-play-btn {
  box-shadow:
    0 0 30px rgba(31, 124, 255, 0.25),
    0 8px 32px rgba(77, 163, 255, 0.2);
  transition: box-shadow 0.3s ease;
}
.main-play-btn:hover {
  box-shadow:
    0 0 40px rgba(31, 124, 255, 0.35),
    0 0 60px rgba(77, 163, 255, 0.15),
    0 8px 32px rgba(77, 163, 255, 0.25);
}

.lyrics-container {
  mask-image: linear-gradient(to bottom, transparent 0%, black 12%, black 88%, transparent 100%);
  -webkit-mask-image: linear-gradient(
    to bottom,
    transparent 0%,
    black 12%,
    black 88%,
    transparent 100%
  );
}

.lyrics-scroll {
  transform: translateY(0);
  transition: transform 0.8s cubic-bezier(0.25, 0.46, 0.45, 0.94);
}

.lyric-line {
  line-height: 1.8;
  padding: 0.5rem 1.5rem;
  margin-bottom: 0.25rem;
  transition: all 0.5s cubic-bezier(0.25, 0.46, 0.45, 0.94);
  white-space: pre-line;
}

.lyric-line.current {
  @apply text-primary;
  text-shadow: 0 0 30px rgba(255, 255, 255, 0.3);
  background: linear-gradient(135deg, rgba(31, 124, 255, 0.08), rgba(77, 163, 255, 0.08));
  transform: scale(1.06);
}
.lyric-line.current .lyric-text {
  @apply text-xl font-semibold lg:text-2xl;
}
.lyric-line.current .lyric-sub {
  @apply text-primary/60 mt-1 text-sm lg:text-base;
}

.lyric-sub {
  @apply text-primary/40 mt-0.5 text-sm;
}

.wheel-preview-line {
  color: var(--glass-text-primary);
  cursor: pointer;
}

.wheel-preview-time {
  background: rgba(15, 23, 42, 0.72);
  border: 1px solid rgba(255, 255, 255, 0.14);
  box-shadow: 0 10px 28px rgba(15, 23, 42, 0.28);
  backdrop-filter: blur(16px);
}

.wheel-preview-dot {
  background: linear-gradient(135deg, rgba(77, 163, 255, 0.95), rgba(255, 105, 180, 0.92));
  box-shadow:
    0 0 0 6px rgba(255, 255, 255, 0.04),
    0 0 24px rgba(77, 163, 255, 0.35);
}

@media (max-width: 1024px) {
  .player-left-panel {
    width: 100%;
  }
}

/* 拖动提示框动画 */
.fade-scale-enter-active,
.fade-scale-leave-active {
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.fade-scale-enter-from,
.fade-scale-leave-to {
  opacity: 0;
  transform: translate(-50%, -50%) scale(0.9);
}

/* 拖动预览卡片样式 */
.drag-preview {
  min-width: 280px;
  max-width: 500px;
  box-shadow:
    0 25px 50px rgba(0, 0, 0, 0.4),
    inset 0 1px 0 rgba(255, 255, 255, 0.06);
}
</style>
