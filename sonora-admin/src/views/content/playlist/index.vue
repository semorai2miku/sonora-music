<script setup lang="ts">
import { onMounted, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import {
  Collection,
  Delete,
  Edit,
  Plus,
  RefreshRight,
  Search
} from "@element-plus/icons-vue";
import {
  createPlaylist,
  deletePlaylist,
  getPlaylistById,
  getPlaylistPage,
  togglePlaylistStatus,
  updatePlaylist,
  type PlaylistItem,
  type PlaylistPayload
} from "@/api/playlist";
import { getSongPage, type SongItem } from "@/api/song";

const loading = ref(false);
const list = ref<PlaylistItem[]>([]);
const total = ref(0);
const pageNum = ref(1);
const pageSize = ref(20);
const keyword = ref("");
const statusFilter = ref<number | undefined>();

const dialogVisible = ref(false);
const isEdit = ref(false);
const submitting = ref(false);
const form = ref<PlaylistPayload>({ name: "", status: 1, songIds: [] });
const editingId = ref<number | null>(null);
const songOptions = ref<SongItem[]>([]);
const optionsLoading = ref(false);

function loadData() {
  loading.value = true;
  getPlaylistPage({
    pageNum: pageNum.value,
    pageSize: pageSize.value,
    keyword: keyword.value,
    status: statusFilter.value
  })
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

function onPageChange(page: number) {
  pageNum.value = page;
  loadData();
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
  form.value = { name: "", cover: "", description: "", tags: "", status: 1, songIds: [] };
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

function handleToggleStatus(row: PlaylistItem) {
  const newStatus = row.status === 1 ? 0 : 1;
  togglePlaylistStatus(row.id, newStatus).then(() => {
    row.status = newStatus;
    ElMessage.success("状态已更新");
  });
}

function formatCount(value?: number) {
  return typeof value === "number" ? value : 0;
}

onMounted(loadData);
</script>

<template>
  <div class="p-4">
    <h2 class="mb-4 text-xl font-bold">歌单管理</h2>

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
        <el-option label="私密" :value="0" />
      </el-select>
      <el-button type="primary" :icon="Search" @click="onSearch">搜索</el-button>
      <el-button :icon="RefreshRight" @click="loadData">刷新</el-button>
      <el-button type="success" :icon="Plus" @click="openCreate">新增歌单</el-button>
    </div>

    <el-table v-loading="loading" :data="list" border stripe>
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column label="封面" width="90">
        <template #default="{ row }">
          <el-image
            v-if="row.cover"
            :src="row.cover"
            fit="cover"
            style="width: 48px; height: 48px; border-radius: 6px"
          />
          <el-icon v-else size="24"><Collection /></el-icon>
        </template>
      </el-table-column>
      <el-table-column prop="name" label="歌单名称" min-width="180" show-overflow-tooltip />
      <el-table-column prop="tags" label="标签" min-width="140" show-overflow-tooltip />
      <el-table-column label="歌曲数" width="90">
        <template #default="{ row }">{{ formatCount(row.songCount) }}</template>
      </el-table-column>
      <el-table-column label="播放" width="90">
        <template #default="{ row }">{{ formatCount(row.playCount) }}</template>
      </el-table-column>
      <el-table-column label="收藏" width="90">
        <template #default="{ row }">{{ formatCount(row.collectCount) }}</template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-switch :model-value="row.status === 1" @change="handleToggleStatus(row)" />
        </template>
      </el-table-column>
      <el-table-column prop="createdAt" label="创建时间" width="170" />
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
        <el-form-item label="封面 URL">
          <el-input v-model="form.cover" placeholder="https://..." />
        </el-form-item>
        <el-form-item label="标签">
          <el-input v-model="form.tags" placeholder="流行,日语,精选" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio-button :label="1">公开</el-radio-button>
            <el-radio-button :label="0">私密</el-radio-button>
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
