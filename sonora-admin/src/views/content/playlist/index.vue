<script setup lang="ts">
import { onMounted, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import ImageUploadCropper from "@/components/ImageUploadCropper.vue";
import {
  Delete,
  Edit,
  Plus,
  RefreshRight,
  Search
} from "@element-plus/icons-vue";
import {
  batchDeletePlaylists,
  createPlaylist,
  deletePlaylist,
  getPlaylistById,
  getPlaylistPage,
  getPlaylistPublisherOptions,
  togglePlaylistStatus,
  updatePlaylist,
  type PlaylistPublisherOption,
  type PlaylistItem,
  type PlaylistPayload
} from "@/api/playlist";
import { getSongPage, type SongItem } from "@/api/song";
import { UserFilled } from "@element-plus/icons-vue";

const DEFAULT_COVER = "/default-cover.svg";

const loading = ref(false);
const list = ref<PlaylistItem[]>([]);
const total = ref(0);
const pageNum = ref(1);
const pageSize = ref(20);
const selectedRows = ref<PlaylistItem[]>([]);
const keyword = ref("");
const statusFilter = ref<number | undefined>();
const selectedPublisherId = ref<number | null>(null);
const publishers = ref<PlaylistPublisherOption[]>([]);
const publisherFilterPageNum = ref(1);
const publisherFilterPageSize = 40;
const publisherFilterTotal = ref(0);
const publisherFilterLoading = ref(false);

const dialogVisible = ref(false);
const isEdit = ref(false);
const submitting = ref(false);
const form = ref<PlaylistPayload>({ name: "", status: 1, songIds: [] });
const editingId = ref<number | null>(null);
const songOptions = ref<SongItem[]>([]);
const optionsLoading = ref(false);

function loadData() {
  loading.value = true;
  const params: Record<string, unknown> = {
    pageNum: pageNum.value,
    pageSize: pageSize.value,
    keyword: keyword.value,
    status: statusFilter.value
  };
  if (selectedPublisherId.value !== null) {
    params.userId = selectedPublisherId.value;
  }
  getPlaylistPage(params)
    .then(res => {
      list.value = res.data.list || [];
      total.value = res.data.total || 0;
    })
    .finally(() => {
      loading.value = false;
    });
}

function onSearch() {
  pageNum.value = 1;
  loadData();
}

function onReset() {
  keyword.value = "";
  statusFilter.value = undefined;
  selectedPublisherId.value = null;
  pageNum.value = 1;
  loadData();
}

function onPageChange(page: number) {
  pageNum.value = page;
  loadData();
}

function mergePublishers(current: PlaylistPublisherOption[], incoming: PlaylistPublisherOption[]) {
  const map = new Map<number, PlaylistPublisherOption>();
  current.forEach(item => map.set(item.id, item));
  incoming.forEach(item => map.set(item.id, item));
  return Array.from(map.values()).sort((a, b) => a.id - b.id);
}

async function loadPublishers() {
  if (publisherFilterLoading.value) return;
  if (publishers.value.length >= publisherFilterTotal.value && publisherFilterTotal.value > 0) return;
  publisherFilterLoading.value = true;
  try {
    const res = await getPlaylistPublisherOptions({
      pageNum: publisherFilterPageNum.value,
      pageSize: publisherFilterPageSize
    });
    const rows = res.data?.list || [];
    publishers.value = mergePublishers(publishers.value, rows);
    publisherFilterTotal.value = res.data?.total || 0;
    publisherFilterPageNum.value += 1;
  } finally {
    publisherFilterLoading.value = false;
  }
}

function selectPublisher(userId: number | null) {
  selectedPublisherId.value = userId;
  pageNum.value = 1;
  loadData();
}

function onPublisherFilterScroll(event: Event) {
  const target = event.currentTarget as HTMLElement;
  if (target.scrollHeight - target.scrollTop - target.clientHeight < 80) {
    loadPublishers();
  }
}

function loadSongOptions() {
  optionsLoading.value = true;
  getSongPage({ pageNum: 1, pageSize: 999, status: 1 })
    .then(res => {
      songOptions.value = res.data.list || [];
    })
    .finally(() => {
      optionsLoading.value = false;
    });
}

function openCreate() {
  isEdit.value = false;
  editingId.value = null;
  form.value = {
    name: "",
    cover: "",
    description: "",
    tags: "",
    status: 1,
    songIds: []
  };
  dialogVisible.value = true;
  loadSongOptions();
}

function openEdit(row: PlaylistItem) {
  isEdit.value = true;
  editingId.value = row.id;
  dialogVisible.value = true;
  loadSongOptions();
  getPlaylistById(row.id).then(res => {
    const data = res.data;
    form.value = {
      name: data.name,
      cover: data.cover || "",
      description: data.description || "",
      tags: data.tags || "",
      status: data.status,
      songIds: data.songIds || []
    };
  });
}

function handleSubmit() {
  if (!form.value.name?.trim()) {
    ElMessage.warning("请输入歌单名称");
    return;
  }

  submitting.value = true;
  const request = isEdit.value
    ? updatePlaylist(editingId.value!, form.value)
    : createPlaylist(form.value);

  request
    .then(() => {
      ElMessage.success(isEdit.value ? "修改成功" : "新增成功");
      dialogVisible.value = false;
      loadData();
    })
    .finally(() => {
      submitting.value = false;
    });
}

function handleDelete(row: PlaylistItem) {
  ElMessageBox.confirm(`确定删除「${row.name}」？`, "提示", { type: "warning" })
    .then(() => deletePlaylist(row.id))
    .then(() => {
      ElMessage.success("已删除");
      loadData();
    });
}

function getErrorMessage(error: any, fallback = "操作失败") {
  return error?.response?.data?.message || error?.message || fallback;
}

async function handleBatchDelete() {
  if (!selectedRows.value.length) {
    ElMessage.warning("请先选择要删除的歌单");
    return;
  }
  const names = selectedRows.value.map(item => item.name).join("、");
  try {
    await ElMessageBox.confirm(`确定批量删除 ${selectedRows.value.length} 个歌单？`, "批量删除", {
      type: "warning"
    });
    const res = await batchDeletePlaylists(selectedRows.value.map(item => item.id));
    if (res.code !== 200) {
      ElMessage.error((res as any).message || "批量删除失败");
      return;
    }
    const deletedNames = (res.data.deleted || []).map(item => item.name).join("、") || names;
    ElMessage.success(`成功删除：${deletedNames}`);
    selectedRows.value = [];
    loadData();
  } catch (error: any) {
    if (error === "cancel" || error === "close") return;
    ElMessage.error(getErrorMessage(error, "批量删除失败"));
  }
}

function handleToggleStatus(row: PlaylistItem) {
  const newStatus = row.status === 1 ? 0 : 1;
  togglePlaylistStatus(row.id, newStatus).then(() => {
    row.status = newStatus;
    ElMessage.success("状态已更新");
  });
}

function coverOf(cover?: string) {
  return cover || DEFAULT_COVER;
}

function firstSelectedSongCover() {
  const firstSongId = form.value.songIds?.[0];
  if (!firstSongId) {
    return DEFAULT_COVER;
  }
  return songOptions.value.find(song => song.id === firstSongId)?.cover || DEFAULT_COVER;
}

function publisherOf(row: PlaylistItem) {
  return row.publisher || row.publisherName || "-";
}

function showDescription(row: PlaylistItem) {
  ElMessageBox.alert(row.description || "暂无简介", `${row.name} 简介`, {
    confirmButtonText: "知道了"
  });
}

function onSelectionChange(rows: PlaylistItem[]) {
  selectedRows.value = rows;
}

onMounted(() => {
  loadPublishers();
  loadData();
});
</script>

<template>
  <div class="p-4">
    <h2 class="mb-4 text-xl font-bold">歌单管理</h2>

    <div class="flex gap-4">
      <aside class="w-60 shrink-0 rounded-md border border-gray-200 bg-white p-3">
        <div class="mb-3 flex items-center gap-2 text-sm font-semibold text-gray-700">
          <el-icon><UserFilled /></el-icon>
          发布者分类
        </div>
        <div class="publisher-filter-scroll" @scroll="onPublisherFilterScroll">
          <div
            class="publisher-filter-item"
            :class="{ active: selectedPublisherId === null }"
            @click="selectPublisher(null)"
          >
            <span class="truncate">全部</span>
          </div>
          <div
            v-for="publisher in publishers"
            :key="publisher.id"
            class="publisher-filter-item"
            :class="{ active: selectedPublisherId === publisher.id }"
            @click="selectPublisher(publisher.id)"
          >
            <span class="mr-2 w-8 shrink-0 text-xs text-gray-400">#{{ publisher.id }}</span>
            <span class="truncate">{{ publisher.name }}</span>
          </div>
          <div v-if="publisherFilterLoading" class="publisher-filter-loading">加载中...</div>
          <div
            v-else-if="publishers.length >= publisherFilterTotal && publisherFilterTotal > 0"
            class="publisher-filter-loading"
          >
            已加载全部发布者
          </div>
        </div>
      </aside>

      <section class="min-w-0 flex-1">
        <div class="mb-4 flex flex-wrap gap-3">
          <el-input
            v-model="keyword"
            clearable
            placeholder="搜索歌单名称"
            style="width: 240px"
            @keyup.enter="onSearch"
          />
          <el-select
            v-model="statusFilter"
            clearable
            placeholder="状态"
            style="width: 120px"
          >
            <el-option label="公开" :value="1" />
            <el-option label="私有" :value="0" />
          </el-select>
          <el-button type="primary" :icon="Search" @click="onSearch">搜索</el-button>
          <el-button :icon="RefreshRight" @click="onReset">重置</el-button>
          <el-button :icon="RefreshRight" @click="loadData">刷新</el-button>
          <el-button type="success" :icon="Plus" @click="openCreate">新增歌单</el-button>
          <el-button type="danger" :icon="Delete" @click="handleBatchDelete">批量删除</el-button>
        </div>

        <el-table v-loading="loading" :data="list" border stripe @selection-change="onSelectionChange">
          <el-table-column type="selection" width="48" fixed="left" />
          <el-table-column prop="id" label="歌单编号" width="100" />
          <el-table-column label="封面" width="90">
            <template #default="{ row }">
              <el-image
                :src="coverOf(row.cover)"
                fit="cover"
                class="playlist-cover"
                :preview-src-list="[coverOf(row.cover)]"
                preview-teleported
              />
            </template>
          </el-table-column>
          <el-table-column prop="name" label="歌单名" min-width="180" show-overflow-tooltip />
          <el-table-column label="发布者" min-width="130" show-overflow-tooltip>
            <template #default="{ row }">{{ publisherOf(row) }}</template>
          </el-table-column>
          <el-table-column label="简介" width="100">
            <template #default="{ row }">
              <el-button
                v-if="row.description"
                link
                type="primary"
                @click="showDescription(row)"
              >
                查看详情
              </el-button>
              <span v-else class="text-gray-400">暂无</span>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="120">
            <template #default="{ row }">
              <el-switch
                :model-value="row.status === 1"
                inline-prompt
                active-text="公开"
                inactive-text="私有"
                @change="handleToggleStatus(row)"
              />
            </template>
          </el-table-column>
          <el-table-column label="操作" width="150" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" :icon="Edit" @click="openEdit(row)">编辑</el-button>
              <el-button link type="danger" :icon="Delete" @click="handleDelete(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>

        <div class="mt-4 flex justify-end">
          <el-pagination
            v-model:current-page="pageNum"
            :page-size="pageSize"
            :total="total"
            layout="total, prev, pager, next"
            @current-change="onPageChange"
          />
        </div>
      </section>
    </div>

    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? '编辑歌单' : '新增歌单'"
      width="640px"
      destroy-on-close
    >
      <el-form :model="form" label-width="86px">
        <el-form-item label="歌单名称" required>
          <el-input v-model="form.name" placeholder="歌单名称" />
        </el-form-item>
        <el-form-item label="封面">
          <ImageUploadCropper
            v-model="form.cover"
            :fallback-src="firstSelectedSongCover()"
            dir="playlist-cover"
            title="修改歌单封面"
            mask-text="修改封面"
            crop-title="裁剪歌单封面"
            confirm-text="使用封面"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio-button :label="1">公开</el-radio-button>
            <el-radio-button :label="0">私有</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="歌曲">
          <el-select
            v-model="form.songIds"
            multiple
            filterable
            clearable
            collapse-tags
            collapse-tags-tooltip
            :loading="optionsLoading"
            placeholder="选择歌曲"
            style="width: 100%"
          >
            <el-option
              v-for="song in songOptions"
              :key="song.id"
              :label="song.name"
              :value="song.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="描述">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="4"
            placeholder="歌单描述"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.playlist-cover {
  width: 48px;
  height: 48px;
  border-radius: 8px;
}

.publisher-filter-scroll {
  height: calc(100vh - 220px);
  overflow-y: auto;
}

.publisher-filter-loading {
  padding: 10px 0;
  color: #909399;
  font-size: 12px;
  text-align: center;
}

.publisher-filter-item {
  display: flex;
  align-items: center;
  height: 36px;
  padding: 0 10px;
  margin-bottom: 4px;
  color: #606266;
  border-radius: 6px;
  cursor: pointer;
  transition:
    background-color 0.18s ease,
    color 0.18s ease;
}

.publisher-filter-item:hover {
  color: #1f7cff;
  background-color: #ecf5ff;
}

.publisher-filter-item.active {
  color: #fff;
  background-color: #1f7cff;
}

.publisher-filter-item.active .text-gray-400 {
  color: rgb(219 234 254);
}
</style>
