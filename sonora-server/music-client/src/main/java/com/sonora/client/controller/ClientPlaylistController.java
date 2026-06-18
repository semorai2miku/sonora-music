package com.sonora.client.controller;

import com.sonora.common.constant.Constants;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sonora.common.result.R;
import com.sonora.file.service.MinioService;
import com.sonora.mapper.PlaylistMapper;
import com.sonora.mapper.PlaylistSongMapper;
import com.sonora.mapper.SongMapper;
import com.sonora.mapper.UserFavoriteMapper;
import com.sonora.mapper.UserMapper;
import com.sonora.mapper.AlbumMapper;
import com.sonora.mapper.ArtistMapper;
import com.sonora.model.entity.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Tag(name = "客户端-歌单", description = "歌单列表、详情、排行榜")
@RestController
@RequestMapping("/api/client")
public class ClientPlaylistController {
    private static final String PLAYLIST_TYPE_NORMAL = "normal";
    private static final String FAVORITE_TYPE_PLAYLIST = "playlist";

    private final PlaylistMapper playlistMapper;
    private final PlaylistSongMapper playlistSongMapper;
    private final SongMapper songMapper;
    private final UserFavoriteMapper userFavoriteMapper;
    private final UserMapper userMapper;
    private final AlbumMapper albumMapper;
    private final ArtistMapper artistMapper;
    private final MinioService minioService;

    public ClientPlaylistController(PlaylistMapper playlistMapper,
                                    PlaylistSongMapper playlistSongMapper,
                                    SongMapper songMapper,
                                    UserFavoriteMapper userFavoriteMapper,
                                    UserMapper userMapper,
                                    AlbumMapper albumMapper,
                                    ArtistMapper artistMapper,
                                    MinioService minioService) {
        this.playlistMapper = playlistMapper;
        this.playlistSongMapper = playlistSongMapper;
        this.songMapper = songMapper;
        this.userFavoriteMapper = userFavoriteMapper;
        this.userMapper = userMapper;
        this.albumMapper = albumMapper;
        this.artistMapper = artistMapper;
        this.minioService = minioService;
    }

    @Operation(summary = "推荐歌单 (按收藏数)")
    @GetMapping("/playlists/recommend")
    public R<List<Map<String, Object>>> recommend(@RequestParam(defaultValue = "20") int limit) {
        int size = normalizePageSize(limit, 20);
        Long viewerUserId = currentUserId();
        List<Playlist> list = playlistMapper.selectList(
                new LambdaQueryWrapper<Playlist>()
                        .eq(Playlist::getStatus, 1)
                        .eq(Playlist::getType, PLAYLIST_TYPE_NORMAL)
                        .orderByDesc(Playlist::getCollectCount)
                        .orderByDesc(Playlist::getId)
                        .last("LIMIT " + size));
        return R.ok(list.stream().map(item -> playlistOf(item, viewerUserId)).toList());
    }

    @Operation(summary = "公开歌单列表")
    @GetMapping("/playlists")
    public R<Map<String, Object>> list(@RequestParam(defaultValue = "1") int pageNum,
                                       @RequestParam(defaultValue = "24") int pageSize,
                                       @RequestParam(required = false) String keyword) {
        int current = Math.max(pageNum, 1);
        int size = normalizePageSize(pageSize, 24);
        LambdaQueryWrapper<Playlist> queryWrapper = new LambdaQueryWrapper<Playlist>()
                .eq(Playlist::getStatus, 1)
                .eq(Playlist::getType, PLAYLIST_TYPE_NORMAL);
        if (StringUtils.hasText(keyword)) {
            String keywordValue = keyword.trim();
            queryWrapper.and(wrapper -> wrapper
                    .like(Playlist::getName, keywordValue)
                    .or()
                    .like(Playlist::getDescription, keywordValue)
                    .or()
                    .like(Playlist::getTags, keywordValue));
        }
        queryWrapper.orderByDesc(Playlist::getCollectCount).orderByDesc(Playlist::getId);
        Page<Playlist> page = playlistMapper.selectPage(
                new Page<>(current, size),
                queryWrapper);
        Long viewerUserId = currentUserId();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", page.getRecords().stream().map(item -> playlistOf(item, viewerUserId)).toList());
        data.put("total", page.getTotal());
        data.put("pageNum", current);
        data.put("pageSize", size);
        return R.ok(data);
    }

    @Operation(summary = "歌单详情")
    @GetMapping("/playlists/{id}")
    public R<Map<String, Object>> detail(@PathVariable Long id) {
        Playlist playlist = playlistMapper.selectById(id);
        if (playlist == null || playlist.getStatus() == 0 || !Objects.equals(playlist.getType(), PLAYLIST_TYPE_NORMAL)) {
            return R.notFound("歌单不存在");
        }
        return R.ok(playlistDetailOf(playlist, currentUserId()));
    }

    @Operation(summary = "排行榜 (按播放量)")
    @GetMapping("/playlists/top")
    public R<List<Map<String, Object>>> top(@RequestParam(defaultValue = "10") int limit) {
        int size = normalizePageSize(limit, 10);
        Long viewerUserId = currentUserId();
        List<Playlist> list = playlistMapper.selectList(
                new LambdaQueryWrapper<Playlist>()
                        .eq(Playlist::getStatus, 1)
                        .eq(Playlist::getType, PLAYLIST_TYPE_NORMAL)
                        .orderByDesc(Playlist::getPlayCount)
                        .orderByDesc(Playlist::getId)
                        .last("LIMIT " + size));
        return R.ok(list.stream().map(item -> playlistOf(item, viewerUserId)).toList());
    }

    private Map<String, Object> playlistDetailOf(Playlist playlist, Long viewerUserId) {
        Map<String, Object> data = new LinkedHashMap<>(playlistOf(playlist, viewerUserId));
        List<PlaylistSong> links = playlistSongMapper.selectList(
                new LambdaQueryWrapper<PlaylistSong>()
                        .eq(PlaylistSong::getPlaylistId, playlist.getId())
                        .orderByAsc(PlaylistSong::getSort)
                        .orderByAsc(PlaylistSong::getId));
        List<Long> songIds = links.stream().map(PlaylistSong::getSongId).toList();
        List<Song> songs = songIds.isEmpty()
                ? List.of()
                : songMapper.selectBatchIds(songIds);
        Map<Long, Song> songMap = songs.stream()
                .collect(Collectors.toMap(Song::getId, song -> song, (a, b) -> a, LinkedHashMap::new));
        Map<Long, Album> albumMap = albumMapFromSongs(songMap.values());
        Map<Long, Artist> artistMap = artistMapFromSongs(songMap.values());
        List<Map<String, Object>> songItems = new ArrayList<>();
        for (Long songId : songIds) {
            Song song = songMap.get(songId);
            if (song != null && !Objects.equals(song.getStatus(), 0)) {
                songItems.add(songOf(song, albumMap, artistMap));
            }
        }

        data.put("playCount", playlist.getPlayCount());
        data.put("collectCount", playlist.getCollectCount());
        data.put("tags", playlist.getTags());
        data.put("songs", songItems);
        data.put("songCount", songItems.size());
        data.put("updatedAt", playlist.getUpdatedAt());
        return data;
    }

    private Map<String, Object> playlistOf(Playlist playlist, Long viewerUserId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", playlist.getId());
        data.put("name", playlist.getName());
        data.put("cover", minioService.resolvePreviewUrl(playlistCoverOf(playlist)));
        data.put("type", playlist.getType());
        data.put("pinned", playlist.getPinned());
        data.put("status", playlist.getStatus());
        data.put("description", playlist.getDescription());
        data.put("playCount", playlist.getPlayCount());
        data.put("collectCount", playlist.getCollectCount());
        data.put("songCount", playlistSongMapper.selectCount(
                new LambdaQueryWrapper<PlaylistSong>().eq(PlaylistSong::getPlaylistId, playlist.getId())));
        data.put("createdAt", playlist.getCreatedAt());
        data.put("subscribed", viewerUserId != null && isPlaylistFavorite(viewerUserId, playlist.getId()));
        data.put("creatorId", playlist.getUserId());

        User owner = playlist.getUserId() == null ? null : userMapper.selectById(playlist.getUserId());
        Map<String, Object> creator = new LinkedHashMap<>();
        creator.put("userId", owner == null ? playlist.getUserId() : owner.getId());
        creator.put("nickname", owner == null
                ? "用户 " + playlist.getUserId()
                : (StringUtils.hasText(owner.getNickname()) ? owner.getNickname() : owner.getUsername()));
        creator.put("avatarUrl", owner == null
                ? minioService.resolvePreviewUrl(Constants.DEFAULT_AVATAR)
                : minioService.resolvePreviewUrl(
                StringUtils.hasText(owner.getAvatar()) ? owner.getAvatar() : Constants.DEFAULT_AVATAR));
        data.put("creator", creator);
        return data;
    }

    private Map<String, Object> songOf(Song song, Map<Long, Album> albumMap, Map<Long, Artist> artistMap) {
        String cover = songCoverOf(song, albumMap);
        Album album = song.getAlbumId() == null ? null : albumMap.get(song.getAlbumId());
        List<Map<String, Object>> artists = artistItems(song.getArtistIds(), artistMap);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", song.getId());
        data.put("name", song.getName());
        data.put("dt", song.getDuration() == null ? 0 : song.getDuration() * 1000);
        data.put("duration", song.getDuration() == null ? 0 : song.getDuration() * 1000);
        data.put("cover", cover);
        data.put("liked", false);
        Map<String, Object> albumData = new LinkedHashMap<>();
        albumData.put("id", song.getAlbumId() == null ? 0 : song.getAlbumId());
        albumData.put("name", album == null || album.getName() == null ? "" : album.getName());
        albumData.put("picUrl", cover);
        data.put("al", albumData);
        data.put("album", albumData);
        data.put("ar", artists);
        data.put("artists", artists);
        data.put("artistName", artistNameOf(song.getArtistIds(), artistMap));
        return data;
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
                .collect(Collectors.toMap(Album::getId, album -> album, (a, b) -> a, LinkedHashMap::new));
    }

    private Map<Long, Artist> artistMapFromSongs(Collection<Song> songs) {
        Set<Long> artistIds = songs.stream()
                .flatMap(song -> parseArtistIds(song.getArtistIds()).stream())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (artistIds.isEmpty()) {
            return new LinkedHashMap<>();
        }
        return artistMapper.selectBatchIds(artistIds).stream()
                .collect(Collectors.toMap(Artist::getId, artist -> artist, (a, b) -> a, LinkedHashMap::new));
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
            return "";
        }
        return artists.stream()
                .map(item -> String.valueOf(item.get("name")))
                .collect(Collectors.joining(" / "));
    }

    private String songCoverOf(Song song, Map<Long, Album> albumMap) {
        Album album = song == null || song.getAlbumId() == null ? null : albumMap.get(song.getAlbumId());
        if (album != null && StringUtils.hasText(album.getCover())) {
            return minioService.resolvePreviewUrl(album.getCover());
        }
        return Constants.DEFAULT_COVER;
    }

    private String playlistCoverOf(Playlist playlist) {
        if (playlist == null) {
            return Constants.DEFAULT_COVER;
        }
        String cover = playlist.getCover();
        if (hasManualPlaylistCover(cover)) {
            return minioService.normalizeForStorage(cover);
        }
        String firstSongCover = firstPlaylistSongCover(playlist.getId());
        return StringUtils.hasText(firstSongCover) ? firstSongCover : Constants.DEFAULT_COVER;
    }

    private String firstPlaylistSongCover(Long playlistId) {
        if (playlistId == null) {
            return Constants.DEFAULT_COVER;
        }
        List<PlaylistSong> links = playlistSongMapper.selectList(
                new LambdaQueryWrapper<PlaylistSong>()
                        .eq(PlaylistSong::getPlaylistId, playlistId)
                        .orderByAsc(PlaylistSong::getSort)
                        .orderByAsc(PlaylistSong::getId)
                        .last("LIMIT 1"));
        if (links.isEmpty()) {
            return Constants.DEFAULT_COVER;
        }
        Song firstSong = songMapper.selectById(links.get(0).getSongId());
        if (firstSong == null || Objects.equals(firstSong.getStatus(), 0)) {
            return Constants.DEFAULT_COVER;
        }
        return songCoverOf(firstSong, albumMapFromSongs(List.of(firstSong)));
    }

    private boolean hasManualPlaylistCover(String cover) {
        if (!StringUtils.hasText(cover)) {
            return false;
        }
        String normalized = minioService.normalizeForStorage(cover);
        return normalized.startsWith("playlist-cover/")
                || normalized.startsWith("cover/")
                || normalized.startsWith("http://")
                || normalized.startsWith("https://");
    }

    private boolean isPlaylistFavorite(Long userId, Long playlistId) {
        return userFavoriteMapper.selectCount(
                new LambdaQueryWrapper<UserFavorite>()
                        .eq(UserFavorite::getUserId, userId)
                        .eq(UserFavorite::getTargetType, FAVORITE_TYPE_PLAYLIST)
                        .eq(UserFavorite::getTargetId, playlistId)) > 0;
    }

    private int normalizePageSize(int requested, int fallback) {
        if (requested <= 0) {
            return fallback;
        }
        return Math.min(requested, Constants.MAX_PAGE_SIZE);
    }

    private Long currentUserId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null || "anonymousUser".equals(auth.getPrincipal())) {
            return null;
        }
        Object principal = auth.getPrincipal();
        if (principal instanceof Long id) {
            return id;
        }
        try {
            return Long.valueOf(String.valueOf(principal));
        } catch (Exception e) {
            return null;
        }
    }
}
