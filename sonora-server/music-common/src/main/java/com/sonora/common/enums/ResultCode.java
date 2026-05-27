package com.sonora.common.enums;

/**
 * 业务操作结果码枚举
 * <p>
 * 用于 BusinessException 精确定位错误类型。
 * 0 = 成功，1xxx = 通用错误，2xxx = 用户模块，3xxx = 内容模块，4xxx = 文件模块
 */
public enum ResultCode {

    /* ========== 通用 ========== */
    SUCCESS(0, "操作成功"),
    PARAM_ERROR(1001, "参数校验失败"),
    SYSTEM_ERROR(1002, "系统内部错误"),
    RATE_LIMIT(1003, "请求过于频繁"),

    /* ========== 用户 / 认证 ========== */
    USER_NOT_FOUND(2001, "用户不存在"),
    USER_DISABLED(2002, "账号已被禁用"),
    PASSWORD_ERROR(2003, "密码错误"),
    TOKEN_EXPIRED(2004, "登录已过期，请重新登录"),
    TOKEN_INVALID(2005, "无效的令牌"),
    UNAUTHORIZED(2006, "未登录或登录已过期"),
    FORBIDDEN(2007, "无访问权限"),
    CAPTCHA_ERROR(2008, "验证码错误"),
    USERNAME_EXISTS(2009, "用户名已存在"),

    /* ========== 内容模块 ========== */
    SONG_NOT_FOUND(3001, "歌曲不存在"),
    ALBUM_NOT_FOUND(3002, "专辑不存在"),
    ARTIST_NOT_FOUND(3003, "歌手不存在"),
    PLAYLIST_NOT_FOUND(3004, "歌单不存在"),
    COMMENT_NOT_FOUND(3005, "评论不存在"),

    /* ========== 文件模块 ========== */
    FILE_UPLOAD_FAILED(4001, "文件上传失败"),
    FILE_NOT_FOUND(4002, "文件不存在"),
    FILE_FORMAT_UNSUPPORTED(4003, "不支持的文件格式"),
    FILE_SIZE_EXCEEDED(4004, "文件大小超出限制");

    private final int code;
    private final String message;

    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
