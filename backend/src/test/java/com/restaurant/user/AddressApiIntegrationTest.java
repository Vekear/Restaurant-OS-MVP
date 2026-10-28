package com.restaurant.user;

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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 收货地址接口集成测试：CRUD、默认地址唯一、逻辑删除、越权与数量上限。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AddressApiIntegrationTest {

    private static final String ADDRESSES = "/api/v1/addresses";

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void clearRateLimitKeys() {
        Set<String> keys = stringRedisTemplate.keys("rate:*");
        if (keys != null && !keys.isEmpty()) {
            stringRedisTemplate.delete(keys);
        }
    }

    @Test
    void addressCrudKeepsSingleDefaultAndDeletesLogically() throws Exception {
        String token = login("customer", "customer123");

        ResponseEntity<String> seedList = get(ADDRESSES, token);
        assertThat(seedList.getStatusCode().value()).isEqualTo(200);
        assertThat(codeOf(seedList)).isZero();
        List<Object> seed = JsonPath.read(seedList.getBody(), "$.data");
        assertThat(seed).isNotEmpty();

        long firstId = createAddress(token, "张三" + suffix(), false);
        long secondId = createAddress(token, "李四" + suffix(), true);

        ResponseEntity<String> list = get(ADDRESSES, token);
        List<Object> addresses = JsonPath.read(list.getBody(), "$.data");
        assertThat(addresses).hasSizeGreaterThanOrEqualTo(2);
        Number topId = JsonPath.read(list.getBody(), "$.data[0].id");
        assertThat(topId.longValue()).isEqualTo(secondId);
        assertThat((Boolean) JsonPath.read(list.getBody(), "$.data[0].isDefault")).isTrue();

        Map<String, Object> update = addressBody("王五" + suffix(), false);
        update.put("detail", "四川省成都市高新区天府三街 100 号 5 栋 502 室");
        ResponseEntity<String> updated = exchange(ADDRESSES + "/" + secondId, HttpMethod.PUT, update, token);
        assertThat(updated.getStatusCode().value()).isEqualTo(200);
        assertThat((Boolean) JsonPath.read(updated.getBody(), "$.data.isDefault")).isFalse();
        assertThat((String) JsonPath.read(updated.getBody(), "$.data.detail"))
                .isEqualTo("四川省成都市高新区天府三街 100 号 5 栋 502 室");

        ResponseEntity<String> deleted = exchange(ADDRESSES + "/" + secondId, HttpMethod.DELETE, null, token);
        assertThat(deleted.getStatusCode().value()).isEqualTo(200);
        assertThat((Boolean) JsonPath.read(deleted.getBody(), "$.data.deleted")).isTrue();
        ResponseEntity<String> detailAfterDelete = get(ADDRESSES + "/" + secondId, token);
        assertThat(detailAfterDelete.getStatusCode().value()).isEqualTo(404);
        assertThat(codeOf(detailAfterDelete)).isEqualTo(40401);

        exchange(ADDRESSES + "/" + firstId, HttpMethod.DELETE, null, token);
    }

    @Test
    void addressOwnershipValidationAndMaxCountAreEnforced() throws Exception {
        String token = login("customer", "customer123");
        String otherToken = register();

        long addressId = createAddress(token, "赵六" + suffix(), false);

        ResponseEntity<String> foreignGet = get(ADDRESSES + "/" + addressId, otherToken);
        assertThat(foreignGet.getStatusCode().value()).isEqualTo(403);
        assertThat(codeOf(foreignGet)).isEqualTo(40302);

        Map<String, Object> update = addressBody("越权" + suffix(), false);
        ResponseEntity<String> foreignUpdate = exchange(ADDRESSES + "/" + addressId, HttpMethod.PUT, update, otherToken);
        assertThat(foreignUpdate.getStatusCode().value()).isEqualTo(403);
        assertThat(codeOf(foreignUpdate)).isEqualTo(40302);

        ResponseEntity<String> foreignDelete = exchange(ADDRESSES + "/" + addressId, HttpMethod.DELETE, null, otherToken);
        assertThat(foreignDelete.getStatusCode().value()).isEqualTo(403);
        assertThat(codeOf(foreignDelete)).isEqualTo(40302);

        ResponseEntity<String> missing = get(ADDRESSES + "/999999999", token);
        assertThat(missing.getStatusCode().value()).isEqualTo(404);
        assertThat(codeOf(missing)).isEqualTo(40401);

        Map<String, Object> invalid = addressBody("格式错误" + suffix(), false);
        invalid.put("phone", "12345");
        ResponseEntity<String> badPhone = exchange(ADDRESSES, HttpMethod.POST, invalid, token);
        assertThat(badPhone.getStatusCode().value()).isEqualTo(400);
        assertThat(codeOf(badPhone)).isEqualTo(40001);
        assertThat((List<Object>) JsonPath.read(badPhone.getBody(), "$.data.errors")).isNotEmpty();

        exchange(ADDRESSES + "/" + addressId, HttpMethod.DELETE, null, token);

        // 单用户 <=20 条（40903）
        String limitToken = register();
        for (int i = 0; i < 20; i++) {
            createAddress(limitToken, "上限" + i + "-" + suffix(), false);
        }
        ResponseEntity<String> overLimit = exchange(ADDRESSES, HttpMethod.POST,
                addressBody("超限" + suffix(), false), limitToken);
        assertThat(overLimit.getStatusCode().value()).isEqualTo(409);
        assertThat(codeOf(overLimit)).isEqualTo(40903);

        ResponseEntity<String> all = get(ADDRESSES, limitToken);
        List<Map<String, Object>> created = JsonPath.read(all.getBody(), "$.data");
        for (Map<String, Object> item : created) {
            Number id = (Number) item.get("id");
            exchange(ADDRESSES + "/" + id.longValue(), HttpMethod.DELETE, null, limitToken);
        }
    }

    private long createAddress(String token, String consignee, boolean isDefault) throws Exception {
        ResponseEntity<String> response = exchange(ADDRESSES, HttpMethod.POST,
                addressBody(consignee, isDefault), token);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(codeOf(response)).isZero();
        return ((Number) JsonPath.read(response.getBody(), "$.data.id")).longValue();
    }

    private Map<String, Object> addressBody(String consignee, boolean isDefault) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("consignee", consignee);
        body.put("phone", "13900000003");
        body.put("province", "四川省");
        body.put("city", "成都市");
        body.put("district", "武侯区");
        body.put("detail", "天府大道北段 888 号 1 栋 101 室");
        body.put("isDefault", isDefault);
        return body;
    }

    private String register() throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("username", "addr" + suffix());
        body.put("password", "pass1234");
        ResponseEntity<String> response = exchange("/api/v1/auth/register", HttpMethod.POST, body, null);
        assertThat(codeOf(response)).isZero();
        return JsonPath.read(response.getBody(), "$.data.token");
    }

    private String login(String username, String password) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("username", username);
        body.put("password", password);
        ResponseEntity<String> response = exchange("/api/v1/auth/login", HttpMethod.POST, body, null);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        return JsonPath.read(response.getBody(), "$.data.token");
    }

    private ResponseEntity<String> get(String path, String token) {
        return restTemplate.exchange(path, HttpMethod.GET,
                new HttpEntity<>(jsonHeaders(token)), String.class);
    }

    private ResponseEntity<String> exchange(String path, HttpMethod method, Object body, String token) throws Exception {
        String payload = body == null ? null : objectMapper.writeValueAsString(body);
        return restTemplate.exchange(path, method, new HttpEntity<>(payload, jsonHeaders(token)), String.class);
    }

    private HttpHeaders jsonHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return headers;
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
}
