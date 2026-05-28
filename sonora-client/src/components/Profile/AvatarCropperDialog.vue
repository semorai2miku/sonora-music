<script setup lang="ts">
import { CircleStencil, Cropper } from 'vue-advanced-cropper'
import 'vue-advanced-cropper/dist/style.css'

const props = defineProps<{
  modelValue: boolean
  file: File | null
}>()

const emit = defineEmits<{
  (event: 'update:modelValue', value: boolean): void
  (event: 'confirm', file: File): void
}>()

const OUTPUT_SIZE = 600

const cropperRef = ref<any>(null)
const imageSrc = ref('')
const objectUrl = ref('')

const visible = computed({
  get: () => props.modelValue,
  set: value => emit('update:modelValue', value),
})

const releaseUrl = () => {
  if (objectUrl.value) {
    URL.revokeObjectURL(objectUrl.value)
    objectUrl.value = ''
  }
}

const loadFile = (file: File | null) => {
  releaseUrl()
  imageSrc.value = ''
  if (!file) return
  objectUrl.value = URL.createObjectURL(file)
  imageSrc.value = objectUrl.value
}

const confirmCrop = () => {
  const result = cropperRef.value?.getResult?.()
  const canvas = result?.canvas as HTMLCanvasElement | undefined
  if (!canvas) return
  canvas.toBlob(blob => {
    if (!blob) return
    emit('confirm', new File([blob], 'avatar.png', { type: 'image/png' }))
    visible.value = false
  }, 'image/png')
}

watch(
  () => props.file,
  file => loadFile(file),
  { immediate: true }
)

onBeforeUnmount(releaseUrl)
</script>

<template>
  <Teleport to="body">
    <Transition name="fade">
      <div v-if="visible" class="fixed inset-0 z-[60] flex items-center justify-center bg-black/70 p-4 backdrop-blur-sm">
        <div class="glass-container-strong w-full max-w-md p-5">
          <div class="mb-4 flex items-center justify-between">
            <h3 class="text-primary text-lg font-semibold">裁剪头像</h3>
            <button class="text-primary/60 hover:text-primary rounded-full p-2" @click="visible = false">
              <span class="icon-[mdi--close] h-5 w-5" />
            </button>
          </div>
          <Cropper
            v-if="imageSrc"
            ref="cropperRef"
            class="avatar-cropper"
            :src="imageSrc"
            :stencil-component="CircleStencil"
            :stencil-size="{ width: 280, height: 280 }"
            :canvas="{
              width: OUTPUT_SIZE,
              height: OUTPUT_SIZE,
              imageSmoothingEnabled: true,
              imageSmoothingQuality: 'high',
            }"
            :resize-image="{ touch: true, wheel: { ratio: 0.08 }, adjustStencil: false }"
            :move-image="{ touch: true, mouse: true }"
            image-restriction="stencil"
            :auto-zoom="true"
          />
          <p class="text-primary/55 mt-3 text-center text-xs">拖动图片调整位置，滚轮或双指缩放头像区域。</p>
          <div class="mt-5 flex justify-end gap-3">
            <button class="text-primary rounded-xl bg-white/10 px-4 py-2 text-sm hover:bg-white/15" @click="visible = false">
              取消
            </button>
            <button
              class="rounded-xl bg-pink-500 px-4 py-2 text-sm font-medium text-white hover:bg-pink-400 disabled:cursor-not-allowed disabled:opacity-60"
              :disabled="!imageSrc"
              @click="confirmCrop"
            >
              使用头像
            </button>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.avatar-cropper {
  width: min(360px, calc(100vw - 64px));
  height: min(360px, calc(100vw - 64px));
  overflow: hidden;
  border-radius: 18px;
  background: rgba(0, 0, 0, 0.26);
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
