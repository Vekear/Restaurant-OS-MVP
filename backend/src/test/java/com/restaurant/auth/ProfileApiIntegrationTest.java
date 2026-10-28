package com.restaurant.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 个人资料接口集成测试（真实 Tomcat + MySQL restaurant_mvp_test + Redis db15）。
 *
 * <p>覆盖 docs/02-api.md 端点 #8 PUT /auth/profile：昵称/手机号/头像更新、GET /auth/me 回读、
 * 手机号置空、参数校验（手机号格式 / 头像协议 / 昵称长度）与未认证 401。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ProfileApiIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void clearRateLimitKeys() {
        Set<String> keys = stringRedisTemplate.keys("rate:*");
        if (!keys.isEmpty()) {
            stringRedisTemplate.delete(keys);
        }
    }

    @Test
    void updateProfileThenReadBackAndClearPhone() throws Exception {
        String token = register("资料顾客");
        String nickname = "资料顾客" + suffix();
        String phone = "139" + randomDigits(8);
        String avatarUrl = "/files/avatar/" + suffix() + ".png";

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("nickname", nickname);
        body.put("phone", phone);
        body.put("avatarUrl", avatarUrl);
        ResponseEntity<String> updated = putJson("/api/v1/auth/profile", body, token);
        assertThat(updated.getStatusCode().value()).isEqualTo(200);
        assertThat(codeOf(updated)).isZero();
        assertThat((String) JsonPath.read(updated.getBody(), "$.data.nickname")).isEqualTo(nickname);
        assertThat((String) JsonPath.read(updated.getBody(), "$.data.phone")).isEqualTo(phone);
        assertThat((String) JsonPath.read(updated.getBody(), "$.data.avatarUrl")).isEqualTo(avatarUrl);

        ResponseEntity<String> me = get("/api/v1/auth/me", token);
        assertThat(me.getStatusCode().value()).isEqualTo(200);
        assertThat((String) JsonPath.read(me.getBody(), "$.data.nickname")).isEqualTo(nickname);
        assertThat((String) JsonPath.read(me.getBody(), "$.data.phone")).isEqualTo(phone);
        assertThat((String) JsonPath.read(me.getBody(), "$.data.avatarUrl")).isEqualTo(avatarUrl);

        ResponseEntity<String> cleared = putJson("/api/v1/auth/profile", Map.of("phone", ""), token);
        assertThat(codeOf(cleared)).isZero();
        Object clearedPhone = JsonPath.read(cleared.getBody(), "$.data.phone");
        assertThat(clearedPhone).isNull();
        ResponseEntity<String> meAfterClear = get("/api/v1/auth/me", token);
        assertThat((Object) JsonPath.read(meAfterClear.getBody(), "$.data.phone")).isNull();
    }

    @Test
    void profileValidationRejectsBadInputsAndRequiresLogin() throws Exception {
        String token = register("资料顾客校验");

        ResponseEntity<String> badPhone = putJson("/api/v1/auth/profile", Map.of("phone", "12345"), token);
        assertThat(badPhone.getStatusCode().value()).isEqualTo(400);
        assertThat(codeOf(badPhone)).isEqualTo(40001);

        ResponseEntity<String> badAvatar = putJson("/api/v1/auth/profile",
                Map.of("avatarUrl", "http://example.com/a.png"), token);
        assertThat(badAvatar.getStatusCode().value()).isEqualTo(400);
        assertThat(codeOf(badAvatar)).isEqualTo(40001);

        ResponseEntity<String> longNickname = putJson("/api/v1/auth/profile",
                Map.of("nickname", "昵称".repeat(11)), token);
        assertThat(longNickname.getStatusCode().value()).isEqualTo(400);
        assertThat(codeOf(longNickname)).isEqualTo(40001);

        ResponseEntity<String> unauthenticated = putJson("/api/v1/auth/profile",
                Map.of("nickname", "未登录"), null);
        assertThat(unauthenticated.getStatusCode().value()).isEqualTo(401);
        assertThat(codeOf(unauthenticated)).isEqualTo(40101);

        String merchantToken = login("merchant", "merchant123");
        String merchantNickname = "资料商家" + suffix();
        ResponseEntity<String> merchantUpdated = putJson("/api/v1/auth/profile",
                Map.of("nickname", merchantNickname), merchantToken);
        assertThat(merchantUpdated.getStatusCode().value()).isEqualTo(200);
        assertThat((String) JsonPath.read(merchantUpdated.getBody(), "$.data.nickname"))
                .isEqualTo(merchantNickname);
    }

    /**
     * 自注册独立顾客，避免修改共享种子数据（H-2：测试可重复，不依赖/污染种子用户）。
     */
    private String register(String nickname) throws Exception {
        ResponseEntity<String> response = postJson("/api/v1/auth/register",
                Map.of("username", "u" + suffix(), "password", "pass1234", "nickname", nickname), null);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        return JsonPath.read(response.getBody(), "$.data.token");
    }

    private String login(String username, String password) throws Exception {
        ResponseEntity<String> response = postJson("/api/v1/auth/login",
                Map.of("username", username, "password", password), null);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        return JsonPath.read(response.getBody(), "$.data.token");
    }

    private ResponseEntity<String> postJson(String path, Object body, String token) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return restTemplate.exchange(path, HttpMethod.POST,
                new HttpEntity<>(objectMapper.writeValueAsString(body), headers), String.class);
    }

    private ResponseEntity<String> putJson(String path, Object body, String token) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return restTemplate.exchange(path, HttpMethod.PUT,
                new HttpEntity<>(objectMapper.writeValueAsString(body), headers), String.class);
    }

    private ResponseEntity<String> get(String path, String token) {
        HttpHeaders headers = new HttpHeaders();
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return restTemplate.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    private int codeOf(ResponseEntity<String> response) {
        try {
            Number code = JsonPath.read(response.getBody(), "$.code");
            return code == null ? -1 : code.intValue();
        } catch (RuntimeException e) {
            return -1;
        }
    }

    private static String suffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 6);
    }

    private static String randomDigits(int length) {
        StringBuilder builder = new StringBuilder();
        String seed = String.valueOf(Math.abs(System.nanoTime()));
        for (int i = 0; i < length; i++) {
            builder.append(seed.charAt(i % seed.length()));
        }
        return builder.toString();
    }
}
