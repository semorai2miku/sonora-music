package com.sonora.common.constant;

/**
 * 全局常量
 */
public final class Constants {

    private Constants() {}

    /** Token 前缀 */
    public static final String TOKEN_PREFIX = "Bearer ";

    /** JWT 签发者 */
    public static final String JWT_ISSUER = "sonora-music";

    /** 验证码 Redis key 前缀 */
    public static final String CAPTCHA_KEY_PREFIX = "captcha:";

    /** Token 黑名单 Redis key 前缀 */
    public static final String TOKEN_BLACKLIST_PREFIX = "token:blacklist:";

    /** 用户默认头像 */
    public static final String DEFAULT_AVATAR = "/default-avatar.svg";

    /** 默认分页大小 */
    public static final int DEFAULT_PAGE_SIZE = 20;

    /** 最大分页大小 */
    public static final int MAX_PAGE_SIZE = 100;

    /** 验证码过期时间 (秒) */
    public static final long CAPTCHA_EXPIRE_SECONDS = 300;

    /** AccessToken 过期时间 (小时) */
    public static final long ACCESS_TOKEN_EXPIRE_HOURS = 2;

    /** RefreshToken 过期时间 (天) */
    public static final long REFRESH_TOKEN_EXPIRE_DAYS = 7;
}
