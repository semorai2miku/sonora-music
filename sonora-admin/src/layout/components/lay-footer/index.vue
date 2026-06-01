<script setup lang="ts">
import { getConfig } from "@/config";

const TITLE = getConfig("Title");
</script>

<template>
  <footer
    class="layout-footer text-[rgba(0,0,0,0.6)] dark:text-[rgba(220,220,242,0.8)]"
  >
    <span>Copyright © 2026-present</span>
    <span class="brand-font">&nbsp;SONORA</span>
    <span>&nbsp;|&nbsp;{{ TITLE }}</span>
  </footer>
</template>

<style lang="scss" scoped>
.layout-footer {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 2px;
  width: 100%;
  padding: 0 0 14px;
  font-size: 13px;
}
</style>
