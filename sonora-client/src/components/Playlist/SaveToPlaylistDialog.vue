<script setup lang="ts">
import Button from '@/components/Ui/Button.vue'
import {
  addSongToMyPlaylist,
  createMyPlaylist,
  myPlaylists,
  type ClientPlaylist,
} from '@/api'
import { resolveMediaUrl, withImageParam } from '@/utils/media'
import { useI18n } from 'vue-i18n'

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
const { t } = useI18n()

const normalPlaylists = computed(() =>
  playlists.value.filter(playlist => playlist.type !== 'liked' && !playlist.subscribed)
)

const dispatchPlaylistUpdated = () => {
  window.dispatchEvent(new CustomEvent('sonora:playlists-updated'))
}

const loadPlaylists = async () => {
  loading.value = true
  errorMessage.value = ''
  try {
    const res = await myPlaylists()
    playlists.value = (Array.isArray(res?.data) ? res.data : []).map((playlist: ClientPlaylist) => ({
      ...playlist,
      cover: resolveMediaUrl(playlist.cover || '/default-cover.svg'),
    }))
  } catch (error: any) {
    errorMessage.value =
      error?.response?.data?.message || error?.message || t('playlist.dialog.loadFailed')
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
    if (res?.code !== 200) throw new Error(res?.message || t('playlist.dialog.saveFailed'))
    dispatchPlaylistUpdated()
    emit('saved', res.data || playlist)
    visible.value = false
  } catch (error: any) {
    errorMessage.value =
      error?.response?.data?.message || error?.message || t('playlist.dialog.saveFailed')
  } finally {
    savingId.value = null
  }
}

const createAndSave = async () => {
  const name = newPlaylistName.value.trim()
  if (!name) {
    errorMessage.value = t('playlist.messages.nameRequired')
    return
  }
  savingId.value = 'new'
  errorMessage.value = ''
  try {
    const createRes = await createMyPlaylist({ name, status: 0 })
    if (createRes?.code !== 200 || !createRes.data?.id) {
      throw new Error(createRes?.message || t('playlist.dialog.createFailed'))
    }
    const saveRes = await addSongToMyPlaylist(createRes.data.id, props.songId)
    if (saveRes?.code !== 200) throw new Error(saveRes?.message || t('playlist.dialog.saveFailed'))
    dispatchPlaylistUpdated()
    emit('saved', saveRes.data || createRes.data)
    visible.value = false
  } catch (error: any) {
    errorMessage.value =
      error?.response?.data?.message || error?.message || t('playlist.dialog.createOrSaveFailed')
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
              <span class="brand-badge">
                <img src="/branding/sonora-logo-icon.svg" alt="Sonora" class="h-6 w-6" />
              </span>
              <div class="min-w-0">
                <h2 class="brand-font text-primary text-xl font-bold">
                  {{ $t('playlist.dialog.title') }}
                </h2>
                <p class="text-primary/50 mt-0.5 truncate text-sm">
                  {{ songName || $t('playlist.dialog.subtitle') }}
                </p>
              </div>
            </div>

            <div class="mb-5 flex gap-2">
              <input
                v-model="newPlaylistName"
                type="text"
                maxlength="80"
                :placeholder="$t('playlist.dialog.newPlaylistPlaceholder')"
                class="text-primary glass-card min-w-0 flex-1 rounded-xl border border-glass px-4 py-3 text-sm outline-none placeholder:text-primary/30 focus:border-sky-400/50"
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
                {{ $t('playlist.dialog.createAndSave') }}
              </Button>
            </div>

            <p v-if="errorMessage" class="mb-4 text-sm text-red-300">{{ errorMessage }}</p>

            <div
              v-if="loading"
              class="flex items-center justify-center py-12 text-primary/60"
            >
              <span class="icon-[mdi--loading] mr-2 h-5 w-5 animate-spin" />
              {{ $t('playlist.dialog.loading') }}
            </div>

            <div
              v-else-if="normalPlaylists.length"
              class="custom-scrollbar max-h-80 space-y-2 overflow-auto pr-1"
            >
              <button
                v-for="playlist in normalPlaylists"
                :key="playlist.id"
                class="group flex w-full items-center gap-3 rounded-xl p-3 text-left transition-colors hover:bg-hover-glass"
                :disabled="savingId !== null"
                @click="saveToPlaylist(playlist)"
              >
                <div
                  class="flex h-12 w-12 shrink-0 items-center justify-center overflow-hidden rounded-xl bg-linear-to-br from-slate-900/90 to-sky-500/90 text-white"
                >
                  <img
                    v-if="playlist.cover"
                    :src="withImageParam(playlist.cover, '120y120')"
                    alt="cover"
                    class="h-full w-full object-cover"
                  />
                  <span v-else class="icon-[mdi--playlist-music] h-6 w-6" />
                </div>
                <div class="min-w-0 flex-1">
                  <p class="text-primary truncate text-sm font-medium">{{ playlist.name }}</p>
                  <div class="text-primary/50 mt-0.5 flex items-center gap-2 text-xs">
                    <span>
                      {{ $t('commonUnits.songsShort', { count: playlist.songCount || 0 }) }}
                    </span>
                    <span
                      class="rounded-full px-2 py-0.5"
                      :class="
                        Number(playlist.status || 0) === 1
                          ? 'bg-emerald-500/15 text-emerald-300'
                          : 'bg-white/8 text-primary/55'
                      "
                    >
                      {{
                        Number(playlist.status || 0) === 1
                          ? $t('playlist.visibility.public')
                          : $t('playlist.visibility.private')
                      }}
                    </span>
                  </div>
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
              <p class="text-primary/60 text-sm">{{ $t('playlist.dialog.empty') }}</p>
            </div>
          </div>
        </div>
      </div>
    </Transition>
  </div>
</template>

<style scoped>
.brand-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 3rem;
  height: 3rem;
  border-radius: 1rem;
  background: linear-gradient(135deg, rgba(8, 17, 28, 0.92), rgba(31, 124, 255, 0.92));
  box-shadow: 0 14px 26px rgba(31, 124, 255, 0.18);
}

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
