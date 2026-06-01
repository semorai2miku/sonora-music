package com.sonora.admin.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sonora.common.result.R;
import com.sonora.file.service.MinioService;
import com.sonora.mapper.BannerMapper;
import com.sonora.model.entity.Banner;
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

@Tag(name = "管理端-轮播图管理", description = "首页轮播图的增删改查")
@RestController
@RequestMapping("/api/admin/banners")
public class BannerController {

    private final BannerMapper bannerMapper;
    private final MinioService minioService;

    public BannerController(BannerMapper bannerMapper, MinioService minioService) {
        this.bannerMapper = bannerMapper;
        this.minioService = minioService;
    }

    @Operation(summary = "分页查询轮播图列表")
    @GetMapping
    public R<Map<String, Object>> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status) {
        LambdaQueryWrapper<Banner> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.like(Banner::getTitle, keyword);
        }
        if (status != null) {
            wrapper.eq(Banner::getStatus, status);
        }
        wrapper.orderByAsc(Banner::getId);

        Page<Banner> page = bannerMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        page.getRecords().forEach(this::resolveBannerPreview);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", page.getRecords());
        data.put("total", page.getTotal());
        data.put("pageNum", pageNum);
        data.put("pageSize", pageSize);
        return R.ok(data);
    }

    @Operation(summary = "轮播图详情")
    @GetMapping("/{id}")
    public R<Banner> getById(@PathVariable Long id) {
        Banner banner = bannerMapper.selectById(id);
        if (banner == null) {
            return R.notFound("轮播图不存在");
        }
        return R.ok(resolveBannerPreview(banner));
    }

    @Operation(summary = "新增轮播图")
    @PostMapping
    public R<Banner> create(@RequestBody Banner banner) {
        R<Banner> invalid = validateBanner(banner);
        if (invalid != null) {
            return invalid;
        }
        if (banner.getSort() == null) {
            banner.setSort(0);
        }
        if (banner.getStatus() == null) {
            banner.setStatus(1);
        }
        banner.setImageUrl(minioService.normalizeForStorage(banner.getImageUrl()));
        bannerMapper.insert(banner);
        return R.ok(resolveBannerPreview(banner));
    }

    @Operation(summary = "编辑轮播图")
    @PutMapping("/{id}")
    public R<Banner> update(@PathVariable Long id, @RequestBody Banner banner) {
        if (bannerMapper.selectById(id) == null) {
            return R.notFound("轮播图不存在");
        }
        R<Banner> invalid = validateBanner(banner);
        if (invalid != null) {
            return invalid;
        }
        banner.setImageUrl(minioService.normalizeForStorage(banner.getImageUrl()));
        banner.setId(id);
        bannerMapper.updateById(banner);
        return R.ok(resolveBannerPreview(bannerMapper.selectById(id)));
    }

    @Operation(summary = "删除轮播图")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        bannerMapper.deleteById(id);
        return R.ok();
    }

    @Operation(summary = "批量删除轮播图")
    @PostMapping("/batch-delete")
    @Transactional
    public R<Map<String, Object>> batchDelete(@RequestBody BatchDeleteRequest body) {
        if (body == null || body.ids() == null || body.ids().isEmpty()) {
            return R.badRequest("请选择要删除的轮播图");
        }
        List<Map<String, Object>> deletedBanners = new ArrayList<>();
        for (Long id : body.ids().stream().filter(Objects::nonNull).distinct().toList()) {
            Banner banner = bannerMapper.selectById(id);
            if (banner == null) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", id);
            item.put("name", "轮播图 " + id);
            bannerMapper.deleteById(id);
            deletedBanners.add(item);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("count", deletedBanners.size());
        data.put("deleted", deletedBanners);
        return R.ok(data);
    }

    @Operation(summary = "切换轮播图启用状态")
    @PutMapping("/{id}/status")
    public R<Banner> toggleStatus(@PathVariable Long id, @RequestParam int status) {
        Banner banner = bannerMapper.selectById(id);
        if (banner == null) {
            return R.notFound("轮播图不存在");
        }
        banner.setStatus(status);
        bannerMapper.updateById(banner);
        return R.ok(resolveBannerPreview(banner));
    }

    private R<Banner> validateBanner(Banner banner) {
        if (banner == null || !StringUtils.hasText(banner.getImageUrl())) {
            return R.badRequest("请填写轮播图图片 URL");
        }
        return null;
    }

    public record BatchDeleteRequest(List<Long> ids) {}

    private Banner resolveBannerPreview(Banner banner) {
        if (banner == null) {
            return null;
        }
        banner.setImageUrl(minioService.resolvePreviewUrl(banner.getImageUrl()));
        return banner;
    }
}
