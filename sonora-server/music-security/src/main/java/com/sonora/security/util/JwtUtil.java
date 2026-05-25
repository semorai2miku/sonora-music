package com.sonora.security.util;

import cn.hutool.core.date.DateUtil;
import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import lombok.extern.slf4j.Slf4j;

import java.util.Date;

/**
 * JWT 工具类 — 生成 / 验证 / 解析 Token
 */
@Slf4j
public final class JwtUtil {

    private JwtUtil() {}

    /** 密钥 (生产环境应放在配置文件中) */
    private static final String SECRET = "SonoraMusicSecretKey2024";

    /** 签发者 */
    private static final String ISSUER = "sonora-music";

    /** AccessToken 过期时间: 2 小时 */
    public static final long ACCESS_EXPIRE_MS = 2 * 60 * 60 * 1000L;

    /** RefreshToken 过期时间: 7 天 */
    public static final long REFRESH_EXPIRE_MS = 7 * 24 * 60 * 60 * 1000L;

    private static final Algorithm ALGORITHM = Algorithm.HMAC256(SECRET);

    /** 生成 AccessToken */
    public static String createAccessToken(Long userId, String username) {
        return JWT.create()
                .withIssuer(ISSUER)
                .withSubject(String.valueOf(userId))
                .withClaim("username", username)
                .withIssuedAt(new Date())
                .withExpiresAt(new Date(System.currentTimeMillis() + ACCESS_EXPIRE_MS))
                .sign(ALGORITHM);
    }

    /** 生成 RefreshToken */
    public static String createRefreshToken(Long userId) {
        return JWT.create()
                .withIssuer(ISSUER)
                .withSubject(String.valueOf(userId))
                .withClaim("type", "refresh")
                .withIssuedAt(new Date())
                .withExpiresAt(new Date(System.currentTimeMillis() + REFRESH_EXPIRE_MS))
                .sign(ALGORITHM);
    }

    /** 验证并解析 Token */
    public static DecodedJWT verify(String token) {
        JWTVerifier verifier = JWT.require(ALGORITHM).withIssuer(ISSUER).build();
        return verifier.verify(token);
    }

    /** 从 Token 中提取用户 ID */
    public static Long getUserId(String token) {
        return Long.valueOf(verify(token).getSubject());
    }

    /** 检查 Token 是否即将过期 (剩余不足 5 分钟) */
    public static boolean isNearExpiry(String token) {
        DecodedJWT jwt = verify(token);
        return jwt.getExpiresAt().getTime() - System.currentTimeMillis() < 5 * 60 * 1000L;
    }
}
