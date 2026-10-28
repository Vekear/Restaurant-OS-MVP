package com.restaurant.security;

import com.jayway.jsonpath.JsonPath;
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

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * L-03：Actuator 端点收敛集成测试（真实 Tomcat + test profile）。
 *
 * <p>验证 {@code /actuator/health}（含探针子路径）保持匿名可用，供容器健康检查；
 * 其余 {@code /actuator/**} 匿名返回 401、CUSTOMER 返回 403、ADMIN 可访问，
 * 避免直连 8080 绕过 Nginx 的 /actuator 白名单。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ActuatorEndpointSecurityIntegrationTest {

    private static final String LOGIN = "/api/v1/auth/login";

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @BeforeEach
    void clearRateLimitKeys() {
        Set<String> keys = stringRedisTemplate.keys("rate:*");
        if (keys != null && !keys.isEmpty()) {
            stringRedisTemplate.delete(keys);
        }
    }

    @Test
    void healthAndProbesRemainAnonymous() {
        ResponseEntity<String> health = restTemplate.getForEntity("/actuator/health", String.class);
        assertThat(health.getStatusCode().value()).isEqualTo(200);
        assertThat(health.getBody()).contains("\"status\":\"UP\"");

        ResponseEntity<String> liveness = restTemplate.getForEntity("/actuator/health/liveness", String.class);
        assertThat(liveness.getStatusCode().value()).isEqualTo(200);
        assertThat(liveness.getBody()).contains("\"status\":\"UP\"");
    }

    @Test
    void anonymousCannotReadNonHealthActuatorEndpoints() {
        for (String path : new String[]{"/actuator", "/actuator/env", "/actuator/metrics", "/actuator/info"}) {
            ResponseEntity<String> response = restTemplate.getForEntity(path, String.class);
            assertThat(response.getStatusCode().value()).as(path).isEqualTo(401);
            assertThat(((Number) JsonPath.read(response.getBody(), "$.code")).intValue())
                    .as(path).isEqualTo(40101);
        }
    }

    @Test
    void adminCanReadActuatorButCustomerIsForbidden() {
        ResponseEntity<String> adminInfo = exchange(HttpMethod.GET, "/actuator/info",
                token("admin", "admin123"));
        assertThat(adminInfo.getStatusCode().value()).isEqualTo(200);

        ResponseEntity<String> customerInfo = exchange(HttpMethod.GET, "/actuator/info",
                token("customer", "customer123"));
        assertThat(customerInfo.getStatusCode().value()).isEqualTo(403);
        assertThat(((Number) JsonPath.read(customerInfo.getBody(), "$.code")).intValue()).isEqualTo(40301);
    }

    private String token(String username, String password) {
        ResponseEntity<String> response = exchange(LOGIN, Map.of("username", username, "password", password));
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        return JsonPath.read(response.getBody(), "$.data.token");
    }

    private ResponseEntity<String> exchange(HttpMethod method, String path, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return restTemplate.exchange(path, method, new HttpEntity<>(null, headers), String.class);
    }

    private ResponseEntity<String> exchange(String path, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.exchange(path, HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
    }
}
