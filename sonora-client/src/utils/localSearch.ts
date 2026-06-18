import type { AlbumData, ArtistData, PlaylistData, SongData } from '@/utils/transformers'

const normalizeKeyword = (value: string) => value.trim().toLowerCase()

const normalizeField = (value: unknown) => String(value || '').trim().toLowerCase()

export const matchesKeyword = (fields: unknown[], keyword: string) => {
  const normalized = normalizeKeyword(keyword)
  if (!normalized) return true
  return fields.some(field => normalizeField(field).includes(normalized))
}

export const filterSongsByKeyword = (songs: SongData[], keyword: string) =>
  songs.filter(song =>
    matchesKeyword(
      [song.name, song.artist, song.album, ...(song.artists || []).map(artist => artist.name)],
      keyword
    )
  )

export const filterArtistsByKeyword = (artists: ArtistData[], keyword: string) =>
  artists.filter(artist =>
    matchesKeyword(
      [artist.name, artist.region, artist.description, ...(artist.alias || [])],
      keyword
    )
  )

export const filterAlbumsByKeyword = (albums: AlbumData[], keyword: string) =>
  albums.filter(album =>
    matchesKeyword(
      [album.name, album.artist, album.region, album.description, album.publishTime],
      keyword
    )
  )

export const filterPlaylistsByKeyword = (playlists: PlaylistData[], keyword: string) =>
  playlists.filter(playlist =>
    matchesKeyword([playlist.name, playlist.description, playlist.creator], keyword)
  )
