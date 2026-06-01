<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import ImageUploadCropper from "@/components/ImageUploadCropper.vue";
import SimpleAudioPreviewBar from "@/components/SimpleAudioPreviewBar.vue";
import RemotePagedTagSelect from "@/components/RemotePagedTagSelect.vue";
import {
  batchDeleteSongs,
  getSongPage,
  createSong,
  replaceSong,
  updateSong,
  deleteSong,
  type SongItem
} from "@/api/song";
import { getArtistOptions, type ArtistItem } from "@/api/artist";
import { getAlbumOptions, type AlbumItem } from "@/api/album";
import { Delete, Plus, Search, RefreshRight, UserFilled } from "@element-plus/icons-vue";

const DEFAULT_COVER = "/default-cover.svg";

// --- 列表 ---
const loading = ref(false);
const list = ref<SongItem[]>([]);
const total = ref(0);
const pageNum = ref(1);
const pageSize = ref(20);
const selectedRows = ref<SongItem[]>([]);
const filters = ref({
  name: "",
  albumId: undefined as number | undefined
});
const selectedArtistId = ref<number | null>(null);
const artists = ref<ArtistItem[]>([]);
const albums = ref<AlbumItem[]>([]);
const albumCache = ref<Record<number, AlbumItem>>({});
const artistCache = ref<Record<number, ArtistItem>>({});
const artistFilterPageNum = ref(1);
const artistFilterPageSize = 40;
const artistFilterTotal = ref(0);
const artistFilterLoading = ref(false);
const availableAlbums = computed(() => {
  if (selectedArtistId.value === null) return albums.value;
  return albums.value.filter(album => album.artistId === selectedArtistId.value);
});

function loadData() {
  loading.value = true;
  const params: Record<string, unknown> = {
    pageNum: pageNum.value,
    pageSize: pageSize.value,
    keyword: filters.value.name
  };
  if (filters.value.albumId) {
    params.albumId = filters.value.albumId;
  }
  if (selectedArtistId.value !== null) {
    params.artistId = selectedArtistId.value;
  }
  getSongPage(params)
    .then(res => {
      list.value = res.data.list;
      total.value = res.data.total;
      ensureArtistOptionsByIds(extractArtistIds(res.data.list.map(item => item.artistIds)));
      ensureAlbumOptionsByIds(
        res.data.list
          .map(item => item.albumId)
          .filter((id): id is number => Number.isFinite(id))
      );
    })
    .finally(() => (loading.value = false));
}

function onSearch() {
  pageNum.value = 1;
  loadData();
}

function onReset() {
  filters.value = {
    name: "",
    albumId: undefined
  };
  selectedArtistId.value = null;
  pageNum.value = 1;
  loadData();
}

function onPageChange(page: number) {
  pageNum.value = page;
  loadData();
}

function selectedAlbumAvailable(albumId?: number) {
  if (!albumId) return true;
  return availableAlbums.value.some(album => album.id === albumId);
}

function selectArtist(artistId: number | null) {
  selectedArtistId.value = artistId;
  filters.value.albumId = undefined;
  pageNum.value = 1;
  loadData();
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

async function loadAlbums() {}

function getArtistNames(artistIds?: string) {
  if (!artistIds) return "-";
  const names = artistIds
    .split(",")
    .map(id => id.trim())
    .filter(Boolean)
    .map(id => artistCache.value[Number(id)]?.name || `ID ${id}`);
  return names.length ? names.join(" / ") : "-";
}

function extractArtistIds(values: Array<string | undefined>) {
  return Array.from(new Set(values.flatMap(value => parseArtistIds(value))));
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

function cacheAlbums(rows: AlbumItem[]) {
  if (!rows.length) return;
  const next = { ...albumCache.value };
  rows.forEach(album => {
    next[album.id] = album;
  });
  albumCache.value = next;
}

async function ensureAlbumOptionsByIds(ids: number[]) {
  const missingIds = ids.filter(id => Number.isFinite(id) && !albumCache.value[id]);
  if (!missingIds.length) return;
  const res = await getAlbumOptions({
    pageNum: 1,
    pageSize: missingIds.length,
    ids: missingIds.join(",")
  });
  cacheAlbums(res.data?.list || []);
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

async function fetchAlbumSelectOptions(params: {
  pageNum: number;
  pageSize: number;
  keyword?: string;
  ids?: string;
}) {
  const res = await getAlbumOptions({
    pageNum: params.pageNum,
    pageSize: params.pageSize,
    name: params.keyword,
    ids: params.ids
  });
  const rows = res.data?.list || [];
  cacheAlbums(rows);
  return {
    list: rows.map(album => ({
      id: album.id,
      name: album.name,
      meta: album.releaseDate || ""
    })),
    total: res.data?.total || 0
  };
}

async function fetchFilterAlbumOptions(params: {
  pageNum: number;
  pageSize: number;
  keyword?: string;
  ids?: string;
}) {
  const res = await getAlbumOptions({
    pageNum: params.pageNum,
    pageSize: params.pageSize,
    name: params.keyword,
    ids: params.ids,
    artistId: selectedArtistId.value === null ? undefined : selectedArtistId.value
  });
  const rows = res.data?.list || [];
  cacheAlbums(rows);
  return {
    list: rows.map(album => ({
      id: album.id,
      name: album.name,
      meta: album.releaseDate || ""
    })),
    total: res.data?.total || 0
  };
}

function onArtistFilterScroll(event: Event) {
  const target = event.currentTarget as HTMLElement;
  if (target.scrollHeight - target.scrollTop - target.clientHeight < 80) {
    loadArtists();
  }
}

function getAlbum(row: SongItem) {
  return albumCache.value[row.albumId];
}

function getAlbumName(row: SongItem) {
  return getAlbum(row)?.name || "-";
}

function getReleaseDate(row: SongItem) {
  return getAlbum(row)?.releaseDate || "-";
}

function coverOf(row: SongItem) {
  return row.cover || getAlbum(row)?.cover || DEFAULT_COVER;
}

function buildSongPageParams(page: number, size: number) {
  const params: Record<string, unknown> = {
    pageNum: page,
    pageSize: size,
    keyword: filters.value.name
  };
  if (filters.value.albumId) {
    params.albumId = filters.value.albumId;
  }
  if (selectedArtistId.value !== null) {
    params.artistId = selectedArtistId.value;
  }
  return params;
}

// --- 新增/编辑 ---
const dialogVisible = ref(false);
const isEdit = ref(false);
const form = ref<Partial<SongItem>>({ name: "", artistIds: "", albumId: undefined, lyrics: "" });
const selectedArtistIds = ref<number[]>([]);
const audioFile = ref<File | null>(null);
const uploadPreviewUrl = ref("");
const detectedDuration = ref<number | null>(null);
const submitting = ref(false);
const backfillLoading = ref(false);
const backfillProgressText = ref("");
const parsedArtistName = ref("");
const artistSelectRef = ref<HTMLElement | null>(null);
const artistSearchInputRef = ref<HTMLInputElement | null>(null);
const artistSelectOpen = ref(false);
const artistSearchKeyword = ref("");
const artistSelectOptions = ref<ArtistItem[]>([]);
const artistSelectPageNum = ref(1);
const artistSelectPageSize = 30;
const artistSelectTotal = ref(0);
const artistSelectLoading = ref(false);
let artistSearchTimer: ReturnType<typeof setTimeout> | null = null;
let artistSelectRequestSeq = 0;
const previewRow = ref<SongItem | null>(null);
const previewSource = ref("");
const previewAutoplayToken = ref(0);

const selectedArtistTags = computed(() =>
  selectedArtistIds.value.map(id => ({
    id,
    name: artistCache.value[id]?.name || `ID ${id}`
  }))
);

const visibleArtistSelectOptions = computed(() =>
  artistSelectOptions.value.filter(artist => !selectedArtistIds.value.includes(artist.id))
);

function parseArtistIds(artistIds?: string) {
  if (!artistIds) return [];
  return artistIds
    .split(",")
    .map(id => Number(id.trim()))
    .filter(id => Number.isFinite(id));
}

function syncSelectedArtistIds() {
  form.value.artistIds = selectedArtistIds.value.join(",");
}

function focusArtistSearch() {
  artistSelectOpen.value = true;
  if (!artistSelectOptions.value.length) {
    resetArtistSelectOptions();
  }
  nextTick(() => artistSearchInputRef.value?.focus());
}

async function resetArtistSelectOptions() {
  artistSelectPageNum.value = 1;
  artistSelectTotal.value = 0;
  artistSelectOptions.value = [];
  await loadArtistSelectOptions(true);
}

async function loadArtistSelectOptions(reset = false) {
  if (artistSelectLoading.value && !reset) return;
  if (!reset && artistSelectTotal.value > 0 && artistSelectOptions.value.length >= artistSelectTotal.value) {
    return;
  }
  const requestSeq = ++artistSelectRequestSeq;
  artistSelectLoading.value = true;
  try {
    const pageNum = reset ? 1 : artistSelectPageNum.value;
    const res = await getArtistOptions({
      pageNum,
      pageSize: artistSelectPageSize,
      name: artistSearchKeyword.value.trim()
    });
    if (requestSeq !== artistSelectRequestSeq) return;
    const rows = res.data?.list || [];
    artistSelectOptions.value = reset ? rows : mergeArtists(artistSelectOptions.value, rows);
    artistSelectTotal.value = res.data?.total || 0;
    artistSelectPageNum.value = pageNum + 1;
    cacheArtists(rows);
  } finally {
    if (requestSeq === artistSelectRequestSeq) {
      artistSelectLoading.value = false;
    }
  }
}

function onArtistSearchInput() {
  artistSelectOpen.value = true;
  if (artistSearchTimer) {
    clearTimeout(artistSearchTimer);
  }
  artistSearchTimer = setTimeout(() => {
    resetArtistSelectOptions();
  }, 240);
}

function onArtistSelectScroll(event: Event) {
  const target = event.currentTarget as HTMLElement;
  if (target.scrollHeight - target.scrollTop - target.clientHeight < 72) {
    loadArtistSelectOptions();
  }
}

function addSelectedArtist(artist: ArtistItem) {
  if (!selectedArtistIds.value.includes(artist.id)) {
    selectedArtistIds.value = [...selectedArtistIds.value, artist.id];
  }
  cacheArtists([artist]);
  syncSelectedArtistIds();
  artistSearchKeyword.value = "";
  resetArtistSelectOptions();
  nextTick(() => artistSearchInputRef.value?.focus());
}

function removeSelectedArtist(artistId: number) {
  selectedArtistIds.value = selectedArtistIds.value.filter(id => id !== artistId);
  syncSelectedArtistIds();
  nextTick(() => artistSearchInputRef.value?.focus());
}

function onArtistSearchBackspace() {
  if (artistSearchKeyword.value || !selectedArtistIds.value.length) return;
  selectedArtistIds.value = selectedArtistIds.value.slice(0, -1);
  syncSelectedArtistIds();
}

function handleDocumentClick(event: MouseEvent) {
  if (!artistSelectRef.value?.contains(event.target as Node)) {
    artistSelectOpen.value = false;
  }
}

function openCreate() {
  isEdit.value = false;
  selectedArtistIds.value = selectedArtistId.value === null ? [] : [selectedArtistId.value];
  ensureArtistOptionsByIds(selectedArtistIds.value);
  form.value = {
    name: "",
    artistIds: selectedArtistIds.value.join(","),
    albumId: undefined,
    cover: "",
    lyrics: "",
    status: 1
  };
  audioFile.value = null;
  resetUploadPreview();
  detectedDuration.value = null;
  parsedArtistName.value = "";
  dialogVisible.value = true;
  loadOptions();
}

function openEdit(row: SongItem) {
  isEdit.value = true;
  form.value = { ...row };
  selectedArtistIds.value = parseArtistIds(row.artistIds);
  ensureArtistOptionsByIds(selectedArtistIds.value);
  audioFile.value = null;
  resetUploadPreview();
  detectedDuration.value = row.duration || null;
  parsedArtistName.value = "";
  dialogVisible.value = true;
  loadOptions();
}

async function loadOptions() {
  await Promise.all([
    resetArtistSelectOptions(),
    ensureAlbumOptionsByIds(
      form.value.albumId && Number.isFinite(Number(form.value.albumId))
        ? [Number(form.value.albumId)]
        : []
    )
  ]);
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

async function findArtistByName(name: string) {
  const res = await getArtistOptions({
    pageNum: 1,
    pageSize: 20,
    name
  });
  const rows = res.data?.list || [];
  cacheArtists(rows);
  const normalizedArtistName = normalizeName(name);
  return rows.find(artist => normalizeName(artist.name) === normalizedArtistName);
}

function readAudioDuration(file: File) {
  return new Promise<number | null>(resolve => {
    const audio = document.createElement("audio");
    const objectUrl = URL.createObjectURL(file);
    const cleanup = () => {
      audio.src = "";
      URL.revokeObjectURL(objectUrl);
    };
    audio.preload = "metadata";
    audio.onloadedmetadata = () => {
      const duration = Number.isFinite(audio.duration) && audio.duration > 0
        ? Math.round(audio.duration)
        : null;
      cleanup();
      resolve(duration);
    };
    audio.onerror = () => {
      cleanup();
      resolve(null);
    };
    audio.src = objectUrl;
  });
}

function updateUploadPreview(file: File | null) {
  resetUploadPreview();
  if (!file) return;
  uploadPreviewUrl.value = URL.createObjectURL(file);
}

function resetUploadPreview() {
  if (uploadPreviewUrl.value) {
    URL.revokeObjectURL(uploadPreviewUrl.value);
    uploadPreviewUrl.value = "";
  }
}

function readSongDurationFromStream(songId: number) {
  return new Promise<number | null>(resolve => {
    const audio = document.createElement("audio");
    const finish = (value: number | null) => {
      audio.src = "";
      resolve(value);
    };
    audio.preload = "metadata";
    audio.onloadedmetadata = () => {
      const duration = Number.isFinite(audio.duration) && audio.duration > 0
        ? Math.round(audio.duration)
        : null;
      finish(duration);
    };
    audio.onerror = () => finish(null);
    audio.src = `/api/client/songs/${songId}/stream`;
  });
}

async function handleAudioFileChange(event: Event) {
  const target = event.target as HTMLInputElement;
  const file = target.files?.[0] || null;
  audioFile.value = file;
  updateUploadPreview(file);
  detectedDuration.value = null;
  parsedArtistName.value = "";
  if (!file) return;

  detectedDuration.value = await readAudioDuration(file);

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

  const matchedArtist = await findArtistByName(parsed.artistName);
  if (matchedArtist) {
    selectedArtistIds.value = [matchedArtist.id];
    syncSelectedArtistIds();
    ElMessage.success(`已识别：${parsed.artistName} - ${parsed.songName}`);
  } else {
    selectedArtistIds.value = [];
    syncSelectedArtistIds();
    ElMessage.warning(`已识别歌曲名，但歌手「${parsed.artistName}」不在歌手库中`);
  }
}

function previewSong(row: SongItem) {
  previewRow.value = row;
  previewSource.value = `/api/client/songs/${row.id}/stream`;
  previewAutoplayToken.value += 1;
}

function handleDialogClosed() {
  audioFile.value = null;
  detectedDuration.value = null;
  parsedArtistName.value = "";
  resetUploadPreview();
}

const uploadPreviewArtistText = computed(() => {
  if (selectedArtistIds.value.length) {
    return selectedArtistTags.value.map(item => item.name).join(" / ");
  }
  return parsedArtistName.value || "";
});

function handleSubmit() {
  if (!form.value.name) { ElMessage.warning("请输入歌曲名称"); return; }
  if (!selectedArtistIds.value.length) { ElMessage.warning("请选择至少一位歌手"); return; }
  submitting.value = true;

  if (isEdit.value) {
    if (audioFile.value) {
      const fd = new FormData();
      fd.append("audioFile", audioFile.value);
      fd.append("name", form.value.name);
      fd.append("artistIds", selectedArtistIds.value.join(","));
      if (form.value.albumId) fd.append("albumId", String(form.value.albumId));
      if (detectedDuration.value && detectedDuration.value > 0) {
        fd.append("duration", String(detectedDuration.value));
      }
      if (form.value.cover) fd.append("cover", form.value.cover);
      if (form.value.lyrics) fd.append("lyrics", form.value.lyrics);
      replaceSong(form.value.id!, fd)
        .then(() => {
          ElMessage.success("歌曲已替换");
          dialogVisible.value = false;
          loadData();
        })
        .catch(error => {
          ElMessage.error(getErrorMessage(error, "替换歌曲失败"));
        })
        .finally(() => (submitting.value = false));
      return;
    }

    updateSong(form.value.id!, {
      name: form.value.name,
      artistIds: selectedArtistIds.value.join(","),
      albumId: form.value.albumId,
      cover: form.value.cover || DEFAULT_COVER,
      lyrics: form.value.lyrics
    })
      .then(() => {
        ElMessage.success("修改成功");
        dialogVisible.value = false;
        loadData();
      })
      .catch(error => {
        ElMessage.error(getErrorMessage(error, "修改歌曲失败"));
      })
      .finally(() => (submitting.value = false));
  } else {
    if (!audioFile.value) { ElMessage.warning("请选择音频文件"); submitting.value = false; return; }
    const fd = new FormData();
    fd.append("audioFile", audioFile.value);
    fd.append("name", form.value.name!);
    fd.append("artistIds", selectedArtistIds.value.join(","));
    if (form.value.albumId) fd.append("albumId", String(form.value.albumId));
    if (detectedDuration.value && detectedDuration.value > 0) {
      fd.append("duration", String(detectedDuration.value));
    }
    if (form.value.cover) fd.append("cover", form.value.cover);
    if (form.value.lyrics) fd.append("lyrics", form.value.lyrics);
    createSong(fd)
      .then(() => {
        ElMessage.success("新增成功");
        dialogVisible.value = false;
        resetUploadPreview();
        loadData();
      })
      .catch(error => {
        ElMessage.error(getErrorMessage(error, "上传歌曲失败"));
      })
      .finally(() => (submitting.value = false));
  }
}

function handleDelete(row: SongItem) {
  ElMessageBox.confirm(`确定删除「${row.name}」？`, "提示", { type: "warning" })
    .then(() => deleteSong(row.id).then(() => { ElMessage.success("已删除"); loadData(); }));
}

function getErrorMessage(error: any, fallback = "操作失败") {
  return error?.response?.data?.message || error?.message || fallback;
}

async function handleBackfillDurations() {
  if (backfillLoading.value) return;
  try {
    await ElMessageBox.confirm(
      "将扫描当前筛选范围内所有时长缺失的歌曲，并按真实音频时长回填到数据库，是否继续？",
      "回填缺失时长",
      { type: "warning" }
    );
  } catch (error) {
    if (error === "cancel" || error === "close") return;
  }

  backfillLoading.value = true;
  backfillProgressText.value = "正在扫描歌曲...";

  try {
    const scanPageSize = 200;
    const firstPage = await getSongPage(buildSongPageParams(1, scanPageSize));
    const totalSongs = firstPage.data.total || 0;
    const totalPages = Math.max(1, Math.ceil(totalSongs / scanPageSize));
    const candidates: SongItem[] = (firstPage.data.list || []).filter(song => !song.duration || song.duration <= 0);

    for (let page = 2; page <= totalPages; page += 1) {
      backfillProgressText.value = `正在扫描歌曲...（${page}/${totalPages}）`;
      const res = await getSongPage(buildSongPageParams(page, scanPageSize));
      candidates.push(...(res.data.list || []).filter(song => !song.duration || song.duration <= 0));
    }

    if (!candidates.length) {
      ElMessage.success("当前范围内没有需要回填时长的歌曲");
      return;
    }

    let successCount = 0;
    let failedCount = 0;
    for (let index = 0; index < candidates.length; index += 1) {
      const song = candidates[index];
      backfillProgressText.value = `正在回填 ${index + 1}/${candidates.length}：${song.name}`;
      const duration = await readSongDurationFromStream(song.id);
      if (!duration || duration <= 0) {
        failedCount += 1;
        continue;
      }
      try {
        await updateSong(song.id, {
          name: song.name,
          artistIds: song.artistIds,
          albumId: song.albumId,
          lyrics: song.lyrics,
          duration
        });
        successCount += 1;
      } catch {
        failedCount += 1;
      }
    }

    ElMessage.success(`时长回填完成：成功 ${successCount} 首，失败 ${failedCount} 首`);
    loadData();
  } catch (error: any) {
    ElMessage.error(getErrorMessage(error, "回填时长失败"));
  } finally {
    backfillLoading.value = false;
    backfillProgressText.value = "";
  }
}

async function handleBatchDelete() {
  if (!selectedRows.value.length) {
    ElMessage.warning("请先选择要删除的歌曲");
    return;
  }
  const names = selectedRows.value.map(item => item.name).join("、");
  try {
    await ElMessageBox.confirm(`确定批量删除 ${selectedRows.value.length} 首歌曲？`, "批量删除", {
      type: "warning"
    });
    const res = await batchDeleteSongs(selectedRows.value.map(item => item.id));
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

function onSelectionChange(rows: SongItem[]) {
  selectedRows.value = rows;
}

onMounted(() => {
  document.addEventListener("click", handleDocumentClick);
  loadArtists();
  loadAlbums();
  loadData();
});

onBeforeUnmount(() => {
  document.removeEventListener("click", handleDocumentClick);
  resetUploadPreview();
  if (artistSearchTimer) {
    clearTimeout(artistSearchTimer);
  }
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
        <!-- 搜索栏 -->
        <div class="song-search">
          <el-input
            v-model="filters.name"
            placeholder="搜索歌名"
            clearable
            style="width: 240px"
            @keyup.enter="onSearch"
          />
          <div style="width: 240px">
            <RemotePagedTagSelect
              v-model="filters.albumId"
              :fetch-options="fetchFilterAlbumOptions"
              placeholder="按专辑查询"
              empty-text="暂无匹配专辑"
            />
          </div>
          <el-button type="primary" :icon="Search" @click="onSearch">搜索</el-button>
          <el-button :icon="RefreshRight" @click="onReset">重置</el-button>
          <el-button :icon="RefreshRight" @click="loadData">刷新</el-button>
          <el-button :loading="backfillLoading" @click="handleBackfillDurations">
            {{ backfillLoading ? "回填中" : "回填缺失时长" }}
          </el-button>
          <el-button type="success" :icon="Plus" @click="openCreate">上传歌曲</el-button>
          <el-button type="danger" :icon="Delete" @click="handleBatchDelete">批量删除</el-button>
        </div>
        <div v-if="backfillLoading && backfillProgressText" class="mb-3 text-sm text-gray-500">
          {{ backfillProgressText }}
        </div>

        <!-- 表格 -->
        <el-table :data="list" v-loading="loading" stripe border @selection-change="onSelectionChange">
          <el-table-column type="selection" width="48" fixed="left" />
          <el-table-column prop="id" label="编号" width="90" sortable />
          <el-table-column label="封面" width="96">
            <template #default="{ row }">
              <el-image
                class="song-cover"
                :src="coverOf(row)"
                :preview-src-list="[coverOf(row)]"
                preview-teleported
                fit="cover"
              />
            </template>
          </el-table-column>
          <el-table-column prop="name" label="歌名" min-width="180" show-overflow-tooltip />
          <el-table-column label="歌手" min-width="150" show-overflow-tooltip>
            <template #default="{ row }">{{ getArtistNames(row.artistIds) }}</template>
          </el-table-column>
          <el-table-column label="专辑" min-width="160" show-overflow-tooltip>
            <template #default="{ row }">{{ getAlbumName(row) }}</template>
          </el-table-column>
          <el-table-column label="发行日期" width="120">
            <template #default="{ row }">{{ getReleaseDate(row) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="240" fixed="right">
            <template #default="{ row }">
              <el-button
                link
                :type="previewRow?.id === row.id ? 'warning' : 'info'"
                @click="previewSong(row)"
              >
                {{ previewRow?.id === row.id ? "重新预览" : "预览" }}
              </el-button>
              <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
              <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>

        <div v-if="previewRow && previewSource" class="song-preview-panel">
          <div class="song-preview-panel__title">歌曲预览</div>
          <SimpleAudioPreviewBar
            :key="`${previewRow.id}-${previewAutoplayToken}`"
            :source="previewSource"
            :title="previewRow.name"
            :subtitle="getArtistNames(previewRow.artistIds)"
            :cover="coverOf(previewRow)"
            auto-play
          />
        </div>

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
    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? '编辑歌曲' : '上传歌曲'"
      width="560px"
      destroy-on-close
      @closed="handleDialogClosed"
    >
      <el-form :model="form" label-width="80px">
        <el-form-item label="歌曲名" required>
          <el-input v-model="form.name" placeholder="歌曲名称" />
        </el-form-item>
        <el-form-item :label="isEdit ? '替换歌曲' : '音频文件'" :required="!isEdit">
          <input type="file" accept="audio/*" @change="handleAudioFileChange" />
          <div v-if="isEdit" class="mt-2 text-xs text-gray-500">
            重新选择音频后，保存时会删除原有歌曲文件并替换为新文件。
          </div>
          <div v-if="parsedArtistName" class="mt-2 text-xs text-gray-500">
            已从文件名识别歌手：{{ parsedArtistName }}
          </div>
          <div v-if="detectedDuration" class="mt-1 text-xs text-gray-500">
            已识别时长：{{ Math.floor(detectedDuration / 60) }}:{{ String(detectedDuration % 60).padStart(2, "0") }}
          </div>
        </el-form-item>
        <el-form-item v-if="uploadPreviewUrl" :label="isEdit ? '新歌预览' : '歌曲预览'">
          <SimpleAudioPreviewBar
            :source="uploadPreviewUrl"
            :title="form.name || '未命名歌曲'"
            :subtitle="uploadPreviewArtistText"
            :cover="form.cover || DEFAULT_COVER"
          />
        </el-form-item>
        <el-form-item label="封面">
          <ImageUploadCropper
            v-model="form.cover"
            :fallback-src="DEFAULT_COVER"
            dir="song-cover"
            title="修改歌曲封面"
            mask-text="修改封面"
            crop-title="裁剪歌曲封面"
            confirm-text="使用封面"
          />
        </el-form-item>
        <el-form-item label="歌手">
          <RemotePagedTagSelect
            v-model="selectedArtistIds"
            multiple
            :fetch-options="fetchArtistSelectOptions"
            placeholder="搜索歌手"
            empty-text="暂无匹配歌手"
          />
        </el-form-item>
        <el-form-item label="所属专辑">
          <RemotePagedTagSelect
            v-model="form.albumId"
            :fetch-options="fetchAlbumSelectOptions"
            placeholder="搜索专辑"
            empty-text="暂无匹配专辑"
          />
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
  </div>
</template>

<style scoped>
.song-search {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: 16px;
}

.song-cover {
  width: 48px;
  height: 48px;
  overflow: hidden;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  background: var(--el-fill-color-light);
  cursor: zoom-in;
}

.song-preview-panel {
  margin-top: 16px;
  padding: 16px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 14px;
  background: var(--el-fill-color-blank);
}

.song-preview-panel__title {
  margin-bottom: 12px;
  color: var(--el-text-color-primary);
  font-size: 14px;
  font-weight: 600;
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

.artist-select {
  position: relative;
  width: 100%;
}

.artist-select__control {
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

.artist-select__control:focus-within {
  border-color: var(--el-color-primary);
}

.artist-select__input {
  flex: 1 1 120px;
  min-width: 120px;
  height: 24px;
  padding: 0;
  border: 0;
  outline: 0;
  color: var(--el-text-color-primary);
  background: transparent;
}

.artist-select__dropdown {
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

.artist-select__option {
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

.artist-select__option:hover {
  color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
}

.artist-select__id {
  width: 54px;
  flex: 0 0 54px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.artist-select__name {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.artist-select__empty {
  padding: 10px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  text-align: center;
}
</style>
