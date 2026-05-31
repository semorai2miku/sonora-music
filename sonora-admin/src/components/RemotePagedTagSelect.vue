<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from "vue";

export type RemoteSelectOption = {
  id: number;
  name: string;
  meta?: string;
};

type FetchParams = {
  pageNum: number;
  pageSize: number;
  keyword?: string;
  ids?: string;
};

const props = withDefaults(
  defineProps<{
    modelValue?: number | number[] | null;
    fetchOptions: (params: FetchParams) => Promise<{ list: RemoteSelectOption[]; total: number }>;
    multiple?: boolean;
    placeholder?: string;
    pageSize?: number;
    disabled?: boolean;
    emptyText?: string;
  }>(),
  {
    modelValue: null,
    multiple: false,
    placeholder: "搜索选择",
    pageSize: 30,
    disabled: false,
    emptyText: "暂无匹配数据"
  }
);

const emit = defineEmits<{
  (e: "update:modelValue", value: number | number[] | null): void;
}>();

const rootRef = ref<HTMLElement | null>(null);
const inputRef = ref<HTMLInputElement | null>(null);
const open = ref(false);
const keyword = ref("");
const options = ref<RemoteSelectOption[]>([]);
const total = ref(0);
const pageNum = ref(1);
const loading = ref(false);
const cache = ref<Record<number, RemoteSelectOption>>({});
let searchTimer: ReturnType<typeof setTimeout> | null = null;
let requestSeq = 0;

const selectedIds = computed(() => {
  if (props.multiple) {
    return Array.isArray(props.modelValue) ? props.modelValue : [];
  }
  return typeof props.modelValue === "number" ? [props.modelValue] : [];
});

const selectedOptions = computed(() =>
  selectedIds.value.map(id => ({
    id,
    name: cache.value[id]?.name || `ID ${id}`
  }))
);

const visibleOptions = computed(() =>
  options.value.filter(option => !selectedIds.value.includes(option.id))
);

function mergeOptions(current: RemoteSelectOption[], incoming: RemoteSelectOption[]) {
  const map = new Map<number, RemoteSelectOption>();
  current.forEach(option => map.set(option.id, option));
  incoming.forEach(option => map.set(option.id, option));
  return Array.from(map.values()).sort((a, b) => a.id - b.id);
}

function cacheOptions(rows: RemoteSelectOption[]) {
  if (!rows.length) return;
  const next = { ...cache.value };
  rows.forEach(option => {
    next[option.id] = option;
  });
  cache.value = next;
}

async function loadOptions(reset = false) {
  if (props.disabled) return;
  if (loading.value && !reset) return;
  if (!reset && total.value > 0 && options.value.length >= total.value) return;
  const seq = ++requestSeq;
  loading.value = true;
  try {
    const currentPage = reset ? 1 : pageNum.value;
    const res = await props.fetchOptions({
      pageNum: currentPage,
      pageSize: props.pageSize,
      keyword: keyword.value.trim()
    });
    if (seq !== requestSeq) return;
    options.value = reset ? res.list : mergeOptions(options.value, res.list);
    total.value = res.total;
    pageNum.value = currentPage + 1;
    cacheOptions(res.list);
  } finally {
    if (seq === requestSeq) {
      loading.value = false;
    }
  }
}

async function resetOptions() {
  pageNum.value = 1;
  total.value = 0;
  options.value = [];
  await loadOptions(true);
}

function focusInput() {
  if (props.disabled) return;
  open.value = true;
  if (!options.value.length) {
    resetOptions();
  }
  nextTick(() => inputRef.value?.focus());
}

function onInput() {
  open.value = true;
  if (searchTimer) clearTimeout(searchTimer);
  searchTimer = setTimeout(resetOptions, 240);
}

function onScroll(event: Event) {
  const target = event.currentTarget as HTMLElement;
  if (target.scrollHeight - target.scrollTop - target.clientHeight < 72) {
    loadOptions();
  }
}

function selectOption(option: RemoteSelectOption) {
  cacheOptions([option]);
  if (props.multiple) {
    emit("update:modelValue", Array.from(new Set([...selectedIds.value, option.id])));
  } else {
    emit("update:modelValue", option.id);
    open.value = false;
  }
  keyword.value = "";
  resetOptions();
  nextTick(() => inputRef.value?.focus());
}

function removeOption(id: number) {
  if (props.multiple) {
    emit("update:modelValue", selectedIds.value.filter(item => item !== id));
  } else {
    emit("update:modelValue", null);
  }
  nextTick(() => inputRef.value?.focus());
}

function onBackspace() {
  if (keyword.value || !selectedIds.value.length) return;
  if (props.multiple) {
    emit("update:modelValue", selectedIds.value.slice(0, -1));
  } else {
    emit("update:modelValue", null);
  }
}

async function ensureSelectedOptions() {
  const missing = selectedIds.value.filter(id => !cache.value[id]);
  if (!missing.length) return;
  const res = await props.fetchOptions({
    pageNum: 1,
    pageSize: missing.length,
    ids: missing.join(",")
  });
  cacheOptions(res.list);
}

function handleDocumentClick(event: MouseEvent) {
  if (!rootRef.value?.contains(event.target as Node)) {
    open.value = false;
  }
}

watch(selectedIds, ensureSelectedOptions, { immediate: true });

onMounted(() => {
  document.addEventListener("click", handleDocumentClick);
});

onBeforeUnmount(() => {
  document.removeEventListener("click", handleDocumentClick);
  if (searchTimer) clearTimeout(searchTimer);
});
</script>

<template>
  <div ref="rootRef" class="remote-tag-select" :class="{ 'is-disabled': disabled }">
    <div class="remote-tag-select__control" @click="focusInput">
      <el-tag
        v-for="option in selectedOptions"
        :key="option.id"
        closable
        disable-transitions
        @close="removeOption(option.id)"
      >
        {{ option.name }}
      </el-tag>
      <input
        ref="inputRef"
        v-model="keyword"
        class="remote-tag-select__input"
        type="text"
        :placeholder="selectedOptions.length ? placeholder : placeholder"
        :disabled="disabled"
        @focus="focusInput"
        @input="onInput"
        @keydown.backspace="onBackspace"
      />
    </div>
    <div v-if="open" class="remote-tag-select__dropdown" @scroll="onScroll">
      <button
        v-for="option in visibleOptions"
        :key="option.id"
        class="remote-tag-select__option"
        type="button"
        @mousedown.prevent="selectOption(option)"
      >
        <span class="remote-tag-select__id">#{{ option.id }}</span>
        <span class="remote-tag-select__name">{{ option.name }}</span>
        <span v-if="option.meta" class="remote-tag-select__meta">{{ option.meta }}</span>
      </button>
      <div v-if="loading" class="remote-tag-select__empty">加载中...</div>
      <div v-else-if="!visibleOptions.length" class="remote-tag-select__empty">{{ emptyText }}</div>
      <div
        v-else-if="options.length >= total && total > 0"
        class="remote-tag-select__empty"
      >
        已加载全部匹配项
      </div>
    </div>
  </div>
</template>

<style scoped>
.remote-tag-select {
  position: relative;
  width: 100%;
}

.remote-tag-select__control {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  align-items: center;
  min-height: 34px;
  padding: 4px 8px;
  border: 1px solid var(--el-border-color);
  border-radius: 4px;
  background: var(--el-fill-color-blank);
  cursor: text;
}

.remote-tag-select__control:focus-within {
  border-color: var(--el-color-primary);
}

.remote-tag-select.is-disabled .remote-tag-select__control {
  background: var(--el-disabled-bg-color);
  cursor: not-allowed;
}

.remote-tag-select__input {
  flex: 1 1 120px;
  min-width: 120px;
  height: 24px;
  padding: 0;
  border: 0;
  outline: 0;
  color: var(--el-text-color-primary);
  background: transparent;
}

.remote-tag-select__dropdown {
  position: absolute;
  z-index: 2050;
  top: calc(100% + 6px);
  right: 0;
  left: 0;
  max-height: 236px;
  overflow-y: auto;
  border: 1px solid var(--el-border-color-light);
  border-radius: 6px;
  background: var(--el-bg-color);
  box-shadow: var(--el-box-shadow-light);
}

.remote-tag-select__option {
  display: flex;
  gap: 10px;
  align-items: center;
  width: 100%;
  height: 34px;
  padding: 0 12px;
  border: 0;
  color: var(--el-text-color-regular);
  text-align: left;
  background: transparent;
  cursor: pointer;
}

.remote-tag-select__option:hover {
  color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
}

.remote-tag-select__id {
  width: 54px;
  flex: 0 0 54px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.remote-tag-select__name {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.remote-tag-select__meta {
  margin-left: auto;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.remote-tag-select__empty {
  padding: 10px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  text-align: center;
}
</style>
