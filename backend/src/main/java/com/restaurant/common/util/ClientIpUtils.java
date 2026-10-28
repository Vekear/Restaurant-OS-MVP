package com.restaurant.common.util;

import jakarta.servlet.http.HttpServletRequest;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 客户端 IP 解析工具（登录限流、审计日志使用）。
 *
 * <p>安全模型（T65，修复 H-01）：只有直连地址 {@code request.getRemoteAddr()} 命中受信代理
 * （{@code app.rate-limit.trusted-proxies}，默认本机回环与 RFC1918/ULA 私网）时，才读取代理头：
 * <ol>
 *   <li>优先 {@code X-Real-IP}，且必须是严格合法的 IPv4/IPv6 字面量；</li>
 *   <li>无 {@code X-Real-IP} 时，取 {@code X-Forwarded-For} 最右侧未受信跳（左侧内容由客户端提供，一律不采信）；</li>
 *   <li>其余情况回退 {@code remoteAddr}。</li>
 * </ol>
 * 客户端可任意伪造的 {@code X-Forwarded-For} 首段永远不会成为限流 key，避免登录限流被绕过。
 * 结果统一规范化并截断到 64 字符以匹配 {@code audit_log.ip}。
 */
public final class ClientIpUtils {

    /** 默认可信代理：本机回环 + IPv4/IPv6 私网。 */
    public static final String DEFAULT_TRUSTED_PROXIES =
            "127.0.0.1,::1,10.0.0.0/8,172.16.0.0/12,192.168.0.0/16,fc00::/7";

    private static final int MAX_LENGTH = 64;

    private static final int MAX_FORWARDED_HEADER_LENGTH = 1024;

    private static final int MAX_FORWARDED_HOPS = 50;

    private static final String UNKNOWN = "unknown";

    private static final Pattern IPV4 = Pattern.compile("^\\d{1,3}(\\.\\d{1,3}){3}$");

    private static final Pattern IPV6_CHARS = Pattern.compile("^[0-9A-Fa-f:.]+$");

    private ClientIpUtils() {
    }

    /** 使用默认受信代理解析客户端 IP，永不返回 null（保留给非配置化调用方）。 */
    public static String resolve(HttpServletRequest request) {
        return resolve(request, DEFAULT_TRUSTED_PROXIES);
    }

    /**
     * 解析客户端 IP，永不返回 null。
     *
     * @param request         HTTP 请求
     * @param trustedProxies  受信代理列表（逗号分隔的精确 IP 或 CIDR），为空表示不信任任何代理，仅用 remoteAddr
     */
    public static String resolve(HttpServletRequest request, String trustedProxies) {
        if (request == null) {
            return UNKNOWN;
        }
        List<Cidr> trusted = Cidr.parseList(trustedProxies);
        String remote = canonical(request.getRemoteAddr());
        String fallback = truncate(remote != null ? remote : UNKNOWN);
        if (remote == null || !isTrusted(remote, trusted)) {
            return fallback;
        }
        String realIp = canonical(request.getHeader("X-Real-IP"));
        if (realIp != null) {
            return truncate(realIp);
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        String forwardedHop = rightmostUntrustedHop(forwarded, trusted);
        if (forwardedHop != null) {
            return truncate(forwardedHop);
        }
        return fallback;
    }

    /**
     * 取 {@code X-Forwarded-For} 最右侧未受信跳：右侧是代理链上离后端最近的节点，
     * 客户端只能在左侧追加内容，无法伪造右侧。
     */
    private static String rightmostUntrustedHop(String forwarded, List<Cidr> trusted) {
        if (forwarded == null || forwarded.isBlank()
                || forwarded.length() > MAX_FORWARDED_HEADER_LENGTH) {
            return null;
        }
        String[] parts = forwarded.split(",");
        int start = Math.max(0, parts.length - MAX_FORWARDED_HOPS);
        for (int i = parts.length - 1; i >= start; i--) {
            String hop = canonical(parts[i]);
            if (hop == null) {
                continue;
            }
            if (!isTrusted(hop, trusted)) {
                return hop;
            }
        }
        return null;
    }

    private static boolean isTrusted(String ip, List<Cidr> trusted) {
        if (trusted.isEmpty()) {
            return false;
        }
        InetAddress address = literal(ip);
        if (address == null) {
            return false;
        }
        for (Cidr cidr : trusted) {
            if (cidr.matches(address)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 严格解析 IP 字面量：拒绝域名、端口、空白、CIDR、zone id 等；不做 DNS 解析。
     *
     * @return 规范化文本（IPv4 点分；IPv6 为 {@link InetAddress#getHostAddress()}），非法返回 null
     */
    static String canonical(String value) {
        InetAddress address = literal(value);
        if (address == null) {
            return null;
        }
        if (address instanceof Inet4Address) {
            return address.getHostAddress();
        }
        // 本机 IPv6 回环统一为 IPv4，避免登录限流 key 出现两种写法
        if (address.isLoopbackAddress() && address instanceof Inet6Address) {
            return "127.0.0.1";
        }
        return address.getHostAddress().toLowerCase(Locale.ROOT);
    }

    private static InetAddress literal(String value) {
        if (value == null) {
            return null;
        }
        String ip = value.trim();
        if (ip.isEmpty() || ip.length() > 45 || UNKNOWN.equalsIgnoreCase(ip)) {
            return null;
        }
        if (ip.indexOf(',') >= 0 || ip.indexOf('/') >= 0 || ip.indexOf('%') >= 0
                || ip.chars().anyMatch(Character::isWhitespace)) {
            return null;
        }
        try {
            if (IPV4.matcher(ip).matches()) {
                byte[] bytes = parseIpv4(ip);
                return bytes == null ? null : InetAddress.getByAddress(bytes);
            }
            if (ip.indexOf(':') >= 0 && IPV6_CHARS.matcher(ip).matches()) {
                // 仅十六进制/冒号/点组成的串才交给 InetAddress，杜绝域名触发 DNS 解析
                return InetAddress.getByName(ip);
            }
            return null;
        } catch (UnknownHostException | NumberFormatException exception) {
            return null;
        }
    }

    private static byte[] parseIpv4(String ip) {
        String[] parts = ip.split("\\.", -1);
        if (parts.length != 4) {
            return null;
        }
        byte[] bytes = new byte[4];
        for (int i = 0; i < 4; i++) {
            String part = parts[i];
            if (part.isEmpty() || part.length() > 3 || (part.length() > 1 && part.charAt(0) == '0')) {
                return null;
            }
            int value;
            try {
                value = Integer.parseInt(part);
            } catch (NumberFormatException exception) {
                return null;
            }
            if (value < 0 || value > 255) {
                return null;
            }
            bytes[i] = (byte) value;
        }
        return bytes;
    }

    private static String truncate(String ip) {
        if (ip == null || ip.isBlank()) {
            return UNKNOWN;
        }
        return ip.length() > MAX_LENGTH ? ip.substring(0, MAX_LENGTH) : ip;
    }

    /** 受信代理条目：精确 IP 或 CIDR。 */
    private record Cidr(byte[] network, int prefixLength) {

        static List<Cidr> parseList(String raw) {
            List<Cidr> result = new ArrayList<>();
            if (raw == null || raw.isBlank()) {
                return result;
            }
            for (String token : raw.split(",")) {
                Cidr cidr = parse(token);
                if (cidr != null) {
                    result.add(cidr);
                }
            }
            return result;
        }

        private static Cidr parse(String token) {
            String text = token == null ? "" : token.trim();
            if (text.isEmpty() || text.indexOf('%') >= 0) {
                return null;
            }
            int slash = text.indexOf('/');
            String ipPart = slash > 0 ? text.substring(0, slash) : text;
            InetAddress address = literal(ipPart);
            if (address == null) {
                return null;
            }
            byte[] bytes = address.getAddress();
            int bits = bytes.length * 8;
            int prefix = bits;
            if (slash > 0) {
                String prefixPart = text.substring(slash + 1);
                if (prefixPart.isEmpty() || prefixPart.length() > 3
                        || (prefixPart.length() > 1 && prefixPart.charAt(0) == '0')) {
                    return null;
                }
                try {
                    prefix = Integer.parseInt(prefixPart);
                } catch (NumberFormatException exception) {
                    return null;
                }
                if (prefix < 0 || prefix > bits) {
                    return null;
                }
            }
            return new Cidr(bytes, prefix);
        }

        boolean matches(InetAddress address) {
            byte[] candidate = address.getAddress();
            if (candidate.length != network.length) {
                return false;
            }
            int fullBytes = prefixLength / 8;
            int remainingBits = prefixLength % 8;
            for (int i = 0; i < fullBytes; i++) {
                if (candidate[i] != network[i]) {
                    return false;
                }
            }
            if (remainingBits == 0) {
                return true;
            }
            int mask = 0xFF << (8 - remainingBits);
            return (candidate[fullBytes] & mask) == (network[fullBytes] & mask);
        }
    }
}
