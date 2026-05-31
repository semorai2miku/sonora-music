<script setup lang="ts">
import { ref, onBeforeUnmount, onMounted } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import ImageUploadCropper from "@/components/ImageUploadCropper.vue";
import RemotePagedTagSelect from "@/components/RemotePagedTagSelect.vue";
import {
  batchDeleteAlbums,
  getAlbumPage,
  createAlbum,
  updateAlbum,
  deleteAlbum,
  type AlbumItem
} from "@/api/album";
import { getSongPage, type SongItem } from "@/api/song";
import { getArtistOptions, type ArtistItem } from "@/api/artist";
import { Delete, Plus, RefreshRight, Search, View, UserFilled } from "@element-plus/icons-vue";

const DEFAULT_COVER = "/default-cover.svg";

const loading = ref(false);
const list = ref<AlbumItem[]>([]);
const total = ref(0);
const pageNum = ref(1);
const pageSize = ref(20);
const selectedRows = ref<AlbumItem[]>([]);
const keyword = ref("");
const selectedArtistId = ref<number | null>(null);
const artists = ref<ArtistItem[]>([]);
const artistFilterPageNum = ref(1);
const artistFilterPageSize = 40;
const artistFilterTotal = ref(0);
const artistFilterLoading = ref(false);

function loadData() {
  loading.value = true;
  const params: Record<string, unknown> = {
    pageNum: pageNum.value,
    pageSize: pageSize.value,
    keyword: keyword.value
  };
  if (selectedArtistId.value !== null) {
    params.artistId = selectedArtistId.value;
  }
  getAlbumPage(params)
    .then(res => {
      list.value = res.data.list;
      total.value = res.data.total;
      ensureArtistOptionsByIds(
        res.data.list
          .map(item => item.artistId)
          .filter((id): id is number => Number.isFinite(id))
      );
    })
    .finally(() => (loading.value = false));
}

const dialogVisible = ref(false);
const isEdit = ref(false);
const form = ref<Partial<AlbumItem>>({ name: "", cover: DEFAULT_COVER, artistId: undefined, type: "album" });
const submitting = ref(false);
const artistCache = ref<Record<number, ArtistItem>>({});
const detailDialogVisible = ref(false);
const detailLoading = ref(false);
const detailAlbum = ref<AlbumItem | null>(null);
const detailSongs = ref<SongItem[]>([]);
const detailTotal = ref(0);
const detailPageNum = ref(1);
const detailPageSize = 10;
const detailDurationText = ref<Record<number, string>>({});
const detailDurationAbortToken = ref(0);

function coverOf(cover?: string) {
  return cover || DEFAULT_COVER;
}

function getArtistName(artistId?: number) {
  if (!artistId) return "-";
  return artistCache.value[artistId]?.name || `ID ${artistId}`;
}

function parseArtistIds(artistIds?: string) {
  if (!artistIds) return [];
  return artistIds
    .split(",")
    .map(id => Number(id.trim()))
    .filter(id => Number.isFinite(id));
}

function extractArtistIds(values: Array<string | undefined>) {
  return Array.from(new Set(values.flatMap(value => parseArtistIds(value))));
}

function getArtistNames(artistIds?: string) {
  if (!artistIds) return "-";
  const names = parseArtistIds(artistIds).map(id => artistCache.value[id]?.name || `ID ${id}`);
  return names.length ? names.join(" / ") : "-";
}

function cacheArtists(rows: ArtistItem[]) {
  if (!rows.length) return;
  const next = { ...artistCache.value };
  rows.forEach(artist => {
    next[artist.id] = artist;
  });
  artistCache.value = next;
}

function mergeArtists(current: ArtistItem[], incoming: ArtistItem[]) {
  const map = new Map<number, ArtistItem>();
  current.forEach(artist => map.set(artist.id, artist));
  incoming.forEach(artist => map.set(artist.id, artist));
  return Array.from(map.values()).sort((a, b) => a.id - b.id);
}

async function loadArtists() {
  if (artistFilterLoading.value) return;
  if (artists.value.length >= artistFilterTotal.value && artistFilterTotal.value > 0) return;
  artistFilterLoading.value = true;
  try {
    const res = await getArtistOptions({
      pageNum: artistFilterPageNum.value,
      pageSize: artistFilterPageSize
    });
    const rows = res.data?.list || [];
    artists.value = mergeArtists(artists.value, rows);
    artistFilterTotal.value = res.data?.total || 0;
    artistFilterPageNum.value += 1;
    cacheArtists(rows);
  } finally {
    artistFilterLoading.value = false;
  }
}

function selectArtist(artistId: number | null) {
  selectedArtistId.value = artistId;
  pageNum.value = 1;
  loadData();
}

function onArtistFilterScroll(event: Event) {
  const target = event.currentTarget as HTMLElement;
  if (target.scrollHeight - target.scrollTop - target.clientHeight < 80) {
    loadArtists();
  }
}

async function ensureArtistOptionsByIds(ids: number[]) {
  const missingIds = ids.filter(id => Number.isFinite(id) && !artistCache.value[id]);
  if (!missingIds.length) return;
  const res = await getArtistOptions({
    pageNum: 1,
    pageSize: missingIds.length,
    ids: missingIds.join(",")
  });
  cacheArtists(res.data?.list || []);
}

async function fetchArtistSelectOptions(params: {
  pageNum: number;
  pageSize: number;
  keyword?: string;
  ids?: string;
}) {
  const res = await getArtistOptions({
    pageNum: params.pageNum,
    pageSize: params.pageSize,
    name: params.keyword,
    ids: params.ids
  });
  const rows = res.data?.list || [];
  cacheArtists(rows);
  return {
    list: rows.map(artist => ({ id: artist.id, name: artist.name })),
    total: res.data?.total || 0
  };
}

function openCreate() {
  isEdit.value = false;
  form.value = { name: "", cover: DEFAULT_COVER, artistId: undefined, type: "album", status: 1 };
  dialogVisible.value = true;
}
function openEdit(row: AlbumItem) {
  isEdit.value = true;
  form.value = { ...row, cover: coverOf(row.cover) };
  dialogVisible.value = true;
  ensureArtistOptionsByIds(row.artistId ? [row.artistId] : []);
}

function coverOfSong(song?: SongItem) {
  return song?.cover || detailAlbum.value?.cover || DEFAULT_COVER;
}

function formatDuration(duration?: number) {
  const rawDuration = duration || 0;
  const totalSeconds = Math.max(0, Math.floor(rawDuration > 1000 ? rawDuration / 1000 : rawDuration));
  const minutes = String(Math.floor(totalSeconds / 60)).padStart(2, "0");
  const seconds = String(totalSeconds % 60).padStart(2, "0");
  return `${minutes}:${seconds}`;
}

function displayDuration(row: SongItem) {
  if (row.duration && row.duration > 0) {
    return formatDuration(row.duration);
  }
  return detailDurationText.value[row.id] || "读取中...";
}

function resolveDurationFromAudio(songId: number, token: number) {
  return new Promise<void>(resolve => {
    const audio = new Audio();
    const finish = (value: string) => {
      audio.src = "";
      if (detailDurationAbortToken.value === token) {
        detailDurationText.value = {
          ...detailDurationText.value,
          [songId]: value
        };
      }
      resolve();
    };
    audio.preload = "metadata";
    audio.addEventListener("loadedmetadata", () => {
      finish(Number.isFinite(audio.duration) && audio.duration > 0 ? formatDuration(audio.duration) : "-");
    }, { once: true });
    audio.addEventListener("error", () => finish("-"), { once: true });
    audio.src = `/api/client/songs/${songId}/stream`;
  });
}

async function hydrateDetailDurations(rows: SongItem[]) {
  const token = ++detailDurationAbortToken.value;
  const unresolved = rows.filter(row => (!row.duration || row.duration <= 0) && detailDurationText.value[row.id] == null);
  for (const row of unresolved) {
    if (detailDurationAbortToken.value !== token) return;
    await resolveDurationFromAudio(row.id, token);
  }
}

function loadDetailSongs() {
  if (!detailAlbum.value?.id) return;
  detailLoading.value = true;
  getSongPage({
    pageNum: detailPageNum.value,
    pageSize: detailPageSize,
    albumId: detailAlbum.value.id
  })
    .then(res => {
      detailSongs.value = res.data.list || [];
      detailTotal.value = res.data.total || 0;
      ensureArtistOptionsByIds(extractArtistIds(detailSongs.value.map(item => item.artistIds)));
      hydrateDetailDurations(detailSongs.value);
    })
    .finally(() => {
      detailLoading.value = false;
    });
}

function openDetail(row: AlbumItem) {
  detailAlbum.value = row;
  detailPageNum.value = 1;
  detailSongs.value = [];
  detailTotal.value = 0;
  detailDurationText.value = {};
  detailDialogVisible.value = true;
  ensureArtistOptionsByIds(row.artistId ? [row.artistId] : []);
  loadDetailSongs();
}

function onDetailPageChange(page: number) {
  detailPageNum.value = page;
  loadDetailSongs();
}

function handleSubmit() {
  if (!form.value.name) { ElMessage.warning("请输入专辑名称"); return; }
  if (!form.value.artistId) { ElMessage.warning("歌手是必填项"); return; }
  submitting.value = true;
  const payload = {
    ...form.value,
    name: form.value.name.trim(),
    cover: coverOf(form.value.cover)
  };
  const api = isEdit.value ? updateAlbum(form.value.id!, payload) : createAlbum(payload);
  api.then(() => { ElMessage.success(isEdit.value ? "修改成功" : "新增成功"); dialogVisible.value = false; loadData(); })
    .catch(error => { ElMessage.error(getErrorMessage(error, isEdit.value ? "修改失败" : "新增失败")); })
    .finally(() => (submitting.value = false));
}
function handleDelete(row: AlbumItem) {
  ElMessageBox.confirm(`确定删除「${row.name}」？`, "提示", { type: "warning" })
    .then(() => deleteAlbum(row.id).then(() => { ElMessage.success("已删除"); loadData(); }));
}
function getErrorMessage(error: any, fallback = "操作失败") {
  return error?.response?.data?.message || error?.message || fallback;
}
async function handleBatchDelete() {
  if (!selectedRows.value.length) {
    ElMessage.warning("请先选择要删除的专辑");
    return;
  }
  const names = selectedRows.value.map(item => item.name).join("、");
  try {
    await ElMessageBox.confirm(`确定批量删除 ${selectedRows.value.length} 张专辑？`, "批量删除", {
      type: "warning"
    });
    const res = await batchDeleteAlbums(selectedRows.value.map(item => item.id));
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
function onSelectionChange(rows: AlbumItem[]) {
  selectedRows.value = rows;
}
function onSearch() { pageNum.value = 1; loadData(); }
function onReset() {
  keyword.value = "";
  selectedArtistId.value = null;
  pageNum.value = 1;
  loadData();
}
function onPageChange(page: number) { pageNum.value = page; loadData(); }
onMounted(() => {
  loadArtists();
  loadData();
});
onBeforeUnmount(() => {
  detailDurationAbortToken.value += 1;
});
</script>

<template>
  <div class="p-4">
    <h2 class="text-xl font-bold mb-4">专辑管理</h2>
    <div class="flex gap-4">
      <aside class="w-60 shrink-0 rounded-md border border-gray-200 bg-white p-3">
        <div class="mb-3 flex items-center gap-2 text-sm font-semibold text-gray-700">
          <el-icon><UserFilled /></el-icon>
          歌手分类
        </div>
        <div class="artist-filter-scroll" @scroll="onArtistFilterScroll">
          <div
            class="artist-filter-item"
            :class="{ active: selectedArtistId === null }"
            @click="selectArtist(null)"
          >
            <span class="truncate">全部</span>
          </div>
          <div
            v-for="artist in artists"
            :key="artist.id"
            class="artist-filter-item"
            :class="{ active: selectedArtistId === artist.id }"
            @click="selectArtist(artist.id)"
          >
            <span class="mr-2 w-8 shrink-0 text-xs text-gray-400">#{{ artist.id }}</span>
            <span class="truncate">{{ artist.name }}</span>
          </div>
          <div v-if="artistFilterLoading" class="artist-filter-loading">加载中...</div>
          <div
            v-else-if="artists.length >= artistFilterTotal && artistFilterTotal > 0"
            class="artist-filter-loading"
          >
            已加载全部歌手
          </div>
        </div>
      </aside>

      <section class="min-w-0 flex-1">
        <div class="flex gap-3 mb-4">
          <el-input v-model="keyword" placeholder="搜索专辑名" clearable style="width: 240px" @keyup.enter="onSearch" />
          <el-button type="primary" :icon="Search" @click="onSearch">搜索</el-button>
          <el-button :icon="RefreshRight" @click="onReset">重置</el-button>
          <el-button :icon="RefreshRight" @click="loadData">刷新</el-button>
          <el-button type="success" :icon="Plus" @click="openCreate">新增专辑</el-button>
          <el-button type="danger" :icon="Delete" @click="handleBatchDelete">批量删除</el-button>
        </div>
        <el-table :data="list" v-loading="loading" stripe border @selection-change="onSelectionChange">
          <el-table-column type="selection" width="48" fixed="left" />
          <el-table-column prop="id" label="编号" width="90" sortable />
          <el-table-column label="封面" width="96">
            <template #default="{ row }">
              <el-image
                class="album-cover"
                :src="coverOf(row.cover)"
                :preview-src-list="[coverOf(row.cover)]"
                preview-teleported
                fit="cover"
              />
            </template>
          </el-table-column>
          <el-table-column prop="name" label="专辑名" min-width="160" show-overflow-tooltip />
          <el-table-column prop="type" label="类型" width="80" />
          <el-table-column label="歌手" min-width="140" show-overflow-tooltip>
            <template #default="{ row }">{{ getArtistName(row.artistId) }}</template>
          </el-table-column>
          <el-table-column prop="releaseDate" label="发行日期" width="120" />
          <el-table-column label="状态" width="80">
            <template #default="{ row }">
              <el-tag :type="row.status === 1 ? 'success' : 'info'">{{ row.status === 1 ? '上架' : '下架' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="200" fixed="right">
            <template #default="{ row }">
              <el-button link type="info" :icon="View" @click="openDetail(row)">详情</el-button>
              <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
              <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <div class="flex justify-end mt-4">
          <el-pagination v-model:current-page="pageNum" :page-size="pageSize" :total="total" layout="total, prev, pager, next" @current-change="onPageChange" />
        </div>
      </section>
    </div>

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑专辑' : '新增专辑'" width="460px" destroy-on-close>
      <el-form :model="form" label-width="80px">
        <el-form-item label="封面">
          <ImageUploadCropper
            v-model="form.cover"
            :fallback-src="DEFAULT_COVER"
            dir="album-cover"
            title="修改专辑封面"
            mask-text="修改封面"
            crop-title="裁剪专辑封面"
            confirm-text="使用封面"
          />
        </el-form-item>
        <el-form-item label="专辑名" required>
          <el-input v-model="form.name" placeholder="专辑名称" />
        </el-form-item>
        <el-form-item label="歌手" required>
          <RemotePagedTagSelect
            v-model="form.artistId"
            :fetch-options="fetchArtistSelectOptions"
            placeholder="搜索歌手"
            empty-text="暂无匹配歌手"
          />
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="form.type" style="width: 100%">
            <el-option label="专辑" value="album" />
            <el-option label="单曲" value="single" />
            <el-option label="EP" value="ep" />
          </el-select>
        </el-form-item>
        <el-form-item label="发行日期">
          <el-date-picker v-model="form.releaseDate" type="date" placeholder="选择日期" style="width: 100%" value-format="YYYY-MM-DD" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="detailDialogVisible" title="专辑详情" width="860px" destroy-on-close>
      <div v-if="detailAlbum" class="album-detail-header">
        <el-image
          class="album-cover album-cover--large"
          :src="coverOf(detailAlbum.cover)"
          :preview-src-list="[coverOf(detailAlbum.cover)]"
          preview-teleported
          fit="cover"
        />
        <div class="album-detail-meta">
          <div class="album-detail-title">{{ detailAlbum.name }}</div>
          <div class="album-detail-line">编号：{{ detailAlbum.id }}</div>
          <div class="album-detail-line">歌手：{{ getArtistName(detailAlbum.artistId) }}</div>
          <div class="album-detail-line">发行日期：{{ detailAlbum.releaseDate || "-" }}</div>
          <div class="album-detail-line">歌曲数量：{{ detailTotal }}</div>
        </div>
      </div>

      <el-table :data="detailSongs" v-loading="detailLoading" border stripe max-height="420">
        <el-table-column prop="id" label="歌曲编号" width="100" />
        <el-table-column label="封面" width="96">
          <template #default="{ row }">
            <el-image
              class="album-cover"
              :src="coverOfSong(row)"
              :preview-src-list="[coverOfSong(row)]"
              preview-teleported
              fit="cover"
            />
          </template>
        </el-table-column>
        <el-table-column prop="name" label="歌曲名" min-width="180" show-overflow-tooltip />
        <el-table-column label="歌手" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ getArtistNames(row.artistIds) }}</template>
        </el-table-column>
        <el-table-column label="时长" width="100">
          <template #default="{ row }">{{ displayDuration(row) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'">{{ row.status === 1 ? "上架" : "下架" }}</el-tag>
          </template>
        </el-table-column>
      </el-table>

      <div class="flex justify-end mt-4">
        <el-pagination
          v-model:current-page="detailPageNum"
          :page-size="detailPageSize"
          :total="detailTotal"
          layout="total, prev, pager, next"
          @current-change="onDetailPageChange"
        />
      </div>
    </el-dialog>
  </div>
</template>

<style scoped>
.album-cover {
  width: 48px;
  height: 48px;
  overflow: hidden;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  background: var(--el-fill-color-light);
  cursor: zoom-in;
}

.album-cover--large {
  width: 84px;
  height: 84px;
}

.album-detail-header {
  display: flex;
  gap: 16px;
  align-items: center;
  margin-bottom: 16px;
}

.album-detail-meta {
  min-width: 0;
}

.album-detail-title {
  margin-bottom: 8px;
  font-size: 18px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.album-detail-line {
  margin-bottom: 4px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.artist-filter-scroll {
  height: calc(100vh - 220px);
  overflow-y: auto;
}

.artist-filter-loading {
  padding: 10px 0;
  color: #909399;
  font-size: 12px;
  text-align: center;
}

.artist-filter-item {
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

.artist-filter-item:hover {
  color: #1f7cff;
  background-color: #ecf5ff;
}

.artist-filter-item.active {
  color: #fff;
  background-color: #1f7cff;
}

.artist-filter-item.active .text-gray-400 {
  color: rgb(219 234 254);
}

</style>
