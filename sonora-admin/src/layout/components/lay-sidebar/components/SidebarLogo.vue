<script setup lang="ts">
import { getTopMenu } from "@/router/utils";
import { useNav } from "@/layout/hooks/useNav";

defineProps({
  collapse: Boolean
});

const { title, getLogo } = useNav();
</script>

<template>
  <div class="sidebar-logo-container" :class="{ collapses: collapse }">
    <transition name="sidebarLogoFade">
      <router-link
        v-if="collapse"
        key="collapse"
        :title="title"
        class="sidebar-logo-link sidebar-logo-link--collapse"
        :to="getTopMenu()?.path ?? '/'"
      >
        <span class="sidebar-badge">
          <img :src="getLogo()" alt="logo" />
        </span>
      </router-link>
      <router-link
        v-else
        key="expand"
        :title="title"
        class="sidebar-logo-link"
        :to="getTopMenu()?.path ?? '/'"
      >
        <span class="sidebar-badge">
          <img :src="getLogo()" alt="logo" />
        </span>
        <span class="sidebar-title brand-font">{{ title }}</span>
      </router-link>
    </transition>
  </div>
</template>

<style lang="scss" scoped>
.sidebar-logo-container {
  position: relative;
  width: 100%;
  height: 56px;
  overflow: hidden;

  .sidebar-logo-link {
    display: flex;
    flex-wrap: nowrap;
    align-items: center;
    gap: 12px;
    height: 100%;
    padding: 0 14px;

    &--collapse {
      justify-content: center;
    }

    .sidebar-badge {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      width: 34px;
      height: 34px;
      border-radius: 12px;
      background: linear-gradient(
        135deg,
        rgba(31, 124, 255, 0.18),
        rgba(77, 163, 255, 0.08)
      );
      box-shadow: 0 8px 18px rgba(31, 124, 255, 0.14);

      img {
        display: inline-block;
        width: 22px;
        height: 22px;
      }
    }

    .sidebar-title {
      display: inline-block;
      overflow: hidden;
      text-overflow: ellipsis;
      font-size: 14px;
      font-weight: 700;
      line-height: 1;
      color: var(--pure-theme-sub-menu-active-text);
      white-space: nowrap;
    }
  }
}
</style>
