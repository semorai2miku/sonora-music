<script setup lang="ts">
import { ref, onMounted } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { getAlbumPage, createAlbum, updateAlbum, deleteAlbum, type AlbumItem } from "@/api/album";
import { getArtistAll, type ArtistItem } from "@/api/artist";
import { Plus, Search } from "@element-plus/icons-vue";

const loading = ref(false);
const list = ref<AlbumItem[]>([]);
const total = ref(0);
const pageNum = ref(1);
const pageSize = ref(20);
const keyword = ref("");

function loadData() {
  loading.value = true;
  getAlbumPage({ pageNum: pageNum.value, pageSize: pageSize.value, keyword: keyword.value })
    .then(res => { list.value = res.data.list; total.value = res.data.total; })
    .finally(() => (loading.value = false));
}

const dialogVisible = ref(false);
const isEdit = ref(false);
const form = ref<Partial<AlbumItem>>({ name: "", artistId: undefined, type: "album" });
const submitting = ref(false);
const artists = ref<ArtistItem[]>([]);

function openCreate() {
  isEdit.value = false;
  form.value = { name: "", artistId: undefined, type: "album", status: 1 };
  dialogVisible.value = true;
  getArtistAll().then(res => (artists.value = res.data || []));
}
function openEdit(row: AlbumItem) {
  isEdit.value = true;
  form.value = { ...row };
  dialogVisible.value = true;
  getArtistAll().then(res => (artists.value = res.data || []));
}
function handleSubmit() {
  if (!form.value.name) { ElMessage.warning("请输入专辑名称"); return; }
  submitting.value = true;
  const api = isEdit.value ? updateAlbum(form.value.id!, form.value) : createAlbum(form.value);
  api.then(() => { ElMessage.success(isEdit.value ? "修改成功" : "新增成功"); dialogVisible.value = false; loadData(); })
    .finally(() => (submitting.value = false));
}
function handleDelete(row: AlbumItem) {
  ElMessageBox.confirm(`确定删除「${row.name}」？`, "提示", { type: "warning" })
    .then(() => deleteAlbum(row.id).then(() => { ElMessage.success("已删除"); loadData(); }));
}
function onSearch() { pageNum.value = 1; loadData(); }
onMounted(loadData);
</script>

<template>
  <div class="p-4">
    <h2 class="text-xl font-bold mb-4">专辑管理</h2>
    <div class="flex gap-3 mb-4">
      <el-input v-model="keyword" placeholder="搜索专辑名" clearable style="width: 240px" @keyup.enter="onSearch" />
      <el-button type="primary" :icon="Search" @click="onSearch">搜索</el-button>
      <el-button type="success" :icon="Plus" @click="openCreate">新增专辑</el-button>
    </div>
    <el-table :data="list" v-loading="loading" stripe border>
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column prop="name" label="专辑名" min-width="160" />
      <el-table-column prop="type" label="类型" width="80" />
      <el-table-column prop="artistId" label="歌手ID" width="80" />
      <el-table-column prop="releaseDate" label="发行日期" width="120" />
      <el-table-column label="状态" width="80">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'info'">{{ row.status === 1 ? '上架' : '下架' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="150" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <div class="flex justify-end mt-4">
      <el-pagination v-model:current-page="pageNum" :page-size="pageSize" :total="total" layout="total, prev, pager, next" @current-change="(p: number) => { pageNum = p; loadData(); }" />
    </div>

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑专辑' : '新增专辑'" width="460px" destroy-on-close>
      <el-form :model="form" label-width="80px">
        <el-form-item label="专辑名" required>
          <el-input v-model="form.name" placeholder="专辑名称" />
        </el-form-item>
        <el-form-item label="歌手">
          <el-select v-model="form.artistId" placeholder="选择歌手" clearable style="width: 100%">
            <el-option v-for="a in artists" :key="a.id" :label="a.name" :value="a.id" />
          </el-select>
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
  </div>
</template>
