package com.restaurant.dinein;

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

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 顾客堂食订单集成测试（真实 Tomcat + MySQL restaurant_mvp_test + Redis db15）。
 *
 * <p>覆盖 docs/02-api.md 端点 #39 我的堂食订单分页、#40 堂食订单详情、#41 取消堂食订单并释放桌台；
 * 同时校验取消后库存回补、其他顾客不可见、商家 token 被角色守卫拒绝。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class DineInCustomerOrderApiIntegrationTest {

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

    /** 清理种子顾客历史遗留的进行中堂食订单（否则 40917 会阻止新开桌）。 */
    private void cancelCustomerActiveDineInOrders() {
        try {
            String token = login("customer", "customer123");
            ResponseEntity<String> list = get(DINE_IN + "/orders?statusGroup=ACTIVE&page=1&size=50", token);
            List<Number> ids = JsonPath.read(list.getBody(), "$.data.list[*].id");
            for (Number id : ids) {
                postJson(DINE_IN + "/orders/" + id.longValue() + "/cancel", Map.of("reason", "E2E 清理"),
                        token, null, Map.of());
            }
        } catch (Exception ignored) {
            // 清理为 best-effort，断言失败由用例自身暴露
        }
    }

    @Test
    void customerDineInOrderListDetailAndCancelRestoresStockAndTable() throws Exception {
        String merchantToken = login("merchant", "merchant123");
        CreatedTable table = createTable(merchantToken);
        String customerToken = login("customer", "customer123");
        String sessionToken = bind(table, customerToken);
        long dishId = createDish(merchantToken, "堂食顾客列表菜" + suffix(), "24.00", 5);

        ResponseEntity<String> created = postJson(DINE_IN + "/orders", dineInOrderBody(dishId, 2, "堂食列表链路"),
                customerToken, "dinein-list-" + suffix(), Map.of("X-Table-Session", sessionToken));
        assertThat(created.getStatusCode().value()).isEqualTo(200);
        assertThat(codeOf(created)).isZero();
        long orderId = ((Number) JsonPath.read(created.getBody(), "$.data.id")).longValue();
        assertThat((String) JsonPath.read(created.getBody(), "$.data.orderType")).isEqualTo("DINE_IN");
        assertThat(((Number) JsonPath.read(created.getBody(), "$.data.payAmount")).doubleValue()).isEqualTo(48.00);

        ResponseEntity<String> list = get(DINE_IN + "/orders?statusGroup=ALL&page=1&size=50", customerToken);
        assertThat(list.getStatusCode().value()).isEqualTo(200);
        assertThat(codeOf(list)).isZero();
        List<Number> ids = JsonPath.read(list.getBody(), "$.data.list[*].id");
        assertThat(ids.stream().map(Number::longValue)).contains(orderId);

        ResponseEntity<String> detail = get(DINE_IN + "/orders/" + orderId, customerToken);
        assertThat(detail.getStatusCode().value()).isEqualTo(200);
        assertThat(((Number) JsonPath.read(detail.getBody(), "$.data.id")).longValue()).isEqualTo(orderId);
        assertThat((String) JsonPath.read(detail.getBody(), "$.data.tableNo")).isEqualTo(table.tableNo());
        assertThat((Boolean) JsonPath.read(detail.getBody(), "$.data.canCancel")).isTrue();

        ResponseEntity<String> cancelled = postJson(DINE_IN + "/orders/" + orderId + "/cancel",
                Map.of("reason", "临时离店"), customerToken, null, Map.of());
        assertThat(cancelled.getStatusCode().value()).isEqualTo(200);
        assertThat(codeOf(cancelled)).isZero();
        assertThat((String) JsonPath.read(cancelled.getBody(), "$.data.status")).isEqualTo("CANCELLED");

        ResponseEntity<String> tableDetail = get(TABLES + "/" + table.id(), merchantToken);
        assertThat((String) JsonPath.read(tableDetail.getBody(), "$.data.status")).isEqualTo("FREE");
        assertThat((Number) JsonPath.read(tableDetail.getBody(), "$.data.currentSessionId")).isNull();

        ResponseEntity<String> dishDetail = get("/api/v1/merchant/dishes/" + dishId, merchantToken);
        assertThat(((Number) JsonPath.read(dishDetail.getBody(), "$.data.stock")).intValue()).isEqualTo(5);
    }

    @Test
    void dineInOrdersAreScopedToOwnerAndCustomerEndpointsRejectMerchantToken() throws Exception {
        String merchantToken = login("merchant", "merchant123");
        CreatedTable table = createTable(merchantToken);
        String customerToken = login("customer", "customer123");
        String sessionToken = bind(table, customerToken);
        long dishId = createDish(merchantToken, "堂食越权菜" + suffix(), "16.00", 3);

        ResponseEntity<String> created = postJson(DINE_IN + "/orders", dineInOrderBody(dishId, 1, "越权检查"),
                customerToken, "dinein-scope-" + suffix(), Map.of("X-Table-Session", sessionToken));
        long orderId = ((Number) JsonPath.read(created.getBody(), "$.data.id")).longValue();

        String otherToken = registerCustomer();
        ResponseEntity<String> otherDetail = get(DINE_IN + "/orders/" + orderId, otherToken);
        assertThat(otherDetail.getStatusCode().value()).isEqualTo(403);
        assertThat(codeOf(otherDetail)).isEqualTo(40302);

        ResponseEntity<String> otherList = get(DINE_IN + "/orders?statusGroup=ALL&page=1&size=50", otherToken);
        List<Number> ids = JsonPath.read(otherList.getBody(), "$.data.list[*].id");
        assertThat(ids.stream().map(Number::longValue)).doesNotContain(orderId);

        // 列表按 userId 过滤：商家 token 只能看到自己的（空）列表，不会看到顾客订单
        ResponseEntity<String> merchantList = get(DINE_IN + "/orders?statusGroup=ALL&page=1&size=50", merchantToken);
        assertThat(merchantList.getStatusCode().value()).isEqualTo(200);
        List<Number> merchantIds = JsonPath.read(merchantList.getBody(), "$.data.list[*].id");
        assertThat(merchantIds).isEmpty();

        ResponseEntity<String> merchantCancel = postJson(DINE_IN + "/orders/" + orderId + "/cancel",
                Map.of("reason", "商家越权取消"), merchantToken, null, Map.of());
        assertThat(merchantCancel.getStatusCode().value()).isEqualTo(403);
        assertThat(codeOf(merchantCancel)).isEqualTo(40302);

        // 清理：取消订单释放桌台并删除桌台
        postJson(DINE_IN + "/orders/" + orderId + "/cancel", Map.of("reason", "清理"), customerToken, null, Map.of());
        postJson(DINE_IN + "/session/close", null, customerToken, null, Map.of("X-Table-Session", sessionToken));
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(merchantToken);
        ResponseEntity<String> deleted = restTemplate.exchange(TABLES + "/" + table.id(), HttpMethod.DELETE,
                new HttpEntity<>(headers), String.class);
        assertThat(codeOf(deleted)).isZero();
    }

    private CreatedTable createTable(String merchantToken) throws Exception {
        String tableNo = "B" + suffix();
        ResponseEntity<String> response = postJson(TABLES, Map.of("tableNo", tableNo, "name", "集成测试桌", "capacity", 4),
                merchantToken, null, Map.of());
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(codeOf(response)).isZero();
        return new CreatedTable(((Number) JsonPath.read(response.getBody(), "$.data.id")).longValue(), tableNo);
    }

    private String bind(CreatedTable table, String customerToken) throws Exception {
        ResponseEntity<String> response = postJson(DINE_IN + "/bind", Map.of("tableNo", table.tableNo()),
                customerToken, null, Map.of());
        assertThat(codeOf(response)).as("bind body=%s", response.getBody()).isZero();
        assertThat((String) JsonPath.read(response.getBody(), "$.data.status")).isEqualTo("ACTIVE");
        return JsonPath.read(response.getBody(), "$.data.sessionToken");
    }

    private long createDish(String merchantToken, String name, String price, int stock) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("categoryId", 1);
        body.put("name", name);
        body.put("price", new BigDecimal(price));
        body.put("stock", stock);
        ResponseEntity<String> response = postJson("/api/v1/merchant/dishes", body, merchantToken, null, Map.of());
        assertThat(codeOf(response)).isZero();
        return ((Number) JsonPath.read(response.getBody(), "$.data.id")).longValue();
    }

    private String registerCustomer() throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("username", "di" + suffix());
        body.put("password", "pass1234");
        body.put("nickname", "堂食越权顾客");
        ResponseEntity<String> response = postJson("/api/v1/auth/register", body, null, null, Map.of());
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        return JsonPath.read(response.getBody(), "$.data.token");
    }

    private String login(String username, String password) throws Exception {
        ResponseEntity<String> response = postJson("/api/v1/auth/login",
                Map.of("username", username, "password", password), null, null, Map.of());
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        return JsonPath.read(response.getBody(), "$.data.token");
    }

    private Map<String, Object> dineInOrderBody(long dishId, int quantity, String remark) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("dishId", dishId);
        item.put("quantity", quantity);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("items", List.of(item));
        body.put("remark", remark);
        return body;
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

    private record CreatedTable(long id, String tableNo) {
    }
}
