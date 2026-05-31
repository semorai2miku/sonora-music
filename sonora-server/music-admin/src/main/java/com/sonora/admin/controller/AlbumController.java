package com.sonora.admin.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sonora.common.constant.Constants;
import com.sonora.common.result.R;
import com.sonora.mapper.AlbumMapper;
import com.sonora.model.entity.Album;
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

@Tag(name = "管理端-专辑管理", description = "专辑的增删改查")
@RestController
@RequestMapping("/api/admin/albums")
public class AlbumController {

    private final AlbumMapper albumMapper;

    public AlbumController(AlbumMapper albumMapper) {
        this.albumMapper = albumMapper;
    }

    @Operation(summary = "分页查询专辑列表")
    @GetMapping
    public R<Map<String, Object>> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long artistId) {
        LambdaQueryWrapper<Album> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.like(Album::getName, keyword);
        }
        if (artistId != null) {
            wrapper.eq(Album::getArtistId, artistId);
        }
        wrapper.orderByAsc(Album::getId);
        Page<Album> page = albumMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", page.getRecords());
        data.put("total", page.getTotal());
        data.put("pageNum", pageNum);
        data.put("pageSize", pageSize);
        return R.ok(data);
    }

    @Operation(summary = "专辑详情")
    @GetMapping("/{id}")
    public R<Album> getById(@PathVariable Long id) {
        return R.ok(albumMapper.selectById(id));
    }

    @Operation(summary = "分页查询专辑选项")
    @GetMapping("/options")
    public R<Map<String, Object>> options(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "30") int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) Long artistId,
            @RequestParam(required = false) String ids) {
        LambdaQueryWrapper<Album> wrapper = new LambdaQueryWrapper<>();
        wrapper.select(Album::getId, Album::getName, Album::getCover, Album::getArtistId,
                        Album::getReleaseDate, Album::getStatus)
                .eq(Album::getStatus, 1);
        List<Long> idList = parseIds(ids);
        if (!idList.isEmpty()) {
            wrapper.in(Album::getId, idList);
        }
        String albumName = StringUtils.hasText(name) ? name : keyword;
        if (StringUtils.hasText(albumName)) {
            wrapper.like(Album::getName, albumName.trim());
        }
        if (artistId != null) {
            wrapper.eq(Album::getArtistId, artistId);
        }
        wrapper.orderByAsc(Album::getId);
        Page<Album> page = albumMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", page.getRecords());
        data.put("total", page.getTotal());
        data.put("pageNum", pageNum);
        data.put("pageSize", pageSize);
        return R.ok(data);
    }

    @Operation(summary = "新增专辑")
    @PostMapping
    public R<Album> create(@RequestBody Album album) {
        if (album == null) {
            return R.badRequest("请输入专辑信息");
        }
        if (album.getArtistId() == null) {
            return R.badRequest("歌手是必填项");
        }
        normalizeAlbum(album, null);
        album.setStatus(1);
        albumMapper.insert(album);
        return R.ok(album);
    }

    @Operation(summary = "编辑专辑")
    @PutMapping("/{id}")
    public R<Album> update(@PathVariable Long id, @RequestBody Album album) {
        if (album == null) {
            return R.badRequest("请输入专辑信息");
        }
        Album existing = albumMapper.selectById(id);
        if (existing == null) {
            return R.notFound("专辑不存在");
        }
        if (album.getArtistId() == null) {
            return R.badRequest("歌手是必填项");
        }
        normalizeAlbum(album, existing);
        album.setId(id);
        albumMapper.updateById(album);
        return R.ok(albumMapper.selectById(id));
    }

    @Operation(summary = "删除专辑")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        albumMapper.deleteById(id);
        return R.ok();
    }

    @Operation(summary = "批量删除专辑")
    @PostMapping("/batch-delete")
    @Transactional
    public R<Map<String, Object>> batchDelete(@RequestBody BatchDeleteRequest body) {
        if (body == null || body.ids() == null || body.ids().isEmpty()) {
            return R.badRequest("请选择要删除的专辑");
        }
        List<Map<String, Object>> deletedAlbums = new ArrayList<>();
        for (Long id : body.ids().stream().filter(Objects::nonNull).distinct().toList()) {
            Album album = albumMapper.selectById(id);
            if (album == null) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", id);
            item.put("name", album.getName());
            albumMapper.deleteById(id);
            deletedAlbums.add(item);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("count", deletedAlbums.size());
        data.put("deleted", deletedAlbums);
        return R.ok(data);
    }

    public record BatchDeleteRequest(List<Long> ids) {}

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

    private void normalizeAlbum(Album album, Album existing) {
        if (album.getName() != null) {
            album.setName(album.getName().trim());
        }
        if (album.getCover() == null) {
            String existingCover = existing == null ? null : existing.getCover();
            album.setCover(StringUtils.hasText(existingCover) ? existingCover : Constants.DEFAULT_COVER);
        } else if (!StringUtils.hasText(album.getCover())) {
            album.setCover(Constants.DEFAULT_COVER);
        } else {
            album.setCover(album.getCover().trim());
        }
        if (album.getDescription() != null) {
            album.setDescription(StringUtils.hasText(album.getDescription()) ? album.getDescription().trim() : null);
        }
    }
}
