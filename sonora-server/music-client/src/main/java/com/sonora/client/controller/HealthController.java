package com.sonora.client.controller;

import com.sonora.common.result.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 客户端 - 健康检查 / 示例接口
 */
@Tag(name = "客户端-公共", description = "客户端基础接口")
@RestController
@RequestMapping("/api/client")
public class HealthController {

    @Operation(summary = "服务健康检查")
    @GetMapping("/ping")
    public R<String> ping() {
        return R.ok("pong");
    }
}
