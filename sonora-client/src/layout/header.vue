<script setup lang="ts">
import { storeToRefs } from 'pinia'
import { useI18n } from 'vue-i18n'

const router = useRouter()
const { t } = useI18n()
const state = reactive({
  searchQuery: '',
  showLogin: false,
  historyOpen: false,
  searchFocused: false,
})
const { searchQuery, showLogin, historyOpen, searchFocused } = toRefs(state)
const userStore = useUserStore()
const globalStore = useGlobalStore()
const { searchHistory, theme } = storeToRefs(globalStore)
const themeIcon = computed(() => {
  if (theme.value === 'system') return 'icon-[mdi--theme-light-dark]'
  if (theme.value === 'dark') return 'icon-[mdi--weather-night]'
  return 'icon-[mdi--weather-sunny]'
})
const cycleTheme = () => {
  const order: Array<'light' | 'dark' | 'system'> = ['light', 'dark', 'system']
  const idx = order.indexOf(theme.value)
  globalStore.setTheme(order[(idx + 1) % 3])
}
const handleSearchEnter = () => {
  const q = state.searchQuery.trim()
  if (!q) return
  globalStore.addSearchHistory(q)
  state.historyOpen = false
  router.push({ path: '/search', query: { q } })
}
const openHistoryIfAny = () => {
  state.searchFocused = true
  if (searchHistory.value.length > 0) {
    updateDropdownPos()
    state.historyOpen = true
  }
}
const onSearchBlur = () => {
  state.searchFocused = false
}
const selectHistory = (q: string) => {
  state.searchQuery = q
  handleSearchEnter()
}
const clearSearch = () => {
  state.searchQuery = ''
  state.historyOpen = false
}
const rootRef = ref<HTMLElement | null>(null)
const dropdownRef = ref<HTMLElement | null>(null)
const dropdownStyle = ref({ top: '0px', left: '0px', width: '0px' })
const updateDropdownPos = () => {
  const el = rootRef.value
  if (!el) return
  const rect = el.getBoundingClientRect()
  dropdownStyle.value = {
    top: `${rect.bottom + 8}px`,
    left: `${rect.left}px`,
    width: `${rect.width}px`,
  }
}
const onDocClick = (e: Event) => {
  const el = rootRef.value
  const dd = dropdownRef.value
  if (!el) return
  const target = e.target as Node
  if (el.contains(target)) return
  if (dd && dd.contains(target)) return
  state.historyOpen = false
}
const onAuthExpired = () => {
  state.showLogin = true
}
onMounted(() => {
  document.addEventListener('pointerdown', onDocClick)
  window.addEventListener('sonora:auth-expired', onAuthExpired)
})
onUnmounted(() => {
  document.removeEventListener('pointerdown', onDocClick)
  window.removeEventListener('sonora:auth-expired', onAuthExpired)
})
</script>
<template>
  <header class="glass-nav sonora-header m-3 mx-4 flex items-center justify-between gap-4 px-4 py-3.5">
    <!-- 左侧：Logo + 导航 -->
    <div class="flex items-center gap-5">
      <!-- Logo -->
      <router-link to="/" class="flex items-center gap-3">
        <span class="brand-badge">
          <img src="/branding/sonora-logo-icon.svg" alt="logo" class="w-5.5" />
        </span>
        <div class="flex flex-col leading-none">
          <h1 class="brand-font text-primary text-[1.05rem] font-bold">SONORA</h1>
          <span class="text-primary/45 text-[11px] font-medium">Music Platform</span>
        </div>
      </router-link>

      <!-- 前进/后退按钮组 -->
      <div
        class="border-glass-subtle hidden items-center overflow-hidden rounded-[12px] border md:flex"
      >
        <Button
          variant="ghost"
          size="none"
          rounded="none"
          class="border-glass-subtle h-[30px] w-8 justify-center border-r"
          aria-label="back"
          @click="router.back()"
        >
          <span class="icon-[mdi--chevron-left] h-4.5 w-4.5"></span>
        </Button>
        <Button
          variant="ghost"
          size="none"
          rounded="none"
          class="h-[30px] w-8 justify-center"
          aria-label="forward"
          @click="router.forward()"
        >
          <span class="icon-[mdi--chevron-right] h-4.5 w-4.5"></span>
        </Button>
      </div>

    </div>

    <!-- 右侧功能区 -->
    <div class="flex items-center gap-2.5">
      <!-- 搜索框 -->
      <div
        ref="rootRef"
        class="bg-button-glass hidden items-center gap-2 rounded-[10px] px-3 py-1.5 transition-all duration-300 lg:flex"
        :class="[
          searchFocused
            ? 'border-glass min-w-80 border'
            : 'min-w-60 border border-transparent',
        ]"
      >
        <span
          class="icon-[mdi--magnify] text-primary h-4 w-4 shrink-0 transition-opacity duration-200"
          :class="searchFocused || searchQuery ? 'opacity-65' : 'opacity-35'"
        ></span>
        <input
          v-model="searchQuery"
          @keyup.enter="handleSearchEnter"
          @focus="openHistoryIfAny"
          @blur="onSearchBlur"
          type="text"
          :placeholder="t('common.search.placeholder')"
          class="text-primary placeholder:text-primary/35 min-w-0 flex-1 bg-transparent text-[13px] font-[450] outline-none placeholder:font-normal"
        />
        <Transition name="fade-scale">
          <Button
            v-if="searchQuery"
            variant="ghost"
            size="none"
            rounded="lg"
            class="h-5.5 w-5.5 shrink-0 justify-center opacity-40 hover:opacity-70"
            :title="t('common.clear')"
            icon="icon-[mdi--close]"
            icon-class="h-3.5 w-3.5"
            @click="clearSearch"
          />
        </Transition>
      </div>

      <!-- 搜索历史下拉 -->
      <Teleport to="body">
        <Transition name="dropdown">
          <div
            v-if="historyOpen && searchHistory.length"
            ref="dropdownRef"
            class="glass-dropdown fixed z-99999 overflow-hidden rounded-2xl p-1 shadow-lg"
            :style="dropdownStyle"
          >
            <ul class="max-h-60 overflow-auto">
              <li
                v-for="opt in searchHistory"
                :key="opt"
                class="group text-glass-contrast hover:bg-hover-glass relative flex cursor-pointer items-center rounded-[10px] px-2.5 py-2 text-[13px] transition-colors"
                @mousedown.prevent="selectHistory(opt)"
              >
                <span class="icon-[mdi--history] mr-2.5 h-3.5 w-3.5 shrink-0 opacity-40"></span>
                <span class="truncate pr-6">{{ opt }}</span>
                <Button
                  variant="ghost"
                  size="none"
                  rounded="lg"
                  icon="icon-[mdi--close]"
                  icon-class="h-3.5 w-3.5 text-glass-contrast"
                  class="absolute top-1/2 right-2 h-5.5 w-5.5 -translate-y-1/2 justify-center opacity-0 transition-opacity duration-150 group-hover:opacity-50 hover:opacity-80!"
                  :title="t('common.delete')"
                  @mousedown.stop.prevent="globalStore.removeSearchHistory(opt)"
                />
              </li>
            </ul>
          </div>
        </Transition>
      </Teleport>

      <!-- 主题切换 -->
      <Button
        variant="ghost"
        size="icon-md"
        rounded="lg"
        class="opacity-60 hover:opacity-100"
        :title="
          theme === 'system'
            ? t('components.settings.themeOptions.system')
            : theme === 'dark'
              ? t('components.settings.themeOptions.dark')
              : t('components.settings.themeOptions.light')
        "
        @click="cycleTheme"
      >
        <span :class="[themeIcon, 'h-[18px] w-[18px] transition-transform duration-300']"></span>
      </Button>

      <!-- 用户头像 / 登录按钮 -->
      <router-link v-if="userStore.isAuthenticated" to="/profile" class="flex items-center gap-2">
        <img
          :src="userStore.avatarUrl"
          alt="avatar"
          class="h-8 w-8 rounded-full object-cover ring-1 ring-black/6 dark:ring-white/10"
        />
        <span class="text-primary/90 text-sm">{{ userStore.nickname }}</span>
      </router-link>
      <Button
        v-else
        variant="glass"
        size="sm"
        rounded="lg"
        class="gap-1.5 px-3.5 py-1.5"
        @click="showLogin = true"
      >
        <span class="icon-[ic--baseline-person-pin] h-4 w-4"></span>
        {{ t('auth.login') }}
      </Button>

      <!-- 移动端菜单按钮 -->
      <Button variant="ghost" size="icon-md" rounded="lg" class="md:hidden">
        <span class="icon-[mdi--menu] text-primary h-5 w-5"></span>
      </Button>
    </div>
  </header>
  <LoginDialog v-if="showLogin" @close="showLogin = false" />
</template>

<style scoped>
@reference "../style/tailwind.css";

.sonora-header {
  border-radius: 12px;
}

.brand-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 2.6rem;
  height: 2.6rem;
  border-radius: 0.85rem;
  background: var(--glass-bg-base);
  border: 1px solid var(--glass-border-default);
  box-shadow: none;
}

/* 搜索清除按钮动画 */
.fade-scale-enter-active,
.fade-scale-leave-active {
  transition:
    opacity 0.15s,
    transform 0.15s;
}
.fade-scale-enter-from,
.fade-scale-leave-to {
  opacity: 0;
  transform: scale(0.8);
}

/* 历史下拉动画 */
.dropdown-enter-active {
  transition:
    opacity 0.2s,
    transform 0.2s;
}
.dropdown-leave-active {
  transition:
    opacity 0.15s,
    transform 0.15s;
}
.dropdown-enter-from {
  opacity: 0;
  transform: translateY(-6px) scale(0.97);
}
.dropdown-leave-to {
  opacity: 0;
  transform: translateY(-4px);
}
</style>
