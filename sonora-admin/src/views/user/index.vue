<script setup lang="ts">
import { onMounted, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import {
  Delete,
  Edit,
  Plus,
  RefreshRight,
  Search,
  UserFilled
} from "@element-plus/icons-vue";
import {
  createClientUser,
  deleteClientUser,
  getClientUserPage,
  toggleClientUserStatus,
  updateClientUser,
  type ClientUserItem,
  type ClientUserPayload
} from "@/api/user";

const loading = ref(false);
const list = ref<ClientUserItem[]>([]);
const total = ref(0);
const pageNum = ref(1);
const pageSize = ref(20);
const keyword = ref("");
const statusFilter = ref<number | undefined>();

const dialogVisible = ref(false);
const isEdit = ref(false);
const submitting = ref(false);
const editingId = ref<number | null>(null);
const form = ref<ClientUserPayload>({
  username: "",
  password: "",
  profileId: "",
  avatar: "/default-avatar.svg",
  bio: "",
  status: 1
});

function loadData() {
  loading.value = true;
  getClientUserPage({
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
  form.value = {
    username: "",
    password: "",
    profileId: "",
    avatar: "/default-avatar.svg",
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
    profileId: row.profileId,
    avatar: row.avatar || "/default-avatar.svg",
    bio: row.bio || "",
    status: row.status
  };
  dialogVisible.value = true;
}

function handleSubmit() {
  if (!form.value.username?.trim()) {
    ElMessage.warning("请输入用户名");
    return;
  }
  if (!isEdit.value && !form.value.password?.trim()) {
    ElMessage.warning("请输入初始密码");
    return;
  }

  const payload = { ...form.value };
  if (isEdit.value && !payload.password) {
    delete payload.password;
  }

  submitting.value = true;
  const request = isEdit.value
    ? updateClientUser(editingId.value!, payload)
    : createClientUser(payload);

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

function handleDelete(row: ClientUserItem) {
  ElMessageBox.confirm(`确定删除用户「${row.username}」？`, "提示", {
    type: "warning"
  })
    .then(() => deleteClientUser(row.id))
    .then(() => {
      ElMessage.success("已删除");
      loadData();
    });
}

function handleToggleStatus(row: ClientUserItem) {
  const newStatus = row.status === 1 ? 0 : 1;
  toggleClientUserStatus(row.id, newStatus).then(() => {
    row.status = newStatus;
    ElMessage.success("状态已更新");
  });
}

onMounted(loadData);
</script>

<template>
  <div class="p-4">
    <h2 class="mb-4 text-xl font-bold">客户端用户管理</h2>

    <div class="mb-4 flex flex-wrap gap-3">
      <el-input
        v-model="keyword"
        clearable
        placeholder="搜索用户名 / 角色ID"
        style="width: 260px"
        @keyup.enter="onSearch"
      />
      <el-select
        v-model="statusFilter"
        clearable
        placeholder="状态"
        style="width: 120px"
      >
        <el-option label="正常" :value="1" />
        <el-option label="禁用" :value="0" />
      </el-select>
      <el-button type="primary" :icon="Search" @click="onSearch">搜索</el-button>
      <el-button :icon="RefreshRight" @click="loadData">刷新</el-button>
      <el-button type="success" :icon="Plus" @click="openCreate">新增用户</el-button>
    </div>

    <el-table v-loading="loading" :data="list" border stripe>
      <el-table-column prop="id" label="用户ID" width="90" />
      <el-table-column label="头像" width="90">
        <template #default="{ row }">
          <el-avatar :src="row.avatar" :icon="UserFilled" />
        </template>
      </el-table-column>
      <el-table-column prop="username" label="用户名" min-width="150" show-overflow-tooltip />
      <el-table-column prop="profileId" label="角色ID" min-width="140" show-overflow-tooltip />
      <el-table-column prop="bio" label="个人简介" min-width="220" show-overflow-tooltip />
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-switch :model-value="row.status === 1" @change="handleToggleStatus(row)" />
        </template>
      </el-table-column>
      <el-table-column prop="lastLoginAt" label="最后登录" width="170" />
      <el-table-column prop="createdAt" label="注册时间" width="170" />
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
      :title="isEdit ? '编辑用户' : '新增用户'"
      width="560px"
      destroy-on-close
    >
      <el-form :model="form" label-width="86px">
        <el-form-item label="用户名" required>
          <el-input v-model="form.username" placeholder="2-32 位，支持中英文与常见字符" />
        </el-form-item>
        <el-form-item label="密码" :required="!isEdit">
          <el-input
            v-model="form.password"
            type="password"
            show-password
            :placeholder="isEdit ? '留空则不修改密码' : '6-72 位初始密码'"
          />
        </el-form-item>
        <el-form-item label="角色ID">
          <el-input v-model="form.profileId" placeholder="留空自动生成，如 S123456" />
        </el-form-item>
        <el-form-item label="头像 URL">
          <el-input v-model="form.avatar" placeholder="/default-avatar.svg" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio-button :label="1">正常</el-radio-button>
            <el-radio-button :label="0">禁用</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="个人简介">
          <el-input v-model="form.bio" type="textarea" :rows="3" placeholder="用户个人简介" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>
