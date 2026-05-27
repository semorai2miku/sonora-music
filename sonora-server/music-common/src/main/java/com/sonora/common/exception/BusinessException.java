package com.sonora.common.exception;

import com.sonora.common.enums.ResultCode;

/**
 * 业务异常
 * <p>
 * 在 Service 层抛出，由 GlobalExceptionHandler 统一捕获并转换为 R 响应。
 */
public class BusinessException extends RuntimeException {

    private final int code;

    public BusinessException(ResultCode resultCode) {
        super(resultCode.getMessage());
        this.code = resultCode.getCode();
    }

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    public BusinessException(String message) {
        super(message);
        this.code = ResultCode.SYSTEM_ERROR.getCode();
    }

    public int getCode() {
        return code;
    }
}
