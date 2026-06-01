import { http } from "@/utils/http";

type SongPageResult = {
  code: number;
  data: {
    list: SongItem[];
    total: number;
    pageNum: number;
    pageSize: number;
  };
};

type SongResult = {
  code: number;
  data: SongItem;
};

type BatchDeleteResult = {
  code: number;
  data: {
    count: number;
    deleted: Array<{ id: number; name: string }>;
  };
};

export type SongItem = {
  id: number;
  name: string;
  artistIds: string;
  albumId: number;
  fileKey: string;
  duration: number;
  fileSize: number;
  format: string;
  cover: string;
  lyrics: string;
  playCount: number;
  status: number;
  createdAt: string;
};

/** 歌曲分页列表 */
export const getSongPage = (params: object) => {
  return http.request<SongPageResult>("get", "/api/admin/songs", { params });
};

/** 歌曲详情 */
export const getSongById = (id: number) => {
  return http.request<SongResult>("get", `/api/admin/songs/${id}`);
};

/** 新增歌曲 (form-data) */
export const createSong = (formData: FormData) => {
  return http.request<SongResult>("post", "/api/admin/songs", {
    data: formData,
    headers: { "Content-Type": "multipart/form-data" }
  });
};

/** 编辑歌曲 */
export const updateSong = (id: number, data: object) => {
  return http.request<SongResult>("put", `/api/admin/songs/${id}`, { data });
};

/** 替换歌曲文件并更新信息 */
export const replaceSong = (id: number, formData: FormData) => {
  return http.request<SongResult>("post", `/api/admin/songs/${id}/replace`, {
    data: formData,
    headers: { "Content-Type": "multipart/form-data" }
  });
};

/** 删除歌曲 */
export const deleteSong = (id: number) => {
  return http.request("delete", `/api/admin/songs/${id}`);
};

/** 批量删除歌曲 */
export const batchDeleteSongs = (ids: number[]) => {
  return http.request<BatchDeleteResult>("post", "/api/admin/songs/batch-delete", {
    data: { ids }
  });
};

/** 切换上架状态 */
export const toggleSongStatus = (id: number, status: number) => {
  return http.request("put", `/api/admin/songs/${id}/status?status=${status}`);
};
