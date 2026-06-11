package com.sonora.client.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sonora.common.constant.Constants;
import com.sonora.common.result.R;
import com.sonora.file.service.MinioService;
import com.sonora.mapper.AlbumMapper;
import com.sonora.mapper.ArtistMapper;
import com.sonora.mapper.SongMapper;
import com.sonora.model.entity.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.util.StringUtils;

@Tag(name = "客户端-歌手", description = "歌手详情、歌曲")
@RestController
@RequestMapping("/api/client")
public class ClientArtistController {

    private final ArtistMapper artistMapper;
    private final SongMapper songMapper;
    private final AlbumMapper albumMapper;
    private final MinioService minioService;

    public ClientArtistController(ArtistMapper artistMapper,
                                  SongMapper songMapper,
                                  AlbumMapper albumMapper,
                                  MinioService minioService) {
        this.artistMapper = artistMapper;
        this.songMapper = songMapper;
        this.albumMapper = albumMapper;
        this.minioService = minioService;
    }

    @Operation(summary = "歌手详情 + 热门歌曲")
    @GetMapping("/artists/{id}")
    public R<Map<String, Object>> detail(@PathVariable Long id) {
        Artist artist = artistMapper.selectById(id);
        if (artist == null || artist.getStatus() == 0) {
            return R.notFound("歌手不存在或已下架");
        }

        List<Song> songs = songMapper.selectList(
                new LambdaQueryWrapper<Song>()
                        .apply("FIND_IN_SET({0}, artist_ids)", id)
                        .eq(Song::getStatus, 1)
                        .orderByAsc(Song::getId));
        Map<Long, Album> albumMap = albumMapFromSongs(songs);
        Map<Long, Artist> artistMap = artistMapFromSongs(songs);

        List<Album> albums = albumMapper.selectList(
                new LambdaQueryWrapper<Album>()
                        .eq(Album::getArtistId, id)
                        .eq(Album::getStatus, 1)
                        .orderByAsc(Album::getId));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", artist.getId());
        data.put("name", artist.getName());
        data.put("avatar", coverOfArtist(artist.getAvatar()));
        data.put("description", artist.getDescription());
        data.put("region", artist.getRegion());
        data.put("songCount", songs.size());
        data.put("albumCount", albums.size());
        data.put("songs", songs.stream().map(song -> songView(song, albumMap, artistMap)).toList());
        data.put("albums", albums.stream().map(album -> albumView(album, artist)).toList());
        return R.ok(data);
    }

    @Operation(summary = "歌手列表 (按地区/首字母)")
    @GetMapping("/artists")
    public R<List<Map<String, Object>>> list(
            @RequestParam(required = false) String region,
            @RequestParam(required = false) Integer limit) {
        LambdaQueryWrapper<Artist> wrapper = new LambdaQueryWrapper<Artist>()
                .eq(Artist::getStatus, 1)
                .orderByAsc(Artist::getId);
        if (StringUtils.hasText(region)) {
            wrapper.eq(Artist::getRegion, region.trim());
        }
        if (limit != null && limit > 0) {
            wrapper.last("LIMIT " + limit);
        }

        List<Artist> artists = artistMapper.selectList(wrapper);
        List<Song> songs = songMapper.selectList(
                new LambdaQueryWrapper<Song>()
                        .select(Song::getId, Song::getArtistIds)
                        .eq(Song::getStatus, 1)
                        .orderByAsc(Song::getId));
        List<Album> albums = albumMapper.selectList(
                new LambdaQueryWrapper<Album>()
                        .select(Album::getId, Album::getArtistId)
                        .eq(Album::getStatus, 1)
                        .orderByAsc(Album::getId));
        return R.ok(artists.stream().map(artist -> artistView(artist, songs, albums)).toList());
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

    private String coverOfArtist(String cover) {
        return minioService.resolvePreviewUrl(StringUtils.hasText(cover) ? cover : Constants.DEFAULT_AVATAR);
    }

    private Map<String, Object> songView(Song song, Map<Long, Album> albumMap, Map<Long, Artist> artistMap) {
        Map<String, Object> track = new LinkedHashMap<>();
        track.put("id", song.getId());
        track.put("name", song.getName());
        track.put("dt", song.getDuration() == null ? 0 : song.getDuration() * 1000);
        track.put("duration", song.getDuration() == null ? 0 : song.getDuration() * 1000);
        track.put("cover", coverOf(song.getAlbumId()));
        track.put("artistIds", song.getArtistIds());
        track.put("artistName", artistNameOf(song.getArtistIds(), artistMap));
        track.put("al", albumItem(song, albumMap));
        track.put("album", albumItem(song, albumMap));
        track.put("ar", artistItems(song.getArtistIds(), artistMap));
        track.put("artists", artistItems(song.getArtistIds(), artistMap));
        return track;
    }

    private Map<String, Object> albumView(Album album, Artist artist) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", album.getId());
        item.put("name", album.getName());
        item.put("picUrl", coverOf(album.getId()));
        item.put("artist", artistItem(artist));
        item.put("artistId", artist == null ? 0 : artist.getId());
        item.put("region", artist == null ? "" : artist.getRegion());
        item.put("publishTime", album.getReleaseDate() == null
                ? null
                : album.getReleaseDate().atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli());
        item.put("description", album.getDescription());
        item.put("size", songMapper.selectCount(new LambdaQueryWrapper<Song>()
                .eq(Song::getAlbumId, album.getId())
                .eq(Song::getStatus, 1)));
        return item;
    }

    private Map<String, Object> artistView(Artist artist, List<Song> songs, List<Album> albums) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", artist.getId());
        item.put("name", artist.getName());
        item.put("avatar", coverOfArtist(artist.getAvatar()));
        item.put("region", artist.getRegion());
        item.put("description", artist.getDescription());
        item.put("musicSize", songs.stream()
                .filter(song -> containsArtist(song.getArtistIds(), artist.getId()))
                .count());
        item.put("albumSize", albums.stream()
                .filter(album -> artist.getId().equals(album.getArtistId()))
                .count());
        return item;
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

    private Map<String, Object> artistItem(Artist artist) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", artist == null ? 0 : artist.getId());
        item.put("name", artist == null || artist.getName() == null ? "" : artist.getName());
        return item;
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

    private boolean containsArtist(String artistIds, Long artistId) {
        return parseArtistIds(artistIds).contains(artistId);
    }
}
