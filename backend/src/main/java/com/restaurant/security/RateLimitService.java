package com.restaurant.security;

import com.restaurant.common.exception.RateLimitExceededException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Redis 固定窗口限流（{@code docs/01-architecture.md §16.8}）。
 *
 * <p>故障策略（ADR-0011）：Redis 不可用时放行（fail-open）并输出 WARN，避免限流组件拖垮登录/下单主流程；
 * 同一 key 的告警最多每 60 秒一次，防止日志风暴。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimitService {

    private static final long WARN_INTERVAL_MILLIS = 60_000L;

    private final StringRedisTemplate stringRedisTemplate;

    private final Map<String, Long> lastWarnAt = new ConcurrentHashMap<>();

    /**
     * 计数并判定是否超限。
     *
     * @param key    限流 key（由 {@link com.restaurant.common.util.CacheKeys} 生成）
     * @param limit  窗口内最大次数，<=0 表示不限制
     * @param window 固定窗口长度
     */
    public RateLimitResult check(String key, int limit, Duration window) {
        if (limit <= 0) {
            return RateLimitResult.allowed(Long.MAX_VALUE);
        }
        try {
            Long count = stringRedisTemplate.opsForValue().increment(key);
            if (count == null) {
                return RateLimitResult.allowed(limit);
            }
            if (count == 1L) {
                stringRedisTemplate.expire(key, window);
            } else {
                Long ttl = stringRedisTemplate.getExpire(key, TimeUnit.SECONDS);
                if (ttl != null && ttl < 0) {
                    // 兜底：INCR 与 EXPIRE 之间进程崩溃时补设 TTL，避免永久计数
                    stringRedisTemplate.expire(key, window);
                }
            }
            if (count > limit) {
                Long ttl = stringRedisTemplate.getExpire(key, TimeUnit.SECONDS);
                long retryAfter = ttl == null || ttl <= 0 ? window.toSeconds() : ttl;
                return RateLimitResult.rejected(retryAfter);
            }
            return RateLimitResult.allowed(limit - count);
        } catch (RuntimeException exception) {
            warnThrottled(key, exception);
            return RateLimitResult.allowed(limit);
        }
    }

    /** 计数并直接抛 429 异常（Service 层登录/支付入口使用）。 */
    public void checkOrThrow(String key, int limit, Duration window) {
        RateLimitResult result = check(key, limit, window);
        if (!result.allowed()) {
            throw new RateLimitExceededException(result.retryAfterSeconds());
        }
    }

    private void warnThrottled(String key, RuntimeException exception) {
        long now = System.currentTimeMillis();
        Long previous = lastWarnAt.get(key);
        if (previous != null && now - previous < WARN_INTERVAL_MILLIS) {
            return;
        }
        lastWarnAt.put(key, now);
        log.warn("Redis 限流不可用，已降级放行 key={}: {}", key, exception.getMessage());
    }
}
