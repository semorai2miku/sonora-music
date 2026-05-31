<script setup lang="ts">
import { onMounted, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import ImageUploadCropper from "@/components/ImageUploadCropper.vue";
import {
  Delete,
  Edit,
  Picture,
  Plus,
  RefreshRight
} from "@element-plus/icons-vue";
import {
  batchDeleteBanners,
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
const selectedRows = ref<BannerItem[]>([]);

const dialogVisible = ref(false);
const isEdit = ref(false);
const submitting = ref(false);
const editingId = ref<number | null>(null);
const form = ref<BannerPayload>({
  imageUrl: "",
  status: 1
});

function loadData() {
  loading.value = true;
  getBannerPage({
    pageNum: pageNum.value,
    pageSize: pageSize.value
  })
    .then(res => {
      list.value = res.data.list || [];
      total.value = res.data.total || 0;
    })
    .finally(() => {
      loading.value = false;
    });
}

function onPageChange(page: number) {
  pageNum.value = page;
  loadData();
}

function openCreate() {
  isEdit.value = false;
  editingId.value = null;
  form.value = { imageUrl: "", status: 1 };
  dialogVisible.value = true;
}

function openEdit(row: BannerItem) {
  isEdit.value = true;
  editingId.value = row.id;
  form.value = {
    imageUrl: row.imageUrl,
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
    imageUrl: form.value.imageUrl.trim()
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
  ElMessageBox.confirm(`确定删除轮播图 ${row.id}？`, "提示", {
    type: "warning"
  })
    .then(() => deleteBanner(row.id))
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
    ElMessage.warning("请先选择要删除的轮播图");
    return;
  }
  const names = selectedRows.value.map(item => `轮播图 ${item.id}`).join("、");
  try {
    await ElMessageBox.confirm(`确定批量删除 ${selectedRows.value.length} 张轮播图？`, "批量删除", {
      type: "warning"
    });
    const res = await batchDeleteBanners(selectedRows.value.map(item => item.id));
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

function handleToggleStatus(row: BannerItem) {
  const newStatus = row.status === 1 ? 0 : 1;
  toggleBannerStatus(row.id, newStatus).then(() => {
    row.status = newStatus;
    ElMessage.success("状态已更新");
  });
}

function onSelectionChange(rows: BannerItem[]) {
  selectedRows.value = rows;
}

onMounted(loadData);
</script>

<template>
  <div class="p-4">
    <h2 class="mb-4 text-xl font-bold">轮播图管理</h2>

    <div class="mb-4 flex flex-wrap gap-3">
      <el-button :icon="RefreshRight" @click="loadData">刷新</el-button>
      <el-button type="success" :icon="Plus" @click="openCreate">新增轮播图</el-button>
      <el-button type="danger" :icon="Delete" @click="handleBatchDelete">批量删除</el-button>
    </div>

    <el-table v-loading="loading" :data="list" border stripe @selection-change="onSelectionChange">
      <el-table-column type="selection" width="48" fixed="left" />
      <el-table-column prop="id" label="轮播图编号" width="120" />
      <el-table-column label="轮播图（预览）" min-width="260">
        <template #default="{ row }">
          <el-image
            v-if="row.imageUrl"
            :src="row.imageUrl"
            fit="cover"
            class="banner-preview"
            :preview-src-list="[row.imageUrl]"
            preview-teleported
          />
          <el-icon v-else size="24"><Picture /></el-icon>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="120">
        <template #default="{ row }">
          <el-switch
            :model-value="row.status === 1"
            inline-prompt
            active-text="启用"
            inactive-text="禁用"
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

    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? '编辑轮播图' : '新增轮播图'"
      width="620px"
      destroy-on-close
    >
      <el-form :model="form" label-width="86px">
        <el-form-item label="轮播图" required>
          <ImageUploadCropper
            v-model="form.imageUrl"
            fallback-src="/default-cover.svg"
            dir="banner"
            shape="rectangle"
            :width="216"
            :height="84"
            :output-width="1080"
            :output-height="420"
            :stencil-width="360"
            :stencil-height="140"
            title="修改轮播图"
            mask-text="修改轮播图"
            crop-title="裁剪轮播图"
            confirm-text="使用轮播图"
            :clearable="false"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio-button :label="1">启用</el-radio-button>
            <el-radio-button :label="0">禁用</el-radio-button>
          </el-radio-group>
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
.banner-preview {
  width: 180px;
  height: 70px;
  border-radius: 8px;
}
</style>
