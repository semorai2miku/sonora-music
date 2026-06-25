/**
 * API 接口集合
 * 封装 Sonora 后端兼容 API 的所有请求方法
 * 按功能模块分组：轮播图、Sonora 账号、搜索、歌曲、歌单、歌手、专辑、MV、评论、推荐
 */
import { httpDelete, httpGet, httpPost, httpPut, httpUpload } from '@/utils/http'

// ═══════ 轮播图 ═══════

/** 获取首页 Banner 轮播图 */
export const banner = (params?: { type?: 0 | 1 | 2 | 3 }) => httpGet('/banner', params)

// ═══════ 客户端账号 ═══════

export interface SonoraUserProfile {
  userId: number
  profileId: string
  username: string
  nickname: string
  email: string
  phone?: string
  avatarUrl: string
  bio?: string
  status?: number
}

export interface SonoraAuthResult {
  code: number
  message: string
  data: {
    profile: SonoraUserProfile
    accessToken: string
    refreshToken: string
    expires: string
  }
}

export interface SonoraResult<T> {
  code: number
  message?: string
  data: T
}

export interface ClientPlaylist {
  id: number
  name: string
  cover?: string
  type?: 'liked' | 'normal' | string
  pinned?: number
  status?: number
  description?: string
  songCount?: number
  createdAt?: string
  playCount?: number
  collectCount?: number
  subscribed?: boolean
  creatorId?: number
  creator?: {
    userId?: number
    nickname?: string
    avatarUrl?: string
  }
}

export interface ClientPlaylistPayload {
  name?: string
  cover?: string
  description?: string
  pinned?: number
  status?: number
}

export interface ClientPlaylistDetail extends ClientPlaylist {
  playCount?: number
  collectCount?: number
  tags?: string
  updatedAt?: string
  creator?: {
    userId?: number
    nickname?: string
    avatarUrl?: string
  }
  songs?: Array<Record<string, unknown>>
}

export interface ClientPlaylistPage {
  list: ClientPlaylist[]
  total: number
  pageNum: number
  pageSize: number
}

export const clientLogin = (data: { username: string; password: string }) =>
  httpPost<SonoraAuthResult>('/api/client/auth/login', data)

export const clientRegister = (data: { username: string; email: string; password: string }) =>
  httpPost<SonoraAuthResult>('/api/client/auth/register', data)

export const clientMe = () => httpGet<SonoraResult<SonoraUserProfile>>('/api/client/auth/me')

export const updateClientMe = (data: {
  username?: string
  profileId?: string
  email?: string
  phone?: string
  avatar?: string
  bio?: string
}) => httpPut<SonoraResult<SonoraUserProfile>>('/api/client/auth/me', data)

export const changeClientPassword = (data: { oldPassword: string; newPassword: string }) =>
  httpPut<SonoraResult<null>>('/api/client/auth/password', data)

export const uploadClientAvatar = (formData: FormData) =>
  httpUpload<SonoraResult<{ objectKey: string; url: string; fileName: string; fileSize: number }>>(
    '/api/client/auth/avatar',
    formData
  )

export const deleteClientMe = () => httpDelete<SonoraResult<null>>('/api/client/auth/me')

export const myPlaylists = () =>
  httpGet<SonoraResult<ClientPlaylist[]>>('/api/client/me/playlists')

export const createMyPlaylist = (data: ClientPlaylistPayload) =>
  httpPost<SonoraResult<ClientPlaylist>>('/api/client/me/playlists', data)

export const updateMyPlaylist = (playlistId: number | string, data: ClientPlaylistPayload) =>
  httpPut<SonoraResult<ClientPlaylist>>(`/api/client/me/playlists/${playlistId}`, data)

export const uploadMyPlaylistCover = (playlistId: number | string, formData: FormData) =>
  httpUpload<SonoraResult<{ objectKey: string; url: string; fileName: string; fileSize: number }>>(
    `/api/client/me/playlists/${playlistId}/cover`,
    formData
  )

export const myPlaylistDetail = (playlistId: number | string) =>
  httpGet<SonoraResult<ClientPlaylistDetail>>(`/api/client/me/playlists/${playlistId}`)

export const clientPlaylistDetail = (playlistId: number | string) =>
  httpGet<SonoraResult<ClientPlaylistDetail>>(`/api/client/playlists/${playlistId}`)

export const recordClientPlaylistPlay = (playlistId: number | string) =>
  httpPost<SonoraResult<null>>(`/api/client/playlists/${playlistId}/play`)

export const clientPublicPlaylists = (params?: {
  pageNum?: number
  pageSize?: number
  keyword?: string
}) =>
  httpGet<SonoraResult<ClientPlaylistPage>>('/api/client/playlists', params)

export const clientRecommendPlaylists = (params?: { limit?: number }) =>
  httpGet<SonoraResult<Array<Record<string, unknown>>>>('/api/client/playlists/recommend', params)

export const pinMyPlaylist = (playlistId: number | string, pinned: boolean) =>
  updateMyPlaylist(playlistId, { pinned: pinned ? 1 : 0 })

export const deleteMyPlaylist = (playlistId: number | string) =>
  httpDelete<SonoraResult<null>>(`/api/client/me/playlists/${playlistId}`)

export const addSongToMyPlaylist = (playlistId: number | string, songId: number | string) =>
  httpPost<SonoraResult<ClientPlaylist>>(`/api/client/me/playlists/${playlistId}/songs/${songId}`)

export const removeSongFromMyPlaylist = (playlistId: number | string, songId: number | string) =>
  httpDelete<SonoraResult<null>>(`/api/client/me/playlists/${playlistId}/songs/${songId}`)

export const collectPlaylist = (playlistId: number | string) =>
  httpPost<SonoraResult<ClientPlaylist>>(`/api/client/me/likes/playlists/${playlistId}`)

export const uncollectPlaylist = (playlistId: number | string) =>
  httpDelete<SonoraResult<ClientPlaylist>>(`/api/client/me/likes/playlists/${playlistId}`)

export const likedSongIds = () =>
  httpGet<SonoraResult<number[]>>('/api/client/me/likes/song-ids')

export const likedSongs = () =>
  httpGet<SonoraResult<Array<Record<string, unknown>>>>('/api/client/me/likes/songs')

export const likeSong = (songId: number | string) =>
  httpPost<SonoraResult<Record<string, unknown>>>(`/api/client/me/likes/songs/${songId}`)

export const unlikeSong = (songId: number | string) =>
  httpDelete<SonoraResult<null>>(`/api/client/me/likes/songs/${songId}`)

export const clientBanners = () => httpGet<SonoraResult<Array<Record<string, unknown>>>>('/api/client/banners')

export const clientSongs = (params?: {
  limit?: number
  sort?: 'id_asc' | 'id_desc' | 'created_desc' | 'play_desc'
}) => httpGet<SonoraResult<Array<Record<string, unknown>>>>('/api/client/songs', params)

export const clientSongDetail = (id: number | string) =>
  httpGet<SonoraResult<Record<string, unknown>>>(`/api/client/songs/${id}`)

export const clientSongLyric = (id: number | string) =>
  httpGet<SonoraResult<Record<string, unknown>>>(`/api/client/songs/${id}/lyric`)

export const clientArtists = (params?: { region?: string; limit?: number }) =>
  httpGet<SonoraResult<Array<Record<string, unknown>>>>('/api/client/artists', params)

export const clientArtistDetail = (id: number | string) =>
  httpGet<SonoraResult<Record<string, unknown>>>(`/api/client/artists/${id}`)

export const clientAlbums = (params?: { region?: string; limit?: number }) =>
  httpGet<SonoraResult<Array<Record<string, unknown>>>>('/api/client/albums', params)

// ═══════ 搜索 ═══════

/** 搜索（type: 1 歌曲, 10 专辑, 100 歌手, 1000 歌单, 1004 MV, ...） */
export const search = (params: {
  keywords: string
  limit?: number
  offset?: number
  type?: 1 | 10 | 100 | 1000 | 1002 | 1004 | 1006 | 1009 | 1014 | 1018 | 2000
}) => httpGet('/search', params)

/** 云搜索（返回更丰富的结果） */
export const cloudSearch = (params: {
  keywords: string
  limit?: number
  offset?: number
  type?: 1 | 10 | 100 | 1000 | 1002 | 1004 | 1006 | 1009 | 1014 | 1018 | 2000
}) => httpGet('/cloudsearch', params)

/** 搜索建议 */
export const searchSuggest = (params: { keywords: string; type?: 'mobile' }) =>
  httpGet('/search/suggest', params)
/** 热门搜索（简略） */
export const searchHot = () => httpGet('/search/hot')
/** 热门搜索（详细，含排名和热度） */
export const searchHotDetail = () => httpGet('/search/hot/detail')
/** 默认搜索关键词 */
export const searchDefault = () => httpGet('/search/default')
/** 搜索多重匹配 */
export const searchMultimatch = (params: { keywords: string }) =>
  httpGet('/search/multimatch', params)

// ═══════ 歌曲播放 ═══════

/** 获取歌曲播放地址 */
export const songUrl = (params: { id: string; br?: number }) => httpGet('/song/url', params)

/** 获取歌曲播放地址 V1（支持更多品质等级） */

export const songUrlV1 = (params: {
  id: string
  level?: 'standard' | 'higher' | 'exhigh' | 'lossless' | 'hires' | 'jyeffect' | 'sky' | 'jymaster'
}) => httpGet('/song/url/v1', params)

/** 检查音乐是否可用 */
export const checkMusic = (params: { id: string; br?: number }) => httpGet('/check/music', params)
/** 获取歌曲下载地址 */
export const songDownloadUrl = (params: { id: string; br?: number }) =>
  httpGet('/song/download/url', params)

// ═══════ 评论 ═══════

/** 歌曲评论 */
export const commentMusic = (params: {
  id: number
  limit?: number
  offset?: number
  before?: number
}) => httpGet('/comment/music', params)

/** 歌单评论 */
export const commentPlaylist = (params: {
  id: number
  limit?: number
  offset?: number
  before?: number
}) => httpGet('/comment/playlist', params)

/** 热门评论 */
export const commentHot = (params: {
  id: number
  type: 0 | 1 | 2 | 3 | 4 | 5 | 6 | 7
  limit?: number
  offset?: number
  before?: number
}) => httpGet('/comment/hot', params)

/** 新版评论（支持排序和分页） */
export const commentNew = (params: {
  id: number
  type: 0 | 1 | 2 | 3 | 4 | 5 | 6 | 7
  sortType?: 1 | 2 | 3
  pageNo?: number
  pageSize?: number
  cursor?: number
}) => httpGet('/comment/new', params)

export const commentSend = (params: {
  t: 1 | 2
  type: 0 | 1 | 2 | 3 | 4 | 5 | 6 | 7
  id?: number
  threadId?: string
  content: string
  commentId?: number
}) => httpGet('/comment', params)

export const commentDelete = (params: {
  t: 0
  type: 0 | 1 | 2 | 3 | 4 | 5 | 6 | 7
  id?: number
  threadId?: string
  commentId: number
}) => httpGet('/comment', params)

// 歌曲详情
export const songDetail = (params: { ids: string }) => httpGet('/song/detail', params)

export const albumDetail = (params: { id: number | string }) => httpGet('/album', params)

// 歌单相关
export const playlistDetail = (params: { id: number; s?: number }) =>
  httpGet('/playlist/detail', params)

export const playlistTrackAll = (params: { id: number; limit?: number; offset?: number }) =>
  httpGet('/playlist/track/all', params)

export const userPlaylist = (params: { uid: number; limit?: number; offset?: number }) =>
  httpGet('/user/playlist', params)

export const topPlaylist = (params: {
  order?: 'new' | 'hot'
  cat?: string
  limit?: number
  offset?: number
}) => httpGet('/top/playlist', params)

export const topSong = (params: { type: 0 | 7 | 96 | 8 | 16 }) => httpGet('/top/song', params)

export const toplist = () => httpGet('/toplist')

export const toplistDetail = () => httpGet('/toplist/detail')

export const recordRecentSong = (params?: { limit?: number }) =>
  httpGet('/record/recent/song', params)

// MV 详情与播放
export const mvDetail = (params: { mvid: number | string }) => httpGet('/mv/detail', params)
export const mvUrl = (params: { id: number | string; r?: number }) => httpGet('/mv/url', params)
export const simiMv = (params: { mvid: number | string }) => httpGet('/simi/mv', params)
export const mvAll = (params: {
  area?: string
  type?: string
  order?: string
  limit?: number
  offset?: number
}) => httpGet('/mv/all', params)
export const searchSong = (params: { keywords: string; limit?: number; offset?: number }) =>
  httpGet('/search', params)

// 歌词相关
export const lyric = (params: { id: number | string }) => httpGet('/lyric', params)

// 推荐相关
export const personalized = (params?: { limit?: number }) => httpGet('/personalized', params)
export const personalizedNewsong = (params?: { limit?: number }) =>
  httpGet('/personalized/newsong', params)
export const personalizedMv = () => httpGet('/personalized/mv')
export const playlistCatlist = () => httpGet('/playlist/catlist')
export const topArtists = (params?: { limit?: number; offset?: number }) =>
  httpGet('/top/artists', params)
export const artistList = (params?: {
  type?: number
  area?: number
  initial?: string
  limit?: number
  offset?: number
}) => httpGet('/artist/list', params)

export const artistDetail = (params: { id: number }) => httpGet('/artist/detail', params)
export const artistTopSong = (params: { id: number }) => httpGet('/artist/top/song', params)
export const artistSongs = (params: {
  id: number
  order?: 'hot' | 'time'
  limit?: number
  offset?: number
}) => httpGet('/artist/songs', params)
export const artistAlbum = (params: { id: number; limit?: number; offset?: number }) =>
  httpGet('/artist/album', params)
export const artistDesc = (params: { id: number }) => httpGet('/artist/desc', params)
export const artistMv = (params: { id: number; limit?: number; offset?: number }) =>
  httpGet('/artist/mv', params)

export const albumNew = (params?: { area?: 'ALL' | 'ZH' | 'EA' | 'KR' | 'JP'; limit?: number; offset?: number }) =>
  httpGet('/album/new', params)

export const albumNewest = () => httpGet('/album/newest')

export const recommendSongs = () => httpGet('/recommend/songs')

export const recommendResource = () => httpGet('/recommend/resource')

export const personalFm = () => httpGet('/personal_fm')
