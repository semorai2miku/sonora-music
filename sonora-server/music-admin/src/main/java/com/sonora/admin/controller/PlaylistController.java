package com.sonora.admin.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sonora.common.constant.Constants;
import com.sonora.common.result.R;
import com.sonora.mapper.AlbumMapper;
import com.sonora.mapper.PlaylistMapper;
import com.sonora.mapper.PlaylistSongMapper;
import com.sonora.mapper.SongMapper;
import com.sonora.mapper.UserMapper;
import com.sonora.model.entity.Album;
import com.sonora.model.entity.Playlist;
import com.sonora.model.entity.PlaylistSong;
import com.sonora.model.entity.Song;
import com.sonora.model.entity.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Tag(name = "管理端-歌单管理", description = "歌单信息与歌曲关联维护")
@RestController
@RequestMapping("/api/admin/playlists")
public class PlaylistController {

    private static final String SYSTEM_PUBLISHER_USERNAME = "sonora-music";
    private static final String NORMAL_PLAYLIST_TYPE = "normal";
    private static final String LIKED_PLAYLIST_TYPE = "liked";

    private final PlaylistMapper playlistMapper;
    private final PlaylistSongMapper playlistSongMapper;
    private final SongMapper songMapper;
    private final AlbumMapper albumMapper;
    private final UserMapper userMapper;

    public PlaylistController(PlaylistMapper playlistMapper,
                              PlaylistSongMapper playlistSongMapper,
                              SongMapper songMapper,
                              AlbumMapper albumMapper,
                              UserMapper userMapper) {
        this.playlistMapper = playlistMapper;
        this.playlistSongMapper = playlistSongMapper;
        this.songMapper = songMapper;
        this.albumMapper = albumMapper;
        this.userMapper = userMapper;
    }

    @Operation(summary = "分页查询歌单列表")
    @GetMapping
    public R<Map<String, Object>> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Long userId) {

        LambdaQueryWrapper<Playlist> wrapper = new LambdaQueryWrapper<>();
        wrapper.and(w -> w.eq(Playlist::getType, NORMAL_PLAYLIST_TYPE).or().isNull(Playlist::getType));
        if (StringUtils.hasText(keyword)) {
            wrapper.like(Playlist::getName, keyword);
        }
        if (status != null) {
            wrapper.eq(Playlist::getStatus, status);
        }
        if (userId != null) {
            wrapper.eq(Playlist::getUserId, userId);
        }
        wrapper.orderByAsc(Playlist::getId);

        Page<Playlist> page = playlistMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        List<Map<String, Object>> list = page.getRecords().stream()
                .map(this::toListItem)
                .toList();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", list);
        data.put("total", page.getTotal());
        data.put("pageNum", pageNum);
        data.put("pageSize", pageSize);
        return R.ok(data);
    }

    @Operation(summary = "分页查询歌单发布者选项")
    @GetMapping("/publishers/options")
    public R<Map<String, Object>> publisherOptions(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "40") int pageSize) {
        List<Long> userIds = playlistMapper.selectObjs(new LambdaQueryWrapper<Playlist>()
                        .select(Playlist::getUserId)
                        .and(w -> w.eq(Playlist::getType, NORMAL_PLAYLIST_TYPE).or().isNull(Playlist::getType))
                        .groupBy(Playlist::getUserId)
                        .orderByAsc(Playlist::getUserId))
                .stream()
                .filter(Objects::nonNull)
                .map(value -> value instanceof Number number ? number.longValue() : Long.parseLong(String.valueOf(value)))
                .distinct()
                .toList();

        int total = userIds.size();
        int fromIndex = Math.max(0, (pageNum - 1) * pageSize);
        int toIndex = Math.min(total, fromIndex + pageSize);
        List<Long> pageIds = fromIndex >= total ? List.of() : userIds.subList(fromIndex, toIndex);

        List<Map<String, Object>> list = pageIds.stream()
                .map(userId -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", userId);
                    item.put("name", publisherName(userId));
                    return item;
                })
                .toList();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", list);
        data.put("total", total);
        data.put("pageNum", pageNum);
        data.put("pageSize", pageSize);
        return R.ok(data);
    }

    @Operation(summary = "歌单详情")
    @GetMapping("/{id}")
    public R<Map<String, Object>> getById(@PathVariable Long id) {
        Playlist playlist = playlistMapper.selectById(id);
        if (playlist == null) {
            return R.notFound("歌单不存在");
        }
        if (isLikedPlaylist(playlist)) {
            return R.notFound("歌单不存在");
        }
        return R.ok(toDetail(playlist));
    }

    @Operation(summary = "新增歌单")
    @PostMapping
    public R<Map<String, Object>> create(@RequestBody PlaylistRequest request) {
        if (!StringUtils.hasText(request.name())) {
            return R.badRequest("歌单名称不能为空");
        }
        if (!isValidStatus(request.status())) {
            return R.badRequest("歌单状态仅支持公开或私有");
        }

        Playlist playlist = new Playlist();
        copyToPlaylist(request, playlist);
        playlist.setCover(resolvePlaylistCover(request.cover(), request.songIds()));
        playlist.setUserId(systemPublisherId());
        playlist.setType(NORMAL_PLAYLIST_TYPE);
        playlist.setPinned(0);
        playlist.setPlayCount(0L);
        playlist.setCollectCount(0L);
        if (playlist.getStatus() == null) {
            playlist.setStatus(1);
        }

        playlistMapper.insert(playlist);
        replaceSongs(playlist.getId(), request.songIds());
        return R.ok(toDetail(playlistMapper.selectById(playlist.getId())));
    }

    @Operation(summary = "编辑歌单")
    @PutMapping("/{id}")
    public R<Map<String, Object>> update(@PathVariable Long id, @RequestBody PlaylistRequest request) {
        Playlist existing = playlistMapper.selectById(id);
        if (existing == null) {
            return R.notFound("歌单不存在");
        }
        if (isLikedPlaylist(existing)) {
            return R.badRequest("默认喜欢歌单不支持在后台管理中编辑");
        }
        if (!StringUtils.hasText(request.name())) {
            return R.badRequest("歌单名称不能为空");
        }
        if (!isValidStatus(request.status())) {
            return R.badRequest("歌单状态仅支持公开或私有");
        }

        Playlist playlist = new Playlist();
        playlist.setId(id);
        copyToPlaylist(request, playlist);
        playlist.setCover(resolvePlaylistCover(request.cover(), request.songIds()));
        playlistMapper.updateById(playlist);
        if (request.songIds() != null) {
            replaceSongs(id, request.songIds());
        }
        return R.ok(toDetail(playlistMapper.selectById(id)));
    }

    @Operation(summary = "删除歌单")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        Playlist playlist = playlistMapper.selectById(id);
        if (playlist == null) {
            return R.notFound("歌单不存在");
        }
        if (isLikedPlaylist(playlist)) {
            return R.badRequest("默认喜欢歌单不支持在后台管理中删除");
        }
        playlistSongMapper.delete(new LambdaQueryWrapper<PlaylistSong>()
                .eq(PlaylistSong::getPlaylistId, id));
        playlistMapper.deleteById(id);
        return R.ok();
    }

    @Operation(summary = "批量删除歌单")
    @PostMapping("/batch-delete")
    @Transactional
    public R<Map<String, Object>> batchDelete(@RequestBody BatchDeleteRequest body) {
        if (body == null || body.ids() == null || body.ids().isEmpty()) {
            return R.badRequest("请选择要删除的歌单");
        }
        List<Map<String, Object>> deletedPlaylists = new ArrayList<>();
        for (Long id : body.ids().stream().filter(Objects::nonNull).distinct().toList()) {
            Playlist playlist = playlistMapper.selectById(id);
            if (playlist == null) {
                continue;
            }
            if (isLikedPlaylist(playlist)) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", id);
            item.put("name", playlist.getName());
            playlistSongMapper.delete(new LambdaQueryWrapper<PlaylistSong>()
                    .eq(PlaylistSong::getPlaylistId, id));
            playlistMapper.deleteById(id);
            deletedPlaylists.add(item);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("count", deletedPlaylists.size());
        data.put("deleted", deletedPlaylists);
        return R.ok(data);
    }

    @Operation(summary = "切换歌单公开/私有状态")
    @PutMapping("/{id}/status")
    public R<Map<String, Object>> toggleStatus(@PathVariable Long id, @RequestParam int status) {
        if (!isValidStatus(status)) {
            return R.badRequest("歌单状态仅支持公开或私有");
        }
        Playlist playlist = playlistMapper.selectById(id);
        if (playlist == null) {
            return R.notFound("歌单不存在");
        }
        if (isLikedPlaylist(playlist)) {
            return R.badRequest("默认喜欢歌单不可修改公开状态");
        }
        playlist.setStatus(status);
        playlistMapper.updateById(playlist);
        return R.ok(toDetail(playlistMapper.selectById(id)));
    }

    @Operation(summary = "替换歌单歌曲")
    @PutMapping("/{id}/songs")
    public R<Map<String, Object>> updateSongs(@PathVariable Long id, @RequestBody PlaylistSongsRequest request) {
        Playlist playlist = playlistMapper.selectById(id);
        if (playlist == null) {
            return R.notFound("歌单不存在");
        }
        if (isLikedPlaylist(playlist)) {
            return R.badRequest("默认喜欢歌单不支持在后台管理中编辑");
        }
        replaceSongs(id, request.songIds());
        return R.ok(toDetail(playlistMapper.selectById(id)));
    }

    private Map<String, Object> toListItem(Playlist playlist) {
        Map<String, Object> item = basePlaylistMap(playlist);
        Long songCount = playlistSongMapper.selectCount(new LambdaQueryWrapper<PlaylistSong>()
                .eq(PlaylistSong::getPlaylistId, playlist.getId()));
        item.put("songCount", songCount);
        return item;
    }

    private Map<String, Object> toDetail(Playlist playlist) {
        Map<String, Object> item = basePlaylistMap(playlist);
        List<PlaylistSong> links = playlistSongMapper.selectList(new LambdaQueryWrapper<PlaylistSong>()
                .eq(PlaylistSong::getPlaylistId, playlist.getId())
                .orderByAsc(PlaylistSong::getSort)
                .orderByAsc(PlaylistSong::getId));

        List<Long> songIds = links.stream().map(PlaylistSong::getSongId).toList();
        List<Song> songs = orderedSongs(songIds);
        item.put("songIds", songIds);
        item.put("songs", songs);
        item.put("songCount", songs.size());
        return item;
    }

    private Map<String, Object> basePlaylistMap(Playlist playlist) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", playlist.getId());
        item.put("name", playlist.getName());
        item.put("cover", playlistCover(playlist));
        item.put("userId", playlist.getUserId());
        String publisher = publisherName(playlist.getUserId());
        item.put("publisher", publisher);
        item.put("publisherName", publisher);
        item.put("description", playlist.getDescription());
        item.put("tags", playlist.getTags());
        item.put("playCount", playlist.getPlayCount());
        item.put("collectCount", playlist.getCollectCount());
        item.put("status", playlist.getStatus());
        item.put("createdAt", playlist.getCreatedAt());
        item.put("updatedAt", playlist.getUpdatedAt());
        return item;
    }

    private List<Song> orderedSongs(List<Long> songIds) {
        if (songIds.isEmpty()) {
            return List.of();
        }
        Map<Long, Song> songMap = songMapper.selectBatchIds(songIds).stream()
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

    private void replaceSongs(Long playlistId, List<Long> songIds) {
        playlistSongMapper.delete(new LambdaQueryWrapper<PlaylistSong>()
                .eq(PlaylistSong::getPlaylistId, playlistId));
        if (songIds == null || songIds.isEmpty()) {
            return;
        }

        int sort = 1;
        for (Long songId : songIds) {
            if (songId == null) continue;
            PlaylistSong link = new PlaylistSong();
            link.setPlaylistId(playlistId);
            link.setSongId(songId);
            link.setSort(sort++);
            playlistSongMapper.insert(link);
        }
    }

    private void copyToPlaylist(PlaylistRequest request, Playlist playlist) {
        playlist.setName(request.name().trim());
        playlist.setCover(trimToNull(request.cover()));
        playlist.setDescription(trimToNull(request.description()));
        playlist.setTags(trimToNull(request.tags()));
        playlist.setStatus(request.status());
    }

    private String resolvePlaylistCover(String cover, List<Long> songIds) {
        if (hasCustomCover(cover)) {
            return cover.trim();
        }
        return firstSongCover(songIds);
    }

    private String playlistCover(Playlist playlist) {
        if (hasCustomCover(playlist.getCover())) {
            return playlist.getCover();
        }
        List<PlaylistSong> links = playlistSongMapper.selectList(new LambdaQueryWrapper<PlaylistSong>()
                .eq(PlaylistSong::getPlaylistId, playlist.getId())
                .orderByAsc(PlaylistSong::getSort)
                .orderByAsc(PlaylistSong::getId)
                .last("LIMIT 1"));
        if (links.isEmpty()) {
            return Constants.DEFAULT_COVER;
        }
        return firstSongCover(links.stream().map(PlaylistSong::getSongId).toList());
    }

    private String firstSongCover(List<Long> songIds) {
        if (songIds == null || songIds.isEmpty()) {
            return Constants.DEFAULT_COVER;
        }
        for (Long songId : songIds) {
            if (songId == null) {
                continue;
            }
            Song song = songMapper.selectById(songId);
            if (song != null && hasCustomCover(song.getCover())) {
                return song.getCover();
            }
            if (song != null && song.getAlbumId() != null) {
                Album album = albumMapper.selectById(song.getAlbumId());
                if (album != null && hasCustomCover(album.getCover())) {
                    return album.getCover();
                }
            }
            break;
        }
        return Constants.DEFAULT_COVER;
    }

    private boolean hasCustomCover(String cover) {
        return StringUtils.hasText(cover) && !Constants.DEFAULT_COVER.equals(cover.trim());
    }

    private boolean isLikedPlaylist(Playlist playlist) {
        return playlist != null && LIKED_PLAYLIST_TYPE.equals(playlist.getType());
    }

    private Long systemPublisherId() {
        User publisher = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, SYSTEM_PUBLISHER_USERNAME)
                .last("LIMIT 1"));
        if (publisher != null) {
            return publisher.getId();
        }
        return 1L;
    }

    private String publisherName(Long userId) {
        if (userId == null) {
            return SYSTEM_PUBLISHER_USERNAME;
        }
        User user = userMapper.selectById(userId);
        if (user == null) {
            return userId == 1L ? SYSTEM_PUBLISHER_USERNAME : "用户 " + userId;
        }
        if (StringUtils.hasText(user.getUsername())) {
            return user.getUsername();
        }
        if (StringUtils.hasText(user.getNickname())) {
            return user.getNickname();
        }
        return userId == 1L ? SYSTEM_PUBLISHER_USERNAME : "用户 " + userId;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private boolean isValidStatus(Integer status) {
        return status == null || status == 0 || status == 1;
    }

    public record PlaylistRequest(
            String name,
            String cover,
            String description,
            String tags,
            Integer status,
            List<Long> songIds
    ) {}

    public record PlaylistSongsRequest(List<Long> songIds) {}

    public record BatchDeleteRequest(List<Long> ids) {}
}
