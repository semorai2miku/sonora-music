import { http } from "@/utils/http";

export type AlbumItem = {
  id: number;
  name: string;
  cover: string;
  artistId: number;
  releaseDate: string;
  description: string;
  type: string;
  status: number;
};

type PageResult = { code: number; data: { list: AlbumItem[]; total: number } };
type ItemResult = { code: number; data: AlbumItem };
type BatchDeleteResult = {
  code: number;
  data: {
    count: number;
    deleted: Array<{ id: number; name: string }>;
  };
};

export const getAlbumPage = (params: object) => http.request<PageResult>("get", "/api/admin/albums", { params });
export const getAlbumOptions = (params: object) =>
  http.request<PageResult>("get", "/api/admin/albums/options", { params });
export const createAlbum = (data: object) => http.request<ItemResult>("post", "/api/admin/albums", { data });
export const updateAlbum = (id: number, data: object) => http.request<ItemResult>("put", `/api/admin/albums/${id}`, { data });
export const deleteAlbum = (id: number) => http.request("delete", `/api/admin/albums/${id}`);
export const batchDeleteAlbums = (ids: number[]) =>
  http.request<BatchDeleteResult>("post", "/api/admin/albums/batch-delete", {
    data: { ids }
  });
