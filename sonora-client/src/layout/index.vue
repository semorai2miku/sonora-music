<script setup lang="ts">
import Header from './header.vue'
import Aside from './aside.vue'
import Footer from './footer.vue'

// 抽屉状态
const state = reactive({
  // 播放器抽屉是否打开
  isDrawerOpen: false,
})
const { isDrawerOpen } = toRefs(state)

const openPlayerDrawer = () => {
  state.isDrawerOpen = true
}
</script>

<template>
  <div class="relative flex h-full w-full overflow-hidden">
    <div class="custom-theme absolute inset-0 h-full w-full"></div>
    <!-- 主容器 -->
    <div class="z-50 flex w-full flex-col px-4 py-4 xl:px-8">
      <div class="shell-frame glass-container flex flex-1 flex-col overflow-hidden">
        <!-- 头部区域 -->
        <Header />
        <!-- 主内容区域 -->
        <main class="flex h-full overflow-x-hidden">
          <!-- 左侧边栏 -->
          <Aside />
          <!-- 右侧主内容 -->
          <router-view v-slot="{ Component }">
            <transition appear name="fade-transform" mode="out-in">
              <keep-alive>
                <component :is="Component" />
              </keep-alive>
            </transition>
          </router-view>
          <!-- 播放器抽屉 -->
          <PlayerDrawer v-model="isDrawerOpen" />
        </main>
        <Footer @show="openPlayerDrawer" />
      </div>
    </div>
  </div>
</template>
<style>
.custom-theme {
  background: var(--glass-bg-solid);
}

html.dark .custom-theme {
  background: var(--glass-bg-solid);
}

.shell-frame {
  min-height: calc(100vh - 2rem);
  background: var(--glass-bg-elevated);
  border: 1px solid var(--glass-border-default);
  box-shadow: none;
}

.fade-transform-enter-active,
.fade-transform-leave-active {
  transition: all 0.3s ease;
}

.fade-transform-enter-from {
  opacity: 0;
  transform: translateX(-20px);
}

.fade-transform-leave-to {
  opacity: 0;
  transform: translateX(20px);
}
</style>
