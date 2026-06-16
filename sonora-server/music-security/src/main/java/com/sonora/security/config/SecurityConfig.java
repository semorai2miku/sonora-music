package com.sonora.security.config;

import com.sonora.security.filter.JwtAuthFilter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.io.IOException;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> {})
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint((request, response, authException) -> {
                    writeJsonError(response, HttpServletResponse.SC_UNAUTHORIZED, "请先登录");
                })
                .accessDeniedHandler((request, response, accessDeniedException) -> {
                    writeJsonError(response, HttpServletResponse.SC_FORBIDDEN, "无权访问或登录已过期");
                })
            )
            .authorizeHttpRequests(auth -> auth
                // CORS 预检请求不携带业务 token，必须先放行，否则浏览器会报 Network Error
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                // 需要客户端登录的个人接口
                .requestMatchers(
                    "/api/client/auth/me",
                    "/api/client/auth/avatar",
                    "/api/client/auth/password",
                    "/api/client/me/**"
                ).authenticated()
                // 管理端接口仅允许超级管理员访问
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                // 公开接口
                .requestMatchers(
                    "/login",
                    "/refresh-token",
                    "/api/auth/**",
                    "/api/files/preview",
                    "/api/client/**",
                    "/swagger-ui/**",
                    "/v3/api-docs/**",
                    "/doc.html",
                    // 网易云兼容路径 (GlassMusicPlayer)
                    "/banner",
                    "/search",
                    "/cloudsearch",
                    "/song/url",
                    "/song/url/v1",
                    "/song/detail",
                    "/lyric",
                    "/playlist/detail",
                    "/playlist/track/all",
                    "/personalized",
                    "/top/playlist",
                    "/top/song",
                    "/recommend/songs",
                    "/recommend/resource",
                    "/artist/detail",
                    "/artist/top/song",
                    "/album",
                    "/record/recent/song",
                    "/user/playlist"
                ).permitAll()
                // 其他接口需认证
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    private void writeJsonError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":" + status + ",\"message\":\"" + message + "\",\"data\":null}");
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
