<script setup lang="ts">
import { clientArtistDetail } from '@/api'
import { usePlayActions } from '@/composables/usePlayActions'
import Button from '@/components/Ui/Button.vue'
import { useI18n } from 'vue-i18n'
import { withImageParam } from '@/utils/media'
import {
  transformSongs,
  transformAlbums,
  type SongData,
  type AlbumData,
} from '@/utils/transformers'

const { t } = useI18n()
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
  activeTab: 0,
  followed: false,
})

const { playAll: playAllAction, shufflePlay: shufflePlayAction } = usePlayActions()

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

const toggleFollow = () => {
  state.followed = !state.followed
}

const goToAlbum = (id: number | string) => {
  router.push(`/album/${id}`)
}

const tabs = ['artistPage.tabs.hotSongs', 'artistPage.tabs.albums']
</script>

<template>
  <div class="artist-page flex flex-1 flex-col overflow-hidden">
    <div v-if="state.loading" class="flex-1 overflow-auto px-4 py-6">
      <PageSkeleton :sections="['hero', 'list']" :list-count="8" />
    </div>
    <template v-else>
      <div class="header-section relative">
        <div class="header-bg absolute inset-0 overflow-hidden">
          <LazyImage
            v-if="state.info.picUrl"
            :src="withImageParam(state.info.picUrl, '400y400')"
            :alt="$t('components.songList.coverAlt')"
            imgClass="h-full w-full object-cover scale-110"
          />
          <div class="header-overlay absolute inset-0"></div>
        </div>

        <div class="header-content relative z-10 px-4 pt-6 pb-6">
          <div class="flex flex-col items-center">
            <div class="avatar-wrapper relative mb-4">
              <LazyImage
                v-if="state.info.picUrl"
                :src="withImageParam(state.info.picUrl, '300y300')"
                :alt="$t('layout.aside.menu.artists')"
                imgClass="artist-avatar h-28 w-28 rounded-full object-cover"
              />
            </div>

            <h1 class="mb-1 text-xl font-bold text-accent">{{ state.info.name }}</h1>

            <div class="mb-4 flex flex-wrap items-center justify-center gap-2">
              <span v-if="state.info.region" class="artist-meta-pill">
                <span class="icon-[mdi--map-marker-radius-outline] h-3.5 w-3.5"></span>
                {{ state.info.region }}
              </span>
              <span class="artist-meta-pill">
                <span class="icon-[mdi--music-note-outline] h-3.5 w-3.5"></span>
                {{ state.info.musicSize }}
              </span>
              <span class="artist-meta-pill">
                <span class="icon-[mdi--album] h-3.5 w-3.5"></span>
                {{ state.info.albumSize }}
              </span>
            </div>

            <p
              v-if="state.info.description"
              class="mb-4 max-w-xs text-center text-xs leading-relaxed text-accent/60"
            >
              {{ state.info.description }}
            </p>
          </div>
        </div>
      </div>

      <div class="action-bar flex items-center gap-3 px-4 py-3">
        <Button
          variant="gradient"
          size="md"
          rounded="full"
          class="play-all-btn flex flex-1 items-center justify-center gap-2 py-2.5 text-sm font-medium"
          icon="icon-[mdi--play-circle]"
          icon-class="h-5 w-5"
          @click="playAll"
        >
          {{ t('actions.playAll') }}
        </Button>
        <Button
          variant="glass"
          size="md"
          rounded="full"
          class="shuffle-btn flex flex-1 items-center justify-center gap-2 py-2.5 text-sm font-medium"
          icon="icon-[mdi--shuffle-variant]"
          icon-class="h-5 w-5"
          @click="shufflePlay"
        >
          {{ t('actions.shufflePlay') }}
        </Button>
        <Button
          variant="glass"
          size="icon-lg"
          rounded="full"
          class="follow-btn"
          :class="state.followed ? 'followed' : ''"
          :icon="state.followed ? 'icon-[mdi--account-check]' : 'icon-[mdi--account-plus]'"
          icon-class="h-5 w-5"
          @click="toggleFollow"
        />
      </div>

      <div class="tabs-bar flex gap-1 px-4 pb-2">
        <Button
          v-for="(tab, i) in tabs"
          :key="i"
          variant="ghost"
          size="none"
          rounded="full"
          class="tab-btn px-4 py-1.5 text-xs font-medium transition-all"
          :class="state.activeTab === i ? 'active' : ''"
          @click="state.activeTab = i"
        >
          {{ $t(tab) }}
        </Button>
      </div>

      <div class="flex-1 overflow-auto px-4 pb-6">
        <section v-if="state.activeTab === 0">
          <MobileSongList :songs="state.songs" :show-index="true" />
        </section>

        <section v-else-if="state.activeTab === 1" class="albums-section">
          <div class="grid grid-cols-2 gap-3">
            <div
              v-for="album in state.albums"
              :key="album.id"
              class="album-card overflow-hidden rounded-2xl"
              @click="goToAlbum(album.id)"
            >
              <div class="relative aspect-square">
                <LazyImage
                  :src="withImageParam(album.picUrl, '200y200')"
                  :alt="album.name"
                  imgClass="h-full w-full object-cover"
                />
                <div class="absolute inset-0 bg-linear-to-t from-black/60 to-transparent"></div>
                <div class="absolute right-2 bottom-2 left-2">
                  <p class="text-primary truncate text-xs font-medium">{{ album.name }}</p>
                  <p class="text-primary/60 text-[10px]">
                    {{ album.publishTime }} · {{ $t('commonUnits.songsShort', album.size) }}
                  </p>
                </div>
              </div>
            </div>
          </div>
          <div v-if="!state.albums.length" class="empty-text py-12 text-center">{{ $t('artistPage.albumsEmpty') }}</div>
        </section>
      </div>
    </template>
  </div>
</template>

<style scoped>
.header-bg::after {
  content: '';
  position: absolute;
  inset: 0;
  backdrop-filter: blur(40px) saturate(1.5);
  -webkit-backdrop-filter: blur(40px) saturate(1.5);
}

.header-overlay {
  background: linear-gradient(
    to bottom,
    rgba(0, 0, 0, 0.3) 0%,
    rgba(0, 0, 0, 0.5) 50%,
    var(--glass-bg-base) 100%
  );
}

.avatar-wrapper {
  filter: drop-shadow(0 8px 24px rgba(0, 0, 0, 0.4));
}

.artist-avatar {
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.3);
  border: 3px solid rgba(255, 255, 255, 0.2);
}

.artist-meta-pill {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  border-radius: 9999px;
  border: 1px solid rgba(255, 255, 255, 0.14);
  background: rgba(255, 255, 255, 0.08);
  padding: 0.4rem 0.65rem;
  font-size: 0.6875rem;
  color: rgba(255, 255, 255, 0.8);
  backdrop-filter: blur(10px);
}

.play-all-btn {
  background: linear-gradient(135deg, #1f7cff, #4da3ff);
  box-shadow: 0 4px 16px rgba(31, 124, 255, 0.35);
  transition: all 0.3s ease;
}

.play-all-btn:active {
  transform: scale(0.97);
  box-shadow: 0 2px 8px rgba(31, 124, 255, 0.3);
}

.shuffle-btn {
  background: var(--glass-bg-card);
  color: var(--glass-text-primary);
  border: 1px solid var(--glass-border-default);
  transition: all 0.3s ease;
}

.shuffle-btn:active {
  transform: scale(0.97);
  background: var(--glass-interactive-hover-muted);
}

.follow-btn {
  background: var(--glass-bg-card);
  color: var(--glass-text-primary);
  border: 1px solid var(--glass-border-default);
  transition: all 0.3s ease;
}

.follow-btn:active {
  transform: scale(0.95);
}

.follow-btn.followed {
  background: linear-gradient(135deg, rgba(31, 124, 255, 0.2), rgba(77, 163, 255, 0.2));
  border-color: rgba(31, 124, 255, 0.3);
  color: #1f7cff;
}

.tabs-bar {
  border-bottom: 1px solid var(--glass-border-default);
}

.tab-btn {
  color: var(--glass-text-primary);
  opacity: 0.6;
}

.tab-btn.active {
  background: linear-gradient(135deg, rgba(31, 124, 255, 0.2), rgba(77, 163, 255, 0.2));
  color: #1f7cff;
  opacity: 1;
}

.album-card {
  background: var(--glass-bg-card);
  border: 1px solid var(--glass-border-default);
  transition: all 0.2s ease;
}

.album-card:active {
  transform: scale(0.98);
  background: var(--glass-interactive-hover-muted);
}

.empty-text {
  color: var(--glass-text-primary);
  opacity: 0.4;
}
</style>
