<script setup lang="ts">
import { clientArtistDetail } from '@/api'
import { usePlayActions } from '@/composables/usePlayActions'
import TabGroup from '@/components/Ui/TabGroup.vue'
import Button from '@/components/Ui/Button.vue'
import { withImageParam } from '@/utils/media'
import {
  transformSongs,
  transformAlbums,
  type SongData,
  type AlbumData,
} from '@/utils/transformers'

const route = useRoute()
const router = useRouter()
const artistId = computed(() => Number(route.params.id))

type ArtistInfo = {
  id: number
  name: string
  picUrl: string
  description: string
  region: string
  albumSize: number
  musicSize: number
  followed: boolean
}

const state = reactive({
  info: {} as ArtistInfo,
  songs: [] as SongData[],
  albums: [] as AlbumData[],
  loading: true,
  playingAlbumId: null as number | string | null,
  followed: false,
  activeTab: 'songs' as 'songs' | 'albums',
})

const { activeTab } = toRefs(state)

const { playAll: playAllAction, shufflePlay: shufflePlayAction, playAlbum: playAlbumAction } =
  usePlayActions()

const load = async (id: number) => {
  state.loading = true
  try {
    const res = await clientArtistDetail(id)
    const data = (res?.data || {}) as Record<string, unknown>

    state.info = {
      id,
      name: String(data.name || ''),
      picUrl: String(data.avatar || ''),
      description: String(data.description || ''),
      region: String(data.region || ''),
      albumSize: Number(data.albumCount || 0),
      musicSize: Number(data.songCount || 0),
      followed: false,
    }
    state.followed = state.info.followed
    state.songs = transformSongs({ songs: (data.songs as Record<string, unknown>[]) || [] })
    state.albums = transformAlbums({ albums: (data.albums as Record<string, unknown>[]) || [] })
  } finally {
    state.loading = false
  }
}

watch(
  artistId,
  id => {
    if (!Number.isNaN(id) && id > 0) {
      load(id)
    }
  },
  { immediate: true }
)

const playAll = () => playAllAction(state.songs)

const shufflePlay = () => shufflePlayAction(state.songs)

const playAlbum = async (albumId: number | string) => {
  if (!albumId || state.playingAlbumId === albumId) return
  state.playingAlbumId = albumId
  try {
    await playAlbumAction(albumId)
  } finally {
    state.playingAlbumId = null
  }
}

const toggleFollow = () => {
  state.followed = !state.followed
}

const tabs = computed(() => [
  {
    key: 'songs',
    labelKey: 'artistPage.tabs.hotSongs',
    icon: 'icon-[mdi--music-note]',
    count: state.songs.length,
  },
  {
    key: 'albums',
    labelKey: 'artistPage.tabs.albums',
    icon: 'icon-[mdi--album]',
    count: state.albums.length,
  },
])
</script>

<template>
  <div class="text-primary flex-1 overflow-hidden px-4">
    <div class="h-full overflow-auto">
      <PageSkeleton v-if="state.loading" :sections="['hero', 'list']" :list-count="12" />
      <template v-else>
        <section class="relative mb-8 flex shrink-0">
          <div class="absolute inset-0 overflow-hidden">
            <img
              v-if="state.info.picUrl"
              :src="withImageParam(state.info.picUrl, '800y800')"
              class="h-full w-full scale-110 object-cover opacity-20 blur-2xl"
            />
            <div
              v-else
              class="h-full w-full bg-linear-to-br from-pink-500/30 to-purple-600/30"
            ></div>
          </div>

          <div class="absolute inset-0">
            <div class="floating-notes">
              <div v-for="i in 6" :key="i" class="note" :style="{ animationDelay: i * 1.2 + 's' }">
                {{ ['🎵', '🎶', '♪', '♫', '🎼', '🎤'][i - 1] }}
              </div>
            </div>
          </div>

          <div class="relative z-10 w-full p-8">
            <div class="flex flex-col items-start gap-8 lg:flex-row lg:items-center">
              <div class="shrink-0">
                <div class="group relative">
                  <div
                    class="h-48 w-48 overflow-hidden rounded-full ring-4 ring-white/20 transition-all group-hover:ring-pink-500/40"
                  >
                    <img
                      v-if="state.info.picUrl"
                      :src="withImageParam(state.info.picUrl, '400y400')"
                      class="h-full w-full object-cover transition-transform duration-500 group-hover:scale-110"
                      :alt="$t('layout.aside.menu.artists')"
                    />
                    <div
                      v-else
                      class="flex h-full w-full items-center justify-center bg-linear-to-br from-pink-400 to-purple-500"
                    >
                      <span class="icon-[mdi--account-music] h-20 w-20"></span>
                    </div>
                  </div>
                  <div
                    class="absolute inset-0 flex items-center justify-center rounded-full bg-black/0 opacity-0 transition-all group-hover:bg-black/30 group-hover:opacity-100"
                  >
                    <Button
                      variant="ghost"
                      size="icon-md"
                      rounded="full"
                      icon="icon-[mdi--play]"
                      icon-class="h-8 w-8 text-white"
                      @click="playAll"
                      class="flex h-16 w-16 items-center justify-center rounded-full bg-white/20 backdrop-blur-sm transition-transform hover:scale-110"
                    >
                    </Button>
                  </div>
                </div>
              </div>

              <div class="min-w-0 flex-1">
                <h1 class="animate-fade-in-up mb-2 text-4xl font-bold lg:text-5xl">
                  {{ state.info.name }}
                </h1>

                <div
                  class="animate-fade-in-up flex flex-wrap items-center gap-3"
                  style="animation-delay: 0.1s"
                >
                  <Button
                    variant="solid"
                    size="md"
                    rounded="full"
                    class="gap-2 px-6 shadow-lg shadow-pink-500/25 hover:shadow-xl hover:shadow-pink-500/30"
                    @click="playAll"
                  >
                    <span class="icon-[mdi--play] h-5 w-5"></span>
                    {{ $t('actions.playAll') }}
                  </Button>
                  <Button
                    variant="soft"
                    size="md"
                    rounded="full"
                    class="gap-2"
                    @click="shufflePlay"
                  >
                    <span class="icon-[mdi--shuffle] h-4 w-4"></span>
                    {{ $t('actions.shufflePlay') }}
                  </Button>
                  <Button
                    variant="soft"
                    size="md"
                    rounded="full"
                    class="gap-2"
                    :class="state.followed ? 'bg-pink-500/20! text-pink-400!' : ''"
                    @click="toggleFollow"
                  >
                    <span
                      :class="state.followed ? 'icon-[mdi--heart]' : 'icon-[mdi--heart-outline]'"
                      class="h-4 w-4"
                    ></span>
                    {{ state.followed ? $t('common.followed') : $t('common.follow') }}
                  </Button>
                </div>

                <div
                  class="animate-fade-in-up mt-4 flex flex-wrap items-center gap-2.5"
                  style="animation-delay: 0.18s"
                >
                  <span v-if="state.info.region" class="artist-stat-chip">
                    <span class="icon-[mdi--map-marker-radius-outline] h-4 w-4"></span>
                    {{ state.info.region }}
                  </span>
                  <span class="artist-stat-chip">
                    <span class="icon-[mdi--music-note-outline] h-4 w-4"></span>
                    {{ state.info.musicSize }} {{ $t('artistPage.stats.songs') }}
                  </span>
                  <span class="artist-stat-chip">
                    <span class="icon-[mdi--album] h-4 w-4"></span>
                    {{ state.info.albumSize }} {{ $t('artistPage.tabs.albums') }}
                  </span>
                </div>
              </div>
            </div>
          </div>
        </section>

        <div class="pb-8">
          <div v-if="state.info.description" class="glass-card mb-6 p-5">
            <h3 class="mb-2 flex items-center gap-2 text-sm font-semibold">
              <span class="icon-[mdi--information-outline] h-4 w-4 text-pink-400"></span>
              {{ $t('artistPage.bioTitle') }}
            </h3>
            <p class="text-primary/70 text-sm leading-relaxed">
              {{ state.info.description }}
            </p>
          </div>

          <div class="mb-6">
            <TabGroup
              v-model="state.activeTab"
              :tabs="tabs"
              size="md"
              @click="val => (activeTab = val as 'songs' | 'albums')"
            />
          </div>

          <div v-show="state.activeTab === 'songs'" class="animate-fade-in">
            <SongList :songs="state.songs" :current-playing-index="-1" :show-header="true" />
          </div>

          <div v-show="state.activeTab === 'albums'" class="animate-fade-in">
            <div class="grid grid-cols-2 gap-5 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-6">
              <div
                v-for="al in state.albums"
                :key="al.id"
                class="glass-card group cursor-pointer overflow-hidden p-3 transition-all hover:bg-white/10"
                @click="router.push(`/album/${al.id}`)"
              >
                <div class="relative mb-3 aspect-square w-full overflow-hidden rounded-xl">
                  <img
                    :src="withImageParam(al.picUrl, '400y400')"
                    class="h-full w-full object-cover transition-transform duration-500 group-hover:scale-110"
                  />
                  <div
                    class="absolute inset-0 flex items-center justify-center bg-black/0 opacity-0 transition-all group-hover:bg-black/40 group-hover:opacity-100"
                  >
                    <button
                      type="button"
                      class="album-play-button"
                      @click.stop="playAlbum(al.id)"
                    >
                      <span
                        :class="
                          state.playingAlbumId === al.id
                            ? 'icon-[mdi--loading] animate-spin'
                            : 'icon-[mdi--play]'
                        "
                        class="h-6 w-6 text-white"
                      ></span>
                    </button>
                  </div>
                </div>
                <p class="truncate text-sm font-medium">{{ al.name }}</p>
                <p class="text-primary/50 mt-1 truncate text-xs">
                  {{ al.publishTime }} · {{ $t('commonUnits.songsShort', al.size) }}
                </p>
              </div>
            </div>
          </div>
        </div>
      </template>
    </div>
  </div>
</template>

<style scoped>
@keyframes fadeInUp {
  from {
    opacity: 0;
    transform: translateY(20px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@keyframes noteFloat {
  0% {
    transform: translateY(100%) rotate(0deg);
    opacity: 0;
  }
  10% {
    opacity: 0.6;
  }
  90% {
    opacity: 0.6;
  }
  100% {
    transform: translateY(-100px) rotate(360deg);
    opacity: 0;
  }
}

.animate-fade-in-up {
  animation: fadeInUp 0.6s ease-out forwards;
}

.animate-fade-in {
  animation: fadeInUp 0.3s ease-out;
}

.artist-stat-chip {
  display: inline-flex;
  align-items: center;
  gap: 0.45rem;
  border-radius: 9999px;
  border: 1px solid rgba(255, 255, 255, 0.12);
  background: rgba(255, 255, 255, 0.08);
  padding: 0.55rem 0.85rem;
  font-size: 0.875rem;
  color: rgba(255, 255, 255, 0.82);
  backdrop-filter: blur(12px);
}

.album-play-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 3.25rem;
  height: 3.25rem;
  border: 0;
  border-radius: 9999px;
  background: rgba(15, 23, 42, 0.55);
  box-shadow: 0 12px 28px rgba(15, 23, 42, 0.35);
  backdrop-filter: blur(12px);
  transition:
    transform 0.2s ease,
    background 0.2s ease;
}

.album-play-button:hover {
  transform: scale(1.06);
  background: rgba(37, 99, 235, 0.78);
}

.floating-notes {
  position: absolute;
  inset: 0;
  overflow: hidden;
  pointer-events: none;
}

.note {
  position: absolute;
  font-size: 1.25rem;
  color: rgba(255, 255, 255, 0.15);
  animation: noteFloat 15s linear infinite;
}

.note:nth-child(1) {
  left: 5%;
  animation-duration: 14s;
}
.note:nth-child(2) {
  left: 20%;
  animation-duration: 16s;
}
.note:nth-child(3) {
  left: 40%;
  animation-duration: 12s;
}
.note:nth-child(4) {
  left: 60%;
  animation-duration: 18s;
}
.note:nth-child(5) {
  left: 75%;
  animation-duration: 13s;
}
.note:nth-child(6) {
  left: 90%;
  animation-duration: 15s;
}
</style>
