package com.sonora.client.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sonora.common.result.R;
import com.sonora.mapper.PlaylistMapper;
import com.sonora.mapper.PlaylistSongMapper;
import com.sonora.mapper.SongMapper;
import com.sonora.model.entity.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Tag(name = "客户端-歌单", description = "歌单列表、详情、排行榜")
@RestController
@RequestMapping("/api/client")
@RequiredArgsConstructor
public class ClientPlaylistController {

    private final PlaylistMapper playlistMapper;
    private final PlaylistSongMapper playlistSongMapper;
    private final SongMapper songMapper;

    @Operation(summary = "推荐歌单 (按收藏数)")
    @GetMapping("/playlists/recommend")
    public R<List<Playlist>> recommend(@RequestParam(defaultValue = "20") int limit) {
        List<Playlist> list = playlistMapper.selectList(
                new LambdaQueryWrapper<Playlist>()
                        .eq(Playlist::getStatus, 1)
                        .orderByDesc(Playlist::getCollectCount)
                        .last("LIMIT " + limit));
        return R.ok(list);
    }

    @Operation(summary = "歌单详情")
    @GetMapping("/playlists/{id}")
    public R<Map<String, Object>> detail(@PathVariable Long id) {
        Playlist playlist = playlistMapper.selectById(id);
        if (playlist == null || playlist.getStatus() == 0) {
            return R.notFound("歌单不存在");
        }

        // 查歌单中的歌曲
        List<PlaylistSong> psList = playlistSongMapper.selectList(
                new LambdaQueryWrapper<PlaylistSong>()
                        .eq(PlaylistSong::getPlaylistId, id)
                        .orderByAsc(PlaylistSong::getSort));

        List<Song> songs = List.of();
        if (!psList.isEmpty()) {
            List<Long> songIds = psList.stream().map(PlaylistSong::getSongId).toList();
            songs = songMapper.selectList(
                    new LambdaQueryWrapper<Song>()
                            .in(Song::getId, songIds)
                            .eq(Song::getStatus, 1));
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", playlist.getId());
        data.put("name", playlist.getName());
        data.put("cover", playlist.getCover());
        data.put("description", playlist.getDescription());
        data.put("tags", playlist.getTags());
        data.put("playCount", playlist.getPlayCount());
        data.put("collectCount", playlist.getCollectCount());
        data.put("userId", playlist.getUserId());
        data.put("songs", songs);
        data.put("songCount", songs.size());
        return R.ok(data);
    }

    @Operation(summary = "排行榜 (按播放量)")
    @GetMapping("/playlists/top")
    public R<List<Playlist>> top(@RequestParam(defaultValue = "10") int limit) {
        List<Playlist> list = playlistMapper.selectList(
                new LambdaQueryWrapper<Playlist>()
                        .eq(Playlist::getStatus, 1)
                        .orderByDesc(Playlist::getPlayCount)
                        .last("LIMIT " + limit));
        return R.ok(list);
    }
}
