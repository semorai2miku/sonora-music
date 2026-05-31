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
const stencilSize = computed(() => {
  if (props.stencilWidth && props.stencilHeight) {
    return { width: props.stencilWidth, height: props.stencilHeight };
  }
  if (props.shape === "rectangle") {
    const width = 360;
    return {
      width,
      height: Math.round((width * outputHeight.value) / outputWidth.value)
    };
  }
  return { width: 280, height: 280 };
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
        :stencil-size="stencilSize"
        :canvas="{
          width: outputWidth,
          height: outputHeight,
          imageSmoothingEnabled: true,
          imageSmoothingQuality: 'high'
        }"
        :resize-image="{ touch: true, wheel: { ratio: 0.08 }, adjustStencil: false }"
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
