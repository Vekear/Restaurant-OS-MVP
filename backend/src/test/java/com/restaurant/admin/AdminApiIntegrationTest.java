package com.restaurant.admin;

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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T10 验收：管理后台用户/商家/优惠券/审计/统计，商家资料与营业状态、看板接口，RBAC 与禁用即时生效。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AdminApiIntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void cleanRateLimitKeys() {
        Set<String> keys = redisTemplate.keys("rate:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    @Test
    void adminUserListMasksPhoneAndDisableTakesEffectImmediately() throws Exception {
        String adminToken = login("admin", "admin123");
        String users = get("/api/v1/admin/users?page=1&size=100", adminToken);
        assertThat(codeOf(users)).isZero();
        List<Map<String, Object>> records = JsonPath.read(users, "$.data.list");
        assertThat(records).isNotEmpty();
        Map<String, Object> customer = records.stream()
                .filter(row -> "customer".equals(row.get("username")))
                .findFirst().orElseThrow();
        assertThat(customer.get("phone").toString()).isEqualTo("139****0003");

        String username = "adm" + suffix();
        String registered = post("/api/v1/auth/register",
                Map.of("username", username, "password", "pass1234"), null);
        assertThat(codeOf(registered)).isZero();
        long userId = ((Number) JsonPath.read(registered, "$.data.user.id")).longValue();
        String userToken = JsonPath.read(registered, "$.data.token");
        assertThat(codeOf(get("/api/v1/auth/me", userToken))).isZero();

        String disabled = patch("/api/v1/admin/users/" + userId + "/status",
                Map.of("status", "DISABLED", "reason", "违规测试"), adminToken);
        assertThat(codeOf(disabled)).isZero();
        assertThat(JsonPath.read(disabled, "$.data.status").toString()).isEqualTo("DISABLED");

        ResponseEntity<String> meAfterDisabled = exchange("/api/v1/auth/me", HttpMethod.GET, null, userToken);
        assertThat(meAfterDisabled.getStatusCode().value()).isEqualTo(403);
        assertThat(codeOf(meAfterDisabled.getBody())).isEqualTo(40304);

        String enabled = patch("/api/v1/admin/users/" + userId + "/status",
                Map.of("status", "ENABLED"), adminToken);
        assertThat(codeOf(enabled)).isZero();
        assertThat(codeOf(get("/api/v1/auth/me", userToken))).isZero();

        long adminId = ((Number) JsonPath.read(get("/api/v1/auth/me", adminToken), "$.data.id")).longValue();
        ResponseEntity<String> selfDisable = exchange("/api/v1/admin/users/" + adminId + "/status",
                HttpMethod.PATCH, Map.of("status", "DISABLED"), adminToken);
        assertThat(selfDisable.getStatusCode().value()).isEqualTo(409);
        assertThat(codeOf(selfDisable.getBody())).isEqualTo(40918);

        ResponseEntity<String> customerList = exchange("/api/v1/admin/users", HttpMethod.GET, null,
                login("customer", "customer123"));
        assertThat(customerList.getStatusCode().value()).isEqualTo(403);
        assertThat(codeOf(customerList.getBody())).isEqualTo(40301);
    }

    @Test
    void adminMerchantOrderCouponAuditAndStatsWork() throws Exception {
        String adminToken = login("admin", "admin123");
        String merchantToken = login("merchant", "merchant123");

        String merchants = get("/api/v1/admin/merchants?page=1&size=100", adminToken);
        assertThat(codeOf(merchants)).isZero();
        List<Map<String, Object>> merchantRecords = JsonPath.read(merchants, "$.data.list");
        assertThat(merchantRecords.stream().anyMatch(row -> ((Number) row.get("id")).longValue() == 1L)).isTrue();
        Map<String, Object> seedMerchant = merchantRecords.stream()
                .filter(row -> ((Number) row.get("id")).longValue() == 1L).findFirst().orElseThrow();
        assertThat(seedMerchant.get("ownerUsername")).isEqualTo("merchant");
        assertThat(seedMerchant.get("auditStatus")).isEqualTo("APPROVED");

        assertThat(codeOf(get("/api/v1/admin/merchants/1", adminToken))).isZero();
        ResponseEntity<String> missingMerchant = exchange("/api/v1/admin/merchants/999999", HttpMethod.GET, null,
                adminToken);
        assertThat(missingMerchant.getStatusCode().value()).isEqualTo(404);

        String name = "验收券" + suffix();
        String created = post("/api/v1/admin/coupons", Map.of(
                "name", name,
                "thresholdAmount", "100.00",
                "discountAmount", "10.00",
                "total", 50,
                "startAt", "2026-01-01T00:00:00",
                "endAt", "2027-12-31T23:59:59"), adminToken);
        assertThat(codeOf(created)).isZero();
        long couponId = ((Number) JsonPath.read(created, "$.data.id")).longValue();
        assertThat(((Number) JsonPath.read(created, "$.data.remaining")).intValue()).isEqualTo(50);
        assertThat(JsonPath.read(created, "$.data.status").toString()).isEqualTo("ENABLED");

        String updated = put("/api/v1/admin/coupons/" + couponId, Map.of(
                "name", name + "改",
                "thresholdAmount", "100.00",
                "discountAmount", "10.00",
                "total", 40,
                "startAt", "2026-01-01T00:00:00",
                "endAt", "2027-12-31T23:59:59"), adminToken);
        assertThat(codeOf(updated)).isZero();
        assertThat(((Number) JsonPath.read(updated, "$.data.remaining")).intValue()).isEqualTo(40);

        String statusChanged = patch("/api/v1/admin/coupons/" + couponId + "/status",
                Map.of("status", "DISABLED"), adminToken);
        assertThat(codeOf(statusChanged)).isZero();
        assertThat(JsonPath.read(statusChanged, "$.data.status").toString()).isEqualTo("DISABLED");

        String listed = get("/api/v1/admin/coupons?keyword=" + name, adminToken);
        assertThat(codeOf(listed)).isZero();
        List<Map<String, Object>> coupons = JsonPath.read(listed, "$.data.list");
        assertThat(coupons.stream().anyMatch(row -> ((Number) row.get("id")).longValue() == couponId)).isTrue();

        String deleted = delete("/api/v1/admin/coupons/" + couponId, adminToken);
        assertThat(codeOf(deleted)).isZero();
        assertThat(JsonPath.read(deleted, "$.data.deleted").toString()).isEqualTo("true");
        assertThat(exchange("/api/v1/admin/coupons/" + couponId, HttpMethod.GET, null, adminToken)
                .getStatusCode().value()).isEqualTo(404);

        ResponseEntity<String> invalidCoupon = exchange("/api/v1/admin/coupons", HttpMethod.POST, Map.of(
                "name", "非法券" + suffix(),
                "thresholdAmount", "10.00",
                "discountAmount", "20.00",
                "total", 10,
                "startAt", "2026-01-01T00:00:00",
                "endAt", "2027-12-31T23:59:59"), adminToken);
        assertThat(invalidCoupon.getStatusCode().value()).isEqualTo(400);
        assertThat(codeOf(invalidCoupon.getBody())).isEqualTo(40001);

        String auditLogs = get("/api/v1/admin/audit-logs?action=COUPON_CREATE&page=1&size=20", adminToken);
        assertThat(codeOf(auditLogs)).isZero();
        List<Map<String, Object>> audits = JsonPath.read(auditLogs, "$.data.list");
        assertThat(audits).isNotEmpty();
        assertThat(audits.get(0).get("action")).isEqualTo("COUPON_CREATE");

        String overview = get("/api/v1/admin/stats/overview", adminToken);
        assertThat(codeOf(overview)).isZero();
        assertThat(((Number) JsonPath.read(overview, "$.data.userCount")).longValue()).isPositive();
        assertThat(((Number) JsonPath.read(overview, "$.data.merchantCount")).longValue()).isPositive();
        assertThat((Object) JsonPath.read(overview, "$.data.startDate")).isNotNull();

        assertThat(codeOf(get("/api/v1/admin/stats/trend", adminToken))).isZero();
        assertThat((Object) JsonPath.read(get("/api/v1/admin/stats/order-type-ratio", adminToken), "$.data.items"))
                .isInstanceOf(List.class);
        assertThat(codeOf(get("/api/v1/admin/stats/hot-dishes?limit=10", adminToken))).isZero();

        String orders = get("/api/v1/admin/orders?page=1&size=5", adminToken);
        assertThat(codeOf(orders)).isZero();
        List<Map<String, Object>> orderRecords = JsonPath.read(orders, "$.data.list");
        if (!orderRecords.isEmpty()) {
            long orderId = ((Number) orderRecords.get(0).get("id")).longValue();
            assertThat(codeOf(get("/api/v1/admin/orders/" + orderId, adminToken))).isZero();
        }

        String profile = get("/api/v1/merchant/profile", merchantToken);
        assertThat(codeOf(profile)).isZero();
        assertThat(JsonPath.read(profile, "$.data.name").toString()).isEqualTo("川味小馆");
        assertThat(JsonPath.read(profile, "$.data.ownerUsername").toString()).isEqualTo("merchant");

        String closed = put("/api/v1/merchant/business-status", Map.of("businessStatus", "CLOSED"), merchantToken);
        assertThat(codeOf(closed)).isZero();
        assertThat(JsonPath.read(closed, "$.data.businessStatus").toString()).isEqualTo("CLOSED");
        String reopened = put("/api/v1/merchant/business-status", Map.of("businessStatus", "OPEN"), merchantToken);
        assertThat(codeOf(reopened)).isZero();

        assertThat(codeOf(get("/api/v1/merchant/dashboard/summary", merchantToken))).isZero();
        assertThat(codeOf(get("/api/v1/merchant/dashboard/hot-dishes?limit=5", merchantToken))).isZero();
        assertThat(codeOf(get("/api/v1/merchant/dashboard/order-type-ratio", merchantToken))).isZero();
        assertThat(codeOf(get("/api/v1/merchant/reviews?page=1&size=10", merchantToken))).isZero();

        ResponseEntity<String> merchantStats = exchange("/api/v1/admin/stats/overview", HttpMethod.GET, null,
                merchantToken);
        assertThat(merchantStats.getStatusCode().value()).isEqualTo(403);
        assertThat(codeOf(merchantStats.getBody())).isEqualTo(40301);
    }

    @Test
    void adminOrderListHandlesEmptyFiltersAndAdminTablesAreOptional() throws Exception {
        String adminToken = login("admin", "admin123");

        // 空筛选：数据库无订单/有订单都应返回 200，而不是空 IN () 引发的 SQL 500。
        String noFilter = get("/api/v1/admin/orders?page=1&size=5", adminToken);
        assertThat(codeOf(noFilter)).isZero();
        assertThat(((Number) JsonPath.read(noFilter, "$.data.page")).intValue()).isEqualTo(1);

        String statusOnly = get("/api/v1/admin/orders?status=COMPLETED&page=1&size=5", adminToken);
        assertThat(codeOf(statusOnly)).isZero();
        String dateOnly = get("/api/v1/admin/orders?startDate=2026-10-01&endDate=2026-10-31&page=1&size=5",
                adminToken);
        assertThat(codeOf(dateOnly)).isZero();
        String keywordMiss = get("/api/v1/admin/orders?keyword=NO-SUCH-ORDER-" + suffix() + "&page=1&size=5",
                adminToken);
        assertThat(codeOf(keywordMiss)).isZero();
        assertThat((List<?>) JsonPath.read(keywordMiss, "$.data.list")).isEmpty();

        // 管理端桌台：merchantId 可选，缺省返回全部桌台。
        String allTables = get("/api/v1/admin/tables", adminToken);
        assertThat(codeOf(allTables)).isZero();
        assertThat((List<?>) JsonPath.read(allTables, "$.data")).isNotNull();
        String merchantTables = get("/api/v1/admin/tables?merchantId=1", adminToken);
        assertThat(codeOf(merchantTables)).isZero();
    }

    @Test
    void merchantProfileUpdateValidatesFieldsAndPersists() throws Exception {
        String merchantToken = login("merchant", "merchant123");
        ResponseEntity<String> invalid = exchange("/api/v1/merchant/profile", HttpMethod.PUT, Map.of(
                "name", "短",
                "phone", "abc@@",
                "address", "x",
                "deliveryFee", "999.00",
                "minOrderAmount", "0.00"), merchantToken);
        assertThat(invalid.getStatusCode().value()).isEqualTo(400);
        assertThat(codeOf(invalid.getBody())).isEqualTo(40001);

        String updated = put("/api/v1/merchant/profile", Map.of(
                "name", "川味小馆",
                "description", "验收更新",
                "phone", "028-88886666",
                "address", "四川省成都市武侯区天府大道北段 888 号",
                "deliveryFee", "5.00",
                "minOrderAmount", "20.00"), merchantToken);
        assertThat(codeOf(updated)).isZero();
        assertThat(JsonPath.read(updated, "$.data.name").toString()).isEqualTo("川味小馆");
        assertThat(JsonPath.read(updated, "$.data.address").toString()).isEqualTo("四川省成都市武侯区天府大道北段 888 号");
    }

    private ResponseEntity<String> exchange(String path, HttpMethod method, Object body, String token) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            headers.setBearerAuth(token);
        }
        String payload = body == null ? null : objectMapper.writeValueAsString(body);
        return rest.exchange(path, method, new HttpEntity<>(payload, headers), String.class);
    }

    private String get(String path, String token) {
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(bearerHeaders(token)), String.class).getBody();
    }

    private String post(String path, Object body, String token) throws Exception {
        return exchange(path, HttpMethod.POST, body, token).getBody();
    }

    private String put(String path, Object body, String token) throws Exception {
        return exchange(path, HttpMethod.PUT, body, token).getBody();
    }

    private String patch(String path, Object body, String token) throws Exception {
        return exchange(path, HttpMethod.PATCH, body, token).getBody();
    }

    private String delete(String path, String token) {
        return rest.exchange(path, HttpMethod.DELETE, new HttpEntity<>(bearerHeaders(token)), String.class).getBody();
    }

    private HttpHeaders bearerHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return headers;
    }

    private String login(String username, String password) throws Exception {
        ResponseEntity<String> response = rest.postForEntity("/api/v1/auth/login",
                new HttpEntity<>(objectMapper.writeValueAsString(Map.of("username", username, "password", password)),
                        jsonHeaders()), String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        return JsonPath.read(response.getBody(), "$.data.token");
    }

    private HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private int codeOf(String body) {
        return JsonPath.read(body, "$.code");
    }

    private String suffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 6);
    }

    @SuppressWarnings("unused")
    private Map<String, Object> unused() {
        return new HashMap<>();
    }
}
