package com.sonora.client.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sonora.common.result.R;
import com.sonora.mapper.AlbumMapper;
import com.sonora.mapper.ArtistMapper;
import com.sonora.mapper.PlaylistMapper;
import com.sonora.mapper.SongMapper;
import com.sonora.model.entity.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Tag(name = "客户端-搜索", description = "全局搜索")
@RestController
@RequestMapping("/api/client")
public class ClientSearchController {

    private final SongMapper songMapper;
    private final AlbumMapper albumMapper;
    private final ArtistMapper artistMapper;
    private final PlaylistMapper playlistMapper;

    public ClientSearchController(SongMapper songMapper,
                                  AlbumMapper albumMapper,
                                  ArtistMapper artistMapper,
                                  PlaylistMapper playlistMapper) {
        this.songMapper = songMapper;
        this.albumMapper = albumMapper;
        this.artistMapper = artistMapper;
        this.playlistMapper = playlistMapper;
    }

    @Operation(summary = "全局搜索 (type: song/album/artist/playlist/all)")
    @GetMapping("/search")
    public R<Map<String, Object>> search(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "all") String type,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {

        if (!StringUtils.hasText(keyword)) {
            return R.badRequest("关键词不能为空");
        }

        Map<String, Object> data = new LinkedHashMap<>();

        if ("all".equals(type) || "song".equals(type)) {
            List<Song> songs = songMapper.selectList(
                    new LambdaQueryWrapper<Song>()
                            .like(Song::getName, keyword)
                            .eq(Song::getStatus, 1)
                            .orderByDesc(Song::getPlayCount)
                            .last("LIMIT " + pageSize));
            data.put("songs", songs);
        }

        if ("all".equals(type) || "album".equals(type)) {
            List<Album> albums = albumMapper.selectList(
                    new LambdaQueryWrapper<Album>()
                            .like(Album::getName, keyword)
                            .eq(Album::getStatus, 1)
                            .last("LIMIT " + pageSize));
            data.put("albums", albums);
        }

        if ("all".equals(type) || "artist".equals(type)) {
            List<Artist> artists = artistMapper.selectList(
                    new LambdaQueryWrapper<Artist>()
                            .like(Artist::getName, keyword)
                            .eq(Artist::getStatus, 1)
                            .last("LIMIT " + pageSize));
            data.put("artists", artists);
        }

        if ("all".equals(type) || "playlist".equals(type)) {
            List<Playlist> playlists = playlistMapper.selectList(
                    new LambdaQueryWrapper<Playlist>()
                            .like(Playlist::getName, keyword)
                            .eq(Playlist::getStatus, 1)
                            .last("LIMIT " + pageSize));
            data.put("playlists", playlists);
        }

        return R.ok(data);
    }

    @Operation(summary = "搜索热词 (简单实现: 返回播放量最高的歌曲名)")
    @GetMapping("/search/hot")
    public R<List<String>> hot() {
        List<Song> songs = songMapper.selectList(
                new LambdaQueryWrapper<Song>()
                        .eq(Song::getStatus, 1)
                        .orderByDesc(Song::getPlayCount)
                        .last("LIMIT 10"));
        return R.ok(songs.stream().map(Song::getName).toList());
    }
}
