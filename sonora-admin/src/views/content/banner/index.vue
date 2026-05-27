<script setup lang="ts">
import { onMounted, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import {
  Delete,
  Edit,
  Picture,
  Plus,
  RefreshRight,
  Search
} from "@element-plus/icons-vue";
import {
  createBanner,
  deleteBanner,
  getBannerPage,
  toggleBannerStatus,
  updateBanner,
  type BannerItem,
  type BannerPayload
} from "@/api/banner";

const loading = ref(false);
const list = ref<BannerItem[]>([]);
const total = ref(0);
const pageNum = ref(1);
const pageSize = ref(20);
const keyword = ref("");
const statusFilter = ref<number | undefined>();

const dialogVisible = ref(false);
const isEdit = ref(false);
const submitting = ref(false);
const editingId = ref<number | null>(null);
const form = ref<BannerPayload>({
  title: "",
  imageUrl: "",
  linkUrl: "",
  sort: 0,
  status: 1
});

function loadData() {
  loading.value = true;
  getBannerPage({
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

function openCreate() {
  isEdit.value = false;
  editingId.value = null;
  form.value = { title: "", imageUrl: "", linkUrl: "", sort: 0, status: 1 };
  dialogVisible.value = true;
}

function openEdit(row: BannerItem) {
  isEdit.value = true;
  editingId.value = row.id;
  form.value = {
    title: row.title || "",
    imageUrl: row.imageUrl,
    linkUrl: row.linkUrl || "",
    sort: row.sort ?? 0,
    status: row.status
  };
  dialogVisible.value = true;
}

function handleSubmit() {
  if (!form.value.imageUrl?.trim()) {
    ElMessage.warning("请填写图片 URL");
    return;
  }

  submitting.value = true;
  const payload = {
    ...form.value,
    sort: Number(form.value.sort || 0)
  };
  const request = isEdit.value
    ? updateBanner(editingId.value!, payload)
    : createBanner(payload);

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

function handleDelete(row: BannerItem) {
  ElMessageBox.confirm(`确定删除「${row.title || row.imageUrl}」？`, "提示", {
    type: "warning"
  })
    .then(() => deleteBanner(row.id))
    .then(() => {
      ElMessage.success("已删除");
      loadData();
    });
}

function handleToggleStatus(row: BannerItem) {
  const newStatus = row.status === 1 ? 0 : 1;
  toggleBannerStatus(row.id, newStatus).then(() => {
    row.status = newStatus;
    ElMessage.success("状态已更新");
  });
}

onMounted(loadData);
</script>

<template>
  <div class="p-4">
    <h2 class="mb-4 text-xl font-bold">轮播图管理</h2>

    <div class="mb-4 flex flex-wrap gap-3">
      <el-input
        v-model="keyword"
        clearable
        placeholder="搜索标题"
        style="width: 240px"
        @keyup.enter="onSearch"
      />
      <el-select
        v-model="statusFilter"
        clearable
        placeholder="状态"
        style="width: 120px"
      >
        <el-option label="启用" :value="1" />
        <el-option label="禁用" :value="0" />
      </el-select>
      <el-button type="primary" :icon="Search" @click="onSearch">搜索</el-button>
      <el-button :icon="RefreshRight" @click="loadData">刷新</el-button>
      <el-button type="success" :icon="Plus" @click="openCreate">新增轮播图</el-button>
    </div>

    <el-table v-loading="loading" :data="list" border stripe>
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column label="图片" width="140">
        <template #default="{ row }">
          <el-image
            v-if="row.imageUrl"
            :src="row.imageUrl"
            fit="cover"
            style="width: 96px; height: 48px; border-radius: 6px"
          />
          <el-icon v-else size="24"><Picture /></el-icon>
        </template>
      </el-table-column>
      <el-table-column prop="title" label="标题" min-width="180" show-overflow-tooltip />
      <el-table-column prop="linkUrl" label="跳转链接" min-width="220" show-overflow-tooltip />
      <el-table-column prop="sort" label="排序" width="90" />
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
      :title="isEdit ? '编辑轮播图' : '新增轮播图'"
      width="620px"
      destroy-on-close
    >
      <el-form :model="form" label-width="86px">
        <el-form-item label="标题">
          <el-input v-model="form.title" placeholder="首页推荐标题" />
        </el-form-item>
        <el-form-item label="图片 URL" required>
          <el-input v-model="form.imageUrl" placeholder="https://..." />
        </el-form-item>
        <el-form-item label="跳转链接">
          <el-input v-model="form.linkUrl" placeholder="/playlist/1 或 https://..." />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sort" :min="0" :step="1" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio-button :label="1">启用</el-radio-button>
            <el-radio-button :label="0">禁用</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="form.imageUrl" label="预览">
          <el-image
            :src="form.imageUrl"
            fit="cover"
            style="width: 240px; height: 120px; border-radius: 6px"
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
