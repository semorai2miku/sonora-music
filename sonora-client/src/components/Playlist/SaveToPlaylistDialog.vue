<script setup lang="ts">
import Button from '@/components/Ui/Button.vue'
import {
  addSongToMyPlaylist,
  createMyPlaylist,
  myPlaylists,
  type ClientPlaylist,
} from '@/api'

const props = defineProps<{
  songId: number | string
  songName?: string
}>()

const emit = defineEmits<{
  (e: 'close'): void
  (e: 'saved', playlist: ClientPlaylist): void
}>()

const visible = ref(true)
const playlists = ref<ClientPlaylist[]>([])
const loading = ref(false)
const savingId = ref<number | string | null>(null)
const errorMessage = ref('')
const newPlaylistName = ref('')

const normalPlaylists = computed(() =>
  playlists.value.filter(playlist => playlist.type !== 'liked')
)

const dispatchPlaylistUpdated = () => {
  window.dispatchEvent(new CustomEvent('sonora:playlists-updated'))
}

const loadPlaylists = async () => {
  loading.value = true
  errorMessage.value = ''
  try {
    const res = await myPlaylists()
    playlists.value = res?.data || []
  } catch (error: any) {
    errorMessage.value = error?.response?.data?.message || error?.message || '歌单加载失败'
  } finally {
    loading.value = false
  }
}

const saveToPlaylist = async (playlist: ClientPlaylist) => {
  if (!playlist?.id || !props.songId) return
  savingId.value = playlist.id
  errorMessage.value = ''
  try {
    const res = await addSongToMyPlaylist(playlist.id, props.songId)
    if (res?.code !== 200) throw new Error(res?.message || '收藏失败')
    dispatchPlaylistUpdated()
    emit('saved', res.data || playlist)
    visible.value = false
  } catch (error: any) {
    errorMessage.value = error?.response?.data?.message || error?.message || '收藏失败'
  } finally {
    savingId.value = null
  }
}

const createAndSave = async () => {
  const name = newPlaylistName.value.trim()
  if (!name) {
    errorMessage.value = '请输入歌单名称'
    return
  }
  savingId.value = 'new'
  errorMessage.value = ''
  try {
    const createRes = await createMyPlaylist({ name })
    if (createRes?.code !== 200 || !createRes.data?.id) {
      throw new Error(createRes?.message || '创建歌单失败')
    }
    const saveRes = await addSongToMyPlaylist(createRes.data.id, props.songId)
    if (saveRes?.code !== 200) throw new Error(saveRes?.message || '收藏失败')
    dispatchPlaylistUpdated()
    emit('saved', saveRes.data || createRes.data)
    visible.value = false
  } catch (error: any) {
    errorMessage.value = error?.response?.data?.message || error?.message || '创建或收藏失败'
  } finally {
    savingId.value = null
  }
}

const handleAfterLeave = () => emit('close')

onMounted(loadPlaylists)
</script>

<template>
  <div class="fixed inset-0 z-50 flex items-center justify-center p-4">
    <Transition name="mask" appear>
      <div
        v-if="visible"
        class="absolute inset-0 bg-black/60 backdrop-blur-sm"
        @click="visible = false"
      />
    </Transition>

    <Transition name="dialog" appear @after-leave="handleAfterLeave">
      <div v-if="visible" class="relative z-10 w-full max-w-lg">
        <div class="glass-container-strong overflow-hidden">
          <Button
            variant="soft"
            size="icon-sm"
            rounded="full"
            icon="icon-[mdi--close]"
            icon-class="h-4 w-4"
            class="absolute top-4 right-4 z-20"
            @click="visible = false"
          />

          <div class="p-6">
            <div class="mb-5 flex items-center gap-4">
              <div
                class="flex h-12 w-12 items-center justify-center rounded-2xl bg-linear-to-br from-pink-500 to-purple-600 shadow-lg shadow-pink-500/25"
              >
                <span class="icon-[mdi--playlist-plus] h-6 w-6 text-white" />
              </div>
              <div class="min-w-0">
                <h2 class="text-primary text-xl font-bold">收藏到歌单</h2>
                <p class="text-primary/50 mt-0.5 truncate text-sm">
                  {{ songName || '选择一个自建歌单' }}
                </p>
              </div>
            </div>

            <div class="mb-5 flex gap-2">
              <input
                v-model="newPlaylistName"
                type="text"
                maxlength="80"
                placeholder="新建歌单名称"
                class="text-primary glass-card min-w-0 flex-1 rounded-xl border border-glass px-4 py-3 text-sm outline-none placeholder:text-white/30 focus:border-pink-400/50"
                @keyup.enter="createAndSave"
              />
              <Button
                variant="solid"
                size="md"
                rounded="xl"
                :loading="savingId === 'new'"
                :disabled="savingId !== null"
                icon="icon-[mdi--plus]"
                @click="createAndSave"
              >
                新建并收藏
              </Button>
            </div>

            <p v-if="errorMessage" class="mb-4 text-sm text-red-300">{{ errorMessage }}</p>

            <div
              v-if="loading"
              class="flex items-center justify-center py-12 text-primary/60"
            >
              <span class="icon-[mdi--loading] mr-2 h-5 w-5 animate-spin" />
              正在加载歌单
            </div>

            <div v-else-if="normalPlaylists.length" class="custom-scrollbar max-h-80 space-y-2 overflow-auto pr-1">
              <button
                v-for="playlist in normalPlaylists"
                :key="playlist.id"
                class="group flex w-full items-center gap-3 rounded-xl p-3 text-left transition-colors hover:bg-white/10"
                :disabled="savingId !== null"
                @click="saveToPlaylist(playlist)"
              >
                <div
                  class="flex h-12 w-12 shrink-0 items-center justify-center overflow-hidden rounded-xl bg-linear-to-br from-pink-500/70 to-purple-600/70 text-white"
                >
                  <img
                    v-if="playlist.cover"
                    :src="playlist.cover"
                    alt="cover"
                    class="h-full w-full object-cover"
                  />
                  <span v-else class="icon-[mdi--playlist-music] h-6 w-6" />
                </div>
                <div class="min-w-0 flex-1">
                  <p class="text-primary truncate text-sm font-medium">{{ playlist.name }}</p>
                  <p class="text-primary/50 mt-0.5 text-xs">{{ playlist.songCount || 0 }} 首</p>
                </div>
                <span
                  v-if="savingId === playlist.id"
                  class="icon-[mdi--loading] text-primary/60 h-5 w-5 animate-spin"
                />
                <span
                  v-else
                  class="icon-[mdi--chevron-right] text-primary/40 h-5 w-5 transition-transform group-hover:translate-x-0.5"
                />
              </button>
            </div>

            <div v-else class="py-10 text-center">
              <span class="icon-[mdi--playlist-music-outline] text-primary/20 mx-auto mb-3 h-12 w-12" />
              <p class="text-primary/60 text-sm">还没有自建歌单，可以先新建一个。</p>
            </div>
          </div>
        </div>
      </div>
    </Transition>
  </div>
</template>

<style scoped>
.dialog-enter-active,
.dialog-leave-active {
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}
.dialog-enter-from,
.dialog-leave-to {
  opacity: 0;
  transform: scale(0.95) translateY(10px);
}

.mask-enter-active,
.mask-leave-active {
  transition: opacity 0.3s ease;
}
.mask-enter-from,
.mask-leave-to {
  opacity: 0;
}
</style>
