package com.restaurant.common.exception;

import com.restaurant.common.api.ErrorCode;
import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * 业务异常：携带 {@link ErrorCode}，由 {@link GlobalExceptionHandler} 统一转换为响应体与 HTTP 状态码。
 *
 * <p>约定：业务校验失败（库存不足、状态不允许、桌台被占用等）一律抛本异常，
 * 禁止在业务代码中直接返回裸错误码；未知异常交由全局兜底处理为 500。
 */
@Getter
public class BizException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final transient ErrorCode errorCode;

    public BizException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public BizException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public BizException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public BizException(String message) {
        this(ErrorCode.PARAM_INVALID, message);
    }

    public int getCode() {
        return errorCode.getCode();
    }

    public HttpStatus getHttpStatus() {
        return errorCode.getStatus();
    }
}
