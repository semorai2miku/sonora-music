<script setup lang="ts">
import SongList from '@/components/SongList.vue'
import PageSkeleton from '@/components/PageSkeleton.vue'
import Button from '@/components/Ui/Button.vue'
import {
  clientAlbums,
  clientArtists,
  clientPublicPlaylists,
  clientSongs,
  myPlaylists,
  searchDefault,
} from '@/api'
import { useAudio } from '@/composables/useAudio'
import { usePlayActions } from '@/composables/usePlayActions'
import { useGlobalStore } from '@/stores/modules/global'
import { useUserStore } from '@/stores/modules/user'
import {
  filterAlbumsByKeyword,
  filterArtistsByKeyword,
  filterPlaylistsByKeyword,
  filterSongsByKeyword,
} from '@/utils/localSearch'
import { storeToRefs } from 'pinia'
import { withImageParam } from '@/utils/media'
import { formatCount } from '@/utils/time'
import {
  transformAlbums,
  transformArtists,
  transformPlaylists,
  transformSongs,
  type AlbumData,
  type ArtistData,
  type PlaylistData,
  type SongData,
} from '@/utils/transformers'

const route = useRoute()
const router = useRouter()
const globalStore = useGlobalStore()
const userStore = useUserStore()
const { searchHistory } = storeToRefs(globalStore)
const { setPlaylist, play } = useAudio()
const { playPlaylist: playPlaylistAction } = usePlayActions()

const q = computed(() => String(route.query.q || '').trim())

const SONG_LIMIT = 20
const ARTIST_LIMIT = 12
const ALBUM_LIMIT = 12
const PLAYLIST_LIMIT = 12
const SONG_POOL_LIMIT = 500
const ARTIST_POOL_LIMIT = 400
const ALBUM_POOL_LIMIT = 400
const PLAYLIST_POOL_LIMIT = 200

const state = reactive({
  songs: [] as SongData[],
  artists: [] as ArtistData[],
  albums: [] as AlbumData[],
  playlists: [] as PlaylistData[],
  songTotal: 0,
  artistTotal: 0,
  albumTotal: 0,
  playlistTotal: 0,
  isLoading: false,
  playingPlaylistId: null as number | string | null,
})

const totalResults = computed(
  () => state.songTotal + state.artistTotal + state.albumTotal + state.playlistTotal
)
const hasAnyResults = computed(
  () =>
    state.songs.length > 0 ||
    state.artists.length > 0 ||
    state.albums.length > 0 ||
    state.playlists.length > 0
)

const searchInput = ref('')
const inputRef = ref<HTMLInputElement | null>(null)
const inputFocused = ref(false)
const placeholder = ref('')

const resetResults = () => {
  state.songs = []
  state.artists = []
  state.albums = []
  state.playlists = []
  state.songTotal = 0
  state.artistTotal = 0
  state.albumTotal = 0
  state.playlistTotal = 0
}

const fetchSongResults = async (keyword: string) => {
  const res = await clientSongs({ sort: 'id_asc', limit: SONG_POOL_LIMIT })
  const pool = transformSongs({ songs: res?.data || [] } as Record<string, unknown>, SONG_POOL_LIMIT)
  const matched = filterSongsByKeyword(pool, keyword)
  return {
    songs: matched.slice(0, SONG_LIMIT),
    total: matched.length,
  }
}

const fetchArtistResults = async (keyword: string) => {
  const res = await clientArtists({ limit: ARTIST_POOL_LIMIT })
  const pool = transformArtists(
    { artists: res?.data || [] } as Record<string, unknown>,
    ARTIST_POOL_LIMIT
  )
  const matched = filterArtistsByKeyword(pool, keyword)
  return {
    artists: matched.slice(0, ARTIST_LIMIT),
    total: matched.length,
  }
}

const fetchAlbumResults = async (keyword: string) => {
  const res = await clientAlbums({ limit: ALBUM_POOL_LIMIT })
  const pool = transformAlbums(
    { albums: res?.data || [] } as Record<string, unknown>,
    ALBUM_POOL_LIMIT
  )
  const matched = filterAlbumsByKeyword(pool, keyword)
  return {
    albums: matched.slice(0, ALBUM_LIMIT),
    total: matched.length,
  }
}

const fetchPlaylistResults = async (keyword: string) => {
  const [publicRes, ownRes] = await Promise.allSettled([
    clientPublicPlaylists({ pageNum: 1, pageSize: PLAYLIST_POOL_LIMIT, keyword }),
    userStore.isAuthenticated ? myPlaylists() : Promise.resolve(null),
  ])

  const merged = new Map<string, PlaylistData>()

  if (publicRes.status === 'fulfilled') {
    const publicRows = filterPlaylistsByKeyword(
      transformPlaylists(publicRes.value as Record<string, unknown>),
      keyword
    )
    publicRows.forEach(item => merged.set(String(item.id), item))
  }

  if (ownRes.status === 'fulfilled' && ownRes.value?.data) {
    const ownRows = filterPlaylistsByKeyword(
      transformPlaylists({ data: ownRes.value.data } as Record<string, unknown>),
      keyword
    )
    ownRows.forEach(item => merged.set(String(item.id), item))
  }

  return {
    playlists: Array.from(merged.values()).slice(0, PLAYLIST_LIMIT),
    total: merged.size,
  }
}

const fetchResults = async () => {
  const keyword = q.value
  if (!keyword) {
    resetResults()
    return
  }

  state.isLoading = true
  try {
    const [songsRes, artistsRes, albumsRes, playlistsRes] = await Promise.allSettled([
      fetchSongResults(keyword),
      fetchArtistResults(keyword),
      fetchAlbumResults(keyword),
      fetchPlaylistResults(keyword),
    ])

    if (songsRes.status === 'fulfilled') {
      const { songs, total } = songsRes.value
      state.songs = songs
      state.songTotal = total
    } else {
      state.songs = []
      state.songTotal = 0
    }

    if (artistsRes.status === 'fulfilled') {
      const { artists, total } = artistsRes.value
      state.artists = artists
      state.artistTotal = total
    } else {
      state.artists = []
      state.artistTotal = 0
    }

    if (albumsRes.status === 'fulfilled') {
      const { albums, total } = albumsRes.value
      state.albums = albums
      state.albumTotal = total
    } else {
      state.albums = []
      state.albumTotal = 0
    }

    if (playlistsRes.status === 'fulfilled') {
      const { playlists, total } = playlistsRes.value
      state.playlists = playlists
      state.playlistTotal = total
    } else {
      state.playlists = []
      state.playlistTotal = 0
    }
  } finally {
    state.isLoading = false
  }
}

const doSearch = (keyword: string) => {
  const trimmed = keyword.trim()
  if (!trimmed) return
  globalStore.addSearchHistory(trimmed)
  searchInput.value = ''
  inputRef.value?.blur()
  router.push({ path: '/search', query: { q: trimmed } })
}

const handleSubmit = () => {
  const keyword = searchInput.value.trim() || placeholder.value
  if (keyword) doSearch(keyword)
}

const clearHistory = () => {
  globalStore.clearSearchHistory()
}

const fetchPlaceholder = async () => {
  try {
    const res: any = await searchDefault()
    placeholder.value = res?.data?.showKeyword || res?.data?.realkeyword || ''
  } catch {
    placeholder.value = ''
  }
}

const playAllSongs = () => {
  if (!state.songs.length) return
  setPlaylist(state.songs, 0)
  play(state.songs[0], 0)
}

const playPlaylist = async (playlistId: number | string) => {
  if (!playlistId || state.playingPlaylistId === playlistId) return
  state.playingPlaylistId = playlistId
  try {
    await playPlaylistAction(playlistId)
  } finally {
    state.playingPlaylistId = null
  }
}

watch(
  q,
  keyword => {
    if (!keyword) {
      resetResults()
      fetchPlaceholder()
      return
    }
    fetchResults()
  },
  { immediate: true }
)

onMounted(() => {
  if (!q.value) {
    fetchPlaceholder()
  }
})
</script>

<template>
  <div class="flex h-full flex-1 flex-col overflow-hidden px-4">
    <template v-if="q">
      <div class="mb-5 shrink-0">
        <div class="mb-4 flex items-center gap-3">
          <div class="search-result-icon flex h-10 w-10 items-center justify-center rounded-xl">
            <span class="icon-[mdi--magnify] h-5 w-5"></span>
          </div>
          <div>
            <h1 class="text-primary text-xl font-bold">
              {{ $t('search.resultsFor') }}
              <span class="search-keyword">"{{ q }}"</span>
            </h1>
            <p class="text-primary/50 text-sm">
              {{ $t('search.foundResults', { count: totalResults }) }}
            </p>
          </div>
        </div>

        <div class="glass-card flex flex-wrap items-center justify-between gap-4 p-3">
          <div class="result-summary flex flex-wrap items-center gap-2">
            <span class="result-chip">
              <span class="icon-[mdi--music-note] h-3.5 w-3.5"></span>
              {{ $t('search.sections.songs') }} {{ state.songTotal }}
            </span>
            <span class="result-chip">
              <span class="icon-[mdi--account-music] h-3.5 w-3.5"></span>
              {{ $t('search.sections.artists') }} {{ state.artistTotal }}
            </span>
            <span class="result-chip">
              <span class="icon-[mdi--album] h-3.5 w-3.5"></span>
              {{ $t('search.sections.albums') }} {{ state.albumTotal }}
            </span>
            <span class="result-chip">
              <span class="icon-[mdi--playlist-music] h-3.5 w-3.5"></span>
              {{ $t('search.sections.playlists') }} {{ state.playlistTotal }}
            </span>
          </div>

          <Button v-if="state.songs.length > 0" variant="gradient" size="sm" @click="playAllSongs">
            <span class="icon-[mdi--play] mr-1.5 h-4 w-4" />
            {{ $t('actions.playAll') }}
          </Button>
        </div>
      </div>

      <div class="custom-scrollbar min-h-0 flex-1 overflow-y-auto pb-6">
        <PageSkeleton
          v-if="state.isLoading"
          :sections="['list', 'grid', 'grid']"
          :list-count="8"
          :grid-count="6"
        />

        <template v-else>
          <div v-if="hasAnyResults" class="space-y-6">
            <section class="glass-card overflow-hidden p-3" v-if="state.songs.length > 0">
              <div class="section-header">
                <div>
                  <h2 class="section-title">{{ $t('search.sections.songs') }}</h2>
                  <p class="section-meta">
                    {{
                      $t('search.showingCount', {
                        count: state.songs.length,
                        total: state.songTotal,
                      })
                    }}
                  </p>
                </div>
              </div>
              <SongList
                :songs="state.songs"
                :show-header="true"
                :show-controls="true"
                :empty-message="$t('search.empty')"
              />
            </section>

            <section class="glass-card p-5" v-if="state.artists.length > 0">
              <div class="section-header">
                <div>
                  <h2 class="section-title">{{ $t('search.sections.artists') }}</h2>
                  <p class="section-meta">
                    {{
                      $t('search.showingCount', {
                        count: state.artists.length,
                        total: state.artistTotal,
                      })
                    }}
                  </p>
                </div>
              </div>

              <div class="grid grid-cols-2 gap-4 md:grid-cols-3 xl:grid-cols-4">
                <router-link
                  v-for="artist in state.artists"
                  :key="artist.id"
                  :to="`/artist/${artist.id}`"
                  class="artist-card group"
                >
                  <div class="artist-card__avatar">
                    <LazyImage
                      :src="artist.picUrl ? withImageParam(artist.picUrl, '300y300') : '/default-cover.svg'"
                      :alt="artist.name"
                      img-class="h-full w-full object-cover transition-transform duration-300 group-hover:scale-[1.04]"
                    />
                  </div>
                  <div class="min-w-0 text-center">
                    <p class="truncate text-sm font-semibold text-[var(--glass-text-primary)]">
                      {{ artist.name }}
                    </p>
                    <p class="mt-1 truncate text-xs text-[var(--glass-text-muted)]">
                      {{ $t('commonUnits.songsShort', artist.musicSize || 0) }}
                    </p>
                  </div>
                </router-link>
              </div>
            </section>

            <section class="glass-card p-5" v-if="state.albums.length > 0">
              <div class="section-header">
                <div>
                  <h2 class="section-title">{{ $t('search.sections.albums') }}</h2>
                  <p class="section-meta">
                    {{
                      $t('search.showingCount', {
                        count: state.albums.length,
                        total: state.albumTotal,
                      })
                    }}
                  </p>
                </div>
              </div>

              <div class="grid grid-cols-2 gap-4 md:grid-cols-3 xl:grid-cols-4">
                <router-link
                  v-for="album in state.albums"
                  :key="album.id"
                  :to="`/album/${album.id}`"
                  class="album-card group"
                >
                  <div class="album-card__cover">
                    <LazyImage
                      :src="album.picUrl ? withImageParam(album.picUrl, '300y300') : '/default-cover.svg'"
                      :alt="album.name"
                      img-class="h-full w-full object-cover transition-transform duration-300 group-hover:scale-[1.04]"
                    />
                  </div>
                  <div class="min-w-0">
                    <p class="truncate text-sm font-semibold text-[var(--glass-text-primary)]">
                      {{ album.name }}
                    </p>
                    <div
                      class="mt-1 flex items-center gap-1.5 text-xs text-[var(--glass-text-muted)]"
                    >
                      <router-link
                        v-if="album.artistId"
                        :to="`/artist/${album.artistId}`"
                        class="truncate transition-colors hover:text-[var(--glass-text-primary)]"
                        @click.stop
                      >
                        {{ album.artist || $t('player.unknownArtist') }}
                      </router-link>
                      <span v-else class="truncate">{{
                        album.artist || $t('player.unknownArtist')
                      }}</span>
                    </div>
                  </div>
                </router-link>
              </div>
            </section>

            <section class="glass-card p-5" v-if="state.playlists.length > 0">
              <div class="section-header">
                <div>
                  <h2 class="section-title">{{ $t('search.sections.playlists') }}</h2>
                  <p class="section-meta">
                    {{
                      $t('search.showingCount', {
                        count: state.playlists.length,
                        total: state.playlistTotal,
                      })
                    }}
                  </p>
                </div>
              </div>

              <div class="grid grid-cols-2 gap-4 md:grid-cols-3 xl:grid-cols-4">
                <router-link
                  v-for="playlist in state.playlists"
                  :key="playlist.id"
                  :to="`/playlist/${playlist.id}`"
                  class="album-card group"
                >
                  <div class="album-card__cover relative">
                    <LazyImage
                      :src="playlist.coverImgUrl ? withImageParam(playlist.coverImgUrl, '300y300') : '/default-cover.svg'"
                      :alt="playlist.name"
                      img-class="h-full w-full object-cover transition-transform duration-300 group-hover:scale-[1.04]"
                    />
                    <button
                      type="button"
                      class="absolute right-3 bottom-3 flex h-10 w-10 items-center justify-center rounded-full bg-black/60 text-white opacity-0 transition-all group-hover:opacity-100"
                      :disabled="state.playingPlaylistId === playlist.id"
                      @click.prevent.stop="playPlaylist(playlist.id)"
                    >
                      <span
                        :class="
                          state.playingPlaylistId === playlist.id
                            ? 'icon-[mdi--loading] animate-spin'
                            : 'icon-[mdi--play]'
                        "
                        class="h-5 w-5"
                      />
                    </button>
                  </div>
                  <div class="min-w-0">
                    <p class="truncate text-sm font-semibold text-[var(--glass-text-primary)]">
                      {{ playlist.name }}
                    </p>
                    <p class="mt-1 truncate text-xs text-[var(--glass-text-muted)]">
                      {{ playlist.creator || $t('common.me') }}
                    </p>
                    <div class="mt-2 flex items-center gap-3 text-xs text-[var(--glass-text-muted)]">
                      <span>{{ $t('commonUnits.songsShort', { count: playlist.trackCount || 0 }) }}</span>
                      <span>{{ formatCount(playlist.playCount || 0) }} {{ $t('common.stats.plays') }}</span>
                    </div>
                  </div>
                </router-link>
              </div>
            </section>
          </div>

          <div
            v-else
            class="glass-card flex flex-col items-center justify-center rounded-2xl px-5 py-12 text-center"
          >
            <div class="empty-icon mb-4 flex h-14 w-14 items-center justify-center rounded-2xl">
              <span class="icon-[mdi--magnify-close] h-6 w-6"></span>
            </div>
            <p class="text-primary text-sm font-semibold">{{ $t('search.empty') }}</p>
            <p class="text-primary/45 mt-2 text-xs">{{ $t('search.searchingHint') }}</p>
          </div>
        </template>
      </div>
    </template>

    <template v-else>
      <div class="custom-scrollbar relative flex h-full flex-col overflow-y-auto">
        <div class="pointer-events-none absolute inset-0 overflow-hidden">
          <div
            class="ambient-a absolute -top-20 left-1/3 h-[420px] w-[420px] -translate-x-1/2 rounded-full"
          />
          <div class="ambient-b absolute right-1/4 -bottom-32 h-[350px] w-[350px] rounded-full" />
        </div>

        <div class="relative z-10 shrink-0 pt-8 pb-6">
          <div
            class="search-box mx-auto w-full max-w-xl transition-all duration-500"
            :class="inputFocused ? 'scale-[1.02]' : ''"
          >
            <div
              class="flex items-center gap-3 rounded-2xl border px-5 py-3.5 backdrop-blur-xl transition-all duration-300"
              :class="[
                inputFocused
                  ? 'border-pink-500/30 bg-white/[0.07] shadow-[0_0_30px_rgba(31,124,255,0.08),0_8px_32px_rgba(0,0,0,0.12)]'
                  : 'border-white/[0.06] bg-white/[0.035] shadow-[0_2px_12px_rgba(0,0,0,0.08)]',
              ]"
            >
              <span
                class="icon-[mdi--magnify] h-5 w-5 shrink-0 transition-all duration-300"
                :class="inputFocused ? 'text-pink-400' : 'text-primary/30'"
              />
              <input
                ref="inputRef"
                v-model="searchInput"
                type="text"
                :placeholder="placeholder || $t('common.search.placeholder')"
                class="text-primary placeholder:text-primary/30 min-w-0 flex-1 bg-transparent text-sm outline-none"
                @focus="inputFocused = true"
                @blur="inputFocused = false"
                @keyup.enter="handleSubmit"
              />
              <Transition name="fade-scale">
                <button
                  v-if="searchInput"
                  class="text-primary/30 hover:text-primary/60 flex h-6 w-6 shrink-0 items-center justify-center rounded-full transition-colors hover:bg-white/10"
                  @click="searchInput = ''"
                >
                  <span class="icon-[mdi--close] h-3.5 w-3.5" />
                </button>
              </Transition>
              <button
                class="search-btn flex h-8 shrink-0 items-center gap-1.5 rounded-xl px-4 text-xs font-medium text-white transition-all duration-300 active:scale-95"
                @click="handleSubmit"
              >
                <span class="icon-[mdi--magnify] h-3.5 w-3.5" />
                {{ $t('common.search.label') }}
              </button>
            </div>
          </div>
        </div>

        <div v-if="searchHistory.length > 0" class="relative z-10 mb-6 shrink-0">
          <div class="mb-2.5 flex items-center justify-between">
            <div class="flex items-center gap-2">
              <span class="icon-[mdi--history] text-primary/25 h-4 w-4" />
              <span class="text-primary/40 text-xs font-medium">{{
                $t('search.recentSearches')
              }}</span>
            </div>
            <button
              class="text-primary/25 hover:text-primary/50 text-xs transition-colors"
              @click="clearHistory"
            >
              {{ $t('common.clear') }}
            </button>
          </div>
          <div class="flex flex-wrap gap-2">
            <button
              v-for="(keyword, ki) in searchHistory"
              :key="keyword"
              class="history-chip group flex items-center gap-1.5 rounded-full border border-white/[0.05] bg-white/[0.025] px-3 py-1.5 text-[13px] transition-all duration-200 hover:border-pink-500/15 hover:bg-white/[0.055]"
              :style="{ animationDelay: `${ki * 35}ms` }"
              @click="doSearch(keyword)"
            >
              <span class="text-primary/55 group-hover:text-primary/85 transition-colors">{{
                keyword
              }}</span>
              <span
                class="icon-[mdi--close] group-hover:text-primary/30 h-3 w-3 shrink-0 text-transparent transition-all hover:text-pink-400!"
                @click.stop="globalStore.removeSearchHistory(keyword)"
              />
            </button>
          </div>
        </div>

        <div class="relative z-10 min-h-0 flex-1 pb-6">
          <div
            class="rounded-2xl border border-white/[0.05] bg-white/[0.025] px-5 py-8 text-center"
          >
            <div
              class="mx-auto mb-4 flex h-14 w-14 items-center justify-center rounded-2xl bg-white/[0.04]"
            >
              <span class="icon-[mdi--magnify] text-primary/35 h-6 w-6" />
            </div>
            <p class="text-primary/80 text-sm font-medium">{{ $t('search.enterKeyword') }}</p>
            <p class="text-primary/40 mt-2 text-xs">{{ $t('search.hint') }}</p>
          </div>
        </div>
      </div>
    </template>
  </div>
</template>

<style scoped>
.ambient-a {
  background: radial-gradient(circle, rgba(31, 124, 255, 0.07) 0%, transparent 65%);
  animation: ambient-float 12s ease-in-out infinite;
}

.ambient-b {
  background: radial-gradient(circle, rgba(77, 163, 255, 0.05) 0%, transparent 65%);
  animation: ambient-float 15s ease-in-out infinite reverse;
}

@keyframes ambient-float {
  0%,
  100% {
    transform: translate(0, 0) scale(1);
    opacity: 0.6;
  }
  33% {
    transform: translate(20px, -15px) scale(1.05);
    opacity: 0.8;
  }
  66% {
    transform: translate(-15px, 10px) scale(0.97);
    opacity: 0.5;
  }
}

.search-result-icon,
.empty-icon {
  background: linear-gradient(135deg, rgba(31, 124, 255, 0.16), rgba(77, 163, 255, 0.1));
  color: var(--sonora-blue);
}

.search-keyword {
  background: linear-gradient(90deg, var(--sonora-blue), var(--sonora-blue-soft));
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}

.result-chip {
  display: inline-flex;
  align-items: center;
  gap: 0.375rem;
  border-radius: 9999px;
  border: 1px solid var(--glass-border-default);
  background: var(--glass-bg-subtle);
  color: var(--glass-text-secondary);
  padding: 0.45rem 0.75rem;
  font-size: 0.75rem;
  font-weight: 500;
}

.section-header {
  margin-bottom: 1rem;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
}

.section-title {
  color: var(--glass-text-primary);
  font-size: 1rem;
  font-weight: 700;
}

.section-meta {
  margin-top: 0.25rem;
  color: var(--glass-text-muted);
  font-size: 0.75rem;
}

.artist-card,
.album-card {
  display: flex;
  flex-direction: column;
  gap: 0.875rem;
  border-radius: 1rem;
  border: 1px solid var(--glass-border-default);
  background: var(--glass-bg-card);
  padding: 0.875rem;
  transition:
    border-color 0.2s ease,
    background 0.2s ease,
    transform 0.2s ease;
}

.artist-card:hover,
.album-card:hover {
  transform: translateY(-2px);
  border-color: var(--glass-border-strong);
  background: var(--glass-bg-elevated);
}

.artist-card__avatar {
  margin-inline: auto;
  aspect-ratio: 1 / 1;
  width: min(100%, 9rem);
  overflow: hidden;
  border-radius: 9999px;
  border: 1px solid var(--glass-border-subtle);
  background: var(--glass-bg-subtle);
}

.album-card__cover {
  aspect-ratio: 1 / 1;
  overflow: hidden;
  border-radius: 0.95rem;
  border: 1px solid var(--glass-border-subtle);
  background: var(--glass-bg-subtle);
}

.search-btn {
  background: linear-gradient(135deg, #1f7cff, #4da3ff);
  box-shadow: 0 4px 16px rgba(31, 124, 255, 0.25);
}

.search-btn:hover {
  box-shadow: 0 6px 24px rgba(31, 124, 255, 0.35);
  filter: brightness(1.1);
}

.history-chip {
  animation: chip-in 0.35s var(--glass-ease-out) both;
}

@keyframes chip-in {
  from {
    opacity: 0;
    transform: translateY(4px) scale(0.97);
  }
  to {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}

.fade-scale-enter-active,
.fade-scale-leave-active {
  transition: all 0.2s ease;
}

.fade-scale-enter-from,
.fade-scale-leave-to {
  opacity: 0;
  transform: scale(0.8);
}
</style>
