package com.sonora.admin.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sonora.common.constant.Constants;
import com.sonora.common.result.R;
import com.sonora.file.service.MinioService;
import com.sonora.mapper.AlbumMapper;
import com.sonora.model.entity.Album;
import com.sonora.model.entity.Song;
import com.sonora.service.SongService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 管理端 — 歌曲管理 CRUD
 */
@Tag(name = "管理端-歌曲管理", description = "歌曲的增删改查")
@RestController
@RequestMapping("/api/admin/songs")
public class SongController {

    private final SongService songService;
    private final MinioService minioService;
    private final AlbumMapper albumMapper;

    public SongController(SongService songService, MinioService minioService, AlbumMapper albumMapper) {
        this.songService = songService;
        this.minioService = minioService;
        this.albumMapper = albumMapper;
    }

    @Operation(summary = "分页查询歌曲列表")
    @GetMapping
    public R<Map<String, Object>> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long albumId,
            @RequestParam(required = false) Long artistId) {

        Page<Song> page = songService.pageSongs(pageNum, pageSize, keyword, albumId, artistId);
        page.getRecords().forEach(this::resolveSongPreview);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", page.getRecords());
        data.put("total", page.getTotal());
        data.put("pageNum", pageNum);
        data.put("pageSize", pageSize);
        return R.ok(data);
    }

    @Operation(summary = "查询歌曲详情")
    @GetMapping("/{id}")
    public R<Song> getById(@PathVariable Long id) {
        Song song = songService.getById(id);
        if (song == null) {
            return R.notFound("歌曲不存在");
        }
        return R.ok(resolveSongPreview(song));
    }

    @Operation(summary = "创建歌曲 (上传音频 + 封面)")
    @PostMapping
    public R<Song> create(
            @RequestParam("audioFile") MultipartFile audioFile,
            @RequestParam(value = "coverFile", required = false) MultipartFile coverFile,
            @RequestParam String name,
            @RequestParam(required = false) String artistIds,
            @RequestParam(required = false) Long albumId,
            @RequestParam(required = false) Integer duration,
            @RequestParam(required = false) String cover,
            @RequestParam(required = false) String lyrics) {

        Song song = new Song();
        song.setName(name);
        song.setArtistIds(artistIds);
        song.setAlbumId(albumId);
        song.setDuration(duration);
        song.setLyrics(lyrics);
        song.setStatus(1);
        song.setPlayCount(0L);

        Song created = songService.createSong(audioFile, coverFile, song);
        return R.ok(resolveSongPreview(created));
    }

    @Operation(summary = "编辑歌曲元数据")
    @PutMapping("/{id}")
    public R<Song> update(@PathVariable Long id, @RequestBody Song song) {
        Song updated = songService.updateSong(id, song);
        return R.ok(resolveSongPreview(updated));
    }

    @Operation(summary = "替换歌曲文件并更新信息")
    @PostMapping("/{id}/replace")
    public R<Song> replace(
            @PathVariable Long id,
            @RequestParam("audioFile") MultipartFile audioFile,
            @RequestParam(value = "coverFile", required = false) MultipartFile coverFile,
            @RequestParam String name,
            @RequestParam(required = false) String artistIds,
            @RequestParam(required = false) Long albumId,
            @RequestParam(required = false) Integer duration,
            @RequestParam(required = false) String cover,
            @RequestParam(required = false) String lyrics) {

        Song song = new Song();
        song.setName(name);
        song.setArtistIds(artistIds);
        song.setAlbumId(albumId);
        song.setDuration(duration);
        song.setLyrics(lyrics);

        Song updated = songService.replaceSong(id, audioFile, coverFile, song);
        return R.ok(resolveSongPreview(updated));
    }

    @Operation(summary = "删除歌曲")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        songService.deleteSong(id);
        return R.ok();
    }

    @Operation(summary = "批量删除歌曲")
    @PostMapping("/batch-delete")
    @Transactional
    public R<Map<String, Object>> batchDelete(@RequestBody BatchDeleteRequest body) {
        if (body == null || body.ids() == null || body.ids().isEmpty()) {
            return R.badRequest("请选择要删除的歌曲");
        }
        List<Map<String, Object>> deletedSongs = new ArrayList<>();
        for (Long id : body.ids().stream().filter(Objects::nonNull).distinct().toList()) {
            Song song = songService.getById(id);
            if (song == null) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", id);
            item.put("name", song.getName());
            songService.deleteSong(id);
            deletedSongs.add(item);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("count", deletedSongs.size());
        data.put("deleted", deletedSongs);
        return R.ok(data);
    }

    @Operation(summary = "切换歌曲上架/下架状态")
    @PutMapping("/{id}/status")
    public R<Song> toggleStatus(@PathVariable Long id, @RequestParam int status) {
        Song song = songService.getById(id);
        if (song == null) {
            return R.notFound("歌曲不存在");
        }
        song.setStatus(status);
        songService.updateById(song);
        return R.ok(resolveSongPreview(song));
    }

    public record BatchDeleteRequest(List<Long> ids) {}

    private Song resolveSongPreview(Song song) {
        if (song == null) {
            return null;
        }
        song.setCover(minioService.resolvePreviewUrl(resolveSongCover(song.getAlbumId())));
        return song;
    }

    private String resolveSongCover(Long albumId) {
        if (albumId == null) {
            return Constants.DEFAULT_COVER;
        }
        Album album = albumMapper.selectById(albumId);
        if (album != null && org.springframework.util.StringUtils.hasText(album.getCover())) {
            return album.getCover();
        }
        return Constants.DEFAULT_COVER;
    }
}
