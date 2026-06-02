<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import {
  Delete,
  Edit,
  Plus,
  RefreshRight,
  Search,
  UserFilled
} from "@element-plus/icons-vue";
import ImageUploadCropper from "@/components/ImageUploadCropper.vue";
import {
  batchDeleteClientUsers,
  createClientUser,
  deleteClientUser,
  getClientUserPage,
  toggleClientUserStatus,
  updateClientUser,
  type ClientUserItem,
  type ClientUserPayload
} from "@/api/user";

const DEFAULT_AVATAR = "/default-avatar.svg";
const EMAIL_PATTERN = /^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$/;
const PASSWORD_PATTERN = /^[\x21-\x7E]{6,72}$/;
const PHONE_PATTERN = /^1[3-9]\d{9}$/;

const loading = ref(false);
const list = ref<ClientUserItem[]>([]);
const total = ref(0);
const pageNum = ref(1);
const pageSize = ref(20);
const selectedRows = ref<ClientUserItem[]>([]);

const filters = ref({
  username: "",
  email: "",
  phone: "",
  status: undefined as number | undefined
});

const dialogVisible = ref(false);
const isEdit = ref(false);
const submitting = ref(false);
const editingId = ref<number | null>(null);
const form = ref<ClientUserPayload>({
  username: "",
  password: "",
  email: "",
  phone: "",
  avatar: DEFAULT_AVATAR,
  bio: "",
  status: 1
});

const dialogTitle = computed(() => (isEdit.value ? "修改用户" : "新增用户"));

function avatarOf(avatar?: string) {
  return avatar || DEFAULT_AVATAR;
}

function loadData() {
  loading.value = true;
  getClientUserPage({
    pageNum: pageNum.value,
    pageSize: pageSize.value,
    username: filters.value.username,
    email: filters.value.email,
    phone: filters.value.phone,
    status: filters.value.status
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
    username: "",
    email: "",
    phone: "",
    status: undefined
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
    username: "",
    password: "",
    email: "",
    phone: "",
    avatar: DEFAULT_AVATAR,
    bio: "",
    status: 1
  };
  dialogVisible.value = true;
}

function openEdit(row: ClientUserItem) {
  isEdit.value = true;
  editingId.value = row.id;
  form.value = {
    username: row.username,
    password: "",
    email: row.email,
    phone: row.phone || "",
    avatar: avatarOf(row.avatar),
    bio: row.bio || "",
    status: row.status
  };
  dialogVisible.value = true;
}

function validateForm() {
  if (!form.value.username?.trim()) {
    ElMessage.warning("请输入用户名");
    return false;
  }
  if (!form.value.email?.trim() || !EMAIL_PATTERN.test(form.value.email.trim())) {
    ElMessage.warning("请输入正确的邮箱");
    return false;
  }
  if (!isEdit.value && !form.value.password?.trim()) {
    ElMessage.warning("请输入初始密码");
    return false;
  }
  if (form.value.password && !PASSWORD_PATTERN.test(form.value.password)) {
    ElMessage.warning("密码需为 6-72 位，且只能包含字母、数字和特殊符号");
    return false;
  }
  if (form.value.phone?.trim() && !PHONE_PATTERN.test(form.value.phone.trim())) {
    ElMessage.warning("手机号需为 11 位大陆手机号");
    return false;
  }
  return true;
}

function getErrorMessage(error: any, fallback = "操作失败") {
  return error?.response?.data?.message || error?.message || fallback;
}

async function handleSubmit() {
  if (!validateForm()) return;

  const payload: ClientUserPayload = {
    username: form.value.username?.trim(),
    email: form.value.email?.trim(),
    avatar: form.value.avatar || DEFAULT_AVATAR,
    status: form.value.status
  };
  const phone = form.value.phone?.trim();
  const bio = form.value.bio?.trim();
  if (phone) {
    payload.phone = phone;
  }
  if (bio) {
    payload.bio = bio;
  }
  if (form.value.password) {
    payload.password = form.value.password;
  }

  submitting.value = true;
  try {
    const res = isEdit.value
      ? await updateClientUser(editingId.value!, payload)
      : await createClientUser(payload);
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

function handleDelete(row: ClientUserItem) {
  ElMessageBox.confirm(`确定删除用户「${row.username}」？删除后将清除该用户的歌单、喜欢和评论数据。`, "删除用户", {
    type: "warning"
  })
    .then(() => deleteClientUser(row.id))
    .then(() => {
      ElMessage.success(`已删除用户：${row.username}`);
      loadData();
    });
}

function handleToggleStatus(row: ClientUserItem, enabled: boolean) {
  const oldStatus = row.status;
  const newStatus = enabled ? 1 : 0;
  row.status = newStatus;
  toggleClientUserStatus(row.id, newStatus)
    .then(() => {
      ElMessage.success("状态已更新");
    })
    .catch(() => {
      row.status = oldStatus;
    });
}

function handleBatchDelete() {
  if (!selectedRows.value.length) {
    ElMessage.warning("请先选择要删除的用户");
    return;
  }
  const names = selectedRows.value.map(item => item.username).join("、");
  ElMessageBox.confirm(`确定批量删除 ${selectedRows.value.length} 名用户？删除后将清除这些用户的所有个人数据。`, "批量删除", {
    type: "warning"
  })
    .then(() => batchDeleteClientUsers(selectedRows.value.map(item => item.id)))
    .then(res => {
      const deletedNames = (res.data.deleted || []).map(item => item.username).join("、") || names;
      ElMessage.success(`成功删除：${deletedNames}`);
      selectedRows.value = [];
      loadData();
    });
}

function showBio(row: ClientUserItem) {
  ElMessageBox.alert(row.bio || "暂无简介", `${row.username} 的简介`, {
    confirmButtonText: "关闭"
  });
}

function onSelectionChange(rows: ClientUserItem[]) {
  selectedRows.value = rows;
}

onMounted(loadData);
</script>

<template>
  <div class="user-page">
    <div class="user-page__header">
      <div>
        <h2>客户端用户管理</h2>
        <p>管理客户端注册用户、头像、资料和账号状态</p>
      </div>
      <div class="user-page__actions">
        <el-button :icon="RefreshRight" @click="loadData">刷新</el-button>
        <el-button type="primary" :icon="Plus" @click="openCreate">新增用户</el-button>
        <el-button type="danger" :icon="Delete" @click="handleBatchDelete">批量删除</el-button>
      </div>
    </div>

    <el-form class="user-search" :model="filters" inline>
      <el-form-item label="用户名">
        <el-input v-model="filters.username" clearable placeholder="请输入用户名" @keyup.enter="onSearch" />
      </el-form-item>
      <el-form-item label="邮箱">
        <el-input v-model="filters.email" clearable placeholder="请输入邮箱" @keyup.enter="onSearch" />
      </el-form-item>
      <el-form-item label="手机号">
        <el-input v-model="filters.phone" clearable placeholder="请输入手机号" @keyup.enter="onSearch" />
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="filters.status" clearable placeholder="全部" style="width: 120px">
          <el-option label="正常" :value="1" />
          <el-option label="禁用" :value="0" />
        </el-select>
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
      <el-table-column prop="id" label="用户编号" width="100" sortable />
      <el-table-column label="头像" width="96">
        <template #default="{ row }">
          <el-image
            class="user-avatar"
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
      <el-table-column prop="username" label="用户名" min-width="140" show-overflow-tooltip />
      <el-table-column prop="email" label="邮箱" min-width="190" show-overflow-tooltip />
      <el-table-column prop="phone" label="手机号" min-width="140" show-overflow-tooltip />
      <el-table-column label="简介" width="110">
        <template #default="{ row }">
          <el-button link type="primary" @click="showBio(row)">
            {{ row.bio ? "查看详情" : "暂无简介" }}
          </el-button>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="88">
        <template #default="{ row }">
          <el-switch
            :model-value="row.status === 1"
            inline-prompt
            active-text="正常"
            inactive-text="禁用"
            @change="value => handleToggleStatus(row, Boolean(value))"
          />
        </template>
      </el-table-column>
      <el-table-column prop="createdAt" label="创建时间" width="170" />
      <el-table-column prop="updatedAt" label="修改时间" width="170" />
      <el-table-column label="操作" width="150" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" :icon="Edit" @click="openEdit(row)">修改</el-button>
          <el-button link type="danger" :icon="Delete" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="user-pagination">
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
        <el-form :model="form" label-width="86px">
        <el-form-item label="头像">
          <ImageUploadCropper
            v-model="form.avatar"
            :fallback-src="DEFAULT_AVATAR"
            dir="avatar"
            shape="circle"
            :size="72"
            :output-width="600"
            :output-height="600"
            title="修改头像"
            mask-text="修改头像"
            crop-title="裁剪用户头像"
            confirm-text="使用头像"
            hint="拖动裁剪框或图片调整头像区域，超出图片范围的部分会自动补白。"
          />
        </el-form-item>
        <el-form-item label="用户名" required>
          <el-input v-model="form.username" placeholder="2-32 位，支持中英文与常见字符" />
        </el-form-item>
        <el-form-item label="邮箱" required>
          <el-input v-model="form.email" placeholder="user@example.com" />
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model="form.phone" maxlength="11" placeholder="可选，11 位手机号，如 13800138000" />
        </el-form-item>
        <el-form-item label="密码" :required="!isEdit">
          <el-input
            v-model="form.password"
            type="password"
            show-password
            :placeholder="isEdit ? '留空则不修改密码' : '6 位以上，支持字母数字和特殊符号'"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio-button :label="1">正常</el-radio-button>
            <el-radio-button :label="0">禁用</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="简介">
          <el-input v-model="form.bio" type="textarea" :rows="4" maxlength="512" show-word-limit placeholder="用户个人简介" />
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
.user-page {
  padding: 16px;
}

.user-page__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}

.user-page__header h2 {
  margin: 0;
  color: var(--el-text-color-primary);
  font-size: 20px;
  font-weight: 700;
}

.user-page__header p {
  margin: 6px 0 0;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.user-page__actions {
  display: flex;
  gap: 10px;
}

.user-search {
  margin-bottom: 12px;
}

.user-avatar {
  width: 42px;
  height: 42px;
  overflow: hidden;
  border-radius: 50%;
  cursor: zoom-in;
}

.user-pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

</style>
