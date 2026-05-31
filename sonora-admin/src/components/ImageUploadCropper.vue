<script setup lang="ts">
import { computed, ref } from "vue";
import { ElMessage } from "element-plus";
import { Upload } from "@element-plus/icons-vue";
import AvatarCropperDialog from "@/components/AvatarCropperDialog.vue";
import { http } from "@/utils/http";

type UploadResult = {
  code: number;
  message?: string;
  data?: {
    objectKey: string;
    url: string;
    fileName: string;
    fileSize: number;
  };
};

const props = withDefaults(
  defineProps<{
    modelValue?: string;
    fallbackSrc?: string;
    dir?: string;
    shape?: "circle" | "square" | "rectangle";
    size?: number;
    width?: number;
    height?: number;
    outputWidth?: number;
    outputHeight?: number;
    stencilWidth?: number;
    stencilHeight?: number;
    maxSizeMb?: number;
    title?: string;
    maskText?: string;
    cropTitle?: string;
    confirmText?: string;
    clearable?: boolean;
    disabled?: boolean;
  }>(),
  {
    modelValue: "",
    fallbackSrc: "/default-cover.svg",
    dir: "cover",
    shape: "square",
    size: 88,
    maxSizeMb: 8,
    title: "修改图片",
    maskText: "修改图片",
    cropTitle: "裁剪图片",
    confirmText: "使用图片",
    clearable: true,
    disabled: false
  }
);

const emit = defineEmits<{
  (e: "update:modelValue", value: string): void;
  (e: "uploaded", value: string): void;
}>();

const inputRef = ref<HTMLInputElement | null>(null);
const cropperVisible = ref(false);
const cropperFile = ref<File | null>(null);
const uploading = ref(false);

const displaySrc = computed(() => props.modelValue || props.fallbackSrc);
const styleVars = computed(() => ({
  "--image-upload-width": `${props.width || props.size}px`,
  "--image-upload-height": `${props.height || props.size}px`,
  "--image-upload-radius": props.shape === "circle" ? "50%" : "8px"
}));

function triggerUpload() {
  if (props.disabled || uploading.value) return;
  inputRef.value?.click();
}

function onFileChange(event: Event) {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  input.value = "";
  if (!file) return;
  if (!file.type.startsWith("image/")) {
    ElMessage.warning("请选择图片文件");
    return;
  }
  if (file.size > props.maxSizeMb * 1024 * 1024) {
    ElMessage.warning(`图片不能超过 ${props.maxSizeMb}MB`);
    return;
  }
  cropperFile.value = file;
  cropperVisible.value = true;
}

async function onCropConfirm(file: File) {
  uploading.value = true;
  try {
    const formData = new FormData();
    formData.append("file", file);
    formData.append("dir", props.dir);
    const res = await http.request<UploadResult>("post", "/api/admin/upload", {
      data: formData,
      headers: { "Content-Type": "multipart/form-data" }
    });
    if (res.code !== 200 || !res.data?.url) {
      ElMessage.error(res.message || "图片上传失败");
      return;
    }
    emit("update:modelValue", res.data.url);
    emit("uploaded", res.data.url);
    ElMessage.success("图片已上传");
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message || error?.message || "图片上传失败");
  } finally {
    uploading.value = false;
  }
}

function clearImage() {
  emit("update:modelValue", "");
}
</script>

<template>
  <div class="image-upload-cropper" :style="styleVars">
    <button
      class="image-upload-cropper__trigger"
      type="button"
      :disabled="disabled || uploading"
      :title="title"
      @click="triggerUpload"
    >
      <el-image class="image-upload-cropper__image" :src="displaySrc" fit="cover" />
      <span class="image-upload-cropper__mask">
        <el-icon><Upload /></el-icon>
        <span>{{ uploading ? "上传中" : maskText }}</span>
      </span>
    </button>
    <el-button
      v-if="clearable && modelValue"
      link
      type="primary"
      class="image-upload-cropper__clear"
      @click="clearImage"
    >
      恢复默认
    </el-button>
    <input
      ref="inputRef"
      class="image-upload-cropper__input"
      type="file"
      accept="image/*"
      @change="onFileChange"
    />
    <AvatarCropperDialog
      v-model="cropperVisible"
      :file="cropperFile"
      :shape="shape"
      :title="cropTitle"
      :confirm-text="confirmText"
      :output-width="outputWidth"
      :output-height="outputHeight"
      :stencil-width="stencilWidth"
      :stencil-height="stencilHeight"
      output-file-name="cover.png"
      hint="拖动图片调整位置，滚轮或双指缩放封面区域。"
      @confirm="onCropConfirm"
    />
  </div>
</template>

<style scoped>
.image-upload-cropper {
  display: inline-flex;
  gap: 12px;
  align-items: center;
}

.image-upload-cropper__trigger {
  position: relative;
  width: var(--image-upload-width);
  height: var(--image-upload-height);
  padding: 0;
  overflow: hidden;
  border: 1px solid var(--el-border-color);
  border-radius: var(--image-upload-radius);
  background: var(--el-fill-color-light);
  cursor: pointer;
}

.image-upload-cropper__trigger:disabled {
  cursor: wait;
}

.image-upload-cropper__image {
  width: 100%;
  height: 100%;
}

.image-upload-cropper__mask {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 12px;
  background: rgb(0 0 0 / 58%);
  opacity: 0;
  transition: opacity 0.18s ease;
}

.image-upload-cropper__trigger:hover .image-upload-cropper__mask {
  opacity: 1;
}

.image-upload-cropper__clear {
  align-self: center;
}

.image-upload-cropper__input {
  display: none;
}
</style>
