<script setup lang="ts">
import { createMyPlaylist, myPlaylists, type ClientPlaylist } from '@/api'
import LoginDialog from '@/components/Auth/LoginDialog.vue'
import Button from '@/components/Ui/Button.vue'
import { useUserStore } from '@/stores/modules/user'
import { withImageParam } from '@/utils/media'
import { useI18n } from 'vue-i18n'

const userStore = useUserStore()
const { t } = useI18n()

const state = reactive({
  playlists: [] as ClientPlaylist[],
  isLoadingPlaylists: false,
  showLogin: false,
  showCreateDialog: false,
  createLoading: false,
  createError: '',
  createForm: {
    name: '',
    description: '',
    status: 0,
  },
})

const likedPlaylist = computed(() => state.playlists.find(playlist => playlist.type === 'liked') || null)
const normalPlaylists = computed(() =>
  state.playlists.filter(playlist => playlist.type !== 'liked' && !playlist.subscribed)
)

const createStatusOptions = [
  { value: 0, label: 'playlist.visibility.private' },
  { value: 1, label: 'playlist.visibility.public' },
]

const loadUserPlaylists = async () => {
  if (!userStore.isAuthenticated) {
    if (userStore.isLoggedIn) userStore.logout()
    state.playlists = []
    return
  }

  state.isLoadingPlaylists = true
  try {
    const res = await myPlaylists()
    if (res?.code === 401) {
      userStore.logout()
      state.playlists = []
      return
    }
    state.playlists = Array.isArray(res?.data) ? res.data : []
  } catch {
    state.playlists = []
  } finally {
    state.isLoadingPlaylists = false
  }
}

const openLogin = () => {
  state.showLogin = true
}

const openCreateDialog = () => {
  if (!userStore.isAuthenticated) {
    if (userStore.isLoggedIn) userStore.logout()
    state.showLogin = true
    return
  }
  state.createError = ''
  state.createForm = {
    name: '',
    description: '',
    status: 0,
  }
  state.showCreateDialog = true
}

const submitCreatePlaylist = async () => {
  const name = state.createForm.name.trim()
  if (!name) {
    state.createError = t('playlist.messages.nameRequired')
    return
  }

  state.createLoading = true
  state.createError = ''
  try {
    const res = await createMyPlaylist({
      name,
      description: state.createForm.description.trim(),
      status: state.createForm.status,
    })
    if (res?.code === 401) {
      userStore.logout()
      state.showLogin = true
      throw new Error(t('auth.login'))
    }
    if (res?.code !== 200 || !res.data?.id) {
      throw new Error(res?.message || t('playlist.dialog.createFailed'))
    }
    state.showCreateDialog = false
    window.dispatchEvent(new CustomEvent('sonora:playlists-updated'))
    await loadUserPlaylists()
  } catch (error: any) {
    state.createError =
      error?.response?.data?.message || error?.message || t('playlist.dialog.createFailed')
  } finally {
    state.createLoading = false
  }
}

onMounted(() => {
  loadUserPlaylists()
  window.addEventListener('sonora:playlists-updated', loadUserPlaylists)
})

onUnmounted(() => {
  window.removeEventListener('sonora:playlists-updated', loadUserPlaylists)
})

watch(() => userStore.isAuthenticated, loadUserPlaylists)
</script>

<template>
  <div class="flex flex-1 flex-col overflow-hidden p-4">
    <section class="glass-card mb-4 shrink-0 p-5">
      <div>
        <div>
          <h1 class="text-primary text-2xl font-bold">{{ $t('myMusic.title') }}</h1>
          <p class="text-primary/60 mt-2 text-sm">{{ $t('myMusic.subtitle') }}</p>
        </div>
      </div>
      <div class="text-primary/55 mt-4 flex flex-wrap items-center gap-4 text-sm">
        <span class="inline-flex items-center gap-2">
          <span class="icon-[mdi--playlist-music] h-4.5 w-4.5"></span>
          {{ $t('myMusic.playlistsCount', { count: state.playlists.length }) }}
        </span>
        <span v-if="likedPlaylist" class="inline-flex items-center gap-2">
          <span class="icon-[mdi--heart] h-4.5 w-4.5"></span>
          {{ $t('commonUnits.songsShort', { count: likedPlaylist.songCount || 0 }) }}
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
          <div class="flex items-center gap-2">
            <Button
              v-if="userStore.isAuthenticated"
              variant="soft"
              size="sm"
              rounded="full"
              class="px-4"
              @click="openCreateDialog"
            >
              <span class="icon-[mdi--playlist-plus] mr-1.5 h-4 w-4"></span>
              {{ $t('playlist.actions.create') }}
            </Button>
            <Button
              v-else
              variant="ghost"
              size="sm"
              rounded="full"
              class="px-4"
              @click="openLogin"
            >
              {{ $t('auth.login') }}
            </Button>
          </div>
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

        <div
          v-else-if="userStore.isAuthenticated && state.playlists.length"
          class="grid grid-cols-2 gap-4 sm:grid-cols-3 xl:grid-cols-4 2xl:grid-cols-5"
        >
          <router-link
            v-if="likedPlaylist"
            to="/likes"
            class="group overflow-hidden rounded-2xl"
          >
            <div class="relative aspect-square overflow-hidden rounded-2xl border border-pink-400/20 bg-linear-to-br from-pink-500/20 via-white/5 to-purple-500/20">
              <div class="absolute inset-0 flex items-center justify-center">
                <span class="icon-[mdi--heart] h-20 w-20 text-pink-300/80"></span>
              </div>
              <div class="absolute right-0 bottom-0 left-0 p-4">
                <div class="mb-2 inline-flex rounded-full bg-pink-500/20 px-2.5 py-1 text-[11px] text-pink-200">
                  {{ $t('playlist.likedBadge') }}
                </div>
                <p class="text-primary line-clamp-2 text-sm font-semibold">
                  {{ likedPlaylist.name }}
                </p>
                <p class="text-primary/60 mt-1 text-xs">
                  {{ $t('commonUnits.songsShort', { count: likedPlaylist.songCount || 0 }) }}
                </p>
              </div>
            </div>
          </router-link>

          <router-link
            v-for="playlist in normalPlaylists"
            :key="playlist.id"
            :to="`/playlist/${playlist.id}`"
            class="group overflow-hidden rounded-2xl"
          >
            <div class="relative aspect-square overflow-hidden rounded-2xl border border-white/6 bg-white/4">
              <img
                :src="withImageParam(playlist.cover || '/default-cover.svg', '300y300')"
                :alt="playlist.name"
                class="h-full w-full object-cover transition-transform duration-300 group-hover:scale-105"
              />
              <div class="absolute inset-0 bg-linear-to-t from-black/75 via-black/10 to-transparent"></div>
              <div class="absolute top-3 left-3 flex items-center gap-2">
                <span
                  class="rounded-full px-2.5 py-1 text-[11px]"
                  :class="
                    Number(playlist.status || 0) === 1
                      ? 'bg-emerald-500/20 text-emerald-200'
                      : 'bg-black/35 text-white/85'
                  "
                >
                  {{
                    Number(playlist.status || 0) === 1
                      ? $t('playlist.visibility.public')
                      : $t('playlist.visibility.private')
                  }}
                </span>
                <span
                  v-if="Number(playlist.pinned || 0) === 1"
                  class="rounded-full bg-pink-500/20 px-2 py-1 text-[11px] text-pink-200"
                >
                  {{ $t('playlist.actions.pinned') }}
                </span>
              </div>
              <div class="absolute right-0 bottom-0 left-0 p-4">
                <p class="line-clamp-2 text-sm font-semibold text-white">{{ playlist.name }}</p>
                <p class="mt-1 text-xs text-white/70">
                  {{ $t('commonUnits.songsShort', { count: playlist.songCount || 0 }) }}
                </p>
                <p v-if="playlist.description" class="mt-1.5 line-clamp-2 text-[11px] text-white/60">
                  {{ playlist.description }}
                </p>
              </div>
            </div>
          </router-link>
        </div>

        <div
          v-else
          class="rounded-2xl border border-white/6 bg-white/4 px-5 py-8 text-center"
        >
          <p class="text-primary/70 text-base font-medium">
            {{
                userStore.isAuthenticated
                ? $t('myMusic.playlistsEmpty')
                : $t('myMusic.playlistsLoginHint')
            }}
          </p>
          <p class="text-primary/45 mt-2 text-sm">{{ $t('myMusic.playlistsHelper') }}</p>
        </div>
      </section>

    </div>
  </div>

  <LoginDialog v-if="state.showLogin" @close="state.showLogin = false" @success="loadUserPlaylists" />

  <div
    v-if="state.showCreateDialog"
    class="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4 backdrop-blur-sm"
    @click.self="state.showCreateDialog = false"
  >
    <div class="glass-container-strong w-full max-w-md p-6">
      <div class="mb-5 flex items-center justify-between">
        <h3 class="text-primary text-xl font-semibold">{{ $t('playlist.dialog.createTitle') }}</h3>
        <Button
          variant="soft"
          size="icon-sm"
          rounded="full"
          icon="icon-[mdi--close]"
          icon-class="h-4 w-4"
          :disabled="state.createLoading"
          @click="state.showCreateDialog = false"
        />
      </div>

      <div class="space-y-4">
        <div>
          <label class="text-primary/60 mb-2 block text-xs font-medium">
            {{ $t('playlist.edit.name') }}
          </label>
          <input
            v-model="state.createForm.name"
            type="text"
            maxlength="80"
            class="text-primary glass-card w-full rounded-xl border border-glass px-4 py-3 text-sm outline-none placeholder:text-white/30 focus:border-pink-400/50"
            :placeholder="$t('playlist.edit.namePlaceholder')"
          />
        </div>

        <div>
          <label class="text-primary/60 mb-2 block text-xs font-medium">
            {{ $t('playlist.edit.description') }}
          </label>
          <textarea
            v-model="state.createForm.description"
            rows="4"
            maxlength="2000"
            class="text-primary glass-card w-full resize-none rounded-xl border border-glass px-4 py-3 text-sm outline-none placeholder:text-white/30 focus:border-pink-400/50"
            :placeholder="$t('playlist.edit.descriptionPlaceholder')"
          />
        </div>

        <div>
          <label class="text-primary/60 mb-2 block text-xs font-medium">
            {{ $t('playlist.edit.visibility') }}
          </label>
          <div class="grid grid-cols-2 gap-2">
            <button
              v-for="option in createStatusOptions"
              :key="option.value"
              type="button"
              class="rounded-xl border px-4 py-3 text-sm transition-colors"
              :class="
                state.createForm.status === option.value
                  ? 'border-sky-400/60 bg-sky-500/20 text-sky-200'
                  : 'border-glass bg-white/5 text-primary/70 hover:bg-white/10'
              "
              @click="state.createForm.status = option.value"
            >
              {{ $t(option.label) }}
            </button>
          </div>
        </div>
      </div>

      <p v-if="state.createError" class="mt-4 text-sm text-red-300">{{ state.createError }}</p>

      <div class="mt-6 flex justify-end gap-3">
        <Button
          variant="ghost"
          size="md"
          rounded="full"
          :disabled="state.createLoading"
          @click="state.showCreateDialog = false"
        >
          {{ $t('playlist.edit.cancel') }}
        </Button>
        <Button
          variant="solid"
          size="md"
          rounded="full"
          :loading="state.createLoading"
          :disabled="state.createLoading"
          icon="icon-[mdi--playlist-plus]"
          @click="submitCreatePlaylist"
        >
          {{ $t('playlist.actions.create') }}
        </Button>
      </div>
    </div>
  </div>
</template>
