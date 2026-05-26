package com.sonora.common.result;

import org.springframework.http.HttpStatus;

/**
 * 统一 API 响应结果封装
 */
public class R<T> {

    private int code;
    private String message;
    private T data;

    public R() {}

    public R(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public int getCode() { return code; }
    public void setCode(int code) { this.code = code; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public T getData() { return data; }
    public void setData(T data) { this.data = data; }

    // ==================== 成功 ====================

    public static <T> R<T> ok() {
        return new R<>(HttpStatus.OK.value(), "success", null);
    }

    public static <T> R<T> ok(T data) {
        return new R<>(HttpStatus.OK.value(), "success", data);
    }

    public static <T> R<T> ok(String message, T data) {
        return new R<>(HttpStatus.OK.value(), message, data);
    }

    // ==================== 失败 ====================

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
