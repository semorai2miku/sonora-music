package com.sonora.admin.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sonora.common.result.R;
import com.sonora.mapper.PlaylistMapper;
import com.sonora.mapper.PlaylistSongMapper;
import com.sonora.mapper.SongMapper;
import com.sonora.model.entity.Playlist;
import com.sonora.model.entity.PlaylistSong;
import com.sonora.model.entity.Song;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Tag(name = "管理端-歌单管理", description = "歌单信息与歌曲关联维护")
@RestController
@RequestMapping("/api/admin/playlists")
public class PlaylistController {

    private final PlaylistMapper playlistMapper;
    private final PlaylistSongMapper playlistSongMapper;
    private final SongMapper songMapper;

    public PlaylistController(PlaylistMapper playlistMapper,
                              PlaylistSongMapper playlistSongMapper,
                              SongMapper songMapper) {
        this.playlistMapper = playlistMapper;
        this.playlistSongMapper = playlistSongMapper;
        this.songMapper = songMapper;
    }

    @Operation(summary = "分页查询歌单列表")
    @GetMapping
    public R<Map<String, Object>> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status) {

        LambdaQueryWrapper<Playlist> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.like(Playlist::getName, keyword);
        }
        if (status != null) {
            wrapper.eq(Playlist::getStatus, status);
        }
        wrapper.orderByDesc(Playlist::getCreatedAt);

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

    @Operation(summary = "歌单详情")
    @GetMapping("/{id}")
    public R<Map<String, Object>> getById(@PathVariable Long id) {
        Playlist playlist = playlistMapper.selectById(id);
        if (playlist == null) {
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

        Playlist playlist = new Playlist();
        copyToPlaylist(request, playlist);
        playlist.setUserId(currentUserId());
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
        if (!StringUtils.hasText(request.name())) {
            return R.badRequest("歌单名称不能为空");
        }

        Playlist playlist = new Playlist();
        playlist.setId(id);
        copyToPlaylist(request, playlist);
        playlistMapper.updateById(playlist);
        if (request.songIds() != null) {
            replaceSongs(id, request.songIds());
        }
        return R.ok(toDetail(playlistMapper.selectById(id)));
    }

    @Operation(summary = "删除歌单")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        playlistSongMapper.delete(new LambdaQueryWrapper<PlaylistSong>()
                .eq(PlaylistSong::getPlaylistId, id));
        playlistMapper.deleteById(id);
        return R.ok();
    }

    @Operation(summary = "切换歌单公开/私密状态")
    @PutMapping("/{id}/status")
    public R<Map<String, Object>> toggleStatus(@PathVariable Long id, @RequestParam int status) {
        Playlist playlist = playlistMapper.selectById(id);
        if (playlist == null) {
            return R.notFound("歌单不存在");
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
        item.put("cover", playlist.getCover());
        item.put("userId", playlist.getUserId());
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
        playlist.setName(request.name());
        playlist.setCover(request.cover());
        playlist.setDescription(request.description());
        playlist.setTags(request.tags());
        playlist.setStatus(request.status());
    }

    private Long currentUserId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Long userId) {
            return userId;
        }
        return 1L;
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
}
