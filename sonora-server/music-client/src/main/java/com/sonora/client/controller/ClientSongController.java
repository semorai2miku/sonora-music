package com.sonora.client.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sonora.common.constant.Constants;
import com.sonora.common.result.R;
import com.sonora.file.service.MinioService;
import com.sonora.mapper.AlbumMapper;
import com.sonora.mapper.SongMapper;
import com.sonora.model.entity.Album;
import com.sonora.model.entity.Artist;
import com.sonora.model.entity.Song;
import com.sonora.mapper.ArtistMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.util.StringUtils;

@Tag(name = "客户端-歌曲", description = "歌曲详情、歌词")
@RestController
@RequestMapping("/api/client")
public class ClientSongController {

    private final SongMapper songMapper;
    private final AlbumMapper albumMapper;
    private final ArtistMapper artistMapper;
    private final MinioService minioService;

    public ClientSongController(SongMapper songMapper, AlbumMapper albumMapper, ArtistMapper artistMapper,
                                MinioService minioService) {
        this.songMapper = songMapper;
        this.albumMapper = albumMapper;
        this.artistMapper = artistMapper;
        this.minioService = minioService;
    }

    @Operation(summary = "歌曲列表")
    @GetMapping("/songs")
    public R<List<Map<String, Object>>> list(
            @RequestParam(defaultValue = "100") int limit,
            @RequestParam(defaultValue = "id_desc") String sort) {
        LambdaQueryWrapper<Song> wrapper = new LambdaQueryWrapper<Song>()
                .eq(Song::getStatus, 1);

        switch (sort) {
            case "id_asc" -> wrapper.orderByAsc(Song::getId);
            case "play_desc" -> wrapper.orderByDesc(Song::getPlayCount).orderByDesc(Song::getId);
            case "created_desc" -> wrapper.orderByDesc(Song::getCreatedAt).orderByDesc(Song::getId);
            default -> wrapper.orderByDesc(Song::getId);
        }

        wrapper.last("LIMIT " + Math.max(limit, 1));
        List<Song> songs = songMapper.selectList(wrapper);
        Map<Long, Album> albumMap = albumMapFromSongs(songs);
        Map<Long, Artist> artistMap = artistMapFromSongs(songs);
        return R.ok(songs.stream().map(song -> songView(song, albumMap, artistMap)).toList());
    }

    @Operation(summary = "歌曲详情")
    @GetMapping("/songs/{id}")
    public R<Map<String, Object>> detail(@PathVariable Long id) {
        Song song = songMapper.selectById(id);
        if (song == null || song.getStatus() == 0) {
            return R.notFound("歌曲不存在或已下架");
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", song.getId());
        data.put("name", song.getName());
        data.put("artistIds", song.getArtistIds());
        data.put("albumId", song.getAlbumId());
        data.put("duration", song.getDuration());
        data.put("cover", coverOf(song.getAlbumId()));
        data.put("format", song.getFormat());
        data.put("fileSize", song.getFileSize());
        data.put("playCount", song.getPlayCount());
        data.put("likeCount", song.getLikeCount() == null ? 0L : song.getLikeCount());
        data.put("streamUrl", "/api/client/songs/" + id + "/stream");
        return R.ok(data);
    }

    @Operation(summary = "歌曲歌词")
    @GetMapping("/songs/{id}/lyric")
    public R<Map<String, Object>> lyric(@PathVariable Long id) {
        Song song = songMapper.selectById(id);
        if (song == null || song.getStatus() == 0) {
            return R.notFound("歌曲不存在或已下架");
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", id);
        data.put("lyric", song.getLyrics() != null ? song.getLyrics() : "");
        return R.ok(data);
    }

    @Operation(summary = "热门歌曲 (按播放量)")
    @GetMapping("/songs/hot")
    public R<List<Song>> hot(@RequestParam(defaultValue = "20") int limit) {
        List<Song> songs = songMapper.selectList(
                new LambdaQueryWrapper<Song>()
                        .eq(Song::getStatus, 1)
                        .orderByDesc(Song::getPlayCount)
                        .last("LIMIT " + limit));
        songs.forEach(song -> song.setCover(coverOf(song.getAlbumId())));
        return R.ok(songs);
    }

    @Operation(summary = "最新歌曲")
    @GetMapping("/songs/new")
    public R<List<Song>> newest(@RequestParam(defaultValue = "20") int limit) {
        List<Song> songs = songMapper.selectList(
                new LambdaQueryWrapper<Song>()
                        .eq(Song::getStatus, 1)
                        .orderByDesc(Song::getCreatedAt)
                        .last("LIMIT " + limit));
        songs.forEach(song -> song.setCover(coverOf(song.getAlbumId())));
        return R.ok(songs);
    }

    private String coverOf(Long albumId) {
        if (albumId == null) {
            return Constants.DEFAULT_COVER;
        }
        Album album = albumMapper.selectById(albumId);
        if (album != null && StringUtils.hasText(album.getCover())) {
            return minioService.resolvePreviewUrl(album.getCover());
        }
        return minioService.resolvePreviewUrl(Constants.DEFAULT_COVER);
    }

    private Map<String, Object> songView(Song song, Map<Long, Album> albumMap, Map<Long, Artist> artistMap) {
        Map<String, Object> track = new LinkedHashMap<>();
        track.put("id", song.getId());
        track.put("name", song.getName());
        track.put("dt", song.getDuration() == null ? 0 : song.getDuration() * 1000);
        track.put("duration", song.getDuration() == null ? 0 : song.getDuration() * 1000);
        track.put("cover", coverOf(song.getAlbumId()));
        track.put("artistIds", song.getArtistIds());
        track.put("playCount", song.getPlayCount() == null ? 0L : song.getPlayCount());
        track.put("likeCount", song.getLikeCount() == null ? 0L : song.getLikeCount());
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
        item.put("picUrl", coverOf(song.getAlbumId()));
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
        return artists.stream().map(item -> String.valueOf(item.get("name"))).reduce((a, b) -> a + " / " + b).orElse("");
    }

    private Map<Long, Album> albumMapFromSongs(List<Song> songs) {
        Set<Long> albumIds = new LinkedHashSet<>();
        for (Song song : songs) {
            if (song.getAlbumId() != null) {
                albumIds.add(song.getAlbumId());
            }
        }
        if (albumIds.isEmpty()) {
            return Map.of();
        }
        return albumMapper.selectBatchIds(albumIds).stream()
                .collect(java.util.stream.Collectors.toMap(Album::getId, album -> album));
    }

    private Map<Long, Artist> artistMapFromSongs(List<Song> songs) {
        Set<Long> artistIds = new LinkedHashSet<>();
        for (Song song : songs) {
            artistIds.addAll(parseArtistIds(song.getArtistIds()));
        }
        if (artistIds.isEmpty()) {
            return Map.of();
        }
        return artistMapper.selectBatchIds(artistIds).stream()
                .collect(java.util.stream.Collectors.toMap(Artist::getId, artist -> artist));
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
            }
        }
        return ids;
    }
}
