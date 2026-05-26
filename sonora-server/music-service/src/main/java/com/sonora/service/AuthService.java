package com.sonora.service;

import java.util.Map;

public interface AuthService {

    /** 用户名密码登录，返回 admin 前端期望的格式 */
    Map<String, Object> login(String username, String password);

    /** 刷新 Token */
    Map<String, Object> refreshToken(String refreshToken);

    /** 获取当前用户的动态路由 */
    Map<String, Object> getAsyncRoutes(Long userId);
}
