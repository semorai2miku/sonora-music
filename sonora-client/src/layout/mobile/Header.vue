<script setup lang="ts">
// 移动端头部：左侧 Logo/标题，右侧搜索与登录入口
import { useI18n } from 'vue-i18n'
import { useUserStore } from '@/stores/modules/user'
const router = useRouter()
const { t } = useI18n()
const userStore = useUserStore()

// 搜索入口：跳转到搜索页
const goSearch = () => router.push('/search')

// 登录入口：沿用 PC 逻辑，显示登录弹窗（若全局自动注册，可直接触发）
const state = reactive({ showLogin: false })
const { showLogin } = toRefs(state)
</script>

<template>
  <header class="glass-nav flex items-center justify-between px-3 py-2">
    <div class="flex items-center gap-2">
      <router-link to="/" class="flex items-center gap-2">
        <span class="brand-badge">
          <img src="/branding/sonora-logo-icon.svg" alt="logo" class="w-4.5" />
        </span>
        <div class="flex flex-col leading-none">
          <h1 class="brand-font text-primary text-sm font-semibold">SONORA</h1>
          <span class="text-primary/45 text-[10px]">Music</span>
        </div>
      </router-link>
    </div>
    <div class="flex items-center gap-2">
      <button
        class="hover:bg-hover-glass rounded-md p-2"
        :title="t('common.search.label')"
        @click="goSearch"
      >
        <span class="icon-[mdi--magnify] h-5 w-5"></span>
      </button>
      <router-link
        v-if="userStore.isLoggedIn"
        to="/profile"
        class="hover:bg-hover-glass rounded-md p-1.5"
      >
        <img :src="userStore.avatarUrl" alt="avatar" class="h-7 w-7 rounded-full object-cover" />
      </router-link>
      <button v-else class="hover:bg-hover-glass rounded-md p-2" :title="t('auth.login')" @click="showLogin = true">
        <span class="icon-[mdi--account] h-5 w-5"></span>
      </button>
    </div>
  </header>
  <LoginDialog v-if="showLogin" @close="showLogin = false" />
</template>

<style scoped>
.brand-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 2.15rem;
  height: 2.15rem;
  border-radius: 0.7rem;
  background: var(--glass-bg-base);
  border: 1px solid var(--glass-border-default);
  box-shadow: none;
}
</style>
