package com.restaurant.common.util;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ClientIpUtils} 单测（T65 / H-01）。
 *
 * <p>覆盖：仅受信代理才采信代理头、X-Real-IP 优先且严格校验、X-Forwarded-For 取最右未受信跳、
 * 非法头回退 remoteAddr、受信配置为空时仅用直连地址。
 */
class ClientIpUtilsTest {

    private static final String DEFAULT_TRUSTED = ClientIpUtils.DEFAULT_TRUSTED_PROXIES;

    @Test
    void untrustedRemoteAddrIgnoresAllForwardingHeaders() {
        MockHttpServletRequest request = request("203.0.113.9");
        request.addHeader("X-Forwarded-For", "1.2.3.4");
        request.addHeader("X-Real-IP", "5.6.7.8");

        assertThat(ClientIpUtils.resolve(request, DEFAULT_TRUSTED)).isEqualTo("203.0.113.9");
    }

    @Test
    void trustedProxyPrefersRealIpOverForwardedFor() {
        MockHttpServletRequest request = request("127.0.0.1");
        request.addHeader("X-Forwarded-For", "1.2.3.4");
        request.addHeader("X-Real-IP", "198.51.100.7");

        assertThat(ClientIpUtils.resolve(request, DEFAULT_TRUSTED)).isEqualTo("198.51.100.7");
    }

    @Test
    void trustedProxyCidrIsAccepted() {
        MockHttpServletRequest request = request("192.168.1.10");
        request.addHeader("X-Real-IP", "198.51.100.8");

        assertThat(ClientIpUtils.resolve(request, DEFAULT_TRUSTED)).isEqualTo("198.51.100.8");
    }

    @Test
    void exactConfiguredProxyIpIsAccepted() {
        MockHttpServletRequest request = request("203.0.113.50");
        request.addHeader("X-Real-IP", "198.51.100.9");

        assertThat(ClientIpUtils.resolve(request, "203.0.113.50"))
                .isEqualTo("198.51.100.9");
    }

    @Test
    void forwardedForUsesRightmostUntrustedHopAndIgnoresSpoofedLeftEntries() {
        MockHttpServletRequest request = request("10.0.0.5");
        request.addHeader("X-Forwarded-For", "1.2.3.4, 203.0.113.7, 10.0.0.9");

        assertThat(ClientIpUtils.resolve(request, DEFAULT_TRUSTED)).isEqualTo("203.0.113.7");
    }

    @Test
    void forwardedForRightmostEntryIsTheObservedClient() {
        MockHttpServletRequest request = request("127.0.0.1");
        request.addHeader("X-Forwarded-For", "1.2.3.4, 198.51.100.7");

        assertThat(ClientIpUtils.resolve(request, DEFAULT_TRUSTED)).isEqualTo("198.51.100.7");
    }

    @Test
    void allForwardedHopsTrustedFallsBackToRemoteAddr() {
        MockHttpServletRequest request = request("127.0.0.1");
        request.addHeader("X-Forwarded-For", "10.1.2.3, 10.2.3.4");

        assertThat(ClientIpUtils.resolve(request, DEFAULT_TRUSTED)).isEqualTo("127.0.0.1");
    }

    @Test
    void blankTrustedProxiesUsesRemoteAddrOnly() {
        MockHttpServletRequest request = request("127.0.0.1");
        request.addHeader("X-Real-IP", "198.51.100.7");
        request.addHeader("X-Forwarded-For", "198.51.100.8");

        assertThat(ClientIpUtils.resolve(request, "")).isEqualTo("127.0.0.1");
        assertThat(ClientIpUtils.resolve(request, null)).isEqualTo("127.0.0.1");
    }

    @Test
    void invalidRealIpFallsBackToForwardedFor() {
        MockHttpServletRequest request = request("127.0.0.1");
        request.addHeader("X-Real-IP", "not-an-ip");
        request.addHeader("X-Forwarded-For", "198.51.100.9");

        assertThat(ClientIpUtils.resolve(request, DEFAULT_TRUSTED)).isEqualTo("198.51.100.9");
    }

    @Test
    void invalidForwardedForEntryIsSkipped() {
        MockHttpServletRequest request = request("127.0.0.1");
        request.addHeader("X-Forwarded-For", "198.51.100.9, 1.2.3.4:8080");

        assertThat(ClientIpUtils.resolve(request, DEFAULT_TRUSTED)).isEqualTo("198.51.100.9");
    }

    @Test
    void allHeadersInvalidFallsBackToRemoteAddr() {
        MockHttpServletRequest request = request("127.0.0.1");
        request.addHeader("X-Real-IP", "1.2.3.4:80");
        request.addHeader("X-Forwarded-For", "evil.example.com, 999.1.1.1");

        assertThat(ClientIpUtils.resolve(request, DEFAULT_TRUSTED)).isEqualTo("127.0.0.1");
    }

    @Test
    void malformedTrustedProxyConfigIsIgnoredFailSafe() {
        MockHttpServletRequest request = request("203.0.113.9");
        request.addHeader("X-Real-IP", "198.51.100.7");

        assertThat(ClientIpUtils.resolve(request, "not-an-ip,999.0.0.1/8"))
                .isEqualTo("203.0.113.9");
    }

    @Test
    void ipv6LoopbackIsNormalizedToIpv4Loopback() {
        MockHttpServletRequest request = request("0:0:0:0:0:0:0:1");

        assertThat(ClientIpUtils.resolve(request, DEFAULT_TRUSTED)).isEqualTo("127.0.0.1");
    }

    @Test
    void validIpv6RealIpIsAccepted() {
        MockHttpServletRequest request = request("127.0.0.1");
        request.addHeader("X-Real-IP", "2001:db8::1");

        assertThat(ClientIpUtils.resolve(request, DEFAULT_TRUSTED)).startsWith("2001:db8");
    }

    @Test
    void ipv4MappedIpv6RealIpIsNormalized() {
        MockHttpServletRequest request = request("127.0.0.1");
        request.addHeader("X-Real-IP", "::ffff:198.51.100.10");

        assertThat(ClientIpUtils.resolve(request, DEFAULT_TRUSTED)).isEqualTo("198.51.100.10");
    }

    private MockHttpServletRequest request(String remoteAddr) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        request.setRemoteAddr(remoteAddr);
        return request;
    }
}
