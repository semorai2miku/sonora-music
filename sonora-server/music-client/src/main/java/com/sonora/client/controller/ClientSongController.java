package com.sonora.client.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sonora.common.result.R;
import com.sonora.mapper.SongMapper;
import com.sonora.model.entity.Song;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Tag(name = "客户端-歌曲", description = "歌曲详情、歌词")
@RestController
@RequestMapping("/api/client")
@RequiredArgsConstructor
public class ClientSongController {

    private final SongMapper songMapper;

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
        data.put("cover", song.getCover());
        data.put("format", song.getFormat());
        data.put("fileSize", song.getFileSize());
        data.put("playCount", song.getPlayCount());
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
        return R.ok(songs);
    }
}
