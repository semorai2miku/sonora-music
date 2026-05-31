import { http } from "@/utils/http";
import type { SongItem } from "@/api/song";

export type PlaylistItem = {
  id: number;
  name: string;
  cover?: string;
  userId?: number;
  publisher?: string;
  publisherName?: string;
  description?: string;
  tags?: string;
  playCount?: number;
  collectCount?: number;
  status: number;
  songCount?: number;
  songIds?: number[];
  songs?: SongItem[];
  createdAt?: string;
  updatedAt?: string;
};

export type PlaylistPayload = {
  name: string;
  cover?: string;
  description?: string;
  tags?: string;
  status?: number;
  songIds?: number[];
};

export type PlaylistPublisherOption = {
  id: number;
  name: string;
};

type PageResult = {
  code: number;
  data: {
    list: PlaylistItem[];
    total: number;
    pageNum: number;
    pageSize: number;
  };
};

type ItemResult = {
  code: number;
  data: PlaylistItem;
};

type BatchDeleteResult = {
  code: number;
  data: {
    count: number;
    deleted: Array<{ id: number; name: string }>;
  };
};

export const getPlaylistPage = (params: object) =>
  http.request<PageResult>("get", "/api/admin/playlists", { params });

export const getPlaylistPublisherOptions = (params: object) =>
  http.request<{
    code: number;
    data: {
      list: PlaylistPublisherOption[];
      total: number;
      pageNum: number;
      pageSize: number;
    };
  }>("get", "/api/admin/playlists/publishers/options", { params });

export const getPlaylistById = (id: number) =>
  http.request<ItemResult>("get", `/api/admin/playlists/${id}`);

export const createPlaylist = (data: PlaylistPayload) =>
  http.request<ItemResult>("post", "/api/admin/playlists", { data });

export const updatePlaylist = (id: number, data: PlaylistPayload) =>
  http.request<ItemResult>("put", `/api/admin/playlists/${id}`, { data });

export const deletePlaylist = (id: number) =>
  http.request("delete", `/api/admin/playlists/${id}`);

export const batchDeletePlaylists = (ids: number[]) =>
  http.request<BatchDeleteResult>("post", "/api/admin/playlists/batch-delete", {
    data: { ids }
  });

export const togglePlaylistStatus = (id: number, status: number) =>
  http.request<ItemResult>(
    "put",
    `/api/admin/playlists/${id}/status?status=${status}`
  );

export const updatePlaylistSongs = (id: number, songIds: number[]) =>
  http.request<ItemResult>("put", `/api/admin/playlists/${id}/songs`, {
    data: { songIds }
  });
