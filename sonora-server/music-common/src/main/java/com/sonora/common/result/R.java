package com.sonora.common.result;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * 统一 API 响应结果封装
 * <p>
 * 所有 Controller 返回此类型，前端统一解析 code/message/data。
 * code 与 HTTP 状态码保持一致 (200=成功, 4xx=客户端错误, 5xx=服务端错误)。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class R<T> {

    /** 状态码，与 HTTP 状态码对齐 */
    private int code;

    /** 提示消息 */
    private String message;

    /** 响应数据 */
    private T data;

    // ==================== 成功响应 ====================

    public static <T> R<T> ok() {
        return new R<>(HttpStatus.OK.value(), "success", null);
    }

    public static <T> R<T> ok(T data) {
        return new R<>(HttpStatus.OK.value(), "success", data);
    }

    public static <T> R<T> ok(String message, T data) {
        return new R<>(HttpStatus.OK.value(), message, data);
    }

    // ==================== 失败响应 ====================

    public static <T> R<T> fail() {
        return new R<>(HttpStatus.INTERNAL_SERVER_ERROR.value(), "系统异常，请稍后重试", null);
    }

    public static <T> R<T> fail(String message) {
        return new R<>(HttpStatus.INTERNAL_SERVER_ERROR.value(), message, null);
    }

    public static <T> R<T> fail(int code, String message) {
        return new R<>(code, message, null);
    }

    // ==================== 客户端错误 ====================

    public static <T> R<T> badRequest(String message) {
        return new R<>(HttpStatus.BAD_REQUEST.value(), message, null);
    }

    public static <T> R<T> unauthorized(String message) {
        return new R<>(HttpStatus.UNAUTHORIZED.value(), message, null);
    }

    public static <T> R<T> forbidden(String message) {
        return new R<>(HttpStatus.FORBIDDEN.value(), message, null);
    }

    public static <T> R<T> notFound(String message) {
        return new R<>(HttpStatus.NOT_FOUND.value(), message, null);
    }
}
