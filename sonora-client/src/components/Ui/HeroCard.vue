<script setup lang="ts">
import LazyImage from '@/components/Ui/LazyImage.vue'
import { formatCount } from '@/utils/time'
import { withImageParam } from '@/utils/media'

interface Props {
  id: number | string
  coverUrl: string
  title: string
  subtitle?: string
  playCount?: number
  trackCount?: number
  to: string
  aspectRatio?: 'square' | 'video'
  enableTilt?: boolean
  playable?: boolean
  collectible?: boolean
  collected?: boolean
  collectDisabled?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  aspectRatio: 'square',
  enableTilt: true,
  playable: false,
  collectible: false,
  collected: false,
  collectDisabled: false,
})
const emit = defineEmits<{
  (e: 'play', id: number | string): void
  (e: 'collect', id: number | string): void
}>()

const router = useRouter()
const cardRef = ref<HTMLElement | null>(null)

// Tilt 效果相关
const tiltConfig = {
  max: 12,
  scale: 1.03,
  speed: 400,
  glareMax: 0.15,
}

// 创建光泽层
const glareRef = ref<HTMLElement | null>(null)

onMounted(() => {
  if (props.enableTilt && cardRef.value) {
    // 创建光泽层
    const glareEl = document.createElement('div')
    glareEl.className = 'tilt-glare'
    glareEl.style.cssText = `
      position: absolute;
      inset: 0;
      border-radius: inherit;
      pointer-events: none;
      background: linear-gradient(
        135deg,
        rgba(255, 255, 255, 0) 0%,
        rgba(255, 255, 255, 0) 50%,
        rgba(255, 255, 255, ${tiltConfig.glareMax}) 100%
      );
      opacity: 0;
      transition: opacity ${tiltConfig.speed}ms ease;
      z-index: 10;
    `
    const innerCard = cardRef.value.querySelector('.hero-card-inner')
    if (innerCard) {
      ;(innerCard as HTMLElement).style.overflow = 'hidden'
      innerCard.appendChild(glareEl)
      glareRef.value = glareEl
    }
  }
})

onUnmounted(() => {
  glareRef.value?.remove()
})

const handleClick = () => {
  router.push(props.to)
}

// 3D Tilt 悬停效果
const handleMouseMove = (e: MouseEvent) => {
  if (!props.enableTilt || !cardRef.value) return

  const card = cardRef.value
  const rect = card.getBoundingClientRect()
  const centerX = rect.left + rect.width / 2
  const centerY = rect.top + rect.height / 2

  // 计算鼠标相对于中心的位置 (-1 到 1)
  const mouseX = (e.clientX - centerX) / (rect.width / 2)
  const mouseY = (e.clientY - centerY) / (rect.height / 2)

  // 计算旋转角度
  const rotateX = -mouseY * tiltConfig.max
  const rotateY = mouseX * tiltConfig.max

  gsap.to(card, {
    rotateX,
    rotateY,
    scale: tiltConfig.scale,
    duration: 0.1,
    ease: 'power2.out',
    transformPerspective: 1000,
  })

  // 更新光泽
  if (glareRef.value) {
    const angle = Math.atan2(mouseY, mouseX) * (180 / Math.PI) + 135
    const intensity = Math.sqrt(mouseX ** 2 + mouseY ** 2)
    glareRef.value.style.background = `linear-gradient(${angle}deg, rgba(255, 255, 255, 0) 0%, rgba(255, 255, 255, ${tiltConfig.glareMax * intensity}) 100%)`
    glareRef.value.style.opacity = '1'
  }
}

const handleMouseEnter = () => {
  if (!props.enableTilt && cardRef.value) {
    // 如果没有启用 tilt，使用简单的悬停效果
    gsap.to(cardRef.value, {
      scale: 1.02,
      y: -4,
      duration: 0.3,
      ease: 'power2.out',
    })
  }
}

const handleMouseLeave = () => {
  if (cardRef.value) {
    gsap.to(cardRef.value, {
      rotateX: 0,
      rotateY: 0,
      scale: 1,
      y: 0,
      duration: 0.5,
      ease: 'elastic.out(1, 0.5)',
      transformPerspective: 1000,
    })
  }
  if (glareRef.value) {
    glareRef.value.style.opacity = '0'
  }
}

const handlePlayClick = (event: MouseEvent) => {
  event.stopPropagation()
  event.preventDefault()
  if (!props.playable) return
  emit('play', props.id)
}

const handleCollectClick = (event: MouseEvent) => {
  event.stopPropagation()
  event.preventDefault()
  if (!props.collectible || props.collectDisabled) return
  emit('collect', props.id)
}
</script>

<template>
  <div
    ref="cardRef"
    class="hero-card group cursor-pointer"
    @click="handleClick"
    @mousemove="handleMouseMove"
    @mouseenter="handleMouseEnter"
    @mouseleave="handleMouseLeave"
  >
    <div
      class="hero-card-inner relative overflow-hidden rounded-2xl shadow-lg transition-shadow duration-300 group-hover:shadow-xl"
      :class="aspectRatio === 'video' ? 'aspect-video' : 'aspect-square'"
    >
      <LazyImage
        :src="withImageParam(coverUrl, '300y300')"
        :alt="title"
        img-class="h-full w-full object-cover transition-transform duration-700 group-hover:scale-110"
        wrapper-class="h-full w-full"
      />

      <!-- 渐变遮罩 -->
      <div class="absolute inset-0 bg-linear-to-t from-black/70 via-black/20 to-transparent" />

      <!-- 播放量标签 -->
      <div
        v-if="playCount"
        class="absolute top-2 right-2 flex items-center gap-1 rounded-full bg-black/50 px-2 py-1 text-[10px] text-white backdrop-blur-sm"
      >
        <span class="icon-[mdi--headphones] h-3 w-3" />
        {{ formatCount(playCount) }}
      </div>

      <button
        v-if="collectible"
        type="button"
        class="absolute top-2 left-2 z-20 flex h-8 w-8 items-center justify-center rounded-full border border-white/10 bg-black/45 text-white shadow-lg backdrop-blur-sm transition-transform duration-200 hover:scale-105"
        :disabled="collectDisabled"
        @click.stop.prevent="handleCollectClick"
      >
        <span
          class="h-4.5 w-4.5"
          :class="collected ? 'icon-[mdi--heart] text-pink-400' : 'icon-[mdi--heart-outline]'"
        ></span>
      </button>

      <!-- 底部信息 -->
      <div class="absolute right-0 bottom-0 left-0 p-2.5">
        <p class="line-clamp-2 text-xs leading-tight font-medium text-white">
          {{ title }}
        </p>
        <div v-if="trackCount" class="mt-1.5 flex items-center gap-1.5 text-[10px] text-white/70">
          <span class="icon-[mdi--music-note] h-3 w-3" />
          <span>{{ trackCount }}首</span>
        </div>
      </div>

      <!-- 悬停播放按钮 -->
      <div
        class="absolute inset-0 flex items-center justify-center bg-black/20 opacity-0 transition-all duration-300 group-hover:opacity-100"
      >
        <button
          v-if="playable"
          type="button"
          class="flex h-12 w-12 scale-75 items-center justify-center rounded-full bg-white/90 shadow-xl transition-transform duration-300 group-hover:scale-100"
          @click.stop.prevent="handlePlayClick"
        >
          <span class="icon-[mdi--play] h-6 w-6 text-sky-500" />
        </button>
        <div
          v-else
          class="flex h-12 w-12 scale-75 items-center justify-center rounded-full bg-white/90 shadow-xl transition-transform duration-300 group-hover:scale-100"
        >
          <span class="icon-[mdi--play] h-6 w-6 text-sky-500" />
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.hero-card {
  will-change: transform;
  transform-origin: center center;
  transform-style: preserve-3d;
}

.hero-card-inner {
  transform-style: preserve-3d;
}
</style>
