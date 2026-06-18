<script setup lang="ts">
import {
  clientAlbums,
  clientArtists,
  clientPublicPlaylists,
  clientSongs,
  myPlaylists,
  searchDefault,
  searchSuggest,
} from '@/api'
import LazyImage from '@/components/Ui/LazyImage.vue'
import Button from '@/components/Ui/Button.vue'
import { useAudio } from '@/composables/useAudio'
import { usePlayActions } from '@/composables/usePlayActions'
import { useUserStore } from '@/stores/modules/user'
import {
  filterAlbumsByKeyword,
  filterArtistsByKeyword,
  filterPlaylistsByKeyword,
  filterSongsByKeyword,
} from '@/utils/localSearch'
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
const userStore = useUserStore()
const { setPlaylist, play } = useAudio()
const { playPlaylist: playPlaylistAction } = usePlayActions()

const q = computed(() => String(route.query.q || '').trim())

const SONG_LIMIT = 12
const ARTIST_LIMIT = 8
const ALBUM_LIMIT = 8
const PLAYLIST_LIMIT = 8
const SONG_POOL_LIMIT = 500
const ARTIST_POOL_LIMIT = 400
const ALBUM_POOL_LIMIT = 400
const PLAYLIST_POOL_LIMIT = 200

const state = reactive({
  placeholder: '',
  suggest: [] as string[],
  loading: false,
  suggestVisible: false,
  searchInput: '',
  songs: [] as SongData[],
  artists: [] as ArtistData[],
  albums: [] as AlbumData[],
  playlists: [] as PlaylistData[],
  songTotal: 0,
  artistTotal: 0,
  albumTotal: 0,
  playlistTotal: 0,
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

const inputRef = useTemplateRef('inputRef')
const searchBoxRef = ref<HTMLElement | null>(null)

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

const fetchDefault = async () => {
  try {
    const res = await searchDefault()
    state.placeholder = (res as any)?.data?.realkeyword || (res as any)?.data?.showKeyword || ''
  } catch {
    state.placeholder = ''
  }
}

const fetchSuggest = async () => {
  if (!state.searchInput.trim()) {
    state.suggest = []
    return
  }
  try {
    const res = await searchSuggest({ keywords: state.searchInput, type: 'mobile' })
    const list = (res as any)?.result?.allMatch || []
    state.suggest = list.map((item: any) => item.keyword).slice(0, 8)
  } catch {
    state.suggest = []
  }
}

const fetchResults = async () => {
  const keyword = q.value
  if (!keyword) {
    resetResults()
    return
  }

  state.loading = true
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
    state.loading = false
  }
}

const submitSearch = (keyword: string) => {
  const trimmed = keyword.trim()
  if (!trimmed) return
  state.suggestVisible = false
  router.push({ path: '/search', query: { q: trimmed } })
  inputRef.value?.blur()
}

const handleSearchClick = () => {
  const keyword = state.searchInput.trim() || state.placeholder
  if (!keyword) return
  submitSearch(keyword)
}

const handleSuggestClick = (keyword: string) => {
  state.searchInput = keyword
  submitSearch(keyword)
}

const clearQuery = () => {
  state.searchInput = ''
  state.suggest = []
  state.suggestVisible = false
  if (q.value) {
    router.push({ path: '/search' })
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

const handleInputFocus = () => {
  state.suggestVisible = true
}

const handleDocClick = (event: MouseEvent) => {
  const target = event.target as Node | null
  if (searchBoxRef.value && target && !searchBoxRef.value.contains(target)) {
    state.suggestVisible = false
  }
}

watch(
  () => state.searchInput,
  () => {
    fetchSuggest()
  }
)

watch(
  q,
  keyword => {
    state.searchInput = keyword
    if (!keyword) {
      resetResults()
      fetchDefault()
      return
    }
    fetchResults()
  },
  { immediate: true }
)

onMounted(async () => {
  if (!q.value) {
    await fetchDefault()
  }
  document.addEventListener('click', handleDocClick)
})

onUnmounted(() => {
  document.removeEventListener('click', handleDocClick)
})
</script>

<template>
  <div class="flex flex-1 flex-col overflow-hidden">
    <div class="shrink-0 px-4 pb-3">
      <div ref="searchBoxRef" class="relative">
        <div class="glass-card flex items-center gap-2 px-3 py-2.5">
          <span class="icon-[mdi--magnify] search-icon h-5 w-5 shrink-0"></span>
          <input
            ref="inputRef"
            v-model="state.searchInput"
            @keyup.enter="handleSearchClick"
            @focus="handleInputFocus"
            type="text"
            :placeholder="state.placeholder || $t('common.search.placeholder')"
            class="search-input min-w-0 flex-1 bg-transparent text-sm outline-none"
          />
          <Button
            v-if="state.searchInput"
            variant="ghost"
            size="none"
            class="clear-btn flex h-7 w-7 items-center justify-center rounded-full transition-all"
            icon="icon-[mdi--close-circle]"
            icon-class="h-4 w-4"
            :title="$t('common.clear')"
            @click="clearQuery"
          />
          <Button
            variant="gradient"
            size="none"
            class="search-btn flex h-8 items-center gap-1.5 rounded-full px-4 text-xs font-medium text-white transition-all active:scale-95"
            icon="icon-[mdi--magnify]"
            icon-class="h-4 w-4"
            @click="handleSearchClick"
          >
            {{ $t('common.search.label') }}
          </Button>
        </div>

        <Transition name="dropdown">
          <div
            v-if="state.suggestVisible && state.suggest.length"
            class="suggest-dropdown absolute top-full right-0 left-0 z-20 mt-2 rounded-2xl p-3"
          >
            <p class="suggest-title mb-2 px-1 text-[10px] font-medium tracking-wider uppercase">
              {{ $t('common.search.suggest') }}
            </p>
            <div class="flex flex-wrap gap-2">
              <Button
                v-for="suggestion in state.suggest"
                :key="suggestion"
                variant="ghost"
                size="none"
                class="suggest-tag rounded-full px-3 py-1.5 text-xs transition-all active:scale-95"
                @click="handleSuggestClick(suggestion)"
              >
                {{ suggestion }}
              </Button>
            </div>
          </div>
        </Transition>
      </div>
    </div>

    <div class="min-h-0 flex-1 overflow-hidden">
      <div v-if="q" class="custom-scrollbar h-full overflow-y-auto px-4 pb-6">
        <div class="mb-4 flex items-center justify-between gap-3 py-1">
          <div class="min-w-0">
            <h1 class="text-primary truncate text-lg font-bold">
              {{ $t('search.resultsFor') }}
              <span class="search-keyword">"{{ q }}"</span>
            </h1>
            <p class="info-text mt-1 text-xs">
              {{ $t('search.foundResults', { count: totalResults }) }}
            </p>
          </div>

          <Button
            v-if="state.songs.length > 0"
            variant="gradient"
            size="none"
            class="play-all-button flex items-center gap-1.5 rounded-full px-4 py-2 text-xs font-medium text-white"
            icon="icon-[mdi--play]"
            icon-class="h-4 w-4"
            @click="playAllSongs"
          >
            {{ $t('actions.playAll') }}
          </Button>
        </div>

        <div v-if="state.loading" class="space-y-4 py-4">
          <PageSkeleton :sections="['list', 'grid', 'grid']" :list-count="8" :grid-count="4" />
        </div>

        <div v-else-if="hasAnyResults" class="space-y-4">
          <section v-if="state.songs.length > 0" class="glass-card overflow-hidden p-3">
            <div class="section-header">
              <div>
                <h2 class="section-title">{{ $t('search.sections.songs') }}</h2>
                <p class="section-meta">
                  {{
                    $t('search.showingCount', { count: state.songs.length, total: state.songTotal })
                  }}
                </p>
              </div>
            </div>
            <MobileSongList :songs="state.songs" variant="compact" :show-index="true" />
          </section>

          <section v-if="state.artists.length > 0" class="glass-card p-4">
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

            <div class="grid grid-cols-2 gap-3">
              <router-link
                v-for="artist in state.artists"
                :key="artist.id"
                :to="`/artist/${artist.id}`"
                class="artist-card"
              >
                <div class="artist-card__avatar">
                  <LazyImage
                    :src="artist.picUrl ? withImageParam(artist.picUrl, '240y240') : '/default-cover.svg'"
                    :alt="artist.name"
                    imgClass="h-full w-full object-cover"
                  />
                </div>
                <div class="text-center">
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

          <section v-if="state.albums.length > 0" class="glass-card p-4">
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

            <div class="grid grid-cols-2 gap-3">
              <router-link
                v-for="album in state.albums"
                :key="album.id"
                :to="`/album/${album.id}`"
                class="album-card"
              >
                <div class="album-card__cover">
                  <LazyImage
                    :src="album.picUrl ? withImageParam(album.picUrl, '240y240') : '/default-cover.svg'"
                    :alt="album.name"
                    imgClass="h-full w-full object-cover"
                  />
                </div>
                <div class="min-w-0">
                  <p class="truncate text-sm font-semibold text-[var(--glass-text-primary)]">
                    {{ album.name }}
                  </p>
                  <p class="mt-1 truncate text-xs text-[var(--glass-text-muted)]">
                    {{ album.artist || $t('player.unknownArtist') }}
                  </p>
                </div>
              </router-link>
            </div>
          </section>

          <section v-if="state.playlists.length > 0" class="glass-card p-4">
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

            <div class="grid grid-cols-2 gap-3">
              <router-link
                v-for="playlist in state.playlists"
                :key="playlist.id"
                :to="`/playlist/${playlist.id}`"
                class="album-card"
              >
                <div class="album-card__cover relative overflow-hidden">
                  <LazyImage
                    :src="playlist.coverImgUrl ? withImageParam(playlist.coverImgUrl, '240y240') : '/default-cover.svg'"
                    :alt="playlist.name"
                    imgClass="h-full w-full object-cover"
                  />
                  <button
                    type="button"
                    class="absolute right-2 bottom-2 flex h-9 w-9 items-center justify-center rounded-full bg-black/60 text-white"
                    :disabled="state.playingPlaylistId === playlist.id"
                    @click.prevent.stop="playPlaylist(playlist.id)"
                  >
                    <span
                      :class="
                        state.playingPlaylistId === playlist.id
                          ? 'icon-[mdi--loading] animate-spin'
                          : 'icon-[mdi--play]'
                      "
                      class="h-4.5 w-4.5"
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
                  <p class="mt-2 truncate text-xs text-[var(--glass-text-muted)]">
                    {{ $t('commonUnits.songsShort', { count: playlist.trackCount || 0 }) }}
                    <span class="mx-1">·</span>
                    {{ formatCount(playlist.playCount || 0) }} {{ $t('common.stats.plays') }}
                  </p>
                </div>
              </router-link>
            </div>
          </section>
        </div>

        <div v-else class="empty-state glass-card flex flex-col items-center py-16">
          <div class="empty-icon mb-4 flex h-16 w-16 items-center justify-center rounded-2xl">
            <span class="icon-[mdi--magnify-close] h-8 w-8"></span>
          </div>
          <p class="empty-title text-sm font-medium">{{ $t('search.empty') }}</p>
          <p class="empty-subtitle mt-2 text-xs">{{ $t('search.searchingHint') }}</p>
        </div>
      </div>

      <div v-else class="flex h-full items-center justify-center px-4 pb-6">
        <div class="glass-card w-full rounded-2xl px-5 py-8 text-center">
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
  </div>
</template>

<style scoped>
.dropdown-enter-active,
.dropdown-leave-active {
  transition: all 0.2s ease;
}

.dropdown-enter-from,
.dropdown-leave-to {
  opacity: 0;
  transform: translateY(-8px);
}

.search-icon {
  color: var(--glass-text-primary);
  opacity: 0.5;
}

.search-input {
  color: var(--glass-text-primary);
}

.search-input::placeholder {
  color: var(--glass-text-primary);
  opacity: 0.4;
}

.clear-btn {
  color: var(--glass-text-primary);
  opacity: 0.4;
}

.clear-btn:active {
  opacity: 0.7;
  background: var(--glass-interactive-hover-muted);
}

.search-btn,
.play-all-button {
  background: linear-gradient(to right, #1f7cff, #4da3ff);
  box-shadow: 0 4px 12px rgba(31, 124, 255, 0.3);
}

.suggest-dropdown {
  background: var(--glass-bg-overlay);
  border: 1px solid var(--glass-border-subtle);
  box-shadow: 0 10px 40px rgba(0, 0, 0, 0.15);
}

.suggest-title,
.info-text,
.section-meta,
.empty-subtitle {
  color: var(--glass-text-primary);
  opacity: 0.5;
}

.suggest-tag {
  background: var(--glass-interactive-hover-muted);
  color: var(--glass-text-primary);
  opacity: 0.8;
}

.search-keyword {
  background: linear-gradient(90deg, var(--sonora-blue), var(--sonora-blue-soft));
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}

.section-header {
  margin-bottom: 0.875rem;
}

.section-title,
.empty-title {
  color: var(--glass-text-primary);
}

.artist-card,
.album-card {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  border-radius: 1rem;
  border: 1px solid var(--glass-border-default);
  background: var(--glass-bg-card);
  padding: 0.75rem;
}

.artist-card__avatar {
  aspect-ratio: 1 / 1;
  overflow: hidden;
  border-radius: 9999px;
  border: 1px solid var(--glass-border-subtle);
  background: var(--glass-bg-subtle);
}

.album-card__cover {
  aspect-ratio: 1 / 1;
  overflow: hidden;
  border-radius: 0.875rem;
  border: 1px solid var(--glass-border-subtle);
  background: var(--glass-bg-subtle);
}

.empty-icon {
  background: linear-gradient(135deg, rgba(31, 124, 255, 0.16), rgba(77, 163, 255, 0.1));
  color: var(--sonora-blue);
}
</style>
