import { http } from "@/utils/http";

export type BannerItem = {
  id: number;
  title?: string;
  imageUrl: string;
  linkUrl?: string;
  sort: number;
  status: number;
  createdAt?: string;
  updatedAt?: string;
};

export type BannerPayload = {
  title?: string;
  imageUrl: string;
  linkUrl?: string;
  sort?: number;
  status?: number;
};

type PageResult = {
  code: number;
  data: {
    list: BannerItem[];
    total: number;
    pageNum: number;
    pageSize: number;
  };
};

type ItemResult = {
  code: number;
  data: BannerItem;
};

export const getBannerPage = (params: object) =>
  http.request<PageResult>("get", "/api/admin/banners", { params });

export const createBanner = (data: BannerPayload) =>
  http.request<ItemResult>("post", "/api/admin/banners", { data });

export const updateBanner = (id: number, data: BannerPayload) =>
  http.request<ItemResult>("put", `/api/admin/banners/${id}`, { data });

export const deleteBanner = (id: number) =>
  http.request("delete", `/api/admin/banners/${id}`);

export const toggleBannerStatus = (id: number, status: number) =>
  http.request<ItemResult>("put", `/api/admin/banners/${id}/status?status=${status}`);
