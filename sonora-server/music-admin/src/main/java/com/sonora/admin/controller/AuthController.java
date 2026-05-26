package com.sonora.admin.controller;

import com.sonora.common.result.R;
import com.sonora.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 管理端 — 认证接口
 * <p>
 * 响应格式完全兼容 pure-admin-thin 前端的期望格式。
 * 前端期望: { success: true, data: {...} }
 */
@Tag(name = "管理端-认证", description = "登录、Token 刷新、动态路由")
@RestController
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "管理端登录")
    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody LoginRequest body) {
        return authService.login(body.username(), body.password());
    }

    public record LoginRequest(String username, String password) {}

    @Operation(summary = "刷新 Token")
    @PostMapping("/refresh-token")
    public Map<String, Object> refreshToken(@RequestBody RefreshRequest body) {
        return authService.refreshToken(body.refreshToken());
    }

    public record RefreshRequest(String refreshToken) {}

    @Operation(summary = "获取动态路由")
    @GetMapping("/get-async-routes")
    public Map<String, Object> getAsyncRoutes() {
        // 从 SecurityContext 获取当前登录用户 ID (由 JwtAuthFilter 设置)
        var auth = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication();
        Long userId = (Long) auth.getPrincipal();
        return authService.getAsyncRoutes(userId);
    }
}
