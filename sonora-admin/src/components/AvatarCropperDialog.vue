<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from "vue";
import { CircleStencil, Cropper, RectangleStencil } from "vue-advanced-cropper";
import "vue-advanced-cropper/dist/style.css";

const props = defineProps<{
  modelValue: boolean;
  file: File | null;
  shape?: "circle" | "square" | "rectangle";
  title?: string;
  confirmText?: string;
  hint?: string;
  outputFileName?: string;
  outputWidth?: number;
  outputHeight?: number;
  stencilWidth?: number;
  stencilHeight?: number;
}>();

const emit = defineEmits<{
  (e: "update:modelValue", value: boolean): void;
  (e: "confirm", file: File): void;
}>();

const cropperRef = ref<any>(null);
const imageSrc = ref("");
const objectUrl = ref("");

const visible = computed({
  get: () => props.modelValue,
  set: value => emit("update:modelValue", value)
});
const stencilComponent = computed(() =>
  props.shape === "circle" ? CircleStencil : RectangleStencil
);
const dialogTitle = computed(() => props.title || "裁剪头像");
const confirmText = computed(() => props.confirmText || "使用头像");
const hintText = computed(
  () => props.hint || "拖动图片调整位置，滚轮或双指缩放头像区域。"
);
const outputFileName = computed(() => props.outputFileName || "avatar.png");
const outputWidth = computed(() => props.outputWidth || 600);
const outputHeight = computed(() => props.outputHeight || outputWidth.value);
const aspectRatio = computed(() => {
  if (props.shape === "circle" || props.shape === "square") {
    return 1;
  }
  return outputWidth.value / outputHeight.value;
});
const stencilProps = computed(() => ({
  aspectRatio: aspectRatio.value,
  movable: true,
  resizable: true
}));

function defaultStencilSize({
  boundaries
}: {
  boundaries: { width: number; height: number };
}) {
  const maxWidth = boundaries.width * 0.82;
  const maxHeight = boundaries.height * 0.82;
  let width = props.stencilWidth || maxWidth;
  let height = props.stencilHeight || width / aspectRatio.value;

  if (!props.stencilWidth && !props.stencilHeight) {
    if (height > maxHeight) {
      height = maxHeight;
      width = height * aspectRatio.value;
    }
    if (width > maxWidth) {
      width = maxWidth;
      height = width / aspectRatio.value;
    }
  } else {
    if (width > maxWidth) {
      width = maxWidth;
      height = width / aspectRatio.value;
    }
    if (height > maxHeight) {
      height = maxHeight;
      width = height * aspectRatio.value;
    }
  }

  return {
    width: Math.max(120, Math.round(width)),
    height: Math.max(120, Math.round(height))
  };
}

function defaultStencilPosition({
  coordinates,
  imageSize
}: {
  coordinates: { width: number; height: number };
  imageSize: { width: number; height: number };
}) {
  return {
    left: Math.round((imageSize.width - coordinates.width) / 2),
    top: Math.round((imageSize.height - coordinates.height) / 2)
  };
}

function defaultBoundaries({
  cropper,
  imageSize
}: {
  cropper: { clientWidth: number; clientHeight: number };
  imageSize: { width: number; height: number };
}) {
  const padding = 24;
  const availableWidth = Math.max(240, cropper.clientWidth - padding * 2);
  const availableHeight = Math.max(240, cropper.clientHeight - padding * 2);
  const scale = Math.min(availableWidth / imageSize.width, availableHeight / imageSize.height, 1);
  return {
    width: Math.round(imageSize.width * scale),
    height: Math.round(imageSize.height * scale)
  };
}

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
    emit("confirm", new File([blob], outputFileName.value, { type: "image/png" }));
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
  <el-dialog v-model="visible" :title="dialogTitle" width="460px" destroy-on-close>
    <div class="avatar-cropper">
      <Cropper
        v-if="imageSrc"
        ref="cropperRef"
        class="avatar-cropper__cropper"
        :src="imageSrc"
        :stencil-component="stencilComponent"
        :stencil-props="stencilProps"
        :default-size="defaultStencilSize"
        :default-position="defaultStencilPosition"
        :default-boundaries="defaultBoundaries"
        :canvas="{
          width: outputWidth,
          height: outputHeight,
          imageSmoothingEnabled: true,
          imageSmoothingQuality: 'high',
          fillColor: '#ffffff'
        }"
        :resize-image="{ touch: true, wheel: { ratio: 0.08 }, adjustStencil: true }"
        :move-image="{ touch: true, mouse: true }"
        image-restriction="none"
        :auto-zoom="false"
      />
      <p class="avatar-cropper__hint">{{ hintText }}</p>
    </div>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :disabled="!imageSrc" @click="confirmCrop">{{ confirmText }}</el-button>
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
