<script setup lang="ts">
import { createMyPlaylist, myPlaylists, type ClientPlaylist } from '@/api'
import Button from '@/components/Ui/Button.vue'
import { useUserStore } from '@/stores/modules/user'
import { resolveMediaUrl, withImageParam } from '@/utils/media'
import { useI18n } from 'vue-i18n'

type SidebarPlaylist = ClientPlaylist & {
  creatorId?: number
  subscribed?: boolean
}

const route = useRoute()
const router = useRouter()
const { t } = useI18n()

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
      { to: '/likes', labelKey: 'layout.aside.menu.likesMusic', icon: 'mdi--heart' },
      { to: '/recent', labelKey: 'layout.aside.menu.recent', icon: 'mdi--history' },
    ],
  },
  {
    titleKey: 'layout.aside.system',
    items: [{ to: '/settings', labelKey: 'layout.aside.menu.settings', icon: 'mdi--cog' }],
  },
]

const state = reactive({
  userPlaylists: [] as SidebarPlaylist[],
  createdExpanded: true,
  collectedExpanded: false,
  createForm: {
    name: '',
    description: '',
    status: 0,
  },
})
const userStore = useUserStore()
const showCreatePlaylist = ref(false)
const createPlaylistError = ref('')
const creatingPlaylist = ref(false)

const createStatusOptions = [
  { value: 0, label: 'playlist.visibility.private' },
  { value: 1, label: 'playlist.visibility.public' },
]

const isCollectedPlaylist = (playlist: SidebarPlaylist) => {
  if (typeof playlist.subscribed === 'boolean') {
    return playlist.subscribed
  }
  if (playlist.creatorId && userStore.userId) {
    return Number(playlist.creatorId) !== Number(userStore.userId)
  }
  return false
}

const createdPlaylists = computed(() =>
  state.userPlaylists.filter(
    playlist => playlist.type !== 'liked' && !isCollectedPlaylist(playlist)
  )
)

const collectedPlaylists = computed(() =>
  state.userPlaylists.filter(
    playlist => playlist.type !== 'liked' && isCollectedPlaylist(playlist)
  )
)

const loadUserPlaylists = async () => {
  if (!userStore.isAuthenticated) {
    if (userStore.isLoggedIn) userStore.logout()
    state.userPlaylists = []
    return
  }
  try {
    const res = await myPlaylists()
    if (res?.code === 401) {
      userStore.logout()
      state.userPlaylists = []
      return
    }
    state.userPlaylists = (Array.isArray(res?.data) ? res.data : []).map((item: any) => ({
      id: Number(item.id),
      name: String(item.name || t('layout.aside.playlistDialog.unnamed')),
      cover: resolveMediaUrl(item.cover || '/default-cover.svg'),
      type: item.type,
      pinned: item.pinned,
      subscribed:
        typeof item.subscribed === 'boolean'
          ? item.subscribed
          : typeof item.subscribed === 'number'
            ? Boolean(item.subscribed)
            : undefined,
      creatorId: Number(item?.creator?.userId || item?.creatorId || 0) || undefined,
    }))
  } catch {
    state.userPlaylists = []
  }
}

const openCreatePlaylist = () => {
  if (!userStore.isAuthenticated) {
    if (userStore.isLoggedIn) userStore.logout()
    return
  }
  createPlaylistError.value = ''
  state.createForm = {
    name: '',
    description: '',
    status: 0,
  }
  showCreatePlaylist.value = true
}

const closeCreatePlaylist = () => {
  if (creatingPlaylist.value) return
  showCreatePlaylist.value = false
}

const submitCreatePlaylist = async () => {
  const name = state.createForm.name.trim()
  if (!name) {
    createPlaylistError.value = t('layout.aside.playlistDialog.required')
    return
  }
  creatingPlaylist.value = true
  createPlaylistError.value = ''
  try {
    const res = await createMyPlaylist({
      name,
      description: state.createForm.description.trim(),
      status: state.createForm.status,
    })
    if (res?.code === 401) {
      userStore.logout()
      throw new Error(t('auth.login'))
    }
    if (res?.code !== 200 || !res.data?.id) {
      throw new Error(res?.message || t('layout.aside.playlistDialog.failed'))
    }
    await loadUserPlaylists()
    window.dispatchEvent(new CustomEvent('sonora:playlists-updated'))
    showCreatePlaylist.value = false
    router.push(`/playlist/${res.data.id}`)
  } catch (error: any) {
    createPlaylistError.value =
      error?.response?.data?.message || error?.message || t('layout.aside.playlistDialog.failed')
  } finally {
    creatingPlaylist.value = false
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
      <div>
        <div v-for="sec in sections.slice(0, 2)" :key="sec.titleKey" class="mb-6">
          <h3 class="text-primary mb-3 text-xs font-semibold tracking-wide uppercase">
            {{ $t(sec.titleKey) }}
          </h3>
          <nav class="relative space-y-1">
            <router-link
              v-for="item in sec.items"
              :key="item.to"
              :to="item.to"
              class="nav-link sonora-row text-primary/70 hover:text-primary relative z-10 flex items-center space-x-3 rounded-xl p-2 transition-all duration-200"
              :class="{
                'nav-link-active sonora-row-active text-primary font-medium': isActive(item.to),
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

        <div class="mb-6" v-if="userStore.isAuthenticated">
          <div class="mb-3 flex items-center justify-between">
            <button
              type="button"
              class="text-primary/60 hover:text-primary/80 flex items-center gap-1.5 text-sm font-medium transition-colors"
              @click="state.createdExpanded = !state.createdExpanded"
            >
              <span>{{ $t('layout.aside.playlists.created') }}</span>
              <span>{{ createdPlaylists.length }}</span>
              <span
                class="icon-[mdi--chevron-down] h-4 w-4 transition-transform duration-200"
                :class="{ 'rotate-180': state.createdExpanded }"
              ></span>
            </button>
            <Button
              variant="ghost"
              size="icon-sm"
              rounded="full"
              icon="icon-[mdi--plus]"
              icon-class="h-4 w-4"
              :title="t('layout.aside.playlistDialog.create')"
              @click="openCreatePlaylist"
            />
          </div>
          <div v-show="state.createdExpanded" class="space-y-2">
            <router-link
              v-for="playlist in createdPlaylists"
              :key="playlist.id"
              :to="`/playlist/${playlist.id}`"
              class="group sonora-row flex cursor-pointer items-center space-x-3 rounded-xl p-2 transition-all duration-200 hover:bg-hover-glass"
              :class="{ 'nav-link-active sonora-row-active': isActive(`/playlist/${playlist.id}`) }"
            >
              <div
                class="flex h-8 w-8 items-center justify-center overflow-hidden rounded-lg border border-glass bg-button-glass text-xs text-primary transition-transform duration-200 group-hover:scale-105"
              >
                <img
                  v-if="playlist.cover"
                  :src="withImageParam(playlist.cover, '120y120')"
                  alt="cover"
                  class="h-full w-full object-cover"
                />
                <span v-else>{{ playlist.name.charAt(0) }}</span>
              </div>
              <span class="text-primary/80 group-hover:text-primary truncate text-sm transition-colors">
                {{ playlist.name }}
              </span>
            </router-link>
          </div>
        </div>

        <div class="mb-6" v-if="userStore.isAuthenticated">
          <div class="mb-3 flex items-center justify-between">
            <button
              type="button"
              class="text-primary/60 hover:text-primary/80 flex items-center gap-1.5 text-sm font-medium transition-colors"
              @click="state.collectedExpanded = !state.collectedExpanded"
            >
              <span>{{ $t('layout.aside.playlists.collected') }}</span>
              <span>{{ collectedPlaylists.length }}</span>
              <span
                class="icon-[mdi--chevron-down] h-4 w-4 transition-transform duration-200"
                :class="{ 'rotate-180': state.collectedExpanded }"
              ></span>
            </button>
          </div>
          <div v-show="state.collectedExpanded" class="space-y-2">
            <router-link
              v-for="playlist in collectedPlaylists"
              :key="playlist.id"
              :to="`/playlist/${playlist.id}`"
              class="group sonora-row flex cursor-pointer items-center space-x-3 rounded-xl p-2 transition-all duration-200 hover:bg-hover-glass"
              :class="{ 'nav-link-active sonora-row-active': isActive(`/playlist/${playlist.id}`) }"
            >
              <div
                class="flex h-8 w-8 items-center justify-center overflow-hidden rounded-lg border border-glass bg-button-glass text-xs text-primary transition-transform duration-200 group-hover:scale-105"
              >
                <img
                  v-if="playlist.cover"
                  :src="withImageParam(playlist.cover, '120y120')"
                  alt="cover"
                  class="h-full w-full object-cover"
                />
                <span v-else>{{ playlist.name.charAt(0) }}</span>
              </div>
              <span class="text-primary/80 group-hover:text-primary truncate text-sm transition-colors">
                {{ playlist.name }}
              </span>
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
              class="nav-link sonora-row text-primary/70 hover:text-primary relative z-10 flex items-center space-x-3 rounded-xl p-2 transition-all duration-200"
              :class="{
                'nav-link-active sonora-row-active text-primary font-medium': isActive(item.to),
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
        <span class="icon-[mdi--heart] h-5 w-5"></span>
        <span class="icon-[mdi--cog] h-5 w-5"></span>
        <span class="icon-[mdi--chevron-right] h-5 w-5"></span>
        <span class="icon-[mdi--chevron-down] h-5 w-5"></span>
        <span class="icon-[mdi--account-music] h-5 w-5"></span>
        <span class="icon-[mdi--album] h-5 w-5"></span>
        <span class="icon-[mdi--music-box-multiple-outline] h-5 w-5"></span>
        <span class="icon-[mdi--magnify] h-5 w-5"></span>
        <span class="icon-[mdi--plus] h-5 w-5"></span>
        <span class="icon-[mdi--playlist-music] h-5 w-5"></span>
        <span class="icon-[mdi--history] h-5 w-5"></span>
      </div>
    </div>

    <div
      v-if="showCreatePlaylist"
      class="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4 backdrop-blur-sm"
      @click.self="closeCreatePlaylist"
    >
      <div class="glass-container-strong w-full max-w-md p-6">
        <div class="mb-4 flex items-center justify-between">
          <h3 class="text-primary text-lg font-semibold">
            {{ t('playlist.dialog.createTitle') }}
          </h3>
          <Button
            variant="soft"
            size="icon-sm"
            rounded="full"
            icon="icon-[mdi--close]"
            icon-class="h-4 w-4"
            :disabled="creatingPlaylist"
            @click="closeCreatePlaylist"
          />
        </div>

        <div class="space-y-4">
          <div>
            <label class="text-primary/60 mb-2 block text-xs font-medium">
              {{ t('playlist.edit.name') }}
            </label>
            <input
              v-model="state.createForm.name"
              type="text"
              maxlength="80"
              :placeholder="t('playlist.edit.namePlaceholder')"
              class="text-primary glass-card w-full rounded-xl border border-glass px-4 py-3 text-sm outline-none placeholder:text-primary/30 focus:border-sky-400/50"
              @keyup.enter="submitCreatePlaylist"
            />
          </div>

          <div>
            <label class="text-primary/60 mb-2 block text-xs font-medium">
              {{ t('playlist.edit.description') }}
            </label>
            <textarea
              v-model="state.createForm.description"
              rows="4"
              maxlength="2000"
              :placeholder="t('playlist.edit.descriptionPlaceholder')"
              class="text-primary glass-card w-full resize-none rounded-xl border border-glass px-4 py-3 text-sm outline-none placeholder:text-primary/30 focus:border-sky-400/50"
            />
          </div>

          <div>
            <label class="text-primary/60 mb-2 block text-xs font-medium">
              {{ t('playlist.edit.visibility') }}
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
                {{ t(option.label) }}
              </button>
            </div>
          </div>
        </div>

        <p v-if="createPlaylistError" class="mt-4 text-sm text-red-300">{{ createPlaylistError }}</p>

        <div class="mt-5 flex justify-end gap-3">
          <Button
            variant="ghost"
            size="md"
            rounded="full"
            :disabled="creatingPlaylist"
            @click="closeCreatePlaylist"
          >
            {{ t('playlist.edit.cancel') }}
          </Button>
          <Button
            variant="solid"
            size="md"
            rounded="full"
            :loading="creatingPlaylist"
            :disabled="creatingPlaylist"
            icon="icon-[mdi--playlist-plus]"
            @click="submitCreatePlaylist"
          >
            {{ t('playlist.actions.create') }}
          </Button>
        </div>
      </div>
    </div>
  </aside>
</template>

<style scoped>
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
  background: transparent;
}

.nav-link-active::before {
  background: transparent;
}

aside .glass-card {
  background:
    linear-gradient(180deg, var(--glass-bg-wash), transparent 34%),
    var(--glass-bg-card);
}
</style>
