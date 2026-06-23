<script setup lang="ts">
import { clientArtists } from '@/api'
import { useI18n } from 'vue-i18n'
import { transformArtists, type ArtistData } from '@/utils/transformers'
import { withImageParam } from '@/utils/media'

const { t } = useI18n()

const PAGE_SIZE = 24
const FETCH_LIMIT = 400

const state = reactive({
  artistPool: [] as ArtistData[],
  artists: [] as ArtistData[],
  isLoading: false,
  region: 'all',
  page: 1,
})

const regionOptions = computed(() => {
  const regions = Array.from(
    new Set(
      state.artistPool
        .map(item => item.region?.trim())
        .filter((region): region is string => Boolean(region))
    )
  )
  return [{ key: 'all', label: t('artists.areas.all') }, ...regions.map(region => ({ key: region, label: region }))]
})

const filteredArtists = computed(() => {
  if (state.region === 'all') return state.artistPool
  return state.artistPool.filter(item => item.region === state.region)
})

const visibleArtists = computed(() => filteredArtists.value.slice(0, state.page * PAGE_SIZE))

const hasMore = computed(() => visibleArtists.value.length < filteredArtists.value.length)

const applyArtists = () => {
  state.artists = visibleArtists.value
}

const loadArtists = async () => {
  state.isLoading = true
  try {
    const res = await clientArtists({ limit: FETCH_LIMIT })
    state.artistPool = transformArtists({ artists: res?.data || [] } as Record<string, unknown>)
    state.page = 1
    applyArtists()
  } finally {
    state.isLoading = false
  }
}

const loadMore = () => {
  if (!hasMore.value) return
  state.page += 1
  applyArtists()
}

watch(
  () => state.region,
  () => {
    state.page = 1
    applyArtists()
  }
)

onMounted(loadArtists)
</script>

<template>
  <div class="flex h-full flex-1 flex-col overflow-hidden">
    <div class="glass-card sonora-page-hero mx-4 mb-0 shrink-0 p-5">
      <div class="flex flex-col gap-4">
        <div class="flex items-start justify-between gap-4">
          <div>
            <h1 class="text-primary text-xl font-bold">{{ t('layout.aside.menu.artists') }}</h1>
            <p class="text-primary/55 mt-2 text-sm">按地区浏览曲库中的歌手内容</p>
          </div>
          <span class="artists-count-pill">
            {{ filteredArtists.length }}
          </span>
        </div>

        <div class="artists-filter-rack">
          <button
            v-for="option in regionOptions"
            :key="option.key"
            type="button"
            class="artists-filter-chip"
            :class="{ 'artists-filter-chip--active': state.region === option.key }"
            @click="state.region = option.key"
          >
            {{ option.label }}
          </button>
        </div>
      </div>
    </div>

    <div class="flex-1 overflow-auto p-4">
      <PageSkeleton v-if="state.isLoading && !state.artists.length" :sections="['grid']" />

      <div
        v-else
        class="grid grid-cols-2 gap-4 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 xl:grid-cols-6"
      >
        <router-link
          v-for="artist in state.artists"
          :key="artist.id"
          :to="`/artist/${artist.id}`"
          class="artist-list-card glass-card group flex h-full flex-col overflow-hidden p-3 transition-all"
        >
          <div class="relative mb-3 aspect-square overflow-hidden rounded-2xl">
            <LazyImage
              :src="withImageParam(artist.picUrl, '320y320')"
              :alt="artist.name"
              class="h-full w-full object-cover transition-transform duration-300 group-hover:scale-105"
            />
            <span v-if="artist.region" class="artist-region-badge">
              {{ artist.region }}
            </span>
          </div>

          <div class="flex min-h-0 flex-1 flex-col">
            <p class="text-primary truncate text-sm font-semibold">{{ artist.name }}</p>
            <p v-if="artist.description" class="text-primary/45 mt-1 line-clamp-2 text-xs leading-5">
              {{ artist.description }}
            </p>
            <div class="text-primary/45 mt-auto flex items-center gap-3 pt-3 text-xs">
              <span>{{ artist.musicSize || 0 }} {{ t('artistPage.stats.songs') }}</span>
              <span>{{ artist.albumSize || 0 }} {{ t('artists.albums') }}</span>
            </div>
          </div>
        </router-link>
      </div>

      <div v-if="hasMore && state.artists.length" class="mt-6 text-center">
        <button :disabled="state.isLoading" class="glass-button px-6 py-2 text-sm" @click="loadMore">
          {{ state.isLoading ? t('common.loading') : t('artists.loadMore') }}
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.artists-filter-rack {
  display: flex;
  flex-wrap: wrap;
  gap: 0.625rem;
  border: 1px solid var(--glass-border-default);
  border-radius: 1rem;
  background: var(--glass-bg-card);
  padding: 0.75rem;
}

.artists-filter-chip {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 2.25rem;
  padding: 0.45rem 0.95rem;
  border-radius: 9999px;
  border: 1px solid var(--glass-border-default);
  background: var(--glass-bg-subtle);
  color: var(--glass-text-secondary);
  font-size: 0.8125rem;
  font-weight: 500;
  transition:
    color 0.2s ease,
    background 0.2s ease,
    border-color 0.2s ease;
}

.artists-filter-chip--active,
.artists-filter-chip:hover {
  color: var(--glass-text-primary);
  border-color: rgba(31, 124, 255, 0.3);
  background: rgba(31, 124, 255, 0.12);
}

.artists-count-pill {
  display: inline-flex;
  min-width: 2.5rem;
  align-items: center;
  justify-content: center;
  border-radius: 9999px;
  padding: 0.5rem 0.8rem;
  border: 1px solid var(--glass-border-default);
  background: var(--glass-bg-subtle);
  color: var(--glass-text-muted);
  font-size: 0.8125rem;
  font-weight: 700;
}

.artist-list-card {
  border: 1px solid var(--glass-border-default);
  background:
    linear-gradient(180deg, var(--glass-bg-wash), transparent 58%),
    var(--glass-bg-card);
  box-shadow: var(--glass-shadow-sm);
}

.artist-list-card:hover,
.artist-list-card:focus-visible {
  border-color: var(--glass-border-strong);
  background: var(--glass-bg-elevated);
  box-shadow: var(--glass-shadow-lg);
  transform: translateY(-2px);
}

.artist-region-badge {
  position: absolute;
  top: 0.75rem;
  left: 0.75rem;
  display: inline-flex;
  align-items: center;
  border-radius: 9999px;
  background: rgba(15, 23, 42, 0.55);
  border: 1px solid rgba(255, 255, 255, 0.12);
  padding: 0.3rem 0.55rem;
  color: white;
  font-size: 0.6875rem;
  backdrop-filter: blur(10px);
}
</style>
