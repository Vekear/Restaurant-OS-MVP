package com.restaurant.security;

/**
 * 限流判定结果。
 *
 * @param allowed           是否放行
 * @param retryAfterSeconds 被限流时建议重试秒数（写入 Retry-After 响应头）
 * @param remaining         本窗口剩余可用次数
 */
public record RateLimitResult(boolean allowed, long retryAfterSeconds, long remaining) {

    public static RateLimitResult allowed(long remaining) {
        return new RateLimitResult(true, 0L, Math.max(0L, remaining));
    }

    public static RateLimitResult rejected(long retryAfterSeconds) {
        return new RateLimitResult(false, Math.max(1L, retryAfterSeconds), 0L);
    }
}
