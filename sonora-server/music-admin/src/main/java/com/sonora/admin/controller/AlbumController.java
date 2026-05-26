package com.sonora.admin.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sonora.common.result.R;
import com.sonora.mapper.AlbumMapper;
import com.sonora.model.entity.Album;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@Tag(name = "管理端-专辑管理", description = "专辑的增删改查")
@RestController
@RequestMapping("/api/admin/albums")
@RequiredArgsConstructor
public class AlbumController {

    private final AlbumMapper albumMapper;

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
        wrapper.orderByDesc(Album::getCreatedAt);
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

    @Operation(summary = "新增专辑")
    @PostMapping
    public R<Album> create(@RequestBody Album album) {
        album.setStatus(1);
        albumMapper.insert(album);
        return R.ok(album);
    }

    @Operation(summary = "编辑专辑")
    @PutMapping("/{id}")
    public R<Album> update(@PathVariable Long id, @RequestBody Album album) {
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
}
