<script setup lang="ts">
import { clientAlbums } from '@/api'
import { useI18n } from 'vue-i18n'
import { transformAlbums, type AlbumData } from '@/utils/transformers'
import { withImageParam } from '@/utils/media'

const { t } = useI18n()

const PAGE_SIZE = 24
const FETCH_LIMIT = 400

const state = reactive({
  albumPool: [] as AlbumData[],
  albums: [] as AlbumData[],
  isLoading: false,
  region: 'all',
  page: 1,
})

const regionOptions = computed(() => {
  const regions = Array.from(
    new Set(
      state.albumPool
        .map(item => item.region?.trim())
        .filter((region): region is string => Boolean(region))
    )
  )
  return [{ key: 'all', label: t('newAlbums.areas.all') }, ...regions.map(region => ({ key: region, label: region }))]
})

const filteredAlbums = computed(() => {
  if (state.region === 'all') return state.albumPool
  return state.albumPool.filter(item => item.region === state.region)
})

const visibleAlbums = computed(() => filteredAlbums.value.slice(0, state.page * PAGE_SIZE))

const hasMore = computed(() => visibleAlbums.value.length < filteredAlbums.value.length)

const applyAlbums = () => {
  state.albums = visibleAlbums.value
}

const loadAlbums = async () => {
  state.isLoading = true
  try {
    const res = await clientAlbums({ limit: FETCH_LIMIT })
    state.albumPool = transformAlbums({ albums: res?.data || [] } as Record<string, unknown>)
    state.page = 1
    applyAlbums()
  } finally {
    state.isLoading = false
  }
}

const loadMore = () => {
  if (!hasMore.value) return
  state.page += 1
  applyAlbums()
}

watch(
  () => state.region,
  () => {
    state.page = 1
    applyAlbums()
  }
)

onMounted(loadAlbums)
</script>

<template>
  <div class="flex h-full flex-1 flex-col overflow-hidden">
    <div class="glass-card mx-4 mb-0 shrink-0 p-5">
      <div class="flex flex-col gap-4">
        <div class="flex items-start justify-between gap-4">
          <div>
            <h1 class="text-primary text-xl font-bold">{{ t('newAlbums.title') }}</h1>
            <p class="text-primary/55 mt-2 text-sm">按地区查看曲库里的专辑内容</p>
          </div>
          <span class="albums-count-pill">
            {{ filteredAlbums.length }}
          </span>
        </div>

        <div class="albums-filter-rack">
          <button
            v-for="option in regionOptions"
            :key="option.key"
            type="button"
            class="albums-filter-chip"
            :class="{ 'albums-filter-chip--active': state.region === option.key }"
            @click="state.region = option.key"
          >
            {{ option.label }}
          </button>
        </div>
      </div>
    </div>

    <div class="flex-1 overflow-auto p-4">
      <PageSkeleton v-if="state.isLoading && !state.albums.length" :sections="['grid']" />

      <div
        v-else
        class="grid grid-cols-2 gap-4 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 xl:grid-cols-6"
      >
        <router-link
          v-for="album in state.albums"
          :key="album.id"
          :to="`/album/${album.id}`"
          class="glass-card group flex h-full flex-col overflow-hidden p-3 transition-all hover:bg-white/10"
        >
          <div class="relative mb-3 aspect-square overflow-hidden rounded-2xl">
            <LazyImage
              :src="withImageParam(album.picUrl, '320y320')"
              :alt="album.name"
              class="h-full w-full object-cover transition-transform duration-300 group-hover:scale-105"
            />
            <span v-if="album.region" class="album-region-badge">
              {{ album.region }}
            </span>
          </div>

          <div class="flex min-h-0 flex-1 flex-col">
            <p class="text-primary truncate text-sm font-semibold">{{ album.name }}</p>
            <p class="text-primary/60 mt-1 truncate text-xs">
              {{ album.artist || t('player.unknownArtist') }}
            </p>
            <p v-if="album.description" class="text-primary/45 mt-2 line-clamp-2 text-xs leading-5">
              {{ album.description }}
            </p>
            <div class="text-primary/45 mt-auto flex items-center justify-between pt-3 text-xs">
              <span>{{ album.publishTime || '-' }}</span>
              <span>{{ t('commonUnits.songsShort', album.size || 0) }}</span>
            </div>
          </div>
        </router-link>
      </div>

      <div v-if="hasMore && state.albums.length" class="mt-6 text-center">
        <button :disabled="state.isLoading" class="glass-button px-6 py-2 text-sm" @click="loadMore">
          {{ state.isLoading ? t('common.loading') : t('newAlbums.loadMore') }}
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.albums-filter-rack {
  display: flex;
  flex-wrap: wrap;
  gap: 0.625rem;
  border: 1px solid var(--glass-border-default);
  border-radius: 1rem;
  background: var(--glass-bg-card);
  padding: 0.75rem;
}

.albums-filter-chip {
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

.albums-filter-chip--active,
.albums-filter-chip:hover {
  color: var(--glass-text-primary);
  border-color: rgba(31, 124, 255, 0.3);
  background: rgba(31, 124, 255, 0.12);
}

.albums-count-pill {
  display: inline-flex;
  min-width: 2.5rem;
  align-items: center;
  justify-content: center;
  border-radius: 9999px;
  padding: 0.5rem 0.8rem;
  background: rgba(255, 255, 255, 0.06);
  color: var(--glass-text-muted);
  font-size: 0.8125rem;
  font-weight: 700;
}

.album-region-badge {
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
