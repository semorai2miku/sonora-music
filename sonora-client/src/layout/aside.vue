<script setup lang="ts">
import { useUserStore } from '@/stores/modules/user'
import { createMyPlaylist, myPlaylists } from '@/api'
import Button from '@/components/Ui/Button.vue'
import { gsap } from 'gsap'

const route = useRoute()
const router = useRouter()

const sections = [
  {
    titleKey: 'layout.aside.explore',
    items: [
      { to: '/', labelKey: 'layout.aside.menu.home', icon: 'mdi--home' },
      { to: '/search', labelKey: 'layout.aside.menu.search', icon: 'mdi--magnify' },
      { to: '/library', labelKey: 'layout.aside.menu.library', icon: 'mdi--music-box-multiple-outline' },
      { to: '/artists', labelKey: 'layout.aside.menu.artists', icon: 'mdi--account-music' },
      { to: '/albums', labelKey: 'layout.aside.menu.albums', icon: 'mdi--album' },
      { to: '/playlists', labelKey: 'layout.aside.menu.playlists', icon: 'mdi--playlist-music' },
    ],
  },
  {
    titleKey: 'layout.aside.myMusic',
    items: [
      { to: '/my-music', labelKey: 'layout.aside.menu.recent', icon: 'mdi--history' },
    ],
  },
  {
    titleKey: 'layout.aside.system',
    items: [{ to: '/settings', labelKey: 'layout.aside.menu.settings', icon: 'mdi--cog' }],
  },
]

const state = reactive({
  // 用户创建的歌单列表
  userPlaylists: [] as Array<{ id: number; name: string; cover?: string; type?: string; pinned?: number }>,
})
const { userPlaylists } = toRefs(state)
const userStore = useUserStore()
const showCreatePlaylist = ref(false)
const createPlaylistName = ref('')
const createPlaylistError = ref('')
const creatingPlaylist = ref(false)

const loadUserPlaylists = async () => {
  if (!userStore.isLoggedIn) {
    state.userPlaylists = []
    return
  }
  try {
    const res = await myPlaylists()
    state.userPlaylists = (res?.data || []).map(item => ({
      id: Number(item.id),
      name: String(item.name || '未命名歌单'),
      cover: item.cover,
      type: item.type,
      pinned: item.pinned,
    }))
  } catch {
    state.userPlaylists = []
  }
}

const openCreatePlaylist = () => {
  createPlaylistName.value = ''
  createPlaylistError.value = ''
  showCreatePlaylist.value = true
}

const closeCreatePlaylist = () => {
  if (creatingPlaylist.value) return
  showCreatePlaylist.value = false
}

const submitCreatePlaylist = async () => {
  const name = createPlaylistName.value.trim()
  if (!name) {
    createPlaylistError.value = '请输入歌单名称'
    return
  }
  creatingPlaylist.value = true
  createPlaylistError.value = ''
  try {
    const res = await createMyPlaylist({ name })
    if (res?.code !== 200 || !res.data?.id) {
      throw new Error(res?.message || '创建歌单失败')
    }
    await loadUserPlaylists()
    window.dispatchEvent(new CustomEvent('sonora:playlists-updated'))
    showCreatePlaylist.value = false
    router.push(`/playlist/${res.data.id}`)
  } catch (error: any) {
    createPlaylistError.value = error?.response?.data?.message || error?.message || '创建歌单失败'
  } finally {
    creatingPlaylist.value = false
  }
}

// 活动指示器相关
const indicatorRef = ref<HTMLElement | null>(null)
const navContainerRef = ref<HTMLElement | null>(null)

// 更新指示器位置
const updateIndicator = () => {
  if (!indicatorRef.value || !navContainerRef.value) return

  const activeLink = navContainerRef.value.querySelector('.nav-link-active') as HTMLElement
  if (!activeLink) {
    // 隐藏指示器
    gsap.to(indicatorRef.value, {
      opacity: 0,
      duration: 0.2,
    })
    return
  }

  const containerRect = navContainerRef.value.getBoundingClientRect()
  const linkRect = activeLink.getBoundingClientRect()

  gsap.to(indicatorRef.value, {
    y: linkRect.top - containerRect.top,
    height: linkRect.height,
    opacity: 1,
    duration: 0.3,
    ease: 'power3.out',
  })
}

// 监听路由变化
watch(
  () => route.path,
  () => {
    nextTick(() => {
      updateIndicator()
    })
  },
  { immediate: true }
)

// 组件挂载后初始化
onMounted(() => {
  nextTick(() => {
    updateIndicator()
  })
  loadUserPlaylists()
  window.addEventListener('sonora:playlists-updated', loadUserPlaylists)
})

onUnmounted(() => {
  window.removeEventListener('sonora:playlists-updated', loadUserPlaylists)
})

watch(() => userStore.isLoggedIn, loadUserPlaylists)

// 检查是否是当前路由
const isActive = (path: string) => {
  if (path === '/') {
    return route.path === '/'
  }
  return route.path.startsWith(path)
}
</script>
<template>
  <aside class="hidden w-64 shrink-0 p-4 py-0 lg:block">
    <div class="glass-card relative h-full p-4">
      <!-- 滑动指示器 -->
      <div
        ref="indicatorRef"
        class="nav-indicator pointer-events-none absolute right-2 left-2 rounded-xl bg-[rgba(31,124,255,0.08)] opacity-0"
        style="height: 40px; z-index: 0"
      ></div>

      <div ref="navContainerRef">
        <div v-for="sec in sections.slice(0, 2)" :key="sec.titleKey" class="mb-6">
          <h3 class="text-primary mb-3 text-xs font-semibold tracking-wide uppercase">
            {{ $t(sec.titleKey) }}
          </h3>
          <nav class="relative space-y-1">
            <router-link
              v-for="item in sec.items"
              :key="item.to"
              :to="item.to"
              class="nav-link text-primary/70 hover:text-primary relative z-10 flex items-center space-x-3 rounded-xl p-2 transition-all duration-200"
              :class="{
                'nav-link-active text-primary font-medium': isActive(item.to),
                'hover:bg-hover-glass': !isActive(item.to),
              }"
            >
              <span
                class="h-5 w-5 transition-transform duration-200"
                :class="[`icon-[${item.icon}]`, { 'scale-110': isActive(item.to) }]"
              ></span>
              <span>{{ $t(item.labelKey) }}</span>
            </router-link>
            <div class="hidden">
              <span class="icon-[mdi--chevron-right]"></span>
            </div>
          </nav>
      </div>
      <div class="mb-6" v-if="userStore.isLoggedIn">
        <div class="mb-3 flex items-center justify-between">
          <h4 class="text-primary/60 text-sm font-medium">
            {{ $t('layout.aside.playlists.created') }}
          </h4>
          <Button
            variant="ghost"
            size="icon-sm"
            rounded="full"
            icon="icon-[mdi--plus]"
            icon-class="h-4 w-4"
            title="新建歌单"
            @click="openCreatePlaylist"
          />
        </div>
        <div class="space-y-2">
          <router-link
            v-for="playlist in userPlaylists"
            :key="playlist.id"
            :to="`/playlist/${playlist.id}`"
            class="group flex cursor-pointer items-center space-x-3 rounded-xl p-2 transition-all duration-200 hover:bg-hover-glass"
            :class="{ 'nav-link-active bg-hover-glass': isActive(`/playlist/${playlist.id}`) }"
          >
            <div
              class="flex h-8 w-8 items-center justify-center overflow-hidden rounded-lg border border-glass bg-button-glass text-xs text-primary transition-transform duration-200 group-hover:scale-105"
            >
              <img
                v-if="playlist.cover"
                :src="playlist.cover"
                alt="cover"
                class="h-full w-full object-cover"
              />
              <span v-else :class="playlist.type === 'liked' ? 'icon-[mdi--heart]' : ''">
                {{ playlist.type === 'liked' ? '' : playlist.name.charAt(0) }}
              </span>
            </div>
            <span
              class="text-primary/80 group-hover:text-primary truncate text-sm transition-colors"
              >{{ playlist.name }}</span
            >
          </router-link>
        </div>
      </div>
      <div v-for="sec in sections.slice(2)" :key="sec.titleKey" class="mb-6">
        <h3 class="text-primary mb-3 text-xs font-semibold tracking-wide uppercase">
          {{ $t(sec.titleKey) }}
        </h3>
        <nav class="relative space-y-1">
          <router-link
            v-for="item in sec.items"
            :key="item.to"
            :to="item.to"
            class="nav-link text-primary/70 hover:text-primary relative z-10 flex items-center space-x-3 rounded-xl p-2 transition-all duration-200"
            :class="{
              'nav-link-active text-primary font-medium': isActive(item.to),
              'hover:bg-hover-glass': !isActive(item.to),
            }"
          >
            <span
              class="h-5 w-5 transition-transform duration-200"
              :class="[`icon-[${item.icon}]`, { 'scale-110': isActive(item.to) }]"
            ></span>
            <span>{{ $t(item.labelKey) }}</span>
          </router-link>
        </nav>
      </div>
      </div>
      <div class="hidden">
        <span class="icon-[mdi--home] h-5 w-5"></span>
        <span class="icon-[mdi--video] h-5 w-5"></span>
        <span class="icon-[mdi--chart-line] h-5 w-5"></span>
        <span class="icon-[ic--round-search] h-5 w-5"></span>
        <span class="icon-[mdi--music-box-multiple] h-5 w-5"></span>
        <span class="icon-[mdi--heart-outline] h-5 w-5"></span>
        <span class="icon-[mdi--cog] h-5 w-5"></span>
        <span class="icon-[mdi--chevron-right] h-5 w-5"></span>
        <span class="icon-[mdi--account-music] h-5 w-5"></span>
        <span class="icon-[mdi--album] h-5 w-5"></span>
        <span class="icon-[mdi--music-box-multiple-outline] h-5 w-5"></span>
        <span class="icon-[mdi--magnify] h-5 w-5"></span>
        <span class="icon-[mdi--plus] h-5 w-5"></span>
        <span class="icon-[mdi--playlist-music] h-5 w-5"></span>
      </div>
    </div>
    <div
      v-if="showCreatePlaylist"
      class="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4 backdrop-blur-sm"
      @click.self="closeCreatePlaylist"
    >
      <div class="glass-container-strong w-full max-w-sm p-5">
        <div class="mb-4 flex items-center justify-between">
          <h3 class="text-primary text-lg font-semibold">新建歌单</h3>
          <Button
            variant="soft"
            size="icon-sm"
            rounded="full"
            icon="icon-[mdi--close]"
            icon-class="h-4 w-4"
            @click="closeCreatePlaylist"
          />
        </div>
        <input
          v-model="createPlaylistName"
          type="text"
          maxlength="80"
          placeholder="请输入歌单名称"
          class="text-primary glass-card mb-3 w-full rounded-xl border border-glass px-4 py-3 text-sm outline-none placeholder:text-primary/30 focus:border-sky-400/50"
          @keyup.enter="submitCreatePlaylist"
        />
        <p v-if="createPlaylistError" class="mb-3 text-sm text-red-300">{{ createPlaylistError }}</p>
        <Button
          variant="solid"
          size="md"
          block
          :loading="creatingPlaylist"
          :disabled="creatingPlaylist"
          icon="icon-[mdi--plus]"
          @click="submitCreatePlaylist"
        >
          创建歌单
        </Button>
      </div>
    </div>
  </aside>
</template>

<style scoped>
/* 导航指示器过渡 */
.nav-indicator {
  transition: opacity 0.2s ease;
}

/* 导航链接悬停效果 */
.nav-link {
  position: relative;
}

.nav-link::before {
  content: '';
  position: absolute;
  inset: 0;
  border-radius: 0.5rem;
  background: transparent;
  transition: background 0.2s ease;
}

.nav-link:hover::before {
  background: rgba(31, 124, 255, 0.05);
}

.nav-link-active::before {
  background: transparent;
}
</style>
