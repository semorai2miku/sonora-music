<script setup lang="ts">
import { computed, nextTick, onMounted, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import {
  Delete,
  Edit,
  Key,
  Plus,
  RefreshRight,
  Search
} from "@element-plus/icons-vue";
import {
  createRole,
  deleteRole,
  getPermissionTree,
  getRolePage,
  getRolePermissionIds,
  toggleRoleStatus,
  updateRole,
  updateRolePermissions,
  type PermissionNode,
  type RoleItem,
  type RolePayload
} from "@/api/role";

defineOptions({
  name: "SystemRole"
});

const loading = ref(false);
const list = ref<RoleItem[]>([]);
const total = ref(0);
const pageNum = ref(1);
const pageSize = ref(20);
const filters = ref({
  keyword: "",
  status: undefined as number | undefined
});

const dialogVisible = ref(false);
const submitting = ref(false);
const editingRole = ref<RoleItem | null>(null);
const form = ref<RolePayload>({
  code: "",
  name: "",
  description: "",
  sort: 0,
  status: 1
});

const permissionDrawerVisible = ref(false);
const permissionLoading = ref(false);
const permissionSaving = ref(false);
const permissionTreeRef = ref<any>();
const permissionTree = ref<PermissionNode[]>([]);
const permissionRole = ref<RoleItem | null>(null);

const dialogTitle = computed(() =>
  editingRole.value ? "编辑角色" : "新增角色"
);

function getErrorMessage(error: any, fallback = "操作失败") {
  return error?.response?.data?.message || error?.message || fallback;
}

function ensureSuccess(response: any, fallback: string) {
  if (response?.code !== 200) {
    throw new Error(response?.message || fallback);
  }
  return response;
}

async function loadData() {
  loading.value = true;
  try {
    const res = ensureSuccess(
      await getRolePage({
        pageNum: pageNum.value,
        pageSize: pageSize.value,
        keyword: filters.value.keyword.trim() || undefined,
        status: filters.value.status
      }),
      "角色列表加载失败"
    );
    list.value = res.data.list || [];
    total.value = res.data.total || 0;
  } catch (error: any) {
    ElMessage.error(getErrorMessage(error, "角色列表加载失败"));
  } finally {
    loading.value = false;
  }
}

function onSearch() {
  pageNum.value = 1;
  loadData();
}

function onReset() {
  filters.value = { keyword: "", status: undefined };
  pageNum.value = 1;
  loadData();
}

function onPageChange(page: number) {
  pageNum.value = page;
  loadData();
}

function openCreate() {
  editingRole.value = null;
  form.value = {
    code: "",
    name: "",
    description: "",
    sort: 0,
    status: 1
  };
  dialogVisible.value = true;
}

function openEdit(row: RoleItem) {
  editingRole.value = row;
  form.value = {
    code: row.code,
    name: row.name,
    description: row.description || "",
    sort: row.sort,
    status: row.status
  };
  dialogVisible.value = true;
}

function normalizeRoleCode() {
  form.value.code = form.value.code.trim().toUpperCase();
}

function validateForm() {
  normalizeRoleCode();
  if (!/^[A-Z][A-Z0-9_]{1,63}$/.test(form.value.code)) {
    ElMessage.warning("角色编码需以字母开头，并使用大写字母、数字或下划线");
    return false;
  }
  if (!form.value.name.trim()) {
    ElMessage.warning("请输入角色名称");
    return false;
  }
  return true;
}

async function handleSubmit() {
  if (!validateForm()) return;
  const payload: RolePayload = {
    code: form.value.code,
    name: form.value.name.trim(),
    description: form.value.description?.trim() || undefined,
    sort: Math.max(0, Number(form.value.sort) || 0),
    status: form.value.status
  };

  submitting.value = true;
  try {
    const response = editingRole.value
      ? await updateRole(editingRole.value.id, payload)
      : await createRole(payload);
    ensureSuccess(
      response,
      editingRole.value ? "角色修改失败" : "角色创建失败"
    );
    ElMessage.success(editingRole.value ? "角色修改成功" : "角色创建成功");
    dialogVisible.value = false;
    loadData();
  } catch (error: any) {
    ElMessage.error(
      getErrorMessage(
        error,
        editingRole.value ? "角色修改失败" : "角色创建失败"
      )
    );
  } finally {
    submitting.value = false;
  }
}

async function handleToggleStatus(row: RoleItem, enabled: boolean) {
  const oldStatus = row.status;
  row.status = enabled ? 1 : 0;
  try {
    ensureSuccess(
      await toggleRoleStatus(row.id, row.status),
      "角色状态更新失败"
    );
    ElMessage.success("角色状态已更新");
  } catch (error: any) {
    row.status = oldStatus;
    ElMessage.error(getErrorMessage(error, "角色状态更新失败"));
  }
}

async function handleDelete(row: RoleItem) {
  if (row.protectedRole) {
    ElMessage.warning("系统内置角色不允许删除");
    return;
  }
  if (row.userCount > 0) {
    ElMessage.warning(`该角色仍关联 ${row.userCount} 个账号`);
    return;
  }
  try {
    await ElMessageBox.confirm(
      `确定删除角色「${row.name}」？已分配的菜单权限也会一并清除。`,
      "删除角色",
      { type: "warning" }
    );
    ensureSuccess(await deleteRole(row.id), "角色删除失败");
    ElMessage.success("角色已删除");
    loadData();
  } catch (error: any) {
    if (error === "cancel" || error === "close") return;
    ElMessage.error(getErrorMessage(error, "角色删除失败"));
  }
}

function selectedLeafKeys(
  nodes: PermissionNode[],
  selectedIds: Set<number>
) {
  const keys: number[] = [];
  const visit = (items: PermissionNode[]) => {
    for (const node of items) {
      if (node.children?.length) {
        visit(node.children);
      } else if (selectedIds.has(node.id)) {
        keys.push(node.id);
      }
    }
  };
  visit(nodes);
  return keys;
}

async function openPermissions(row: RoleItem) {
  if (!row.permissionsEditable) {
    ElMessage.info(
      row.code === "ADMIN"
        ? "超级管理员固定拥有全部系统权限"
        : "客户端内置角色不参与后台菜单分配"
    );
    return;
  }
  permissionRole.value = row;
  permissionDrawerVisible.value = true;
  permissionLoading.value = true;
  try {
    const [treeRes, idsRes] = await Promise.all([
      getPermissionTree(),
      getRolePermissionIds(row.id)
    ]);
    ensureSuccess(treeRes, "权限树加载失败");
    ensureSuccess(idsRes, "角色权限加载失败");
    permissionTree.value = treeRes.data || [];
    await nextTick();
    permissionTreeRef.value?.setCheckedKeys(
      selectedLeafKeys(permissionTree.value, new Set(idsRes.data || [])),
      false
    );
  } catch (error: any) {
    ElMessage.error(getErrorMessage(error, "权限加载失败"));
    permissionDrawerVisible.value = false;
  } finally {
    permissionLoading.value = false;
  }
}

async function savePermissions() {
  if (!permissionRole.value || !permissionTreeRef.value) return;
  const permissionIds = [
    ...permissionTreeRef.value.getCheckedKeys(false),
    ...permissionTreeRef.value.getHalfCheckedKeys()
  ].map(Number);
  const uniqueIds = [...new Set(permissionIds)];

  permissionSaving.value = true;
  try {
    ensureSuccess(
      await updateRolePermissions(permissionRole.value.id, uniqueIds),
      "权限保存失败"
    );
    ElMessage.success("菜单权限已保存，下次登录后生效");
    permissionDrawerVisible.value = false;
    loadData();
  } catch (error: any) {
    ElMessage.error(getErrorMessage(error, "权限保存失败"));
  } finally {
    permissionSaving.value = false;
  }
}

function permissionTypeName(type: string) {
  if (type === "button") return "按钮";
  if (type === "api") return "接口";
  return "菜单";
}

onMounted(loadData);
</script>

<template>
  <div class="role-page">
    <div class="role-page__header">
      <div>
        <h2>角色管理</h2>
        <p>管理后台登录角色、启用状态和可访问的菜单权限</p>
      </div>
      <div class="role-page__actions">
        <el-button :icon="RefreshRight" @click="loadData">刷新</el-button>
        <el-button type="primary" :icon="Plus" @click="openCreate">
          新增角色
        </el-button>
      </div>
    </div>

    <el-form class="role-search" :model="filters" inline>
      <el-form-item label="角色">
        <el-input
          v-model="filters.keyword"
          clearable
          placeholder="角色名称或编码"
          @keyup.enter="onSearch"
        />
      </el-form-item>
      <el-form-item label="状态">
        <el-select
          v-model="filters.status"
          clearable
          placeholder="全部"
          style="width: 120px"
        >
          <el-option label="正常" :value="1" />
          <el-option label="禁用" :value="0" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :icon="Search" @click="onSearch">
          搜索
        </el-button>
        <el-button :icon="RefreshRight" @click="onReset">重置</el-button>
      </el-form-item>
    </el-form>

    <el-table v-loading="loading" :data="list" border stripe>
      <el-table-column prop="id" label="角色编号" width="100" />
      <el-table-column label="角色名称" min-width="170">
        <template #default="{ row }">
          <div class="role-name-cell">
            <span>{{ row.name }}</span>
            <el-tag v-if="row.protectedRole" size="small" type="info">
              系统内置
            </el-tag>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="角色编码" min-width="150">
        <template #default="{ row }">
          <el-tag effect="plain">{{ row.code }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column
        prop="description"
        label="说明"
        min-width="220"
        show-overflow-tooltip
      >
        <template #default="{ row }">
          {{ row.description || "-" }}
        </template>
      </el-table-column>
      <el-table-column prop="userCount" label="关联账号" width="100" align="center" />
      <el-table-column prop="permissionCount" label="菜单权限" width="100" align="center" />
      <el-table-column prop="sort" label="排序" width="78" align="center" />
      <el-table-column label="状态" width="92" align="center">
        <template #default="{ row }">
          <el-switch
            :model-value="row.status === 1"
            :disabled="row.protectedRole"
            inline-prompt
            active-text="正常"
            inactive-text="禁用"
            @change="value => handleToggleStatus(row, Boolean(value))"
          />
        </template>
      </el-table-column>
      <el-table-column prop="createdAt" label="创建时间" width="170" />
      <el-table-column label="操作" width="245" fixed="right">
        <template #default="{ row }">
          <el-button
            link
            type="primary"
            :icon="Key"
            :disabled="!row.permissionsEditable"
            @click="openPermissions(row)"
          >
            分配权限
          </el-button>
          <el-button link type="primary" :icon="Edit" @click="openEdit(row)">
            编辑
          </el-button>
          <el-button
            link
            type="danger"
            :icon="Delete"
            :disabled="row.protectedRole || row.userCount > 0"
            @click="handleDelete(row)"
          >
            删除
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="role-pagination">
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
      width="560px"
      destroy-on-close
    >
      <el-form :model="form" label-width="88px">
        <el-form-item label="角色名称" required>
          <el-input
            v-model="form.name"
            maxlength="64"
            placeholder="例如：内容审核员"
          />
        </el-form-item>
        <el-form-item label="角色编码" required>
          <el-input
            v-model="form.code"
            :disabled="Boolean(editingRole?.protectedRole)"
            maxlength="64"
            placeholder="例如：REVIEWER"
            @blur="normalizeRoleCode"
          />
        </el-form-item>
        <el-form-item label="显示排序">
          <el-input-number v-model="form.sort" :min="0" :max="9999" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group
            v-model="form.status"
            :disabled="Boolean(editingRole?.protectedRole)"
          >
            <el-radio-button :label="1">正常</el-radio-button>
            <el-radio-button :label="0">禁用</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="角色说明">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="4"
            maxlength="256"
            show-word-limit
            placeholder="说明该角色的职责范围"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">
          保存
        </el-button>
      </template>
    </el-dialog>

    <el-drawer
      v-model="permissionDrawerVisible"
      :title="`分配菜单权限${permissionRole ? ` · ${permissionRole.name}` : ''}`"
      size="520px"
      destroy-on-close
    >
      <div class="permission-drawer">
        <div class="permission-drawer__hint">
          勾选角色登录后台后可以看到的菜单。保存后，该角色账号下次登录时生效。
        </div>
        <div v-loading="permissionLoading" class="permission-tree-wrap">
          <el-tree
            ref="permissionTreeRef"
            :data="permissionTree"
            node-key="id"
            show-checkbox
            default-expand-all
            :expand-on-click-node="false"
          >
            <template #default="{ data }">
              <div class="permission-node">
                <span>{{ data.label }}</span>
                <span class="permission-node__code">{{ data.code }}</span>
                <el-tag size="small" effect="plain">
                  {{ permissionTypeName(data.type) }}
                </el-tag>
              </div>
            </template>
          </el-tree>
        </div>
      </div>
      <template #footer>
        <el-button @click="permissionDrawerVisible = false">取消</el-button>
        <el-button
          type="primary"
          :loading="permissionSaving"
          @click="savePermissions"
        >
          保存权限
        </el-button>
      </template>
    </el-drawer>
  </div>
</template>

<style scoped>
.role-page {
  padding: 16px;
}

.role-page__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}

.role-page__header h2 {
  margin: 0;
  color: var(--el-text-color-primary);
  font-size: 20px;
  font-weight: 700;
}

.role-page__header p {
  margin: 6px 0 0;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.role-page__actions {
  display: flex;
  gap: 10px;
}

.role-search {
  margin-bottom: 12px;
}

.role-name-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}

.role-pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.permission-drawer {
  display: flex;
  height: 100%;
  min-height: 0;
  flex-direction: column;
}

.permission-drawer__hint {
  margin-bottom: 14px;
  padding: 10px 12px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
  line-height: 1.6;
  background: var(--el-fill-color-light);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
}

.permission-tree-wrap {
  min-height: 260px;
  overflow: auto;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
}

.permission-tree-wrap :deep(.el-tree-node__content) {
  min-height: 38px;
  padding-right: 10px;
}

.permission-node {
  display: flex;
  min-width: 0;
  flex: 1;
  align-items: center;
  gap: 8px;
}

.permission-node__code {
  overflow: hidden;
  flex: 1;
  color: var(--el-text-color-secondary);
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

@media (max-width: 760px) {
  .role-page__header {
    flex-direction: column;
  }

  .role-page__actions {
    width: 100%;
  }
}
</style>
