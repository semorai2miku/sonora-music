<script setup lang="ts">
import MiniPlayerMobile from '@/components/Mobile/MiniPlayerMobile.vue'

import { useI18n } from 'vue-i18n'
const { t } = useI18n()
const route = useRoute()
const items = [
  { to: '/', icon: 'icon-[mdi--home]', labelKey: 'layout.nav.home' },
  { to: '/library', icon: 'icon-[mdi--music-box-multiple-outline]', labelKey: 'layout.aside.menu.library' },
  { to: '/playlists', icon: 'icon-[mdi--playlist-music]', labelKey: 'layout.aside.menu.playlists' },
  { to: '/recent', icon: 'icon-[mdi--history]', labelKey: 'layout.aside.menu.recent' },
]

const emit = defineEmits(['show-player'])

const tabbarRef = useTemplateRef('tabbarRef')
let ro: ResizeObserver | null = null
const updateTabbarHeight = () => {
  const h = tabbarRef.value?.offsetHeight ?? 0
  document.documentElement.style.setProperty('--mobile-tabbar-h', `${h}px`)
}
onMounted(() => {
  updateTabbarHeight()
  if (tabbarRef.value) {
    ro = new ResizeObserver(updateTabbarHeight)
    ro.observe(tabbarRef.value)
  }
  window.addEventListener('resize', updateTabbarHeight)
})
onUnmounted(() => {
  ro?.disconnect()
  ro = null
  window.removeEventListener('resize', updateTabbarHeight)
})

const isActive = (path: string) => {
  if (path === '/') return route.path === '/'
  return route.path.startsWith(path)
}
</script>

<template>
  <MiniPlayerMobile @open="emit('show-player')" />

  <nav ref="tabbarRef" class="mobile-tabbar fixed right-0 bottom-0 left-0 z-50">
    <div class="mx-auto flex items-center justify-around">
      <RouterLink
        v-for="it in items"
        :key="it.to"
        :to="it.to"
        class="flex flex-col items-center justify-center py-3 text-xs"
        :class="isActive(it.to) ? 'text-primary' : 'text-primary/60'"
      >
        <component :is="'span'" :class="it.icon" class="mb-1 h-6 w-6" />
        <span>{{ t(it.labelKey) }}</span>
      </RouterLink>
    </div>
  </nav>
</template>
