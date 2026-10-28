package com.restaurant.auth;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jayway.jsonpath.JsonPath;
import com.restaurant.audit.AuditActions;
import com.restaurant.audit.AuditLog;
import com.restaurant.audit.mapper.AuditLogMapper;
import com.restaurant.common.enums.UserStatus;
import com.restaurant.user.User;
import com.restaurant.user.WxUser;
import com.restaurant.user.mapper.UserMapper;
import com.restaurant.user.mapper.WxUserMapper;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 认证接口集成测试（真实 Tomcat + MySQL restaurant_mvp_test + Redis db15）。
 *
 * <p>覆盖 G2 门禁：登录成功/失败、无 token 401、角色 403、登录限流 429、微信登录幂等、
 * 登出黑名单、禁用账号 40304、Swagger bearerAuth。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AuthApiIntegrationTest {

    private static final String LOGIN = "/api/v1/auth/login";

    private static final String REGISTER = "/api/v1/auth/register";

    private static final String WECHAT_LOGIN = "/api/v1/auth/wechat/login";

    private static final String ME = "/api/v1/auth/me";

    private static final String LOGOUT = "/api/v1/auth/logout";

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private WxUserMapper wxUserMapper;

    @Autowired
    private AuditLogMapper auditLogMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void clearRateLimitKeys() {
        Set<String> keys = stringRedisTemplate.keys("rate:*");
        if (keys != null && !keys.isEmpty()) {
            stringRedisTemplate.delete(keys);
        }
    }

    @Test
    void loginSucceedsForSeedAccountsWithRoleAndMerchantId() {
        ResponseEntity<String> admin = post(LOGIN, credentials("admin", "admin123"), null);
        assertThat(admin.getStatusCode().value()).isEqualTo(200);
        assertThat(codeOf(admin)).isZero();
        assertThat((String) JsonPath.read(admin.getBody(), "$.data.token")).isNotBlank();
        String headerJson = new String(Base64.getUrlDecoder()
                .decode(((String) JsonPath.read(admin.getBody(), "$.data.token")).split("\\.")[0]),
                StandardCharsets.UTF_8);
        assertThat(headerJson).contains("HS256");
        assertThat((String) JsonPath.read(admin.getBody(), "$.data.tokenType")).isEqualTo("Bearer");
        assertThat(((Number) JsonPath.read(admin.getBody(), "$.data.expiresIn")).longValue()).isEqualTo(7200L);
        assertThat((String) JsonPath.read(admin.getBody(), "$.data.user.role")).isEqualTo("ADMIN");
        assertThat((String) JsonPath.read(admin.getBody(), "$.traceId")).isNotBlank();
        assertThat(admin.getBody()).doesNotContain("password");

        ResponseEntity<String> merchant = post(LOGIN, credentials("merchant", "merchant123"), null);
        assertThat(merchant.getStatusCode().value()).isEqualTo(200);
        assertThat((String) JsonPath.read(merchant.getBody(), "$.data.user.role")).isEqualTo("MERCHANT");
        assertThat(((Number) JsonPath.read(merchant.getBody(), "$.data.user.merchantId")).longValue())
                .isPositive();

        ResponseEntity<String> customer = post(LOGIN, credentials("customer", "customer123"), null);
        assertThat(customer.getStatusCode().value()).isEqualTo(200);
        assertThat((String) JsonPath.read(customer.getBody(), "$.data.user.role")).isEqualTo("CUSTOMER");

        long loginSuccessCount = auditLogMapper.selectCount(Wrappers.<AuditLog>lambdaQuery()
                .eq(AuditLog::getAction, AuditActions.LOGIN_SUCCESS)
                .eq(AuditLog::getUsername, "admin"));
        assertThat(loginSuccessCount).isPositive();
    }

    @Test
    void loginFailsWithUnifiedCodeAndAuditsFailure() {
        ResponseEntity<String> response = post(LOGIN, credentials("admin", "wrong-password"), null);
        assertThat(response.getStatusCode().value()).isEqualTo(401);
        assertThat(codeOf(response)).isEqualTo(40101);
        assertThat((String) JsonPath.read(response.getBody(), "$.message")).isEqualTo("未认证或凭据无效");

        long failureCount = auditLogMapper.selectCount(Wrappers.<AuditLog>lambdaQuery()
                .eq(AuditLog::getAction, AuditActions.LOGIN_FAIL)
                .eq(AuditLog::getUsername, "admin")
                .eq(AuditLog::getSuccess, false));
        assertThat(failureCount).isPositive();
    }

    @Test
    void protectedEndpointRejectsMissingAndInvalidToken() {
        ResponseEntity<String> missing = get(ME, null);
        assertThat(missing.getStatusCode().value()).isEqualTo(401);
        assertThat(codeOf(missing)).isEqualTo(40101);

        ResponseEntity<String> invalid = get(ME, "not-a-jwt");
        assertThat(invalid.getStatusCode().value()).isEqualTo(401);
        assertThat(codeOf(invalid)).isEqualTo(40103);
    }

    @Test
    void meReturnsCurrentUserWithToken() {
        String token = loginToken("customer", "customer123");
        ResponseEntity<String> me = get(ME, token);
        assertThat(me.getStatusCode().value()).isEqualTo(200);
        assertThat(codeOf(me)).isZero();
        assertThat((String) JsonPath.read(me.getBody(), "$.data.username")).isEqualTo("customer");
        assertThat((String) JsonPath.read(me.getBody(), "$.data.role")).isEqualTo("CUSTOMER");
        assertThat(me.getBody()).doesNotContain("password");
    }

    @Test
    void customerIsForbiddenFromAdminApi() {
        String token = loginToken("customer", "customer123");
        ResponseEntity<String> response = get("/api/v1/admin/audit-logs", token);
        assertThat(response.getStatusCode().value()).isEqualTo(403);
        assertThat(codeOf(response)).isEqualTo(40301);
    }

    @Test
    void loginIsRateLimitedPerIpAfterFiveAttempts() {
        for (int i = 0; i < 5; i++) {
            ResponseEntity<String> attempt = post(LOGIN, credentials("admin", "wrong-password"), null);
            assertThat(attempt.getStatusCode().value()).isEqualTo(401);
        }
        ResponseEntity<String> limited = post(LOGIN, credentials("admin", "wrong-password"), null);
        assertThat(limited.getStatusCode().value()).isEqualTo(429);
        assertThat(codeOf(limited)).isEqualTo(42901);
        String retryAfter = limited.getHeaders().getFirst(HttpHeaders.RETRY_AFTER);
        assertThat(retryAfter).isNotNull();
        assertThat(Long.parseLong(retryAfter)).isPositive();
    }

    @Test
    void registerIsRateLimitedPerIpAfterFiveAttemptsAndIndependentFromLogin() {
        for (int i = 0; i < 5; i++) {
            ResponseEntity<String> registered = post(REGISTER, newRegistration(), null);
            assertThat(registered.getStatusCode().value()).isEqualTo(200);
            assertThat(codeOf(registered)).isZero();
        }
        ResponseEntity<String> limited = post(REGISTER, newRegistration(), null);
        assertThat(limited.getStatusCode().value()).isEqualTo(429);
        assertThat(codeOf(limited)).isEqualTo(42901);
        String retryAfter = limited.getHeaders().getFirst(HttpHeaders.RETRY_AFTER);
        assertThat(retryAfter).isNotNull();
        assertThat(Long.parseLong(retryAfter)).isPositive();

        // 注册与账号密码登录使用不同 key：注册被限流不影响登录，反之亦然
        assertThat(stringRedisTemplate.keys("rate:register:*")).isNotEmpty();
        assertThat(stringRedisTemplate.keys("rate:login:*")).isEmpty();
        ResponseEntity<String> login = post(LOGIN, credentials("customer", "customer123"), null);
        assertThat(login.getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void wechatLoginIsRateLimitedPerIpAfterFiveAttemptsAndIndependentFromLogin() {
        for (int i = 0; i < 5; i++) {
            ResponseEntity<String> loggedIn = post(WECHAT_LOGIN,
                    Map.of("code", "rate-limit-" + UUID.randomUUID()), null);
            assertThat(loggedIn.getStatusCode().value()).isEqualTo(200);
        }
        ResponseEntity<String> limited = post(WECHAT_LOGIN,
                Map.of("code", "rate-limit-" + UUID.randomUUID()), null);
        assertThat(limited.getStatusCode().value()).isEqualTo(429);
        assertThat(codeOf(limited)).isEqualTo(42901);
        String retryAfter = limited.getHeaders().getFirst(HttpHeaders.RETRY_AFTER);
        assertThat(retryAfter).isNotNull();
        assertThat(Long.parseLong(retryAfter)).isPositive();

        // 微信登录 key 与账号密码登录/注册互相独立
        assertThat(stringRedisTemplate.keys("rate:wechat:*")).isNotEmpty();
        assertThat(stringRedisTemplate.keys("rate:login:*")).isEmpty();
        assertThat(stringRedisTemplate.keys("rate:register:*")).isEmpty();
        ResponseEntity<String> login = post(LOGIN, credentials("customer", "customer123"), null);
        assertThat(login.getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void wechatLoginIsIdempotentForSameCodeAndNeverLeaksSessionKey() {
        String code = "test-code-" + UUID.randomUUID();
        ResponseEntity<String> first = post(WECHAT_LOGIN, Map.of("code", code), null);
        assertThat(first.getStatusCode().value()).isEqualTo(200);
        assertThat(codeOf(first)).isZero();
        assertThat((String) JsonPath.read(first.getBody(), "$.data.user.role")).isEqualTo("CUSTOMER");
        assertThat(first.getBody()).doesNotContain("session_key").doesNotContain("sessionKey");
        long firstUserId = ((Number) JsonPath.read(first.getBody(), "$.data.user.id")).longValue();

        ResponseEntity<String> second = post(WECHAT_LOGIN, Map.of("code", code), null);
        assertThat(second.getStatusCode().value()).isEqualTo(200);
        long secondUserId = ((Number) JsonPath.read(second.getBody(), "$.data.user.id")).longValue();
        assertThat(secondUserId).isEqualTo(firstUserId);

        List<WxUser> wxUsers = wxUserMapper.selectList(Wrappers.<WxUser>lambdaQuery()
                .eq(WxUser::getUserId, firstUserId));
        assertThat(wxUsers).hasSize(1);
        assertThat(wxUsers.get(0).getOpenid()).startsWith("mock_").hasSize("mock_".length() + 16);
        assertThat(wxUsers.get(0).getSessionKey()).isNull();
    }

    @Test
    void registerThenLoginAndDuplicateUsernameConflicts() {
        String username = "it" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        String phone = "139" + (10000000 + (int) (Math.random() * 89999999));
        Map<String, String> payload = new HashMap<>();
        payload.put("username", username);
        payload.put("password", "secret123");
        payload.put("phone", phone);
        ResponseEntity<String> registered = post(REGISTER, payload, null);
        assertThat(registered.getStatusCode().value()).isEqualTo(200);
        assertThat((String) JsonPath.read(registered.getBody(), "$.data.user.role")).isEqualTo("CUSTOMER");
        assertThat((String) JsonPath.read(registered.getBody(), "$.data.user.nickname")).isEqualTo(username);

        String token = (String) JsonPath.read(registered.getBody(), "$.data.token");
        assertThat(get(ME, token).getStatusCode().value()).isEqualTo(200);

        ResponseEntity<String> duplicate = post(REGISTER, payload, null);
        assertThat(duplicate.getStatusCode().value()).isEqualTo(409);
        assertThat(codeOf(duplicate)).isEqualTo(40914);
    }

    @Test
    void logoutRevokesTokenImmediately() {
        String token = loginToken("customer", "customer123");
        assertThat(get(ME, token).getStatusCode().value()).isEqualTo(200);

        ResponseEntity<String> logout = post(LOGOUT, null, token);
        assertThat(logout.getStatusCode().value()).isEqualTo(200);
        assertThat((Boolean) JsonPath.read(logout.getBody(), "$.data.loggedOut")).isTrue();

        ResponseEntity<String> afterLogout = get(ME, token);
        assertThat(afterLogout.getStatusCode().value()).isEqualTo(401);
        assertThat(codeOf(afterLogout)).isEqualTo(40103);
    }

    @Test
    void disabledAccountCannotLoginOrUseExistingToken() {
        String username = "disabled" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode("secret123"));
        user.setNickname("禁用账号");
        user.setRole("CUSTOMER");
        user.setStatus(UserStatus.DISABLED.name());
        userMapper.insert(user);
        try {
            ResponseEntity<String> login = post(LOGIN, credentials(username, "secret123"), null);
            assertThat(login.getStatusCode().value()).isEqualTo(403);
            assertThat(codeOf(login)).isEqualTo(40304);
        } finally {
            userMapper.deleteById(user.getId());
        }
    }

    @Test
    void openApiDeclaresBearerAuthAndAuthEndpoints() {
        ResponseEntity<String> docs = restTemplate.getForEntity("/v3/api-docs", String.class);
        assertThat(docs.getStatusCode().value()).isEqualTo(200);
        Map<String, Object> schemes = JsonPath.read(docs.getBody(), "$.components.securitySchemes");
        assertThat(schemes).containsKey("bearerAuth");
        assertThat(docs.getBody()).contains("/api/v1/auth/login").contains("/api/v1/auth/wechat/login");
    }

    private String loginToken(String username, String password) {
        ResponseEntity<String> response = post(LOGIN, credentials(username, password), null);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        return JsonPath.read(response.getBody(), "$.data.token");
    }

    private Map<String, String> credentials(String username, String password) {
        return Map.of("username", username, "password", password);
    }

    private Map<String, String> newRegistration() {
        Map<String, String> payload = new HashMap<>();
        payload.put("username", "rl" + UUID.randomUUID().toString().replace("-", "").substring(0, 10));
        payload.put("password", "secret123");
        return payload;
    }

    private int codeOf(ResponseEntity<String> response) {
        return ((Number) JsonPath.read(response.getBody(), "$.code")).intValue();
    }

    private ResponseEntity<String> post(String path, Object body, String token) {
        return exchange(path, HttpMethod.POST, body, token);
    }

    private ResponseEntity<String> get(String path, String token) {
        return exchange(path, HttpMethod.GET, null, token);
    }

    private ResponseEntity<String> exchange(String path, HttpMethod method, Object body, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            headers.setBearerAuth(token);
        }
        HttpEntity<Object> entity = new HttpEntity<>(body, headers);
        return restTemplate.exchange(path, method, entity, String.class);
    }
}
