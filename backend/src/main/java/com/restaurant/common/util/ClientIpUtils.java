package com.restaurant.common.util;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 客户端 IP 解析工具（登录限流、审计日志使用）。
 *
 * <p>Nginx 反向代理下优先取 {@code X-Forwarded-For} 首个地址，其次 {@code X-Real-IP}，
 * 最后回退到 {@code request.getRemoteAddr()}；结果长度截断到 64 字符以匹配 audit_log.ip。
 */
public final class ClientIpUtils {

    private static final int MAX_LENGTH = 64;

    private static final String UNKNOWN = "unknown";

    private ClientIpUtils() {
    }

    /** 解析客户端 IP，永不返回 null。 */
    public static String resolve(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (isUsable(forwarded)) {
            int comma = forwarded.indexOf(',');
            String first = comma > 0 ? forwarded.substring(0, comma) : forwarded;
            return normalize(first.trim());
        }
        String realIp = request.getHeader("X-Real-IP");
        if (isUsable(realIp)) {
            return normalize(realIp.trim());
        }
        return normalize(request.getRemoteAddr());
    }

    private static boolean isUsable(String value) {
        return value != null && !value.isBlank() && !UNKNOWN.equalsIgnoreCase(value.trim());
    }

    private static String normalize(String ip) {
        if (ip == null || ip.isBlank()) {
            return UNKNOWN;
        }
        // 本机 IPv6 回环统一为 IPv4，避免登录限流 key 出现两种写法
        String normalized = "0:0:0:0:0:0:0:1".equals(ip) ? "127.0.0.1" : ip;
        return normalized.length() > MAX_LENGTH ? normalized.substring(0, MAX_LENGTH) : normalized;
    }
}
