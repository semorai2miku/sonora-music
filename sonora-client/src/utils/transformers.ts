/**
 * API 响应数据转换器
 * 统一处理各种 API 响应格式的数据转换
 */
import { resolveMediaUrl } from '@/utils/media'

const DEFAULT_COVER = '/default-cover.svg'

// ============ 类型定义 ============

export interface BannerData {
  coverImgUrl: string
  title: string
  description: string
  url: string
}

export interface PlaylistData {
  id: number | string
  name: string
  coverImgUrl: string
  playCount: number
  collectCount?: number
  trackCount?: number
  creator?: string
  creatorId?: number | string
  description?: string
  subscribed?: boolean
}

export interface SongData {
  id: number | string
  name: string
  artist: string
  artistId?: number | string
  artists?: { id: number | string; name: string }[]
  album: string
  albumId?: number | string
  cover: string
  duration: number
  liked?: boolean
  mvId?: number | string
}

export interface ArtistData {
  id: number | string
  name: string
  picUrl: string
  alias?: string[]
  albumSize?: number
  musicSize?: number
  mvSize?: number
  region?: string
  description?: string
}

export interface MVData {
  id: number | string
  name: string
  cover: string
  artist: string
  playCount?: number
  duration?: number
}

export interface AlbumData {
  id: number | string
  name: string
  picUrl: string
  artist: string
  artistId?: number | string
  publishTime?: string
  size?: number
  description?: string
  region?: string
}

export interface PlaylistDetailData {
  name: string
  description: string
  creator: string
  creatorAvatar: string
  createTime: string
  songCount: number
  playCount: string | number
  likes: string | number
  category: string
  coverImgUrl: string
}

export interface ClientPlaylistDetailData extends PlaylistDetailData {
  status?: number
  type?: string
  pinned?: number
}

// ============ 数据提取器 ============

type ApiResponse = Record<string, unknown>

/**
 * 从 API 响应中提取数据，支持多种响应格式
 */
export function extractData<T>(response: ApiResponse, ...paths: string[]): T | undefined {
  for (const path of paths) {
    const keys = path.split('.')
    let result: unknown = response
    for (const key of keys) {
      if (result && typeof result === 'object' && key in result) {
        result = (result as Record<string, unknown>)[key]
      } else {
        result = undefined
        break
      }
    }
    if (result !== undefined) return result as T
  }
  return undefined
}

/**
 * 提取数组数据
 */
export function extractArray<T = unknown>(response: ApiResponse, ...paths: string[]): T[] {
  const data = extractData<T[]>(response, ...paths)
  return Array.isArray(data) ? data : []
}

function limitSourceItems<T>(items: T[], limit?: number): T[] {
  if (!Array.isArray(items)) return []
  return typeof limit === 'number' ? items.slice(0, limit) : items
}

// ============ 转换器 ============

/**
 * 转换 Banner 数据
 */
export function transformBanner(item: Record<string, unknown>): BannerData {
  return {
    coverImgUrl: resolveMediaUrl((item?.pic as string) || (item?.imageUrl as string) || ''),
    title: (item?.typeTitle as string) || '',
    description: (item?.title as string) || '',
    url: (item?.url as string) || '',
  }
}

/**
 * 批量转换 Banner
 */
export function transformBanners(response: ApiResponse, limit?: number): BannerData[] {
  const list = extractArray(response, 'data.banners', 'banners')
  return limitSourceItems(list, limit).map(item => transformBanner(item as Record<string, unknown>))
}

/**
 * 转换歌单数据
 */
export function transformPlaylist(
  item: Record<string, unknown>,
  fallbackName = '未知歌单'
): PlaylistData {
  const creator = item?.creator as Record<string, unknown> | undefined
  return {
    id: (item?.id as number | string) || 0,
    name: (item?.name as string) || fallbackName,
    coverImgUrl: resolveMediaUrl(
      (item?.picUrl as string) ||
        (item?.coverImgUrl as string) ||
        (item?.cover as string) ||
        DEFAULT_COVER
    ),
    playCount: (item?.playCount as number) || 0,
    collectCount: (item?.collectCount as number) || 0,
    trackCount: (item?.trackCount as number) || (item?.songCount as number) || 0,
    creator:
      (creator?.nickname as string) ||
      (item?.creatorName as string) ||
      (item?.artist as string) ||
      '',
    creatorId:
      (creator?.userId as number | string) ||
      (item?.creatorId as number | string) ||
      0,
    description: (item?.description as string) || '',
    subscribed:
      typeof item?.subscribed === 'boolean'
        ? (item.subscribed as boolean)
        : typeof item?.subscribed === 'number'
          ? Boolean(item.subscribed)
          : false,
  }
}

/**
 * 批量转换歌单（推荐歌单）
 */
export function transformPlaylists(
  response: ApiResponse,
  limit?: number,
  fallbackName = '未知歌单'
): PlaylistData[] {
  const list = extractArray(
    response,
    'result',
    'data.result',
    'playlists',
    'data.playlists',
    'data.list',
    'list',
    'data'
  )
  return limitSourceItems(list, limit).map(item =>
    transformPlaylist(item as Record<string, unknown>, fallbackName)
  )
}

/**
 * 提取艺术家名称
 */
function extractArtistName(item: Record<string, unknown>): string {
  const ar = item?.ar as Array<Record<string, unknown>> | undefined
  const artists = item?.artists as Array<Record<string, unknown>> | undefined
  const songArtists = (item?.song as Record<string, unknown>)?.artists as
    | Array<Record<string, unknown>>
    | undefined

  const artistList = ar || artists || songArtists
  if (Array.isArray(artistList)) {
    return artistList.map(a => a?.name || '').join(' / ')
  }
  return (item?.artistName as string) || (item?.artist as string) || ''
}

/**
 * 转换歌曲数据
 */
export function transformSong(item: Record<string, unknown>): SongData {
  const song = item?.song as Record<string, unknown> | undefined
  const al = item?.al as Record<string, unknown> | undefined
  const album = item?.album as Record<string, unknown> | undefined
  const songAlbum = song?.album as Record<string, unknown> | undefined

  const albumData = al || album || songAlbum

  // 提取歌手数组
  const ar = item?.ar as Array<Record<string, unknown>> | undefined
  const artistsRaw = item?.artists as Array<Record<string, unknown>> | undefined
  const songArtists = song?.artists as Array<Record<string, unknown>> | undefined
  const artistList = ar || artistsRaw || songArtists

  const artists = Array.isArray(artistList)
    ? artistList.map(a => ({
        id: (a?.id as number | string) || 0,
        name: (a?.name as string) || '',
      }))
    : undefined

  // 提取单个歌手 ID（用于只有一个歌手的情况）
  const artistId = artists?.[0]?.id || 0

  return {
    id: (item?.id as number | string) || (song?.id as number | string) || 0,
    name: (item?.name as string) || (song?.name as string) || '',
    artist: extractArtistName(item),
    artistId,
    artists,
    album: (albumData?.name as string) || '',
    albumId: (albumData?.id as number | string) || 0,
    cover: resolveMediaUrl(
      (albumData?.picUrl as string) ||
        (item?.cover as string) ||
        (item?.picUrl as string) ||
        DEFAULT_COVER
    ),
    duration: (item?.dt as number) ?? (item?.duration as number) ?? (song?.duration as number) ?? 0,
    liked: false,
    mvId: (item?.mv as number | string) || (item?.mvid as number | string) || 0,
  }
}

/**
 * 批量转换歌曲
 */
export function transformSongs(response: ApiResponse, limit?: number): SongData[] {
  const list = extractArray(
    response,
    'data.dailySongs',
    'songs',
    'data.songs',
    'result',
    'data.result',
    'result.songs'
  )
  return limitSourceItems(list, limit).map(item => transformSong(item as Record<string, unknown>))
}

/**
 * 转换歌手数据
 */
export function transformArtist(item: Record<string, unknown>): ArtistData {
  return {
    id: (item?.id as number | string) || 0,
    name: (item?.name as string) || '',
    picUrl: resolveMediaUrl(
      (item?.picUrl as string) ||
        (item?.img1v1Url as string) ||
        (item?.cover as string) ||
        (item?.avatar as string) ||
        ''
    ),
    alias: (item?.alias as string[]) || [],
    albumSize: (item?.albumSize as number) || 0,
    musicSize: (item?.musicSize as number) || 0,
    mvSize: (item?.mvSize as number) || 0,
    region: (item?.region as string) || '',
    description: (item?.description as string) || '',
  }
}

/**
 * 批量转换歌手
 */
export function transformArtists(response: ApiResponse, limit?: number): ArtistData[] {
  const list = extractArray(response, 'artists', 'data.artists', 'result.artists')
  return limitSourceItems(list, limit).map(item => transformArtist(item as Record<string, unknown>))
}

/**
 * 转换 MV 数据
 */
export function transformMV(item: Record<string, unknown>): MVData {
  return {
    id: (item?.id as number | string) || 0,
    name: (item?.name as string) || '',
    cover: resolveMediaUrl(
      (item?.cover as string) || (item?.picUrl as string) || (item?.imgurl as string) || ''
    ),
    artist: (item?.artistName as string) || '',
    playCount: (item?.playCount as number) || 0,
    duration: (item?.duration as number) || 0,
  }
}

/**
 * 批量转换 MV
 */
export function transformMVs(response: ApiResponse, limit?: number): MVData[] {
  const list = extractArray(response, 'result', 'data.result', 'mvs', 'data.mvs', 'result.mvs')
  return limitSourceItems(list, limit).map(item => transformMV(item as Record<string, unknown>))
}

/**
 * 转换专辑数据
 */
export function transformAlbum(item: Record<string, unknown>): AlbumData {
  const artist = item?.artist as Record<string, unknown> | undefined
  const artistName =
    typeof item?.artist === 'string'
      ? (item.artist as string)
      : (artist?.name as string) || (item?.artistName as string) || ''
  const artistId =
    typeof item?.artist === 'string'
      ? ((item?.artistId as number | string) || 0)
      : ((artist?.id as number | string) || (item?.artistId as number | string) || 0)
  const publishTime =
    typeof item?.publishTime === 'number'
      ? new Date(item.publishTime as number).toLocaleDateString()
      : ((item?.publishTime as string) || (item?.releaseDate as string) || '')

  return {
    id: (item?.id as number | string) || 0,
    name: (item?.name as string) || '',
    picUrl: resolveMediaUrl(
      (item?.picUrl as string) ||
        (item?.cover as string) ||
        (item?.blurPicUrl as string) ||
        DEFAULT_COVER
    ),
    artist: artistName,
    artistId,
    publishTime,
    size: (item?.size as number) || 0,
    description: (item?.description as string) || '',
    region: (item?.region as string) || '',
  }
}

/**
 * 批量转换专辑
 */
export function transformAlbums(response: ApiResponse, limit?: number): AlbumData[] {
  const list = extractArray(
    response,
    'albums',
    'data.albums',
    'hotAlbums',
    'data.hotAlbums',
    'result.albums'
  )
  return limitSourceItems(list, limit).map(item => transformAlbum(item as Record<string, unknown>))
}

/**
 * 转换歌单详情
 */
export function transformPlaylistDetail(
  response: ApiResponse,
  fallbackCategory = '歌单'
): PlaylistDetailData | null {
  const detail = extractData<Record<string, unknown>>(response, 'playlist', 'data.playlist', 'data')

  if (!detail) return null

  const creator = detail?.creator as Record<string, unknown> | undefined
  const tags = detail?.tags as string[] | undefined

  return {
    name: (detail?.name as string) || '',
    description: (detail?.description as string) || '',
    creator: (creator?.nickname as string) || '',
    creatorAvatar: resolveMediaUrl((creator?.avatarUrl as string) || ''),
    createTime: detail?.createTime
      ? new Date(detail.createTime as number).toLocaleDateString()
      : '',
    songCount: (detail?.trackCount as number) || 0,
    playCount: (detail?.playCount as string | number) || 0,
    likes: (detail?.subscribedCount as number) || (detail?.bookedCount as number) || 0,
    category: tags?.[0] || fallbackCategory,
    coverImgUrl: resolveMediaUrl((detail?.coverImgUrl as string) || ''),
  }
}

export function transformClientPlaylistDetail(
  response: ApiResponse,
  fallbackCategory = '歌单'
): ClientPlaylistDetailData | null {
  const detail = extractData<Record<string, unknown>>(response, 'data', 'playlist', 'data.playlist')
  if (!detail) return null

  const creator = detail?.creator as Record<string, unknown> | undefined

  return {
    name: (detail?.name as string) || '',
    description: (detail?.description as string) || '',
    creator: (creator?.nickname as string) || '',
    creatorAvatar: resolveMediaUrl((creator?.avatarUrl as string) || ''),
    createTime: (detail?.createdAt as string)
      ? new Date(detail.createdAt as string).toLocaleDateString()
      : '',
    songCount: (detail?.songCount as number) || 0,
    playCount: (detail?.playCount as string | number) || 0,
    likes: (detail?.collectCount as string | number) || 0,
    category: fallbackCategory,
    coverImgUrl: resolveMediaUrl((detail?.cover as string) || DEFAULT_COVER),
    status: (detail?.status as number) || 0,
    type: (detail?.type as string) || '',
    pinned: (detail?.pinned as number) || 0,
  }
}

/**
 * 转换歌手详情
 */
export function transformArtistDetail(response: ApiResponse): ArtistData | null {
  const artist = extractData<Record<string, unknown>>(response, 'data.artist', 'artist')

  if (!artist) return null

  return {
    id: (artist?.id as number | string) || 0,
    name: (artist?.name as string) || '',
    picUrl: resolveMediaUrl(
      (artist?.cover as string) || (artist?.picUrl as string) || (artist?.avatar as string) || ''
    ),
    alias: (artist?.alias as string[]) || [],
    albumSize: (artist?.albumSize as number) || 0,
    musicSize: (artist?.musicSize as number) || 0,
    mvSize: (artist?.mvSize as number) || 0,
  }
}

/**
 * 转换专辑详情
 */
export function transformAlbumDetail(response: ApiResponse): AlbumData | null {
  const album = extractData<Record<string, unknown>>(response, 'album', 'data.album')

  if (!album) return null

  return transformAlbum(album)
}

/**
 * 转换专辑详情中的歌曲列表
 */
export function transformAlbumSongs(
  response: ApiResponse,
  fallbackAlbum?: Partial<AlbumData>
): SongData[] {
  const songs = extractArray(
    response,
    'songs',
    'data.songs',
    'album.songs',
    'data.album.songs'
  )

  return songs.map(item => {
    const raw = item as Record<string, unknown>
    const song = transformSong(raw)
    const rawAlbum =
      (raw?.al as Record<string, unknown> | undefined) ||
      (raw?.album as Record<string, unknown> | undefined)

    const hasExplicitCover = Boolean(
      (rawAlbum?.picUrl as string) || (raw?.cover as string) || (raw?.picUrl as string)
    )

    return {
      ...song,
      album: song.album || String(fallbackAlbum?.name || ''),
      albumId: song.albumId || fallbackAlbum?.id || 0,
      cover:
        hasExplicitCover || !fallbackAlbum?.picUrl
          ? song.cover
          : resolveMediaUrl(String(fallbackAlbum.picUrl)),
    }
  })
}

// ============ 搜索结果转换器 ============

/**
 * 搜索歌曲结果
 */
export function transformSearchSongs(
  response: ApiResponse,
  limit?: number
): { songs: SongData[]; total: number } {
  const songs = extractArray(response, 'result.songs', 'data.result.songs')
  const total =
    extractData<number>(response, 'result.songCount', 'data.result.songCount') || songs.length
  return {
    songs: limitSourceItems(songs, limit).map(item =>
      transformSong(item as Record<string, unknown>)
    ),
    total,
  }
}

/**
 * 搜索歌单结果
 */
export function transformSearchPlaylists(
  response: ApiResponse,
  limit?: number
): { playlists: PlaylistData[]; total: number } {
  const playlists = extractArray(response, 'result.playlists', 'data.result.playlists')
  const total =
    extractData<number>(response, 'result.playlistCount', 'data.result.playlistCount') ||
    playlists.length
  return {
    playlists: limitSourceItems(playlists, limit).map(item =>
      transformPlaylist(item as Record<string, unknown>)
    ),
    total,
  }
}

export function transformSearchArtists(
  response: ApiResponse,
  limit?: number
): { artists: ArtistData[]; total: number } {
  const artists = extractArray(response, 'result.artists', 'data.result.artists')
  const total =
    extractData<number>(response, 'result.artistCount', 'data.result.artistCount') || artists.length
  return {
    artists: limitSourceItems(artists, limit).map(item =>
      transformArtist(item as Record<string, unknown>)
    ),
    total,
  }
}

export function transformSearchAlbums(
  response: ApiResponse,
  limit?: number
): { albums: AlbumData[]; total: number } {
  const albums = extractArray(response, 'result.albums', 'data.result.albums')
  const total =
    extractData<number>(response, 'result.albumCount', 'data.result.albumCount') || albums.length
  return {
    albums: limitSourceItems(albums, limit).map(item =>
      transformAlbum(item as Record<string, unknown>)
    ),
    total,
  }
}

/**
 * 搜索 MV 结果
 */
export function transformSearchMVs(
  response: ApiResponse,
  limit?: number
): { mvs: MVData[]; total: number } {
  const mvs = extractArray(response, 'result.mvs', 'data.result.mvs')
  const total = extractData<number>(response, 'result.mvCount', 'data.result.mvCount') || mvs.length
  return {
    mvs: limitSourceItems(mvs, limit).map(item => transformMV(item as Record<string, unknown>)),
    total,
  }
}

// ============ 新歌/榜单转换器 ============

/**
 * 转换新歌榜数据 (topSong API)
 * 复用 transformSong 避免重复映射逻辑
 */
export function transformTopSongs(response: ApiResponse, limit?: number): SongData[] {
  const list = extractArray(response, 'data.data', 'data.songs', 'songs', 'data')
  return limitSourceItems(list, limit).map(item => transformSong(item as Record<string, unknown>))
}

/**
 * 转换 MV 列表 (mvAll API)
 */
export function transformMVList(response: ApiResponse, limit?: number): MVData[] {
  const list = extractArray(response, 'data', 'mvs', 'result')
  return limitSourceItems(list, limit).map((item: unknown) => {
    const m = item as Record<string, unknown>
    return {
      id: (m?.id as number | string) || (m?.vid as string) || 0,
      name: (m?.name as string) || (m?.title as string) || '',
      cover: (m?.cover as string) || (m?.imgurl as string) || (m?.pic as string) || '',
      artist: (m?.artistName as string) || '',
      playCount: (m?.playCount as number) || 0,
      duration: (m?.duration as number) || 0,
    }
  })
}
