package com.sonora.security.config;

import com.sonora.security.filter.JwtAuthFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

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
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // 公开接口
                .requestMatchers(
                    "/login",
                    "/refresh-token",
                    "/api/auth/**",
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
                    "/recommend/songs",
                    "/recommend/resource",
                    "/artist/detail",
                    "/artist/top/song",
                    "/album",
                    "/record/recent/song",
                    "/user/playlist"
                ).permitAll()
                // 管理端接口（暂时放宽，调试阶段）
                .requestMatchers("/api/admin/**").authenticated()
                // 其他接口需认证
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
