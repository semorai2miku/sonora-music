import { http } from "@/utils/http";

export type AlbumItem = {
  id: number;
  name: string;
  cover: string;
  artistId: number;
  releaseDate: string;
  type: string;
  status: number;
};

type PageResult = { code: number; data: { list: AlbumItem[]; total: number } };
type ItemResult = { code: number; data: AlbumItem };

export const getAlbumPage = (params: object) => http.request<PageResult>("get", "/api/admin/albums", { params });
export const createAlbum = (data: object) => http.request<ItemResult>("post", "/api/admin/albums", { data });
export const updateAlbum = (id: number, data: object) => http.request<ItemResult>("put", `/api/admin/albums/${id}`, { data });
export const deleteAlbum = (id: number) => http.request("delete", `/api/admin/albums/${id}`);
