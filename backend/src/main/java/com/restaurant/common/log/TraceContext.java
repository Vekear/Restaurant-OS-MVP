package com.restaurant.common.log;

import org.slf4j.MDC;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 请求链路 traceId 上下文。
 *
 * <p>traceId 通过 SLF4J {@link MDC} 绑定到请求线程，统一响应体与日志格式
 * （{@code %X{traceId}}）都从这里取值；跨线程场景需要显式传递时再复制 MDC map。
 */
public final class TraceContext {

    /** MDC key，与 logback pattern 中的 {@code %X{traceId}} 对应。 */
    public static final String MDC_KEY = "traceId";

    /** 入站/出站请求头名称（{@code docs/02-api.md §0.2}）。 */
    public static final String HEADER_NAME = "X-Trace-Id";

    /** 允许透传的 traceId 格式，避免日志注入。 */
    private static final Pattern VALID_PATTERN = Pattern.compile("^[A-Za-z0-9._-]{1,64}$");

    private static final int GENERATED_LENGTH = 16;

    private TraceContext() {
    }

    /** 当前线程绑定的 traceId，可能为 null（非 Web 线程，例如定时任务）。 */
    public static String currentTraceId() {
        return MDC.get(MDC_KEY);
    }

    /**
     * 解析入站 traceId：合法则沿用，否则生成新的。
     */
    public static String resolve(String incomingTraceId) {
        if (incomingTraceId == null) {
            return generate();
        }
        String trimmed = incomingTraceId.trim();
        return VALID_PATTERN.matcher(trimmed).matches() ? trimmed : generate();
    }

    /** 生成 16 位 traceId（UUID 去横线后截断）。 */
    public static String generate() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, GENERATED_LENGTH);
    }

    public static void set(String traceId) {
        MDC.put(MDC_KEY, traceId);
    }

    public static void clear() {
        MDC.remove(MDC_KEY);
    }
}
