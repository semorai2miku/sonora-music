package com.sonora.client.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sonora.common.constant.Constants;
import com.sonora.common.result.R;
import com.sonora.file.service.MinioService;
import com.sonora.mapper.AlbumMapper;
import com.sonora.mapper.ArtistMapper;
import com.sonora.mapper.SongMapper;
import com.sonora.model.entity.Album;
import com.sonora.model.entity.Artist;
import com.sonora.model.entity.Song;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Tag(name = "客户端-专辑", description = "专辑列表")
@RestController
@RequestMapping("/api/client")
public class ClientAlbumController {

    private final AlbumMapper albumMapper;
    private final ArtistMapper artistMapper;
    private final SongMapper songMapper;
    private final MinioService minioService;

    public ClientAlbumController(AlbumMapper albumMapper, ArtistMapper artistMapper, SongMapper songMapper,
                                 MinioService minioService) {
        this.albumMapper = albumMapper;
        this.artistMapper = artistMapper;
        this.songMapper = songMapper;
        this.minioService = minioService;
    }

    @Operation(summary = "专辑列表")
    @GetMapping("/albums")
    public R<List<Map<String, Object>>> list(
            @RequestParam(required = false) String region,
            @RequestParam(required = false) Integer limit) {
        LambdaQueryWrapper<Album> wrapper = new LambdaQueryWrapper<Album>()
                .eq(Album::getStatus, 1)
                .orderByAsc(Album::getId);
        if (limit != null && limit > 0) {
            wrapper.last("LIMIT " + limit);
        }

        List<Album> albums = albumMapper.selectList(wrapper);
        Map<Long, Artist> artistMap = artistMapper.selectList(
                        new LambdaQueryWrapper<Artist>().eq(Artist::getStatus, 1).orderByAsc(Artist::getId))
                .stream()
                .collect(java.util.stream.Collectors.toMap(Artist::getId, artist -> artist));

        List<Map<String, Object>> items = albums.stream()
                .filter(album -> {
                    Artist artist = artistMap.get(album.getArtistId());
                    if (!StringUtils.hasText(region)) {
                        return true;
                    }
                    return artist != null && region.trim().equals(artist.getRegion());
                })
                .map(album -> albumView(album, artistMap.get(album.getArtistId())))
                .toList();
        return R.ok(items);
    }

    private Map<String, Object> albumView(Album album, Artist artist) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", album.getId());
        item.put("name", album.getName());
        item.put("picUrl", resolveCover(album.getCover()));
        item.put("artist", artistItem(artist));
        item.put("artistId", artist == null ? 0 : artist.getId());
        item.put("region", artist == null ? "" : artist.getRegion());
        item.put("publishTime", album.getReleaseDate() == null
                ? null
                : album.getReleaseDate().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli());
        item.put("description", album.getDescription());
        item.put("size", songMapper.selectCount(new LambdaQueryWrapper<Song>()
                .eq(Song::getAlbumId, album.getId())
                .eq(Song::getStatus, 1)));
        return item;
    }

    private Map<String, Object> artistItem(Artist artist) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", artist == null ? 0 : artist.getId());
        item.put("name", artist == null || artist.getName() == null ? "" : artist.getName());
        return item;
    }

    private String resolveCover(String cover) {
        return minioService.resolvePreviewUrl(StringUtils.hasText(cover) ? cover : Constants.DEFAULT_COVER);
    }
}
