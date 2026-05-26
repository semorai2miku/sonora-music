package com.sonora.admin.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sonora.common.result.R;
import com.sonora.mapper.ArtistMapper;
import com.sonora.model.entity.Artist;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@Tag(name = "管理端-歌手管理", description = "歌手的增删改查")
@RestController
@RequestMapping("/api/admin/artists")
@RequiredArgsConstructor
public class ArtistController {

    private final ArtistMapper artistMapper;

    @Operation(summary = "分页查询歌手列表")
    @GetMapping
    public R<Map<String, Object>> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String keyword) {
        LambdaQueryWrapper<Artist> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.like(Artist::getName, keyword);
        }
        wrapper.orderByAsc(Artist::getName);
        Page<Artist> page = artistMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", page.getRecords());
        data.put("total", page.getTotal());
        data.put("pageNum", pageNum);
        data.put("pageSize", pageSize);
        return R.ok(data);
    }

    @Operation(summary = "查询所有歌手 (下拉列表用)")
    @GetMapping("/all")
    public R<java.util.List<Artist>> all() {
        return R.ok(artistMapper.selectList(
                new LambdaQueryWrapper<Artist>().eq(Artist::getStatus, 1).orderByAsc(Artist::getName)));
    }

    @Operation(summary = "歌手详情")
    @GetMapping("/{id}")
    public R<Artist> getById(@PathVariable Long id) {
        return R.ok(artistMapper.selectById(id));
    }

    @Operation(summary = "新增歌手")
    @PostMapping
    public R<Artist> create(@RequestBody Artist artist) {
        artist.setStatus(1);
        artistMapper.insert(artist);
        return R.ok(artist);
    }

    @Operation(summary = "编辑歌手")
    @PutMapping("/{id}")
    public R<Artist> update(@PathVariable Long id, @RequestBody Artist artist) {
        artist.setId(id);
        artistMapper.updateById(artist);
        return R.ok(artistMapper.selectById(id));
    }

    @Operation(summary = "删除歌手")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        artistMapper.deleteById(id);
        return R.ok();
    }
}
