<script setup lang="ts">
import { myPlaylists, type ClientPlaylist } from '@/api'
import LoginDialog from '@/components/Auth/LoginDialog.vue'
import { useAudioStore } from '@/stores/modules/audio'
import type { Song as StoreSong } from '@/stores/interface'
import { useUserStore } from '@/stores/modules/user'

const userStore = useUserStore()
const audioStore = useAudioStore()
const { audio } = storeToRefs(audioStore)

const state = reactive({
  playlists: [] as ClientPlaylist[],
  isLoadingPlaylists: false,
  showLogin: false,
})

const recentList = computed<StoreSong[]>(() => audio.value?.playHistory || [])

const loadUserPlaylists = async () => {
  if (!userStore.isLoggedIn) {
    state.playlists = []
    return
  }

  state.isLoadingPlaylists = true
  try {
    const res = await myPlaylists()
    state.playlists = Array.isArray(res?.data) ? res.data : []
  } catch {
    state.playlists = []
  } finally {
    state.isLoadingPlaylists = false
  }
}

onMounted(() => {
  loadUserPlaylists()
  window.addEventListener('sonora:playlists-updated', loadUserPlaylists)
})

onUnmounted(() => {
  window.removeEventListener('sonora:playlists-updated', loadUserPlaylists)
})

watch(() => userStore.isLoggedIn, loadUserPlaylists)
</script>

<template>
  <div class="flex-1 overflow-auto px-4 pb-6">
    <section class="mb-5 pt-1">
      <h1 class="text-primary text-xl font-semibold">{{ $t('myMusic.title') }}</h1>
      <p class="text-primary/55 mt-2 text-sm">{{ $t('myMusic.subtitle') }}</p>
    </section>

    <section class="mb-6">
      <div class="mb-3 flex items-center justify-between gap-3">
        <div>
          <h2 class="text-primary text-sm font-semibold">{{ $t('myMusic.playlistsTitle') }}</h2>
          <p class="text-primary/45 mt-1 text-xs">{{ $t('myMusic.playlistsSubtitle') }}</p>
        </div>
        <button
          v-if="!userStore.isLoggedIn"
          class="glass-button rounded-full px-3 py-1.5 text-xs"
          @click="state.showLogin = true"
        >
          {{ $t('auth.login') }}
        </button>
      </div>

      <div v-if="state.isLoadingPlaylists" class="grid grid-cols-2 gap-3">
        <div
          v-for="idx in 4"
          :key="idx"
          class="aspect-square animate-pulse rounded-2xl border border-white/6 bg-white/5"
        ></div>
      </div>

      <div v-else-if="userStore.isLoggedIn && state.playlists.length" class="grid grid-cols-2 gap-3">
        <router-link
          v-for="playlist in state.playlists"
          :key="playlist.id"
          :to="`/playlist/${playlist.id}`"
          class="group overflow-hidden rounded-2xl"
        >
          <div class="relative aspect-square overflow-hidden rounded-2xl border border-white/6 bg-white/4">
            <LazyImage
              :src="(playlist.cover || '/default-cover.svg') + '?param=240y240'"
              :alt="playlist.name"
              imgClass="h-full w-full object-cover transition-transform duration-300 group-active:scale-105"
            />
            <div class="absolute inset-0 bg-linear-to-t from-black/60 via-transparent to-transparent"></div>
            <div class="absolute right-0 bottom-0 left-0 p-3">
              <p class="line-clamp-2 text-sm font-medium text-white">{{ playlist.name }}</p>
              <p class="mt-1 text-xs text-white/70">
                {{ $t('commonUnits.songsShort', { count: playlist.songCount || 0 }) }}
              </p>
            </div>
          </div>
        </router-link>
      </div>

      <div v-else class="rounded-2xl border border-white/6 bg-white/4 px-4 py-6 text-center">
        <p class="text-primary/70 text-sm">
          {{
            userStore.isLoggedIn ? $t('myMusic.playlistsEmpty') : $t('myMusic.playlistsLoginHint')
          }}
        </p>
        <p class="text-primary/45 mt-2 text-xs">{{ $t('myMusic.playlistsHelper') }}</p>
      </div>
    </section>

    <section>
      <div class="mb-3">
        <h2 class="text-primary text-sm font-semibold">{{ $t('recent.title') }}</h2>
        <p class="text-primary/45 mt-1 text-xs">{{ $t('recent.subtitle') }}</p>
      </div>
      <div v-if="recentList.length">
        <MobileSongList :songs="recentList" variant="card" />
      </div>
      <div v-else class="rounded-2xl border border-white/6 bg-white/4 px-4 py-7 text-center">
        <p class="text-primary/70 text-sm">{{ $t('recent.empty') }}</p>
      </div>
    </section>
  </div>
  <LoginDialog v-if="state.showLogin" @close="state.showLogin = false" @success="loadUserPlaylists" />
</template>
