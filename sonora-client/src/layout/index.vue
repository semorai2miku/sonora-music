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
    <div class="custom-theme absolute inset-0 h-full w-full">
      <div class="shell-backdrop h-full w-full" />
    </div>
    <div class="shell-filter absolute inset-0"></div>
    <!-- 主容器 -->
    <div class="z-50 flex w-full flex-col px-6 py-5 xl:px-16">
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
  background:
    linear-gradient(180deg, #f3f7fb 0%, #f8fbff 40%, #f1f5fa 100%);
}

html.dark .custom-theme {
  background:
    linear-gradient(180deg, #06111d 0%, #08131f 44%, #050d18 100%);
}

.shell-backdrop {
  background:
    linear-gradient(135deg, rgba(31, 124, 255, 0.06), transparent 42%),
    linear-gradient(315deg, rgba(77, 163, 255, 0.06), transparent 38%);
}

html.dark .shell-backdrop {
  background:
    linear-gradient(135deg, rgba(77, 163, 255, 0.08), transparent 42%),
    linear-gradient(315deg, rgba(31, 124, 255, 0.07), transparent 38%);
}

.shell-filter {
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.12) 0%, rgba(255, 255, 255, 0) 100%);
  pointer-events: none;
}

html.dark .shell-filter {
  background:
    linear-gradient(180deg, rgba(5, 10, 20, 0.1) 0%, rgba(5, 10, 20, 0) 100%);
}

.shell-frame {
  min-height: calc(100vh - 2.5rem);
  background: var(--glass-bg-elevated);
  border: 1px solid var(--glass-border-default);
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
