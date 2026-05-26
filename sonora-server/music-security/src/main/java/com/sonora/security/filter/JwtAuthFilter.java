package com.sonora.security.filter;

import cn.hutool.core.util.StrUtil;
import com.auth0.jwt.exceptions.TokenExpiredException;
import com.sonora.common.constant.Constants;
import com.sonora.common.util.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * JWT 认证过滤器
 * <p>
 * 在每个请求中提取 Bearer Token，验证并设置 Spring Security 上下文。
 */
@Slf4j
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        String token = extractToken(request);

        if (StrUtil.isBlank(token)) {
            chain.doFilter(request, response);
            return;
        }

        try {
            var decoded = JwtUtil.verify(token);
            Long userId = Long.valueOf(decoded.getSubject());

            // 从 Token Claims 中获取角色
            List<SimpleGrantedAuthority> authorities;
            try {
                authorities = decoded.getClaim("roles").asList(String.class).stream()
                        .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                        .toList();
            } catch (Exception e) {
                log.debug("无法解析 roles claim: {}", e.getMessage());
                authorities = List.of();
            }
            log.debug("Token userId={}, authorities={}", userId, authorities);

            var authentication = new UsernamePasswordAuthenticationToken(
                    userId, null, authorities);
            SecurityContextHolder.getContext().setAuthentication(authentication);

        } catch (TokenExpiredException e) {
            log.debug("Token 已过期: {}", e.getMessage());
        } catch (Exception e) {
            log.warn("Token 验证失败: {}", e.getMessage());
        }

        chain.doFilter(request, response);
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (StrUtil.isNotBlank(header) && header.startsWith(Constants.TOKEN_PREFIX)) {
            return header.substring(Constants.TOKEN_PREFIX.length());
        }
        return null;
    }
}
