package com.restaurant.auth;

import com.jayway.jsonpath.JsonPath;
import com.restaurant.common.config.AppProperties;
import com.restaurant.common.util.CacheKeys;
import com.restaurant.common.util.ClientIpUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 登录限流 IP 解析集成测试（T65 / H-01，修复可伪造 X-Forwarded-For 绕过 5 次/分钟限制）。
 *
 * <p>真实 Tomcat + MySQL restaurant_mvp_test + Redis db15。用例通过请求头模拟三种拓扑：
 * <ol>
 *   <li>直连后端（受信列表为空）：伪造 X-Forwarded-For 不改变限流 key，同一 remoteAddr 第 6 次 429；</li>
 *   <li>受信代理 + X-Real-IP：按 X-Real-IP 计数，攻击者伪造的 XFF 首段被忽略；</li>
 *   <li>受信代理 + 非法 IP 头：回退 remoteAddr 计数。</li>
 * </ol>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ClientIpRateLimitIntegrationTest {

    private static final String LOGIN = "/api/v1/auth/login";

    private static final String LOOPBACK = "127.0.0.1";

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private AppProperties appProperties;

    @BeforeEach
    void clearRateLimitKeys() {
        clearKeys();
        appProperties.getRateLimit().setTrustedProxies(ClientIpUtils.DEFAULT_TRUSTED_PROXIES);
    }

    @AfterEach
    void restoreConfiguration() {
        clearKeys();
        appProperties.getRateLimit().setTrustedProxies(ClientIpUtils.DEFAULT_TRUSTED_PROXIES);
    }

    @Test
    void directClientCannotBypassLoginLimitWithSpoofedForwardedFor() {
        appProperties.getRateLimit().setTrustedProxies("");

        for (int i = 1; i <= 5; i++) {
            ResponseEntity<String> attempt = login("1.2.3." + i, null);
            assertThat(attempt.getStatusCode().value()).isEqualTo(401);
            assertThat(codeOf(attempt)).isEqualTo(40101);
        }
        ResponseEntity<String> limited = login("9.9.9.9", null);
        assertThat(limited.getStatusCode().value()).isEqualTo(429);
        assertThat(codeOf(limited)).isEqualTo(42901);
        assertThat(Long.parseLong(limited.getHeaders().getFirst(HttpHeaders.RETRY_AFTER))).isPositive();

        assertThat(loginKeyCount(LOOPBACK)).isEqualTo(6);
        assertThat(stringRedisTemplate.hasKey(CacheKeys.rateLoginKey("1.2.3.1"))).isFalse();
        assertThat(stringRedisTemplate.hasKey(CacheKeys.rateLoginKey("9.9.9.9"))).isFalse();
    }

    @Test
    void loginIsLimitedPerRealIpWhenRequestComesFromTrustedProxy() {
        appProperties.getRateLimit().setTrustedProxies("127.0.0.1,::1");

        for (int i = 0; i < 5; i++) {
            ResponseEntity<String> attempt = login("1.2.3.4", "198.51.100.21");
            assertThat(attempt.getStatusCode().value()).isEqualTo(401);
        }
        ResponseEntity<String> limited = login("1.2.3.4", "198.51.100.21");
        assertThat(limited.getStatusCode().value()).isEqualTo(429);
        assertThat(codeOf(limited)).isEqualTo(42901);

        assertThat(loginKeyCount("198.51.100.21")).isEqualTo(6);
        assertThat(stringRedisTemplate.hasKey(CacheKeys.rateLoginKey("1.2.3.4"))).isFalse();

        // 另一个真实 IP 有独立窗口，不会被前一 IP 的 429 牵连
        ResponseEntity<String> other = login("1.2.3.4", "198.51.100.22");
        assertThat(other.getStatusCode().value()).isEqualTo(401);
        assertThat(loginKeyCount("198.51.100.22")).isEqualTo(1);
    }

    @Test
    void invalidIpHeadersFallBackToRemoteAddr() {
        appProperties.getRateLimit().setTrustedProxies("127.0.0.1,::1");

        for (int i = 0; i < 5; i++) {
            ResponseEntity<String> attempt = login("1.2.3.4:8080", "not-an-ip");
            assertThat(attempt.getStatusCode().value()).isEqualTo(401);
        }
        ResponseEntity<String> limited = login("evil.example.com", "999.1.1.1");
        assertThat(limited.getStatusCode().value()).isEqualTo(429);
        assertThat(codeOf(limited)).isEqualTo(42901);

        assertThat(loginKeyCount(LOOPBACK)).isEqualTo(6);
        assertThat(stringRedisTemplate.hasKey(CacheKeys.rateLoginKey("not-an-ip"))).isFalse();
    }

    private ResponseEntity<String> login(String forwardedFor, String realIp) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (forwardedFor != null) {
            headers.add("X-Forwarded-For", forwardedFor);
        }
        if (realIp != null) {
            headers.add("X-Real-IP", realIp);
        }
        Map<String, Object> body = new HashMap<>();
        body.put("username", "admin");
        body.put("password", "wrong-password");
        return restTemplate.exchange(LOGIN, HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
    }

    private int codeOf(ResponseEntity<String> response) {
        return JsonPath.read(response.getBody(), "$.code");
    }

    private long loginKeyCount(String ip) {
        String value = stringRedisTemplate.opsForValue().get(CacheKeys.rateLoginKey(ip));
        return value == null ? 0 : Long.parseLong(value);
    }

    private void clearKeys() {
        Set<String> keys = stringRedisTemplate.keys("rate:*");
        if (keys != null && !keys.isEmpty()) {
            stringRedisTemplate.delete(keys);
        }
    }
}
