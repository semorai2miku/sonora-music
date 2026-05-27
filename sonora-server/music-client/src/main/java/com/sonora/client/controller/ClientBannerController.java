package com.sonora.client.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sonora.common.result.R;
import com.sonora.mapper.BannerMapper;
import com.sonora.model.entity.Banner;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "客户端-轮播图", description = "首页轮播图")
@RestController
@RequestMapping("/api/client")
public class ClientBannerController {

    private final BannerMapper bannerMapper;

    public ClientBannerController(BannerMapper bannerMapper) {
        this.bannerMapper = bannerMapper;
    }

    @Operation(summary = "轮播图列表")
    @GetMapping("/banners")
    public R<List<Banner>> list() {
        List<Banner> banners = bannerMapper.selectList(
                new LambdaQueryWrapper<Banner>()
                        .eq(Banner::getStatus, 1)
                        .orderByAsc(Banner::getSort));
        return R.ok(banners);
    }
}
