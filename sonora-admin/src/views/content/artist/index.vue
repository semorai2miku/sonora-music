<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import {
  Delete,
  Edit,
  Plus,
  RefreshRight,
  Search,
  Upload,
  UserFilled
} from "@element-plus/icons-vue";
import AvatarCropperDialog from "@/components/AvatarCropperDialog.vue";
import {
  batchDeleteArtists,
  createArtist,
  deleteArtist,
  getArtistPage,
  updateArtist,
  uploadArtistAvatar,
  type ArtistItem,
  type ArtistRelatedItem
} from "@/api/artist";

type ArtistPayload = {
  name: string;
  avatar: string;
  region?: string;
  description?: string;
  status: number;
};

const DEFAULT_AVATAR = "/default-avatar.svg";

const loading = ref(false);
const list = ref<ArtistItem[]>([]);
const total = ref(0);
const pageNum = ref(1);
const pageSize = ref(20);
const selectedRows = ref<ArtistItem[]>([]);

const filters = ref({
  name: "",
  region: ""
});

const dialogVisible = ref(false);
const isEdit = ref(false);
const submitting = ref(false);
const editingId = ref<number | null>(null);
const form = ref<ArtistPayload>({
  name: "",
  avatar: DEFAULT_AVATAR,
  region: "",
  description: "",
  status: 1
});

const cropperVisible = ref(false);
const cropperFile = ref<File | null>(null);
const uploadInputRef = ref<HTMLInputElement | null>(null);
const uploadingAvatar = ref(false);

const relationDialog = ref({
  visible: false,
  title: "",
  items: [] as ArtistRelatedItem[],
  emptyText: ""
});

const dialogTitle = computed(() => (isEdit.value ? "编辑歌手" : "新增歌手"));

function avatarOf(avatar?: string) {
  return avatar || DEFAULT_AVATAR;
}

function loadData() {
  loading.value = true;
  getArtistPage({
    pageNum: pageNum.value,
    pageSize: pageSize.value,
    name: filters.value.name,
    region: filters.value.region
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

function onReset() {
  filters.value = {
    name: "",
    region: ""
  };
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
  form.value = {
    name: "",
    avatar: DEFAULT_AVATAR,
    region: "",
    description: "",
    status: 1
  };
  dialogVisible.value = true;
}

function openEdit(row: ArtistItem) {
  isEdit.value = true;
  editingId.value = row.id;
  form.value = {
    name: row.name,
    avatar: avatarOf(row.avatar),
    region: row.region || "",
    description: row.description || "",
    status: row.status ?? 1
  };
  dialogVisible.value = true;
}

function validateForm() {
  if (!form.value.name.trim()) {
    ElMessage.warning("请输入歌手名称");
    return false;
  }
  if (form.value.name.trim().length > 128) {
    ElMessage.warning("歌手名称不能超过 128 个字符");
    return false;
  }
  if ((form.value.region || "").trim().length > 32) {
    ElMessage.warning("国籍不能超过 32 个字符");
    return false;
  }
  return true;
}

function getErrorMessage(error: any, fallback = "操作失败") {
  return error?.response?.data?.message || error?.message || fallback;
}

async function handleSubmit() {
  if (!validateForm()) return;

  const payload: ArtistPayload = {
    name: form.value.name.trim(),
    avatar: form.value.avatar || DEFAULT_AVATAR,
    region: form.value.region?.trim() || undefined,
    description: form.value.description?.trim() || undefined,
    status: form.value.status ?? 1
  };

  submitting.value = true;
  try {
    const res = isEdit.value
      ? await updateArtist(editingId.value!, payload)
      : await createArtist(payload);
    if (res.code !== 200) {
      ElMessage.error((res as any).message || (isEdit.value ? "修改失败" : "新增失败"));
      return;
    }
    ElMessage.success(isEdit.value ? "修改成功" : "新增成功");
    dialogVisible.value = false;
    loadData();
  } catch (error: any) {
    ElMessage.error(getErrorMessage(error, isEdit.value ? "修改失败" : "新增失败"));
  } finally {
    submitting.value = false;
  }
}

async function handleDelete(row: ArtistItem) {
  try {
    await ElMessageBox.confirm(`确定删除歌手「${row.name}」？`, "删除歌手", {
      type: "warning"
    });
    const res = await deleteArtist(row.id);
    if ((res as any)?.code && (res as any).code !== 200) {
      ElMessage.error((res as any).message || "删除失败");
      return;
    }
    ElMessage.success(`已删除歌手：${row.name}`);
    loadData();
  } catch (error: any) {
    if (error === "cancel" || error === "close") return;
    ElMessage.error(getErrorMessage(error, "删除失败"));
  }
}

async function handleBatchDelete() {
  if (!selectedRows.value.length) {
    ElMessage.warning("请先选择要删除的歌手");
    return;
  }
  const names = selectedRows.value.map(item => item.name).join("、");
  try {
    await ElMessageBox.confirm(`确定批量删除 ${selectedRows.value.length} 名歌手？`, "批量删除", {
      type: "warning"
    });
    const res = await batchDeleteArtists(selectedRows.value.map(item => item.id));
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

function showDescription(row: ArtistItem) {
  ElMessageBox.alert(row.description || "暂无简介", `${row.name} 的简介`, {
    confirmButtonText: "关闭"
  });
}

function openRelation(row: ArtistItem, type: "songs" | "albums") {
  const isSong = type === "songs";
  relationDialog.value = {
    visible: true,
    title: `${row.name} 的${isSong ? "歌曲" : "专辑"}`,
    items: (isSong ? row.songs : row.albums) || [],
    emptyText: isSong ? "暂无关联歌曲" : "暂无关联专辑"
  };
}

function triggerAvatarUpload() {
  uploadInputRef.value?.click();
}

function onAvatarFileChange(event: Event) {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  input.value = "";
  if (!file) return;
  if (!file.type.startsWith("image/")) {
    ElMessage.warning("请选择图片文件");
    return;
  }
  if (file.size > 5 * 1024 * 1024) {
    ElMessage.warning("头像图片不能超过 5MB");
    return;
  }
  cropperFile.value = file;
  cropperVisible.value = true;
}

function onCropConfirm(file: File) {
  uploadingAvatar.value = true;
  uploadArtistAvatar(file)
    .then(res => {
      if (res.code !== 200 || !res.data?.url) {
        ElMessage.error((res as any).message || "头像上传失败");
        return;
      }
      form.value.avatar = res.data.url;
      ElMessage.success("头像已上传");
    })
    .catch(error => {
      ElMessage.error(getErrorMessage(error, "头像上传失败"));
    })
    .finally(() => {
      uploadingAvatar.value = false;
    });
}

function onSelectionChange(rows: ArtistItem[]) {
  selectedRows.value = rows;
}

onMounted(loadData);
</script>

<template>
  <div class="artist-page">
    <div class="artist-page__header">
      <div>
        <h2>歌手管理</h2>
        <p>维护歌手头像、国籍、简介，以及查看关联歌曲和专辑</p>
      </div>
      <div class="artist-page__actions">
        <el-button :icon="RefreshRight" @click="loadData">刷新</el-button>
        <el-button type="primary" :icon="Plus" @click="openCreate">新增歌手</el-button>
        <el-button type="danger" :icon="Delete" @click="handleBatchDelete">批量删除</el-button>
      </div>
    </div>

    <el-form class="artist-search" :model="filters" inline>
      <el-form-item label="歌手">
        <el-input v-model="filters.name" clearable placeholder="请输入歌手名称" @keyup.enter="onSearch" />
      </el-form-item>
      <el-form-item label="国籍">
        <el-input v-model="filters.region" clearable placeholder="请输入国籍" @keyup.enter="onSearch" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :icon="Search" @click="onSearch">搜索</el-button>
        <el-button :icon="RefreshRight" @click="onReset">重置</el-button>
      </el-form-item>
    </el-form>

    <el-table
      v-loading="loading"
      :data="list"
      border
      stripe
      @selection-change="onSelectionChange"
    >
      <el-table-column type="selection" width="48" fixed="left" />
      <el-table-column prop="id" label="编号" width="90" sortable />
      <el-table-column label="头像" width="96">
        <template #default="{ row }">
          <el-image
            class="artist-avatar"
            :src="avatarOf(row.avatar)"
            :preview-src-list="[avatarOf(row.avatar)]"
            preview-teleported
            fit="cover"
          >
            <template #error>
              <el-avatar :icon="UserFilled" />
            </template>
          </el-image>
        </template>
      </el-table-column>
      <el-table-column prop="name" label="歌手" min-width="150" show-overflow-tooltip />
      <el-table-column prop="region" label="国籍" width="120" show-overflow-tooltip />
      <el-table-column label="简介" width="110">
        <template #default="{ row }">
          <el-button link type="primary" @click="showDescription(row)">
            {{ row.description ? "查看详情" : "暂无简介" }}
          </el-button>
        </template>
      </el-table-column>
      <el-table-column label="歌曲" width="100">
        <template #default="{ row }">
          <el-button link type="primary" @click="openRelation(row, 'songs')">
            {{ row.songCount || 0 }} 首
          </el-button>
        </template>
      </el-table-column>
      <el-table-column label="专辑" width="100">
        <template #default="{ row }">
          <el-button link type="primary" @click="openRelation(row, 'albums')">
            {{ row.albumCount || 0 }} 张
          </el-button>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="88">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'info'">
            {{ row.status === 1 ? "正常" : "禁用" }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="150" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" :icon="Edit" @click="openEdit(row)">修改</el-button>
          <el-button link type="danger" :icon="Delete" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="artist-pagination">
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
      :title="dialogTitle"
      width="620px"
      destroy-on-close
    >
      <el-form :model="form" label-width="80px">
        <el-form-item label="头像">
          <div class="avatar-editor">
            <button
              class="avatar-editor__trigger"
              type="button"
              :disabled="uploadingAvatar"
              title="修改头像"
              @click="triggerAvatarUpload"
            >
              <el-image class="avatar-editor__image" :src="avatarOf(form.avatar)" fit="cover" />
              <span class="avatar-editor__mask">
                <el-icon><Upload /></el-icon>
                <span>{{ uploadingAvatar ? "上传中" : "修改头像" }}</span>
              </span>
            </button>
            <input
              ref="uploadInputRef"
              class="avatar-editor__input"
              type="file"
              accept="image/*"
              @change="onAvatarFileChange"
            />
          </div>
        </el-form-item>
        <el-form-item label="歌手" required>
          <el-input v-model="form.name" maxlength="128" placeholder="请输入歌手名称" />
        </el-form-item>
        <el-form-item label="国籍">
          <el-input v-model="form.region" maxlength="32" placeholder="如：中国、日本、美国" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio-button :label="1">正常</el-radio-button>
            <el-radio-button :label="0">禁用</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="简介">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="4"
            maxlength="1000"
            show-word-limit
            placeholder="歌手简介"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="relationDialog.visible" :title="relationDialog.title" width="520px">
      <el-empty v-if="!relationDialog.items.length" :description="relationDialog.emptyText" />
      <el-table v-else :data="relationDialog.items" border stripe>
        <el-table-column prop="id" label="编号" width="100" />
        <el-table-column prop="name" label="名称" min-width="220" show-overflow-tooltip />
      </el-table>
    </el-dialog>

    <AvatarCropperDialog v-model="cropperVisible" :file="cropperFile" @confirm="onCropConfirm" />
  </div>
</template>

<style scoped>
.artist-page {
  padding: 16px;
}

.artist-page__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}

.artist-page__header h2 {
  margin: 0;
  color: var(--el-text-color-primary);
  font-size: 20px;
  font-weight: 700;
}

.artist-page__header p {
  margin: 6px 0 0;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.artist-page__actions {
  display: flex;
  gap: 10px;
}

.artist-search {
  margin-bottom: 12px;
}

.artist-avatar {
  width: 42px;
  height: 42px;
  overflow: hidden;
  border-radius: 50%;
  cursor: zoom-in;
}

.artist-pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.avatar-editor {
  display: inline-flex;
  align-items: center;
}

.avatar-editor__trigger {
  position: relative;
  width: 72px;
  height: 72px;
  padding: 0;
  overflow: hidden;
  border: 1px solid var(--el-border-color);
  border-radius: 50%;
  background: transparent;
  cursor: pointer;
}

.avatar-editor__trigger:disabled {
  cursor: wait;
}

.avatar-editor__image {
  width: 100%;
  height: 100%;
}

.avatar-editor__mask {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 12px;
  background: rgb(0 0 0 / 58%);
  opacity: 0;
  transition: opacity 0.18s ease;
}

.avatar-editor__trigger:hover .avatar-editor__mask {
  opacity: 1;
}

.avatar-editor__input {
  display: none;
}
</style>
