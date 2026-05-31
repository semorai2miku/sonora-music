package com.sonora.client.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sonora.common.constant.Constants;
import com.sonora.common.result.R;
import com.sonora.mapper.AlbumMapper;
import com.sonora.mapper.ArtistMapper;
import com.sonora.mapper.SongMapper;
import com.sonora.model.entity.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.util.StringUtils;

@Tag(name = "客户端-歌手", description = "歌手详情、歌曲")
@RestController
@RequestMapping("/api/client")
public class ClientArtistController {

    private final ArtistMapper artistMapper;
    private final SongMapper songMapper;
    private final AlbumMapper albumMapper;

    public ClientArtistController(ArtistMapper artistMapper,
                                  SongMapper songMapper,
                                  AlbumMapper albumMapper) {
        this.artistMapper = artistMapper;
        this.songMapper = songMapper;
        this.albumMapper = albumMapper;
    }

    @Operation(summary = "歌手详情 + 热门歌曲")
    @GetMapping("/artists/{id}")
    public R<Map<String, Object>> detail(@PathVariable Long id) {
        Artist artist = artistMapper.selectById(id);
        if (artist == null || artist.getStatus() == 0) {
            return R.notFound("歌手不存在或已下架");
        }

        // 歌手的歌曲
        List<Song> songs = songMapper.selectList(
                new LambdaQueryWrapper<Song>()
                        .apply("FIND_IN_SET({0}, artist_ids)", id)
                        .eq(Song::getStatus, 1)
                        .orderByDesc(Song::getPlayCount)
                        .last("LIMIT 50"));
        songs.forEach(song -> song.setCover(coverOf(song.getCover())));

        // 歌手的专辑
        List<Album> albums = albumMapper.selectList(
                new LambdaQueryWrapper<Album>()
                        .eq(Album::getArtistId, id)
                        .eq(Album::getStatus, 1)
                        .orderByDesc(Album::getCreatedAt));
        albums.forEach(album -> album.setCover(coverOf(album.getCover())));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", artist.getId());
        data.put("name", artist.getName());
        data.put("avatar", artist.getAvatar());
        data.put("description", artist.getDescription());
        data.put("region", artist.getRegion());
        data.put("songs", songs);
        data.put("albums", albums);
        return R.ok(data);
    }

    @Operation(summary = "歌手列表 (按地区/首字母)")
    @GetMapping("/artists")
    public R<List<Artist>> list(@RequestParam(required = false) String region) {
        LambdaQueryWrapper<Artist> wrapper = new LambdaQueryWrapper<Artist>()
                .eq(Artist::getStatus, 1)
                .orderByAsc(Artist::getName);
        if (region != null && !region.isEmpty()) {
            wrapper.eq(Artist::getRegion, region);
        }
        return R.ok(artistMapper.selectList(wrapper));
    }

    private String coverOf(String cover) {
        return StringUtils.hasText(cover) ? cover : Constants.DEFAULT_COVER;
    }
}
