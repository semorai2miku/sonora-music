<script setup lang="ts">
import { ref, onMounted } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { getSongPage, createSong, updateSong, deleteSong, toggleSongStatus, type SongItem } from "@/api/song";
import { getArtistAll, type ArtistItem } from "@/api/artist";
import { getAlbumPage, type AlbumItem } from "@/api/album";
import { Plus, Search, RefreshRight, UserFilled } from "@element-plus/icons-vue";

// --- 播放 ---
const audio = ref<HTMLAudioElement | null>(null);
const playingId = ref<number | null>(null);

function togglePlay(row: SongItem) {
  if (!audio.value) return;
  if (playingId.value === row.id) {
    audio.value.paused ? audio.value.play() : audio.value.pause();
    return;
  }
  audio.value.src = `/api/client/songs/${row.id}/stream`;
  audio.value.play();
  playingId.value = row.id;
}

function onAudioEnded() {
  playingId.value = null;
}

// --- 列表 ---
const loading = ref(false);
const list = ref<SongItem[]>([]);
const total = ref(0);
const pageNum = ref(1);
const pageSize = ref(20);
const keyword = ref("");
const selectedArtistId = ref<number | null>(null);
const artists = ref<ArtistItem[]>([]);
const albums = ref<AlbumItem[]>([]);

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
  getSongPage(params)
    .then(res => {
      list.value = res.data.list;
      total.value = res.data.total;
    })
    .finally(() => (loading.value = false));
}

function onSearch() {
  pageNum.value = 1;
  loadData();
}

function onPageChange(page: number) {
  pageNum.value = page;
  loadData();
}

function selectArtist(artistId: number | null) {
  selectedArtistId.value = artistId;
  pageNum.value = 1;
  loadData();
}

async function loadArtists() {
  const res = await getArtistAll();
  artists.value = (res.data || []).sort((a, b) => a.id - b.id);
}

function getArtistNames(artistIds?: string) {
  if (!artistIds) return "-";
  const names = artistIds
    .split(",")
    .map(id => id.trim())
    .filter(Boolean)
    .map(id => artists.value.find(artist => String(artist.id) === id)?.name || `ID ${id}`);
  return names.length ? names.join(" / ") : "-";
}

// --- 新增/编辑 ---
const dialogVisible = ref(false);
const isEdit = ref(false);
const form = ref<Partial<SongItem>>({ name: "", artistIds: "", albumId: undefined, lyrics: "" });
const audioFile = ref<File | null>(null);
const coverFile = ref<File | null>(null);
const submitting = ref(false);
const parsedArtistName = ref("");

function openCreate() {
  isEdit.value = false;
  form.value = {
    name: "",
    artistIds: selectedArtistId.value === null ? "" : String(selectedArtistId.value),
    albumId: undefined,
    lyrics: "",
    status: 1
  };
  audioFile.value = null;
  coverFile.value = null;
  parsedArtistName.value = "";
  dialogVisible.value = true;
  loadOptions();
}

function openEdit(row: SongItem) {
  isEdit.value = true;
  form.value = { ...row };
  audioFile.value = null;
  coverFile.value = null;
  parsedArtistName.value = "";
  dialogVisible.value = true;
  loadOptions();
}

async function loadOptions() {
  const [, albumRes] = await Promise.all([
    loadArtists(),
    getAlbumPage({ pageSize: 999 })
  ]);
  albums.value = albumRes.data?.list || [];
}

function stripExtension(fileName: string) {
  return fileName.replace(/\.[^/.]+$/, "").trim();
}

function parseSongFileName(fileName: string) {
  const baseName = stripExtension(fileName).replace(/^\d+\s*[.-]\s*/, "").trim();
  const matched = baseName.match(/^(.+?)\s*[-–—]\s*(.+)$/);
  if (!matched) return null;
  const artistName = matched[1].trim();
  const songName = matched[2].trim();
  if (!artistName || !songName) return null;
  return { artistName, songName };
}

function normalizeName(name: string) {
  return name.trim().replace(/\s+/g, "").toLowerCase();
}

async function ensureArtistsLoaded() {
  if (artists.value.length > 0) return;
  await loadArtists();
}

async function handleAudioFileChange(event: Event) {
  const target = event.target as HTMLInputElement;
  const file = target.files?.[0] || null;
  audioFile.value = file;
  parsedArtistName.value = "";
  if (!file) return;

  const parsed = parseSongFileName(file.name);
  if (!parsed) {
    if (!form.value.name) {
      form.value.name = stripExtension(file.name);
    }
    ElMessage.warning("文件名未匹配“歌手名-歌曲名”格式，请手动填写");
    return;
  }

  form.value.name = parsed.songName;
  parsedArtistName.value = parsed.artistName;
  await ensureArtistsLoaded();

  const normalizedArtistName = normalizeName(parsed.artistName);
  const matchedArtist = artists.value.find(artist => normalizeName(artist.name) === normalizedArtistName);
  if (matchedArtist) {
    form.value.artistIds = String(matchedArtist.id);
    ElMessage.success(`已识别：${parsed.artistName} - ${parsed.songName}`);
  } else {
    form.value.artistIds = "";
    ElMessage.warning(`已识别歌曲名，但歌手「${parsed.artistName}」不在歌手库中`);
  }
}

function handleSubmit() {
  if (!form.value.name) { ElMessage.warning("请输入歌曲名称"); return; }
  submitting.value = true;

  if (isEdit.value) {
    updateSong(form.value.id!, {
      name: form.value.name,
      artistIds: form.value.artistIds,
      albumId: form.value.albumId,
      lyrics: form.value.lyrics
    })
      .then(() => { ElMessage.success("修改成功"); dialogVisible.value = false; loadData(); })
      .finally(() => (submitting.value = false));
  } else {
    if (!audioFile.value) { ElMessage.warning("请选择音频文件"); submitting.value = false; return; }
    const fd = new FormData();
    fd.append("audioFile", audioFile.value);
    if (coverFile.value) fd.append("coverFile", coverFile.value);
    fd.append("name", form.value.name!);
    fd.append("artistIds", form.value.artistIds || "");
    if (form.value.albumId) fd.append("albumId", String(form.value.albumId));
    if (form.value.lyrics) fd.append("lyrics", form.value.lyrics);
    createSong(fd)
      .then(() => { ElMessage.success("新增成功"); dialogVisible.value = false; loadData(); })
      .finally(() => (submitting.value = false));
  }
}

function handleDelete(row: SongItem) {
  ElMessageBox.confirm(`确定删除「${row.name}」？`, "提示", { type: "warning" })
    .then(() => deleteSong(row.id).then(() => { ElMessage.success("已删除"); loadData(); }));
}

function handleToggleStatus(row: SongItem) {
  const newStatus = row.status === 1 ? 0 : 1;
  toggleSongStatus(row.id, newStatus).then(() => { row.status = newStatus; ElMessage.success("状态已更新"); });
}

function formatSize(bytes: number) {
  if (!bytes) return "-";
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + " KB";
  return (bytes / 1024 / 1024).toFixed(1) + " MB";
}

onMounted(() => {
  loadArtists();
  loadData();
});
</script>

<template>
  <div class="p-4">
    <h2 class="text-xl font-bold mb-4">歌曲管理</h2>
    <div class="flex gap-4">
      <aside class="w-60 shrink-0 rounded-md border border-gray-200 bg-white p-3">
        <div class="mb-3 flex items-center gap-2 text-sm font-semibold text-gray-700">
          <el-icon><UserFilled /></el-icon>
          歌手分类
        </div>
        <el-scrollbar height="calc(100vh - 220px)">
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
        </el-scrollbar>
      </aside>

      <section class="min-w-0 flex-1">
        <!-- 搜索栏 -->
        <div class="flex gap-3 mb-4">
          <el-input v-model="keyword" placeholder="搜索歌曲名" clearable style="width: 240px" @keyup.enter="onSearch" />
          <el-button type="primary" :icon="Search" @click="onSearch">搜索歌曲</el-button>
          <el-button :icon="RefreshRight" @click="loadData">刷新</el-button>
          <el-button type="success" :icon="Plus" @click="openCreate">上传歌曲</el-button>
        </div>

        <!-- 表格 -->
        <el-table :data="list" v-loading="loading" stripe border>
          <el-table-column prop="id" label="ID" width="70" sortable />
          <el-table-column prop="name" label="歌曲名" min-width="160" show-overflow-tooltip />
          <el-table-column label="歌手" min-width="150" show-overflow-tooltip>
            <template #default="{ row }">{{ getArtistNames(row.artistIds) }}</template>
          </el-table-column>
          <el-table-column prop="format" label="格式" width="70" />
          <el-table-column label="大小" width="90">
            <template #default="{ row }">{{ formatSize(row.fileSize) }}</template>
          </el-table-column>
          <el-table-column prop="playCount" label="播放" width="80" />
          <el-table-column label="状态" width="80">
            <template #default="{ row }">
              <el-switch :model-value="row.status === 1" @change="handleToggleStatus(row)" />
            </template>
          </el-table-column>
          <el-table-column prop="createdAt" label="创建时间" width="170" />
          <el-table-column label="操作" width="240" fixed="right">
            <template #default="{ row }">
              <el-button link :type="playingId === row.id ? 'warning' : 'info'" @click="togglePlay(row)">
                {{ playingId === row.id ? '⏸ 暂停' : '▶ 试听' }}
              </el-button>
              <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
              <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>

        <!-- 分页 -->
        <div class="flex justify-end mt-4">
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

    <!-- 新增/编辑对话框 -->
    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑歌曲' : '上传歌曲'" width="560px" destroy-on-close>
      <el-form :model="form" label-width="80px">
        <el-form-item label="歌曲名" required>
          <el-input v-model="form.name" placeholder="歌曲名称" />
        </el-form-item>
        <el-form-item v-if="!isEdit" label="音频文件" required>
          <input type="file" accept="audio/*" @change="handleAudioFileChange" />
          <div v-if="parsedArtistName" class="mt-2 text-xs text-gray-500">
            已从文件名识别歌手：{{ parsedArtistName }}
          </div>
        </el-form-item>
        <el-form-item v-if="!isEdit" label="封面图片">
          <input type="file" accept="image/*" @change="(e: any) => coverFile = e.target.files[0]" />
        </el-form-item>
        <el-form-item label="歌手">
          <el-select v-model="form.artistIds" placeholder="选择歌手" clearable style="width: 100%">
            <el-option v-for="a in artists" :key="a.id" :label="a.name" :value="String(a.id)" />
          </el-select>
        </el-form-item>
        <el-form-item label="所属专辑">
          <el-select v-model="form.albumId" placeholder="选择专辑" clearable style="width: 100%">
            <el-option v-for="a in albums" :key="a.id" :label="a.name" :value="a.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="歌词">
          <el-input v-model="form.lyrics" type="textarea" :rows="4" placeholder="LRC 格式歌词" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">{{ isEdit ? '保存' : '上传' }}</el-button>
      </template>
    </el-dialog>

    <!-- 音频播放器 (隐藏) -->
    <audio ref="audio" @ended="onAudioEnded" @pause="() => {}" preload="none" style="display:none" />
  </div>
</template>

<style scoped>
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
  color: #409eff;
  background-color: #ecf5ff;
}

.artist-filter-item.active {
  color: #fff;
  background-color: #409eff;
}

.artist-filter-item.active .text-gray-400 {
  color: rgb(219 234 254);
}
</style>
