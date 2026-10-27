package com.restaurant.common.exception;

import com.restaurant.common.api.ErrorCode;
import lombok.Getter;

/**
 * 限流异常：HTTP 429 + 业务码 42901，并携带建议重试秒数（写入 {@code Retry-After} 响应头）。
 *
 * <p>由 GlobalExceptionHandler 统一转换为响应体；限流 key、阈值仅记录日志，不返回给客户端。
 */
@Getter
public class RateLimitExceededException extends BizException {

    private static final long serialVersionUID = 1L;

    private final long retryAfterSeconds;

    public RateLimitExceededException(long retryAfterSeconds) {
        super(ErrorCode.TOO_MANY_REQUESTS);
        this.retryAfterSeconds = Math.max(1L, retryAfterSeconds);
    }
}
