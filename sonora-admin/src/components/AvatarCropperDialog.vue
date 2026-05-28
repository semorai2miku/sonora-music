<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from "vue";
import { CircleStencil, Cropper } from "vue-advanced-cropper";
import "vue-advanced-cropper/dist/style.css";

const props = defineProps<{
  modelValue: boolean;
  file: File | null;
}>();

const emit = defineEmits<{
  (e: "update:modelValue", value: boolean): void;
  (e: "confirm", file: File): void;
}>();

const OUTPUT_SIZE = 600;

const cropperRef = ref<any>(null);
const imageSrc = ref("");
const objectUrl = ref("");

const visible = computed({
  get: () => props.modelValue,
  set: value => emit("update:modelValue", value)
});

function releaseUrl() {
  if (objectUrl.value) {
    URL.revokeObjectURL(objectUrl.value);
    objectUrl.value = "";
  }
}

function loadFile(file: File | null) {
  releaseUrl();
  imageSrc.value = "";
  if (!file) return;
  objectUrl.value = URL.createObjectURL(file);
  imageSrc.value = objectUrl.value;
}

function confirmCrop() {
  const result = cropperRef.value?.getResult?.();
  const canvas = result?.canvas as HTMLCanvasElement | undefined;
  if (!canvas) return;
  canvas.toBlob(blob => {
    if (!blob) return;
    emit("confirm", new File([blob], "avatar.png", { type: "image/png" }));
    visible.value = false;
  }, "image/png");
}

watch(
  () => props.file,
  file => loadFile(file),
  { immediate: true }
);

onBeforeUnmount(releaseUrl);
</script>

<template>
  <el-dialog v-model="visible" title="裁剪头像" width="460px" destroy-on-close>
    <div class="avatar-cropper">
      <Cropper
        v-if="imageSrc"
        ref="cropperRef"
        class="avatar-cropper__cropper"
        :src="imageSrc"
        :stencil-component="CircleStencil"
        :stencil-size="{ width: 280, height: 280 }"
        :canvas="{
          width: OUTPUT_SIZE,
          height: OUTPUT_SIZE,
          imageSmoothingEnabled: true,
          imageSmoothingQuality: 'high'
        }"
        :resize-image="{ touch: true, wheel: { ratio: 0.08 }, adjustStencil: false }"
        :move-image="{ touch: true, mouse: true }"
        image-restriction="stencil"
        :auto-zoom="true"
      />
      <p class="avatar-cropper__hint">拖动图片调整位置，滚轮或双指缩放头像区域。</p>
    </div>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :disabled="!imageSrc" @click="confirmCrop">使用头像</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.avatar-cropper {
  display: flex;
  flex-direction: column;
  gap: 12px;
  align-items: center;
}

.avatar-cropper__cropper {
  width: 360px;
  height: 360px;
  overflow: hidden;
  border-radius: 8px;
  background: #f5f7fb;
}

.avatar-cropper__hint {
  margin: 0;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
</style>
