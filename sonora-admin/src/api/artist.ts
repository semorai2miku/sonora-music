import { http } from "@/utils/http";

export type ArtistItem = {
  id: number;
  name: string;
  avatar: string;
  region: string;
  description: string;
  status: number;
  createdAt?: string;
  updatedAt?: string;
  songCount?: number;
  albumCount?: number;
  songs?: ArtistRelatedItem[];
  albums?: ArtistRelatedItem[];
};

export type ArtistRelatedItem = {
  id: number;
  name: string;
};

type ListResult = { code: number; data: ArtistItem[] };
type PageResult = { code: number; data: { list: ArtistItem[]; total: number } };
type ItemResult = { code: number; data: ArtistItem };
type BatchDeleteResult = {
  code: number;
  data: {
    count: number;
    deleted: Array<{ id: number; name: string }>;
  };
};
type UploadResult = {
  code: number;
  data: {
    objectKey: string;
    url: string;
    fileName: string;
    fileSize: number;
  };
};

export const getArtistAll = () => http.request<ListResult>("get", "/api/admin/artists/all");
export const getArtistPage = (params: object) => http.request<PageResult>("get", "/api/admin/artists", { params });
export const getArtistOptions = (params: object) =>
  http.request<PageResult>("get", "/api/admin/artists/options", { params });
export const createArtist = (data: object) => http.request<ItemResult>("post", "/api/admin/artists", { data });
export const updateArtist = (id: number, data: object) => http.request<ItemResult>("put", `/api/admin/artists/${id}`, { data });
export const deleteArtist = (id: number) => http.request("delete", `/api/admin/artists/${id}`);
export const batchDeleteArtists = (ids: number[]) =>
  http.request<BatchDeleteResult>("post", "/api/admin/artists/batch-delete", {
    data: { ids }
  });

export const uploadArtistAvatar = (file: File) => {
  const formData = new FormData();
  formData.append("file", file);
  formData.append("dir", "artist-avatar");
  return http.request<UploadResult>("post", "/api/admin/upload", {
    data: formData,
    headers: { "Content-Type": "multipart/form-data" }
  });
};
