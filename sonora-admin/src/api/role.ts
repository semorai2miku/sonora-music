import { http } from "@/utils/http";

export type RoleItem = {
  id: number;
  code: string;
  name: string;
  description?: string;
  sort: number;
  status: number;
  userCount: number;
  permissionCount: number;
  protectedRole: boolean;
  permissionsEditable: boolean;
  createdAt?: string;
  updatedAt?: string;
};

export type RolePayload = {
  code: string;
  name: string;
  description?: string;
  sort?: number;
  status?: number;
};

export type PermissionNode = {
  id: number;
  label: string;
  code: string;
  type: "menu" | "button" | "api";
  visible: number;
  children: PermissionNode[];
};

type RolePageResult = {
  code: number;
  message: string;
  data: {
    list: RoleItem[];
    total: number;
    pageNum: number;
    pageSize: number;
  };
};

type RoleResult = {
  code: number;
  message: string;
  data: RoleItem;
};

type PermissionTreeResult = {
  code: number;
  message: string;
  data: PermissionNode[];
};

type PermissionIdsResult = {
  code: number;
  message: string;
  data: number[];
};

export const getRolePage = (params: object) =>
  http.request<RolePageResult>("get", "/api/admin/roles", { params });

export const createRole = (data: RolePayload) =>
  http.request<RoleResult>("post", "/api/admin/roles", { data });

export const updateRole = (id: number, data: RolePayload) =>
  http.request<RoleResult>("put", `/api/admin/roles/${id}`, { data });

export const deleteRole = (id: number) =>
  http.request<{ code: number; message: string }>(
    "delete",
    `/api/admin/roles/${id}`
  );

export const toggleRoleStatus = (id: number, status: number) =>
  http.request<RoleResult>(
    "put",
    `/api/admin/roles/${id}/status?status=${status}`
  );

export const getPermissionTree = () =>
  http.request<PermissionTreeResult>(
    "get",
    "/api/admin/roles/permission-tree"
  );

export const getRolePermissionIds = (id: number) =>
  http.request<PermissionIdsResult>(
    "get",
    `/api/admin/roles/${id}/permissions`
  );

export const updateRolePermissions = (id: number, permissionIds: number[]) =>
  http.request<PermissionIdsResult>(
    "put",
    `/api/admin/roles/${id}/permissions`,
    { data: { permissionIds } }
  );
