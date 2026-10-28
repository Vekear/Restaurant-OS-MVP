package com.restaurant.table;

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

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 商家桌台生命周期集成测试（真实 Tomcat + MySQL restaurant_mvp_test + Redis db15）。
 *
 * <p>覆盖 docs/02-api.md 端点 #72 详情、#73 更新（改桌号自动重签）、#74 删除、#76 重签二维码：
 * 旧二维码立即失效、有活动会话时禁止删除（40909）、删除后详情 404、参数校验与角色守卫。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class MerchantTableLifecycleApiIntegrationTest {

    private static final String TABLES = "/api/v1/merchant/tables";

    private static final String DINE_IN = "/api/v1/dine-in";

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void clearRateLimitKeys() throws Exception {
        Set<String> keys = stringRedisTemplate.keys("rate:*");
        if (keys != null && !keys.isEmpty()) {
            stringRedisTemplate.delete(keys);
        }
        cancelCustomerActiveDineInOrders();
    }

    private void cancelCustomerActiveDineInOrders() {
        try {
            String token = login("customer", "customer123");
            ResponseEntity<String> list = get("/api/v1/dine-in/orders?statusGroup=ACTIVE&page=1&size=50", token);
            List<Number> ids = JsonPath.read(list.getBody(), "$.data.list[*].id");
            for (Number id : ids) {
                postJson("/api/v1/dine-in/orders/" + id.longValue() + "/cancel",
                        Map.of("reason", "E2E 清理"), token, null, Map.of());
            }
        } catch (Exception ignored) {
            // best-effort 清理
        }
    }

    @Test
    void updateTableNoRotatesQrAndDetailReflectsChanges() throws Exception {
        String merchantToken = login("merchant", "merchant123");
        CreatedTable table = createTable(merchantToken, "更新桌");
        assertThat(table.qrVersion()).isEqualTo(1);

        ResponseEntity<String> renamed = putJson(TABLES + "/" + table.id(),
                Map.of("name", "窗边桌", "capacity", 2, "zone", "A 区"), merchantToken);
        assertThat(renamed.getStatusCode().value()).isEqualTo(200);
        assertThat(codeOf(renamed)).isZero();
        assertThat((String) JsonPath.read(renamed.getBody(), "$.data.name")).isEqualTo("窗边桌");
        assertThat(((Number) JsonPath.read(renamed.getBody(), "$.data.capacity")).intValue()).isEqualTo(2);
        assertThat(((Number) JsonPath.read(renamed.getBody(), "$.data.qrVersion")).intValue())
                .as("仅改名不重签二维码").isEqualTo(1);

        String newTableNo = "C" + suffix();
        ResponseEntity<String> moved = putJson(TABLES + "/" + table.id(),
                Map.of("tableNo", newTableNo), merchantToken);
        assertThat(moved.getStatusCode().value()).isEqualTo(200);
        assertThat((String) JsonPath.read(moved.getBody(), "$.data.tableNo")).isEqualTo(newTableNo);
        assertThat(((Number) JsonPath.read(moved.getBody(), "$.data.qrVersion")).intValue())
                .as("改桌号自动重签，qrVersion 递增").isEqualTo(2);
        String newQrUrl = JsonPath.read(moved.getBody(), "$.data.qrUrl");
        assertThat(newQrUrl).isNotEqualTo(table.qrUrl());

        ResponseEntity<String> detail = get(TABLES + "/" + table.id(), merchantToken);
        assertThat(detail.getStatusCode().value()).isEqualTo(200);
        assertThat((String) JsonPath.read(detail.getBody(), "$.data.tableNo")).isEqualTo(newTableNo);
        assertThat(((Number) JsonPath.read(detail.getBody(), "$.data.qrVersion")).intValue()).isEqualTo(2);

        // 旧二维码签名已失效
        String customerToken = login("customer", "customer123");
        Map<String, String> oldParams = qrParams(table.qrUrl());
        ResponseEntity<String> oldCode = postJson(DINE_IN + "/scan",
                scanBody(table.id(), oldParams.get("s"), Long.parseLong(oldParams.get("t"))), customerToken,
                null, Map.of());
        assertThat(oldCode.getStatusCode().value()).isEqualTo(400);
        assertThat(codeOf(oldCode)).isEqualTo(40004);

        deleteTable(merchantToken, table.id());
    }

    @Test
    void regenerateQrInvalidatesOldCodeAndDeleteRejectsActiveSession() throws Exception {
        String merchantToken = login("merchant", "merchant123");
        CreatedTable table = createTable(merchantToken, "重签桌");
        String customerToken = login("customer", "customer123");
        Map<String, String> firstParams = qrParams(table.qrUrl());

        ResponseEntity<String> regenerated = postJson(TABLES + "/" + table.id() + "/qr", null, merchantToken,
                null, Map.of());
        assertThat(regenerated.getStatusCode().value()).isEqualTo(200);
        assertThat(codeOf(regenerated)).isZero();
        assertThat(((Number) JsonPath.read(regenerated.getBody(), "$.data.qrVersion")).intValue()).isEqualTo(2);
        String newQrUrl = JsonPath.read(regenerated.getBody(), "$.data.qrContent");

        ResponseEntity<String> oldCode = postJson(DINE_IN + "/scan",
                scanBody(table.id(), firstParams.get("s"), Long.parseLong(firstParams.get("t"))), customerToken,
                null, Map.of());
        assertThat(oldCode.getStatusCode().value()).isEqualTo(400);
        assertThat(codeOf(oldCode)).isEqualTo(40004);

        Map<String, String> newParams = qrParams(newQrUrl);
        ResponseEntity<String> newCode = postJson(DINE_IN + "/scan",
                scanBody(table.id(), newParams.get("s"), Long.parseLong(newParams.get("t"))), customerToken,
                null, Map.of());
        assertThat(codeOf(newCode)).as("new qr=%s body=%s", newQrUrl, newCode.getBody()).isZero();
        String sessionToken = JsonPath.read(newCode.getBody(), "$.data.sessionToken");

        ResponseEntity<String> deleteWithSession = deleteJson(TABLES + "/" + table.id(), merchantToken);
        assertThat(deleteWithSession.getStatusCode().value()).isEqualTo(409);
        assertThat(codeOf(deleteWithSession)).isEqualTo(40909);

        ResponseEntity<String> closed = postJson(DINE_IN + "/session/close", null, customerToken, null,
                Map.of("X-Table-Session", sessionToken));
        assertThat(codeOf(closed)).isZero();
        deleteTable(merchantToken, table.id());

        ResponseEntity<String> gone = get(TABLES + "/" + table.id(), merchantToken);
        assertThat(gone.getStatusCode().value()).isEqualTo(404);
        assertThat(codeOf(gone)).isEqualTo(40401);
    }

    @Test
    void tableValidationAndRoleGuards() throws Exception {
        String merchantToken = login("merchant", "merchant123");
        CreatedTable table = createTable(merchantToken, "校验桌");

        ResponseEntity<String> badCapacity = putJson(TABLES + "/" + table.id(),
                Map.of("capacity", 21), merchantToken);
        assertThat(badCapacity.getStatusCode().value()).isEqualTo(400);
        assertThat(codeOf(badCapacity)).isEqualTo(40001);

        ResponseEntity<String> blankNo = putJson(TABLES + "/" + table.id(),
                Map.of("tableNo", ""), merchantToken);
        assertThat(blankNo.getStatusCode().value()).isEqualTo(400);
        assertThat(codeOf(blankNo)).isEqualTo(40001);

        String customerToken = login("customer", "customer123");
        ResponseEntity<String> customerCreate = postJson(TABLES, Map.of("tableNo", "X" + suffix()),
                customerToken, null, Map.of());
        assertThat(customerCreate.getStatusCode().value()).isEqualTo(403);
        assertThat(codeOf(customerCreate)).isEqualTo(40301);

        ResponseEntity<String> customerUpdate = putJson(TABLES + "/" + table.id(),
                Map.of("name", "越权改名"), customerToken);
        assertThat(customerUpdate.getStatusCode().value()).isEqualTo(403);
        assertThat(codeOf(customerUpdate)).isEqualTo(40301);

        ResponseEntity<String> customerDelete = deleteJson(TABLES + "/" + table.id(), customerToken);
        assertThat(customerDelete.getStatusCode().value()).isEqualTo(403);
        assertThat(codeOf(customerDelete)).isEqualTo(40301);

        deleteTable(merchantToken, table.id());
    }

    private CreatedTable createTable(String merchantToken, String name) throws Exception {
        String tableNo = "A" + suffix();
        ResponseEntity<String> response = postJson(TABLES, Map.of("tableNo", tableNo, "name", name, "capacity", 4),
                merchantToken, null, Map.of());
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(codeOf(response)).isZero();
        long id = ((Number) JsonPath.read(response.getBody(), "$.data.id")).longValue();
        int qrVersion = ((Number) JsonPath.read(response.getBody(), "$.data.qrVersion")).intValue();
        String qrUrl = JsonPath.read(response.getBody(), "$.data.qrUrl");
        return new CreatedTable(id, tableNo, qrVersion, qrUrl);
    }

    private void deleteTable(String merchantToken, long tableId) {
        ResponseEntity<String> response = deleteJson(TABLES + "/" + tableId, merchantToken);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(codeOf(response)).isZero();
        assertThat((Boolean) JsonPath.read(response.getBody(), "$.data.deleted")).isTrue();
    }

    private String login(String username, String password) throws Exception {
        ResponseEntity<String> response = postJson("/api/v1/auth/login",
                Map.of("username", username, "password", password), null, null, Map.of());
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        return JsonPath.read(response.getBody(), "$.data.token");
    }

    private ResponseEntity<String> postJson(String path, Object body, String token, String idempotencyKey,
                                            Map<String, String> extraHeaders) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            headers.setBearerAuth(token);
        }
        if (idempotencyKey != null) {
            headers.set("Idempotency-Key", idempotencyKey);
        }
        extraHeaders.forEach(headers::set);
        String payload = body == null ? "{}" : objectMapper.writeValueAsString(body);
        return restTemplate.exchange(path, HttpMethod.POST, new HttpEntity<>(payload, headers), String.class);
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

    private ResponseEntity<String> deleteJson(String path, String token) {
        HttpHeaders headers = new HttpHeaders();
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return restTemplate.exchange(path, HttpMethod.DELETE, new HttpEntity<>(headers), String.class);
    }

    private ResponseEntity<String> get(String path, String token) {
        HttpHeaders headers = new HttpHeaders();
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return restTemplate.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    private Map<String, Object> scanBody(long tableId, String signature, long timestamp) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("p", String.valueOf(tableId));
        body.put("s", signature);
        body.put("t", timestamp);
        return body;
    }

    private Map<String, String> qrParams(String qrContent) {
        Map<String, String> params = new HashMap<>();
        String query = qrContent.substring(qrContent.indexOf('?') + 1);
        for (String pair : query.split("&")) {
            int index = pair.indexOf('=');
            params.put(pair.substring(0, index), URLDecoder.decode(pair.substring(index + 1),
                    StandardCharsets.UTF_8));
        }
        return params;
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

    private record CreatedTable(long id, String tableNo, int qrVersion, String qrUrl) {
    }
}
