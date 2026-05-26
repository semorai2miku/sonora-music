<script setup lang="ts">
import { ref, onMounted } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { getArtistPage, createArtist, updateArtist, deleteArtist, type ArtistItem } from "@/api/artist";
import { Plus, Search } from "@element-plus/icons-vue";

const loading = ref(false);
const list = ref<ArtistItem[]>([]);
const total = ref(0);
const pageNum = ref(1);
const pageSize = ref(20);
const keyword = ref("");

function loadData() {
  loading.value = true;
  getArtistPage({ pageNum: pageNum.value, pageSize: pageSize.value, keyword: keyword.value })
    .then(res => { list.value = res.data.list; total.value = res.data.total; })
    .finally(() => (loading.value = false));
}

const dialogVisible = ref(false);
const isEdit = ref(false);
const form = ref<Partial<ArtistItem>>({ name: "", region: "", description: "" });
const submitting = ref(false);

function openCreate() {
  isEdit.value = false;
  form.value = { name: "", region: "", description: "", status: 1 };
  dialogVisible.value = true;
}
function openEdit(row: ArtistItem) {
  isEdit.value = true;
  form.value = { ...row };
  dialogVisible.value = true;
}
function handleSubmit() {
  if (!form.value.name) { ElMessage.warning("请输入歌手名称"); return; }
  submitting.value = true;
  const api = isEdit.value ? updateArtist(form.value.id!, form.value) : createArtist(form.value);
  api.then(() => { ElMessage.success(isEdit.value ? "修改成功" : "新增成功"); dialogVisible.value = false; loadData(); })
    .finally(() => (submitting.value = false));
}
function handleDelete(row: ArtistItem) {
  ElMessageBox.confirm(`确定删除「${row.name}」？`, "提示", { type: "warning" })
    .then(() => deleteArtist(row.id).then(() => { ElMessage.success("已删除"); loadData(); }));
}
function onSearch() { pageNum.value = 1; loadData(); }
onMounted(loadData);
</script>

<template>
  <div class="p-4">
    <h2 class="text-xl font-bold mb-4">歌手管理</h2>
    <div class="flex gap-3 mb-4">
      <el-input v-model="keyword" placeholder="搜索歌手名" clearable style="width: 240px" @keyup.enter="onSearch" />
      <el-button type="primary" :icon="Search" @click="onSearch">搜索</el-button>
      <el-button type="success" :icon="Plus" @click="openCreate">新增歌手</el-button>
    </div>
    <el-table :data="list" v-loading="loading" stripe border>
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column prop="name" label="歌手名" min-width="160" />
      <el-table-column prop="region" label="地区" width="100" />
      <el-table-column prop="description" label="简介" min-width="200" show-overflow-tooltip />
      <el-table-column label="状态" width="80">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'info'">{{ row.status === 1 ? '正常' : '禁用' }}</el-tag>
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

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑歌手' : '新增歌手'" width="460px" destroy-on-close>
      <el-form :model="form" label-width="60px">
        <el-form-item label="名称" required>
          <el-input v-model="form.name" placeholder="歌手名称" />
        </el-form-item>
        <el-form-item label="地区">
          <el-input v-model="form.region" placeholder="如：日本" />
        </el-form-item>
        <el-form-item label="简介">
          <el-input v-model="form.description" type="textarea" :rows="3" placeholder="歌手简介" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>
