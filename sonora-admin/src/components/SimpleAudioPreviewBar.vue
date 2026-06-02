<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from "vue";
import { VideoPause, VideoPlay } from "@element-plus/icons-vue";

const DEFAULT_COVER = "/default-cover.svg";

const props = withDefaults(
  defineProps<{
    source?: string;
    title?: string;
    subtitle?: string;
    cover?: string;
    autoPlay?: boolean;
  }>(),
  {
    source: "",
    title: "未选择歌曲",
    subtitle: "",
    cover: "/default-cover.svg",
    autoPlay: false
  }
);

const audioRef = ref<HTMLAudioElement | null>(null);
const duration = ref(0);
const currentTime = ref(0);
const playing = ref(false);
const seeking = ref(false);
const coverSrc = ref(props.cover || DEFAULT_COVER);

const progress = computed({
  get: () => {
    if (!duration.value) return 0;
    return Number(((currentTime.value / duration.value) * 100).toFixed(2));
  },
  set: value => {
    if (!duration.value) return;
    currentTime.value = (value / 100) * duration.value;
  }
});

const hasSource = computed(() => Boolean(props.source));

function resetPlayer() {
  currentTime.value = 0;
  duration.value = 0;
  playing.value = false;
}

function stopPlayback() {
  if (!audioRef.value) return;
  audioRef.value.pause();
  audioRef.value.currentTime = 0;
  resetPlayer();
}

function syncFromAudio() {
  if (!audioRef.value) return;
  currentTime.value = audioRef.value.currentTime || 0;
  duration.value = Number.isFinite(audioRef.value.duration) ? audioRef.value.duration : 0;
  playing.value = !audioRef.value.paused;
}

async function playAudio() {
  if (!audioRef.value || !props.source) return;
  try {
    await audioRef.value.play();
    playing.value = true;
  } catch {
    playing.value = false;
  }
}

function togglePlay() {
  if (!audioRef.value || !props.source) return;
  if (audioRef.value.paused) {
    playAudio();
    return;
  }
  audioRef.value.pause();
  playing.value = false;
}

function onLoadedMetadata() {
  syncFromAudio();
  if (props.autoPlay) {
    playAudio();
  }
}

function onTimeUpdate() {
  if (seeking.value) return;
  syncFromAudio();
}

function onPause() {
  playing.value = false;
}

function onPlay() {
  playing.value = true;
}

function onEnded() {
  if (!audioRef.value) return;
  audioRef.value.currentTime = 0;
  currentTime.value = 0;
  playing.value = false;
}

function onRangeInput(event: Event) {
  const target = event.target as HTMLInputElement;
  const nextValue = Number(target.value);
  progress.value = nextValue;
  seeking.value = true;
}

function onRangeChange(event: Event) {
  const target = event.target as HTMLInputElement;
  const nextValue = Number(target.value);
  if (audioRef.value && duration.value) {
    audioRef.value.currentTime = (nextValue / 100) * duration.value;
  }
  seeking.value = false;
}

function onCoverError() {
  coverSrc.value = DEFAULT_COVER;
}

function formatTime(value: number) {
  if (!Number.isFinite(value) || value <= 0) return "00:00";
  const minutes = Math.floor(value / 60);
  const seconds = Math.floor(value % 60);
  return `${String(minutes).padStart(2, "0")}:${String(seconds).padStart(2, "0")}`;
}

watch(
  () => props.source,
  async () => {
    if (!audioRef.value) return;
    stopPlayback();
    audioRef.value.load();
    if (props.source && props.autoPlay) {
      await playAudio();
    }
  }
);

watch(
  () => props.cover,
  value => {
    coverSrc.value = value || DEFAULT_COVER;
  },
  { immediate: true }
);

onBeforeUnmount(() => {
  if (!audioRef.value) return;
  audioRef.value.pause();
  audioRef.value.src = "";
});
</script>

<template>
  <div class="preview-bar" :class="{ 'is-empty': !hasSource }">
    <div class="preview-bar__cover">
      <img :src="coverSrc" alt="cover" @error="onCoverError" />
    </div>
    <div class="preview-bar__main">
      <div class="preview-bar__meta">
        <div class="preview-bar__title">{{ title }}</div>
        <div v-if="subtitle" class="preview-bar__subtitle">{{ subtitle }}</div>
        <div v-else class="preview-bar__subtitle preview-bar__subtitle--muted">
          选择歌曲后可在这里预览
        </div>
      </div>
      <div class="preview-bar__controls">
        <el-button
          class="preview-bar__play"
          circle
          type="primary"
          :disabled="!hasSource"
          @click="togglePlay"
        >
          <el-icon size="18">
            <component :is="playing ? VideoPause : VideoPlay" />
          </el-icon>
        </el-button>
        <div class="preview-bar__progress">
          <span class="preview-bar__time">{{ formatTime(currentTime) }}</span>
          <div class="preview-bar__track-wrap" :class="{ 'is-disabled': !hasSource }">
            <div class="preview-bar__track">
              <div class="preview-bar__track-fill" :style="{ width: `${progress}%` }" />
            </div>
            <input
              class="preview-bar__range"
              type="range"
              min="0"
              max="100"
              step="0.1"
              :value="progress"
              :disabled="!hasSource"
              @input="onRangeInput"
              @change="onRangeChange"
            />
          </div>
          <span class="preview-bar__time">{{ formatTime(duration) }}</span>
        </div>
      </div>
    </div>
    <audio
      ref="audioRef"
      :src="source"
      preload="metadata"
      @loadedmetadata="onLoadedMetadata"
      @timeupdate="onTimeUpdate"
      @pause="onPause"
      @play="onPlay"
      @ended="onEnded"
    />
  </div>
</template>

<style scoped>
.preview-bar {
  display: flex;
  gap: 16px;
  align-items: center;
  padding: 14px 16px;
  border: 1px solid var(--el-border-color-light);
  border-radius: 14px;
  background: var(--el-bg-color-overlay);
  box-shadow: 0 10px 24px rgb(8 17 28 / 6%);
}

.preview-bar.is-empty {
  background: var(--el-fill-color-blank);
}

.preview-bar__cover {
  flex: 0 0 60px;
  width: 60px;
  height: 60px;
  overflow: hidden;
  border-radius: 12px;
  background: var(--el-fill-color-light);
}

.preview-bar__cover img {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.preview-bar__main {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 10px;
  min-width: 0;
}

.preview-bar__meta {
  min-width: 0;
}

.preview-bar__title {
  overflow: hidden;
  color: var(--el-text-color-primary);
  font-size: 15px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.preview-bar__subtitle {
  margin-top: 4px;
  overflow: hidden;
  color: var(--el-text-color-regular);
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.preview-bar__subtitle--muted {
  color: var(--el-text-color-secondary);
}

.preview-bar__controls {
  display: flex;
  gap: 14px;
  align-items: center;
}

.preview-bar__play {
  flex: 0 0 auto;
  width: 36px;
  height: 36px;
  padding: 0;
}

.preview-bar__progress {
  display: grid;
  flex: 1;
  grid-template-columns: 48px minmax(0, 1fr) 48px;
  gap: 10px;
  align-items: center;
  min-width: 0;
}

.preview-bar__time {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  text-align: center;
  font-variant-numeric: tabular-nums;
}

.preview-bar__track-wrap {
  position: relative;
  display: flex;
  align-items: center;
  height: 18px;
}

.preview-bar__track {
  position: absolute;
  top: 50%;
  right: 0;
  left: 0;
  height: 4px;
  overflow: hidden;
  border-radius: 999px;
  background: var(--el-fill-color);
  transform: translateY(-50%);
}

.preview-bar__track-fill {
  height: 100%;
  border-radius: inherit;
  background: var(--el-color-primary);
}

.preview-bar__range {
  position: relative;
  z-index: 1;
  width: 100%;
  height: 18px;
  margin: 0;
  appearance: none;
  cursor: pointer;
  background: transparent;
}

.preview-bar__track-wrap.is-disabled .preview-bar__range {
  cursor: not-allowed;
}

.preview-bar__range::-webkit-slider-runnable-track {
  height: 4px;
  background: transparent;
}

.preview-bar__range::-webkit-slider-thumb {
  width: 11px;
  height: 11px;
  margin-top: -4px;
  appearance: none;
  border: 2px solid #fff;
  border-radius: 50%;
  background: var(--el-color-primary);
  box-shadow: 0 2px 8px rgb(31 124 255 / 28%);
}

.preview-bar__range::-moz-range-track {
  height: 4px;
  background: transparent;
}

.preview-bar__range::-moz-range-thumb {
  width: 11px;
  height: 11px;
  border: 2px solid #fff;
  border-radius: 50%;
  background: var(--el-color-primary);
  box-shadow: 0 2px 8px rgb(31 124 255 / 28%);
}
</style>
