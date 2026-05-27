package com.sonora.client.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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

    public NeteaseCompatController(BannerMapper bannerMapper,
                                   SongMapper songMapper,
                                   PlaylistMapper playlistMapper,
                                   PlaylistSongMapper playlistSongMapper,
                                   ArtistMapper artistMapper,
                                   AlbumMapper albumMapper) {
        this.bannerMapper = bannerMapper;
        this.songMapper = songMapper;
        this.playlistMapper = playlistMapper;
        this.playlistSongMapper = playlistSongMapper;
        this.artistMapper = artistMapper;
        this.albumMapper = albumMapper;
    }

    // ==================== Banner ====================

    @GetMapping("/banner")
    public Map<String, Object> banner() {
        List<Banner> banners = bannerMapper.selectList(
                new LambdaQueryWrapper<Banner>().eq(Banner::getStatus, 1).orderByAsc(Banner::getSort));
        List<Map<String, Object>> list = banners.stream().map(b -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("imageUrl", b.getImageUrl());
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
            result.put("songs", songs.stream().map(s -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", s.getId());
                m.put("name", s.getName());
                m.put("dt", s.getDuration() * 1000);
                m.put("al", Map.of("id", s.getAlbumId() != null ? s.getAlbumId() : 0, "name", ""));
                m.put("ar", List.of(Map.of("id", 1, "name", s.getArtistIds())));
                return m;
            }).toList());
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
        List<Map<String, Object>> list = songs.stream().map(s -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", s.getId());
            m.put("name", s.getName());
            m.put("dt", s.getDuration() * 1000);
            m.put("al", Map.of("id", s.getAlbumId() != null ? s.getAlbumId() : 0, "picUrl", s.getCover()));
            m.put("ar", List.of(Map.of("id", 1, "name", s.getArtistIds())));
            return m;
        }).toList();
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

        List<Map<String, Object>> tracks = songs.stream().map(s -> {
            Map<String, Object> t = new LinkedHashMap<>();
            t.put("id", s.getId());
            t.put("name", s.getName());
            t.put("dt", s.getDuration() * 1000);
            t.put("al", Map.of("id", s.getAlbumId() != null ? s.getAlbumId() : 0, "picUrl", s.getCover()));
            t.put("ar", List.of(Map.of("id", 1, "name", s.getArtistIds())));
            return t;
        }).toList();

        Map<String, Object> pl = new LinkedHashMap<>();
        pl.put("id", playlist.getId());
        pl.put("name", playlist.getName());
        pl.put("coverImgUrl", playlist.getCover());
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

        List<Map<String, Object>> tracks = songs.stream().map(s -> {
            Map<String, Object> t = new LinkedHashMap<>();
            t.put("id", s.getId());
            t.put("name", s.getName());
            t.put("dt", s.getDuration() * 1000);
            t.put("al", Map.of("id", s.getAlbumId() != null ? s.getAlbumId() : 0, "picUrl", s.getCover()));
            t.put("ar", List.of(Map.of("id", 1, "name", s.getArtistIds())));
            return t;
        }).toList();

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
            m.put("picUrl", p.getCover());
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

    @GetMapping("/recommend/songs")
    public Map<String, Object> recommendSongs() {
        List<Song> songs = songMapper.selectList(
                new LambdaQueryWrapper<Song>().eq(Song::getStatus, 1)
                        .orderByDesc(Song::getCreatedAt).last("LIMIT 30"));

        List<Map<String, Object>> list = songs.stream().map(s -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", s.getId());
            m.put("name", s.getName());
            m.put("dt", s.getDuration() * 1000);
            m.put("al", Map.of("id", s.getAlbumId() != null ? s.getAlbumId() : 0, "picUrl", s.getCover()));
            m.put("ar", List.of(Map.of("id", 1, "name", s.getArtistIds())));
            return m;
        }).toList();

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
        data.put("picUrl", artist.getAvatar());
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
        List<Map<String, Object>> list = songs.stream().map(s -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", s.getId());
            m.put("name", s.getName());
            m.put("dt", s.getDuration() * 1000);
            m.put("al", Map.of("id", s.getAlbumId() != null ? s.getAlbumId() : 0, "picUrl", s.getCover()));
            m.put("ar", List.of(Map.of("id", 1, "name", s.getArtistIds())));
            return m;
        }).toList();
        return Map.of("code", 200, "songs", list);
    }

    @GetMapping("/album")
    public Map<String, Object> album(@RequestParam Long id) {
        Album album = albumMapper.selectById(id);
        if (album == null) {
            return Map.of("code", 404, "message", "专辑不存在");
        }
        List<Song> songs = songMapper.selectList(
                new LambdaQueryWrapper<Song>().eq(Song::getAlbumId, id).eq(Song::getStatus, 1));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", album.getId());
        data.put("name", album.getName());
        data.put("picUrl", album.getCover());
        data.put("songs", songs.stream().map(s -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", s.getId());
            m.put("name", s.getName());
            m.put("dt", s.getDuration() * 1000);
            return m;
        }).toList());
        return Map.of("code", 200, "album", data);
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
            m.put("coverImgUrl", p.getCover());
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
}
