import { http } from "@/utils/http";

export type ArtistItem = {
  id: number;
  name: string;
  avatar: string;
  region: string;
  description: string;
  status: number;
};

type ListResult = { code: number; data: ArtistItem[] };
type PageResult = { code: number; data: { list: ArtistItem[]; total: number } };
type ItemResult = { code: number; data: ArtistItem };

export const getArtistAll = () => http.request<ListResult>("get", "/api/admin/artists/all");
export const getArtistPage = (params: object) => http.request<PageResult>("get", "/api/admin/artists", { params });
export const createArtist = (data: object) => http.request<ItemResult>("post", "/api/admin/artists", { data });
export const updateArtist = (id: number, data: object) => http.request<ItemResult>("put", `/api/admin/artists/${id}`, { data });
export const deleteArtist = (id: number) => http.request("delete", `/api/admin/artists/${id}`);
