import { http } from "@/utils/http";

export type UserResult = {
  success: boolean;
  data: {
    /** 头像 */
    avatar: string;
    /** 用户名 */
    username: string;
    /** 昵称 */
    nickname: string;
    /** 当前登录用户的角色 */
    roles: Array<string>;
    /** 按钮级别权限 */
    permissions: Array<string>;
    /** `token` */
    accessToken: string;
    /** 用于调用刷新`accessToken`的接口时所需的`token` */
    refreshToken: string;
    /** `accessToken`的过期时间（格式'xxxx/xx/xx xx:xx:xx'） */
    expires: Date;
  };
};

export type RefreshTokenResult = {
  success: boolean;
  data: {
    /** `token` */
    accessToken: string;
    /** 用于调用刷新`accessToken`的接口时所需的`token` */
    refreshToken: string;
    /** `accessToken`的过期时间（格式'xxxx/xx/xx xx:xx:xx'） */
    expires: Date;
  };
};

/** 登录 */
export const getLogin = (data?: object) => {
  return http.request<UserResult>("post", "/login", { data });
};

/** 刷新`token` */
export const refreshTokenApi = (data?: object) => {
  return http.request<RefreshTokenResult>("post", "/refresh-token", { data });
};

export type ClientUserItem = {
  id: number;
  profileId: string;
  username: string;
  nickname?: string;
  avatar?: string;
  bio?: string;
  status: number;
  lastLoginAt?: string;
  createdAt?: string;
  updatedAt?: string;
};

export type ClientUserPayload = {
  username?: string;
  password?: string;
  profileId?: string;
  avatar?: string;
  bio?: string;
  status?: number;
};

type ClientUserPageResult = {
  code: number;
  data: {
    list: ClientUserItem[];
    total: number;
    pageNum: number;
    pageSize: number;
  };
};

type ClientUserResult = {
  code: number;
  data: ClientUserItem;
};

export const getClientUserPage = (params: object) =>
  http.request<ClientUserPageResult>("get", "/api/admin/users", { params });

export const createClientUser = (data: ClientUserPayload) =>
  http.request<ClientUserResult>("post", "/api/admin/users", { data });

export const updateClientUser = (id: number, data: ClientUserPayload) =>
  http.request<ClientUserResult>("put", `/api/admin/users/${id}`, { data });

export const deleteClientUser = (id: number) =>
  http.request("delete", `/api/admin/users/${id}`);

export const toggleClientUserStatus = (id: number, status: number) =>
  http.request<ClientUserResult>("put", `/api/admin/users/${id}/status?status=${status}`);
