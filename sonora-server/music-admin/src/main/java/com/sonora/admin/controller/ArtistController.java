package com.sonora.admin.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

@Tag(name = "管理端-歌手管理", description = "歌手的增删改查")
@RestController
@RequestMapping("/api/admin/artists")
public class ArtistController {

    private final ArtistMapper artistMapper;
    private final SongMapper songMapper;
    private final AlbumMapper albumMapper;
    private final MinioService minioService;

    public ArtistController(ArtistMapper artistMapper, SongMapper songMapper, AlbumMapper albumMapper,
                            MinioService minioService) {
        this.artistMapper = artistMapper;
        this.songMapper = songMapper;
        this.albumMapper = albumMapper;
        this.minioService = minioService;
    }

    @Operation(summary = "分页查询歌手列表")
    @GetMapping
    public R<Map<String, Object>> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String region) {
        LambdaQueryWrapper<Artist> wrapper = new LambdaQueryWrapper<>();
        String singerName = StringUtils.hasText(name) ? name : keyword;
        if (StringUtils.hasText(singerName)) {
            wrapper.like(Artist::getName, singerName.trim());
        }
        if (StringUtils.hasText(region)) {
            wrapper.like(Artist::getRegion, region.trim());
        }
        wrapper.orderByAsc(Artist::getId);
        Page<Artist> page = artistMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        List<Song> songs = songMapper.selectList(
                new LambdaQueryWrapper<Song>()
                        .select(Song::getId, Song::getName, Song::getArtistIds)
                        .orderByAsc(Song::getId));
        List<Album> albums = albumMapper.selectList(
                new LambdaQueryWrapper<Album>()
                        .select(Album::getId, Album::getName, Album::getArtistId)
                        .orderByAsc(Album::getId));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", page.getRecords().stream()
                .map(artist -> artistOf(artist, songs, albums))
                .toList());
        data.put("total", page.getTotal());
        data.put("pageNum", pageNum);
        data.put("pageSize", pageSize);
        return R.ok(data);
    }

    @Operation(summary = "查询所有歌手 (下拉列表用)")
    @GetMapping("/all")
    public R<java.util.List<Artist>> all() {
        List<Artist> artists = artistMapper.selectList(
                new LambdaQueryWrapper<Artist>().eq(Artist::getStatus, 1).orderByAsc(Artist::getId));
        artists.forEach(this::resolveArtistPreview);
        return R.ok(artists);
    }

    @Operation(summary = "分页查询歌手选项")
    @GetMapping("/options")
    public R<Map<String, Object>> options(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "30") int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String ids) {
        LambdaQueryWrapper<Artist> wrapper = new LambdaQueryWrapper<>();
        wrapper.select(Artist::getId, Artist::getName, Artist::getAvatar, Artist::getStatus)
                .eq(Artist::getStatus, 1);
        List<Long> idList = parseIds(ids);
        if (!idList.isEmpty()) {
            wrapper.in(Artist::getId, idList);
        }
        String singerName = StringUtils.hasText(name) ? name : keyword;
        if (StringUtils.hasText(singerName)) {
            wrapper.like(Artist::getName, singerName.trim());
        }
        wrapper.orderByAsc(Artist::getId);
        Page<Artist> page = artistMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        page.getRecords().forEach(this::resolveArtistPreview);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", page.getRecords());
        data.put("total", page.getTotal());
        data.put("pageNum", pageNum);
        data.put("pageSize", pageSize);
        return R.ok(data);
    }

    @Operation(summary = "歌手详情")
    @GetMapping("/{id}")
    public R<Map<String, Object>> getById(@PathVariable Long id) {
        Artist artist = artistMapper.selectById(id);
        if (artist == null) {
            return R.notFound("歌手不存在");
        }
        return R.ok(artistOf(artist, allSongs(), allAlbums()));
    }

    @Operation(summary = "新增歌手")
    @PostMapping
    public R<Map<String, Object>> create(@RequestBody Artist artist) {
        if (artist == null) {
            return R.badRequest("请输入歌手信息");
        }
        normalizeArtist(artist);
        if (!StringUtils.hasText(artist.getName())) {
            return R.badRequest("请输入歌手名称");
        }
        artistMapper.insert(artist);
        return R.ok(artistOf(artistMapper.selectById(artist.getId()), allSongs(), allAlbums()));
    }

    @Operation(summary = "编辑歌手")
    @PutMapping("/{id}")
    public R<Map<String, Object>> update(@PathVariable Long id, @RequestBody Artist artist) {
        if (artist == null) {
            return R.badRequest("请输入歌手信息");
        }
        if (artistMapper.selectById(id) == null) {
            return R.notFound("歌手不存在");
        }
        normalizeArtist(artist);
        if (!StringUtils.hasText(artist.getName())) {
            return R.badRequest("请输入歌手名称");
        }
        artist.setId(id);
        artistMapper.updateById(artist);
        return R.ok(artistOf(artistMapper.selectById(id), allSongs(), allAlbums()));
    }

    @Operation(summary = "删除歌手")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        Artist artist = artistMapper.selectById(id);
        if (artist == null) {
            return R.notFound("歌手不存在");
        }
        ArtistRelationStats stats = relationStats(id);
        if (stats.hasRelation()) {
            return R.badRequest(relationBlockMessage(artist.getName(), stats));
        }
        artistMapper.hardDeleteById(id);
        return R.ok();
    }

    @Operation(summary = "批量删除歌手")
    @PostMapping("/batch-delete")
    @Transactional
    public R<Map<String, Object>> batchDelete(@RequestBody BatchDeleteRequest body) {
        if (body == null || body.ids() == null || body.ids().isEmpty()) {
            return R.badRequest("请选择要删除的歌手");
        }
        List<String> blockedArtists = new ArrayList<>();
        List<Artist> artistsToDelete = new ArrayList<>();
        for (Long id : body.ids().stream().filter(Objects::nonNull).distinct().toList()) {
            Artist artist = artistMapper.selectById(id);
            if (artist == null) {
                continue;
            }
            ArtistRelationStats stats = relationStats(id);
            if (stats.hasRelation()) {
                blockedArtists.add(relationBlockMessage(artist.getName(), stats));
                continue;
            }
            artistsToDelete.add(artist);
        }
        if (!blockedArtists.isEmpty()) {
            return R.badRequest("存在关联内容，批量删除已取消：" + String.join("；", blockedArtists));
        }
        List<Map<String, Object>> deletedArtists = new ArrayList<>();
        for (Artist artist : artistsToDelete) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", artist.getId());
            item.put("name", artist.getName());
            artistMapper.hardDeleteById(artist.getId());
            deletedArtists.add(item);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("count", deletedArtists.size());
        data.put("deleted", deletedArtists);
        return R.ok(data);
    }

    public record BatchDeleteRequest(List<Long> ids) {}

    private record ArtistRelationStats(long songCount, long albumCount) {
        private boolean hasRelation() {
            return songCount > 0 || albumCount > 0;
        }
    }

    private Map<String, Object> artistOf(Artist artist, List<Song> songs, List<Album> albums) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", artist.getId());
        data.put("name", artist.getName());
        data.put("avatar", minioService.resolvePreviewUrl(
                StringUtils.hasText(artist.getAvatar()) ? artist.getAvatar() : Constants.DEFAULT_AVATAR));
        data.put("region", artist.getRegion());
        data.put("description", artist.getDescription());
        data.put("status", artist.getStatus());
        data.put("createdAt", artist.getCreatedAt());
        data.put("updatedAt", artist.getUpdatedAt());

        List<Map<String, Object>> relatedSongs = songs.stream()
                .filter(song -> songBelongsToArtist(song.getArtistIds(), artist.getId()))
                .map(song -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", song.getId());
                    item.put("name", song.getName());
                    return item;
                })
                .toList();
        List<Map<String, Object>> relatedAlbums = albums.stream()
                .filter(album -> Objects.equals(album.getArtistId(), artist.getId()))
                .map(album -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", album.getId());
                    item.put("name", album.getName());
                    return item;
                })
                .toList();
        data.put("songs", relatedSongs);
        data.put("songCount", relatedSongs.size());
        data.put("albums", relatedAlbums);
        data.put("albumCount", relatedAlbums.size());
        return data;
    }

    private boolean songBelongsToArtist(String artistIds, Long artistId) {
        if (!StringUtils.hasText(artistIds) || artistId == null) {
            return false;
        }
        String expected = String.valueOf(artistId);
        for (String id : artistIds.split(",")) {
            if (expected.equals(id.trim())) {
                return true;
            }
        }
        return false;
    }

    private List<Song> allSongs() {
        return songMapper.selectList(
                new LambdaQueryWrapper<Song>()
                        .select(Song::getId, Song::getName, Song::getArtistIds)
                        .orderByAsc(Song::getId));
    }

    private List<Album> allAlbums() {
        return albumMapper.selectList(
                new LambdaQueryWrapper<Album>()
                        .select(Album::getId, Album::getName, Album::getArtistId)
                        .orderByAsc(Album::getId));
    }

    private List<Long> parseIds(String ids) {
        if (!StringUtils.hasText(ids)) {
            return List.of();
        }
        List<Long> result = new ArrayList<>();
        for (String part : ids.split(",")) {
            try {
                result.add(Long.parseLong(part.trim()));
            } catch (NumberFormatException ignored) {
                // 忽略非法 ID，避免一个脏值影响整次选项查询。
            }
        }
        return result.stream().filter(Objects::nonNull).distinct().toList();
    }

    private ArtistRelationStats relationStats(Long artistId) {
        long songCount = songMapper.selectCount(
                new LambdaQueryWrapper<Song>().apply("FIND_IN_SET({0}, artist_ids)", artistId));
        long albumCount = albumMapper.selectCount(
                new LambdaQueryWrapper<Album>().eq(Album::getArtistId, artistId));
        return new ArtistRelationStats(songCount, albumCount);
    }

    private String relationBlockMessage(String artistName, ArtistRelationStats stats) {
        return "歌手「" + artistName + "」仍关联 " + stats.songCount()
                + " 首歌曲、" + stats.albumCount() + " 张专辑，请先转移或解除关联后再删除";
    }

    private void normalizeArtist(Artist artist) {
        artist.setName(artist.getName() == null ? "" : artist.getName().trim());
        artist.setAvatar(StringUtils.hasText(artist.getAvatar())
                ? minioService.normalizeForStorage(artist.getAvatar().trim())
                : Constants.DEFAULT_AVATAR);
        artist.setRegion(StringUtils.hasText(artist.getRegion()) ? artist.getRegion().trim() : null);
        artist.setDescription(StringUtils.hasText(artist.getDescription()) ? artist.getDescription().trim() : null);
        artist.setStatus(artist.getStatus() == null ? 1 : artist.getStatus());
    }

    private Artist resolveArtistPreview(Artist artist) {
        if (artist == null) {
            return null;
        }
        artist.setAvatar(minioService.resolvePreviewUrl(artist.getAvatar()));
        return artist;
    }
}
