package com.sonora.client.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sonora.common.constant.Constants;
import com.sonora.file.service.MinioService;
import com.sonora.mapper.*;
import com.sonora.model.entity.*;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 网易云 API 兼容层
 * <p>
 * GlassMusicPlayer 前端调用网易云格式的接口路径和响应格式，
 * 此 Controller 将请求转换为我们的后端数据格式并返回兼容的响应。
 */
@RestController
public class NeteaseCompatController {

    private final BannerMapper bannerMapper;
    private final SongMapper songMapper;
    private final PlaylistMapper playlistMapper;
    private final PlaylistSongMapper playlistSongMapper;
    private final ArtistMapper artistMapper;
    private final AlbumMapper albumMapper;
    private final MinioService minioService;

    public NeteaseCompatController(BannerMapper bannerMapper,
                                   SongMapper songMapper,
                                   PlaylistMapper playlistMapper,
                                   PlaylistSongMapper playlistSongMapper,
                                   ArtistMapper artistMapper,
                                   AlbumMapper albumMapper,
                                   MinioService minioService) {
        this.bannerMapper = bannerMapper;
        this.songMapper = songMapper;
        this.playlistMapper = playlistMapper;
        this.playlistSongMapper = playlistSongMapper;
        this.artistMapper = artistMapper;
        this.albumMapper = albumMapper;
        this.minioService = minioService;
    }

    // ==================== Banner ====================

    @GetMapping("/banner")
    public Map<String, Object> banner() {
        List<Banner> banners = bannerMapper.selectList(
                new LambdaQueryWrapper<Banner>().eq(Banner::getStatus, 1).orderByAsc(Banner::getSort));
        List<Map<String, Object>> list = banners.stream().map(b -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("imageUrl", previewUrl(b.getImageUrl()));
            m.put("title", b.getTitle());
            m.put("url", b.getLinkUrl());
            return m;
        }).toList();
        return Map.of("code", 200, "banners", list);
    }

    // ==================== 搜索 ====================

    @GetMapping("/search")
    public Map<String, Object> search(@RequestParam String keywords,
                                       @RequestParam(defaultValue = "1") int type,
                                       @RequestParam(defaultValue = "30") int limit) {
        Map<String, Object> result = new LinkedHashMap<>();

        if (type == 1 || type == 0) { // 歌曲
            List<Song> songs = songMapper.selectList(
                    new LambdaQueryWrapper<Song>().like(Song::getName, keywords)
                            .eq(Song::getStatus, 1).last("LIMIT " + limit));
            Map<Long, Album> albumMap = albumMapFromSongs(songs);
            Map<Long, Artist> artistMap = artistMapFromSongs(songs);
            result.put("songs", songs.stream().map(song -> trackOf(song, albumMap, artistMap)).toList());
        }

        return Map.of("code", 200, "result", result);
    }

    @GetMapping("/cloudsearch")
    public Map<String, Object> cloudSearch(@RequestParam String keywords,
                                            @RequestParam(defaultValue = "1") int type,
                                            @RequestParam(defaultValue = "30") int limit) {
        return search(keywords, type, limit);
    }

    // ==================== 歌曲 ====================

    @GetMapping("/song/url/v1")
    public Map<String, Object> songUrlV1(@RequestParam String id,
                                          @RequestParam(defaultValue = "standard") String level) {
        List<Map<String, Object>> data = Arrays.stream(id.split(",")).map(sid -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", Long.parseLong(sid.trim()));
            m.put("url", "/client/songs/" + sid.trim() + "/stream");
            m.put("br", 320000);
            m.put("type", "mp3");
            return m;
        }).toList();
        return Map.of("code", 200, "data", data);
    }

    @GetMapping("/song/detail")
    public Map<String, Object> songDetail(@RequestParam String ids) {
        List<Long> idList = Arrays.stream(ids.split(","))
                .map(String::trim).map(Long::parseLong).toList();
        List<Song> songs = songMapper.selectBatchIds(idList);
        Map<Long, Album> albumMap = albumMapFromSongs(songs);
        Map<Long, Artist> artistMap = artistMapFromSongs(songs);
        List<Map<String, Object>> list = songs.stream()
                .map(song -> trackOf(song, albumMap, artistMap))
                .toList();
        return Map.of("code", 200, "songs", list);
    }

    // ==================== 歌词 ====================

    @GetMapping("/lyric")
    public Map<String, Object> lyric(@RequestParam String id) {
        Song song = songMapper.selectById(Long.parseLong(id));
        String lrc = (song != null && song.getLyrics() != null) ? song.getLyrics() : "[00:00.00]暂无歌词";
        return Map.of("code", 200, "lrc", Map.of("lyric", lrc));
    }

    // ==================== 歌单 ====================

    @GetMapping("/playlist/detail")
    public Map<String, Object> playlistDetail(@RequestParam Long id) {
        Playlist playlist = playlistMapper.selectById(id);
        if (playlist == null) {
            return Map.of("code", 404, "message", "歌单不存在");
        }

        List<PlaylistSong> psList = playlistSongMapper.selectList(
                new LambdaQueryWrapper<PlaylistSong>().eq(PlaylistSong::getPlaylistId, id)
                        .orderByAsc(PlaylistSong::getSort));
        List<Long> songIds = psList.stream().map(PlaylistSong::getSongId).toList();
        List<Song> songs = songIds.isEmpty() ? List.of() : orderedSongs(songIds);
        Map<Long, Album> albumMap = albumMapFromSongs(songs);
        Map<Long, Artist> artistMap = artistMapFromSongs(songs);

        List<Map<String, Object>> tracks = songs.stream()
                .map(song -> trackOf(song, albumMap, artistMap))
                .toList();

        Map<String, Object> pl = new LinkedHashMap<>();
        pl.put("id", playlist.getId());
        pl.put("name", playlist.getName());
        pl.put("coverImgUrl", previewUrl(playlist.getCover()));
        pl.put("description", playlist.getDescription());
        pl.put("trackCount", songs.size());
        pl.put("playCount", playlist.getPlayCount());
        pl.put("tracks", tracks);
        // 也返回 trackIds 让前端能获取完整歌曲列表
        pl.put("trackIds", tracks.stream().map(t -> Map.of("id", t.get("id"))).toList());

        return Map.of("code", 200, "playlist", pl);
    }

    @GetMapping("/playlist/track/all")
    public Map<String, Object> playlistTrackAll(@RequestParam Long id,
                                                 @RequestParam(defaultValue = "1000") int limit) {
        // 和 detail 类似，但只返回歌曲
        List<PlaylistSong> psList = playlistSongMapper.selectList(
                new LambdaQueryWrapper<PlaylistSong>().eq(PlaylistSong::getPlaylistId, id)
                        .orderByAsc(PlaylistSong::getSort));
        List<Long> songIds = psList.stream().map(PlaylistSong::getSongId).toList();
        List<Song> songs = songIds.isEmpty() ? List.of() : orderedSongs(songIds);
        Map<Long, Album> albumMap = albumMapFromSongs(songs);
        Map<Long, Artist> artistMap = artistMapFromSongs(songs);

        List<Map<String, Object>> tracks = songs.stream()
                .map(song -> trackOf(song, albumMap, artistMap))
                .toList();

        return Map.of("code", 200, "songs", tracks);
    }

    // ==================== 推荐 ====================

    @GetMapping("/personalized")
    public Map<String, Object> personalized(@RequestParam(defaultValue = "10") int limit) {
        List<Playlist> playlists = playlistMapper.selectList(
                new LambdaQueryWrapper<Playlist>().eq(Playlist::getStatus, 1)
                        .orderByDesc(Playlist::getCollectCount).last("LIMIT " + limit));

        List<Map<String, Object>> result = playlists.stream().map(p -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", p.getId());
            m.put("name", p.getName());
            m.put("picUrl", previewUrl(p.getCover()));
            m.put("playCount", p.getPlayCount());
            return m;
        }).toList();

        return Map.of("code", 200, "result", result);
    }

    @GetMapping("/top/playlist")
    public Map<String, Object> topPlaylist(@RequestParam(defaultValue = "hot") String order,
                                            @RequestParam(defaultValue = "20") int limit) {
        return personalized(limit);
    }

    @GetMapping("/top/song")
    public Map<String, Object> topSong(@RequestParam(defaultValue = "0") int type) {
        List<Song> songs = songMapper.selectList(
                new LambdaQueryWrapper<Song>().eq(Song::getStatus, 1)
                        .orderByDesc(Song::getCreatedAt)
                        .last("LIMIT 30"));
        Map<Long, Album> albumMap = albumMapFromSongs(songs);
        Map<Long, Artist> artistMap = artistMapFromSongs(songs);

        List<Map<String, Object>> list = songs.stream()
                .map(song -> trackOf(song, albumMap, artistMap))
                .toList();

        return Map.of("code", 200, "data", list);
    }

    @GetMapping("/recommend/songs")
    public Map<String, Object> recommendSongs() {
        List<Song> songs = songMapper.selectList(
                new LambdaQueryWrapper<Song>().eq(Song::getStatus, 1)
                        .orderByDesc(Song::getCreatedAt).last("LIMIT 30"));
        Map<Long, Album> albumMap = albumMapFromSongs(songs);
        Map<Long, Artist> artistMap = artistMapFromSongs(songs);

        List<Map<String, Object>> list = songs.stream()
                .map(song -> trackOf(song, albumMap, artistMap))
                .toList();

        return Map.of("code", 200, "data", Map.of("dailySongs", list));
    }

    @GetMapping("/recommend/resource")
    public Map<String, Object> recommendResource() {
        return personalized(10);
    }

    // ==================== 歌手 ====================

    @GetMapping("/artist/detail")
    public Map<String, Object> artistDetail(@RequestParam Long id) {
        Artist artist = artistMapper.selectById(id);
        if (artist == null) {
            return Map.of("code", 404, "message", "歌手不存在");
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", artist.getId());
        data.put("name", artist.getName());
        data.put("picUrl", previewUrl(artist.getAvatar()));
        data.put("briefDesc", artist.getDescription());
        return Map.of("code", 200, "data", Map.of("artist", data));
    }

    @GetMapping("/artist/top/song")
    public Map<String, Object> artistTopSong(@RequestParam Long id) {
        List<Song> songs = songMapper.selectList(
                new LambdaQueryWrapper<Song>()
                        .apply("FIND_IN_SET({0}, artist_ids)", id)
                        .eq(Song::getStatus, 1)
                        .orderByDesc(Song::getPlayCount)
                        .last("LIMIT 50"));
        Map<Long, Album> albumMap = albumMapFromSongs(songs);
        Map<Long, Artist> artistMap = artistMapFromSongs(songs);
        List<Map<String, Object>> list = songs.stream()
                .map(song -> trackOf(song, albumMap, artistMap))
                .toList();
        return Map.of("code", 200, "songs", list);
    }

    @GetMapping("/album")
    public Map<String, Object> album(@RequestParam Long id) {
        Album album = albumMapper.selectById(id);
        if (album == null) {
            return Map.of("code", 404, "message", "专辑不存在");
        }
        List<Song> songs = songMapper.selectList(
                new LambdaQueryWrapper<Song>()
                        .eq(Song::getAlbumId, id)
                        .eq(Song::getStatus, 1)
                        .orderByAsc(Song::getId));
        Map<Long, Album> albumMap = albumMapFromSongs(songs);
        albumMap.putIfAbsent(album.getId(), album);
        Map<Long, Artist> artistMap = artistMapFromSongs(songs);
        Artist albumArtist = album.getArtistId() == null ? null : artistMapper.selectById(album.getArtistId());
        List<Map<String, Object>> tracks = songs.stream()
                .map(song -> trackOf(song, albumMap, artistMap))
                .toList();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", album.getId());
        data.put("name", album.getName());
        data.put("picUrl", coverOf(album));
        data.put("description", album.getDescription());
        data.put("publishTime", album.getReleaseDate() == null ? null : album.getReleaseDate().toString());
        data.put("size", tracks.size());
        data.put("artistName", albumArtist == null ? "" : albumArtist.getName());
        data.put("artist", artistItem(albumArtist));

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("code", 200);
        response.put("album", data);
        response.put("songs", tracks);
        return response;
    }

    @GetMapping("/record/recent/song")
    public Map<String, Object> recentSongs() {
        return recommendSongs();
    }

    @GetMapping("/user/playlist")
    public Map<String, Object> userPlaylist() {
        List<Playlist> playlists = playlistMapper.selectList(
                new LambdaQueryWrapper<Playlist>().eq(Playlist::getStatus, 1).last("LIMIT 30"));
        return Map.of("code", 200, "playlist", playlists.stream().map(p -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", p.getId());
            m.put("name", p.getName());
            m.put("coverImgUrl", previewUrl(p.getCover()));
            return m;
        }).toList());
    }

    private List<Song> orderedSongs(List<Long> songIds) {
        Map<Long, Song> songMap = songMapper.selectList(
                        new LambdaQueryWrapper<Song>()
                                .in(Song::getId, songIds)
                                .eq(Song::getStatus, 1))
                .stream()
                .collect(Collectors.toMap(Song::getId, Function.identity(), (a, b) -> a));
        List<Song> songs = new ArrayList<>();
        for (Long songId : songIds) {
            Song song = songMap.get(songId);
            if (song != null) {
                songs.add(song);
            }
        }
        return songs;
    }

    private Map<Long, Album> albumMapFromSongs(Collection<Song> songs) {
        Set<Long> albumIds = songs.stream()
                .map(Song::getAlbumId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (albumIds.isEmpty()) {
            return new LinkedHashMap<>();
        }
        return albumMapper.selectBatchIds(albumIds).stream()
                .collect(Collectors.toMap(Album::getId, Function.identity(), (a, b) -> a, LinkedHashMap::new));
    }

    private Map<Long, Artist> artistMapFromSongs(Collection<Song> songs) {
        Set<Long> artistIds = songs.stream()
                .flatMap(song -> parseArtistIds(song.getArtistIds()).stream())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (artistIds.isEmpty()) {
            return new LinkedHashMap<>();
        }
        return artistMapper.selectBatchIds(artistIds).stream()
                .collect(Collectors.toMap(Artist::getId, Function.identity(), (a, b) -> a, LinkedHashMap::new));
    }

    private List<Long> parseArtistIds(String artistIds) {
        if (!StringUtils.hasText(artistIds)) {
            return List.of();
        }
        List<Long> ids = new ArrayList<>();
        for (String part : artistIds.split(",")) {
            try {
                ids.add(Long.parseLong(part.trim()));
            } catch (NumberFormatException ignored) {
                // 忽略非法歌手编号，兼容旧数据。
            }
        }
        return ids;
    }

    private Map<String, Object> trackOf(Song song, Map<Long, Album> albumMap, Map<Long, Artist> artistMap) {
        Map<String, Object> track = new LinkedHashMap<>();
        track.put("id", song.getId());
        track.put("name", song.getName());
        track.put("dt", song.getDuration() == null ? 0 : song.getDuration() * 1000);
        track.put("duration", song.getDuration() == null ? 0 : song.getDuration() * 1000);
        track.put("cover", coverOf(song, albumMap));
        track.put("albumId", song.getAlbumId() == null ? 0 : song.getAlbumId());
        track.put("artistIds", song.getArtistIds());
        track.put("artistName", artistNameOf(song.getArtistIds(), artistMap));
        track.put("al", albumItem(song, albumMap));
        track.put("album", albumItem(song, albumMap));
        track.put("ar", artistItems(song.getArtistIds(), artistMap));
        track.put("artists", artistItems(song.getArtistIds(), artistMap));
        return track;
    }

    private Map<String, Object> albumItem(Song song, Map<Long, Album> albumMap) {
        Album album = song.getAlbumId() == null ? null : albumMap.get(song.getAlbumId());
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", song.getAlbumId() == null ? 0 : song.getAlbumId());
        item.put("name", album == null || album.getName() == null ? "" : album.getName());
        item.put("picUrl", coverOf(song, albumMap));
        return item;
    }

    private List<Map<String, Object>> artistItems(String artistIds, Map<Long, Artist> artistMap) {
        List<Long> ids = parseArtistIds(artistIds);
        if (ids.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> items = new ArrayList<>();
        for (Long artistId : ids) {
            Artist artist = artistMap.get(artistId);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", artistId);
            item.put("name", artist == null || !StringUtils.hasText(artist.getName())
                    ? "歌手 " + artistId
                    : artist.getName());
            items.add(item);
        }
        return items;
    }

    private String artistNameOf(String artistIds, Map<Long, Artist> artistMap) {
        List<Map<String, Object>> artists = artistItems(artistIds, artistMap);
        if (artists.isEmpty()) {
            return StringUtils.hasText(artistIds) ? artistIds : "";
        }
        return artists.stream()
                .map(item -> String.valueOf(item.get("name")))
                .collect(Collectors.joining(" / "));
    }

    private Map<String, Object> artistItem(Artist artist) {
        Map<String, Object> item = new LinkedHashMap<>();
        if (artist == null) {
            item.put("id", 0);
            item.put("name", "");
            return item;
        }
        item.put("id", artist.getId());
        item.put("name", artist.getName() == null ? "" : artist.getName());
        return item;
    }

    private String coverOf(Song song, Map<Long, Album> albumMap) {
        if (song != null && StringUtils.hasText(song.getCover())) {
            return previewUrl(song.getCover());
        }
        Album album = song == null || song.getAlbumId() == null ? null : albumMap.get(song.getAlbumId());
        if (album != null && StringUtils.hasText(album.getCover())) {
            return previewUrl(album.getCover());
        }
        return previewUrl(Constants.DEFAULT_COVER);
    }

    private String coverOf(Album album) {
        return previewUrl(album != null && StringUtils.hasText(album.getCover())
                ? album.getCover()
                : Constants.DEFAULT_COVER);
    }

    private String previewUrl(String value) {
        String resolved = minioService.resolvePreviewUrl(value);
        return StringUtils.hasText(resolved) ? resolved : value;
    }
}
