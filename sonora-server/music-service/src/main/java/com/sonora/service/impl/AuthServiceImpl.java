package com.sonora.service.impl;

import cn.hutool.core.date.DateUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sonora.common.constant.Constants;
import com.sonora.common.enums.ResultCode;
import com.sonora.common.exception.BusinessException;
import com.sonora.common.util.JwtUtil;
import com.sonora.mapper.PermissionMapper;
import com.sonora.mapper.RoleMapper;
import com.sonora.mapper.UserMapper;
import com.sonora.model.entity.Permission;
import com.sonora.model.entity.User;
import com.sonora.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final PermissionMapper permissionMapper;
    private final PasswordEncoder passwordEncoder;
    private final StringRedisTemplate redisTemplate;

    @Override
    public Map<String, Object> login(String username, String password) {
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, username));
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        if (user.getStatus() == 0) {
            throw new BusinessException(ResultCode.USER_DISABLED);
        }

        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new BusinessException(ResultCode.PASSWORD_ERROR);
        }

        List<String> roles = roleMapper.selectRoleCodesByUserId(user.getId());
        List<String> permissions = permissionMapper.selectPermsByUserId(user.getId());

        String accessToken = JwtUtil.createAccessToken(user.getId(), user.getUsername(), roles);
        String refreshToken = JwtUtil.createRefreshToken(user.getId());

        String redisKey = Constants.TOKEN_BLACKLIST_PREFIX + "refresh:" + user.getId();
        try {
            redisTemplate.opsForValue().set(redisKey, refreshToken,
                    Constants.REFRESH_TOKEN_EXPIRE_DAYS, TimeUnit.DAYS);
        } catch (Exception e) {
            log.warn("Redis 写入 refreshToken 失败 (不影响登录): {}", e.getMessage());
        }

        user.setLastLoginAt(new Date().toInstant()
                .atZone(java.time.ZoneId.systemDefault()).toLocalDateTime());
        userMapper.updateById(user);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("avatar", user.getAvatar() != null ? user.getAvatar() : Constants.DEFAULT_AVATAR);
        data.put("username", user.getUsername());
        data.put("nickname", user.getNickname() != null ? user.getNickname() : user.getUsername());
        data.put("roles", roles);
        data.put("permissions", permissions);
        data.put("accessToken", accessToken);
        data.put("refreshToken", refreshToken);
        data.put("expires", DateUtil.format(
                new Date(System.currentTimeMillis() + JwtUtil.ACCESS_EXPIRE_MS),
                "yyyy/MM/dd HH:mm:ss"));

        return Map.<String, Object>of("success", true, "data", data);
    }

    @Override
    public Map<String, Object> refreshToken(String refreshToken) {
        try {
            var decoded = JwtUtil.verify(refreshToken);
            Long userId = Long.valueOf(decoded.getSubject());

            String redisKey = Constants.TOKEN_BLACKLIST_PREFIX + "refresh:" + userId;
            String storedToken = null;
            try {
                storedToken = redisTemplate.opsForValue().get(redisKey);
            } catch (Exception e) {
                log.warn("Redis 读取 refreshToken 失败 (跳过校验): {}", e.getMessage());
            }
            if (storedToken != null && !storedToken.equals(refreshToken)) {
                throw new BusinessException(ResultCode.TOKEN_INVALID);
            }

            User user = userMapper.selectById(userId);
            if (user == null || user.getStatus() == 0) {
                throw new BusinessException(ResultCode.USER_NOT_FOUND);
            }

            List<String> roles = roleMapper.selectRoleCodesByUserId(userId);

            String newAccessToken = JwtUtil.createAccessToken(userId, user.getUsername(), roles);
            String newRefreshToken = JwtUtil.createRefreshToken(userId);

            try {
                redisTemplate.opsForValue().set(redisKey, newRefreshToken,
                        Constants.REFRESH_TOKEN_EXPIRE_DAYS, TimeUnit.DAYS);
            } catch (Exception e) {
                log.warn("Redis 更新 refreshToken 失败: {}", e.getMessage());
            }

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("accessToken", newAccessToken);
            data.put("refreshToken", newRefreshToken);
            data.put("expires", DateUtil.format(
                    new Date(System.currentTimeMillis() + JwtUtil.ACCESS_EXPIRE_MS),
                    "yyyy/MM/dd HH:mm:ss"));

            return Map.<String, Object>of("success", true, "data", data);

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ResultCode.TOKEN_INVALID);
        }
    }

    @Override
    public Map<String, Object> getAsyncRoutes(Long userId) {
        List<String> permCodes = permissionMapper.selectPermsByUserId(userId);
        if (permCodes.isEmpty()) {
            return Map.<String, Object>of("success", true, "data", List.of());
        }

        List<Permission> allMenus = permissionMapper.selectList(
                new LambdaQueryWrapper<Permission>()
                        .eq(Permission::getType, "menu")
                        .eq(Permission::getVisible, 1)
                        .orderByAsc(Permission::getSort));

        Set<String> codeSet = new HashSet<>(permCodes);
        List<Map<String, Object>> routes = buildRouteTree(allMenus, codeSet, 0L);

        return Map.<String, Object>of("success", true, "data", routes);
    }

    private List<Map<String, Object>> buildRouteTree(List<Permission> allMenus,
                                                      Set<String> codeSet,
                                                      Long parentId) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Permission perm : allMenus) {
            if (!perm.getParentId().equals(parentId)) continue;
            // 有匹配的权限码才显示菜单
            if (!codeSet.contains(perm.getCode())) continue;

            Map<String, Object> node = new LinkedHashMap<>();
            node.put("path", perm.getPath());
            if (perm.getComponent() != null) {
                node.put("component", perm.getComponent());
            }
            if (perm.getName() != null) {
                node.put("name", perm.getName());
            }

            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("title", perm.getName());
            if (perm.getIcon() != null) meta.put("icon", perm.getIcon());
            if (perm.getSort() != null) meta.put("rank", perm.getSort());
            node.put("meta", meta);

            List<Map<String, Object>> children = buildRouteTree(allMenus, codeSet, perm.getId());
            if (!children.isEmpty()) {
                node.put("children", children);
            }

            result.add(node);
        }
        return result;
    }
}
