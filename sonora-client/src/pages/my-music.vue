<script setup lang="ts">
import { myPlaylists, type ClientPlaylist } from '@/api'
import LoginDialog from '@/components/Auth/LoginDialog.vue'
import SongList from '@/components/SongList.vue'
import Button from '@/components/Ui/Button.vue'
import HeroCard from '@/components/Ui/HeroCard.vue'
import { useAudio } from '@/composables/useAudio'
import { useUserStore } from '@/stores/modules/user'
import type { Song as StoreSong } from '@/stores/interface'
import { useAudioStore } from '@/stores/modules/audio'

const userStore = useUserStore()
const audioStore = useAudioStore()
const { audio } = storeToRefs(audioStore)
const { setPlaylist, play } = useAudio()

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

const playAllRecent = () => {
  if (!recentList.value.length) return
  const list = recentList.value
  setPlaylist(list, 0)
  play(list[0], 0)
}

const openLogin = () => {
  state.showLogin = true
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
  <div class="flex flex-1 flex-col overflow-hidden p-4">
    <section class="glass-card mb-4 shrink-0 p-5">
      <div class="flex flex-wrap items-center justify-between gap-4">
        <div>
          <h1 class="text-primary text-2xl font-bold">{{ $t('myMusic.title') }}</h1>
          <p class="text-primary/60 mt-2 text-sm">{{ $t('myMusic.subtitle') }}</p>
        </div>
        <Button
          variant="solid"
          size="md"
          rounded="full"
          class="px-6"
          :disabled="!recentList.length"
          @click="playAllRecent"
        >
          <span class="icon-[mdi--play] mr-2 h-4.5 w-4.5"></span>
          {{ $t('actions.playAll') }}
        </Button>
      </div>
      <div class="text-primary/55 mt-4 flex flex-wrap items-center gap-4 text-sm">
        <span class="inline-flex items-center gap-2">
          <span class="icon-[mdi--history] h-4.5 w-4.5"></span>
          {{ $t('commonUnits.songsShort', { count: recentList.length }) }}
        </span>
        <span class="inline-flex items-center gap-2">
          <span class="icon-[mdi--playlist-music] h-4.5 w-4.5"></span>
          {{ $t('myMusic.playlistsCount', { count: state.playlists.length }) }}
        </span>
      </div>
    </section>

    <div class="custom-scrollbar min-h-0 flex-1 overflow-y-auto pr-1">
      <section class="mb-6">
        <div class="mb-4 flex items-center justify-between gap-3">
          <div>
            <h2 class="text-primary text-lg font-semibold">{{ $t('myMusic.playlistsTitle') }}</h2>
            <p class="text-primary/50 mt-1 text-sm">{{ $t('myMusic.playlistsSubtitle') }}</p>
          </div>
          <Button
            v-if="!userStore.isLoggedIn"
            variant="ghost"
            size="sm"
            rounded="full"
            class="px-4"
            @click="openLogin"
          >
            {{ $t('auth.login') }}
          </Button>
        </div>

        <div
          v-if="state.isLoadingPlaylists"
          class="grid grid-cols-2 gap-4 sm:grid-cols-3 xl:grid-cols-4 2xl:grid-cols-5"
        >
          <div
            v-for="idx in 5"
            :key="idx"
            class="aspect-square animate-pulse rounded-2xl border border-white/6 bg-white/5"
          ></div>
        </div>

        <div v-else-if="userStore.isLoggedIn && state.playlists.length" class="grid grid-cols-2 gap-4 sm:grid-cols-3 xl:grid-cols-4 2xl:grid-cols-5">
          <HeroCard
            v-for="playlist in state.playlists"
            :key="playlist.id"
            :id="playlist.id"
            :cover-url="playlist.cover || '/default-cover.svg'"
            :title="playlist.name"
            :track-count="playlist.songCount"
            :to="`/playlist/${playlist.id}`"
            :enable-tilt="false"
          />
        </div>

        <div
          v-else
          class="rounded-2xl border border-white/6 bg-white/4 px-5 py-8 text-center"
        >
          <p class="text-primary/70 text-base font-medium">
            {{
              userStore.isLoggedIn
                ? $t('myMusic.playlistsEmpty')
                : $t('myMusic.playlistsLoginHint')
            }}
          </p>
          <p class="text-primary/45 mt-2 text-sm">{{ $t('myMusic.playlistsHelper') }}</p>
        </div>
      </section>

      <section class="min-h-[420px]">
        <div class="mb-4">
          <h2 class="text-primary text-lg font-semibold">{{ $t('recent.title') }}</h2>
          <p class="text-primary/50 mt-1 text-sm">{{ $t('recent.subtitle') }}</p>
        </div>
        <SongList
          :songs="recentList"
          :show-header="true"
          :show-controls="true"
          :empty-message="$t('recent.empty')"
        />
      </section>
    </div>
  </div>
  <LoginDialog v-if="state.showLogin" @close="state.showLogin = false" @success="loadUserPlaylists" />
</template>
