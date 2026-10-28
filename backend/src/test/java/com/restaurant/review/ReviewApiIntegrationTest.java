package com.restaurant.review;

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
 * 评价接口集成测试（真实 Tomcat + MySQL restaurant_mvp_test + Redis db15）。
 *
 * <p>覆盖 docs/02-api.md 端点 #32 提交评价、#33 查看评价、#89 商家评价列表：
 * 一单一评、仅已完成订单可评、越权拒绝、商家按评分筛选。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ReviewApiIntegrationTest {

    private static final String ORDERS = "/api/v1/orders";

    private static final String PAYMENTS = "/api/v1/payments";

    private static final String MERCHANT_ORDERS = "/api/v1/merchant/orders";

    private static final long MERCHANT_ID = 1L;

    private static final long ADDRESS_ID = 1L;

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
    void createReviewOnCompletedOrderThenReadBackAndRejectDuplicate() throws Exception {
        String merchantToken = login("merchant", "merchant123");
        String dishName = "评价菜品" + suffix();
        long dishId = createDish(merchantToken, dishName, "30.00", 10);
        String customerToken = login("customer", "customer123");
        CompletedOrder order = completeTakeoutOrder(customerToken, merchantToken, dishId, 2, "评价链路");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("rating", 5);
        body.put("content", "味道很好，配送很快");
        ResponseEntity<String> created = postJson(ORDERS + "/" + order.id() + "/review", body, customerToken, null);
        assertThat(created.getStatusCode().value()).isEqualTo(200);
        assertThat(codeOf(created)).isZero();
        long reviewId = ((Number) JsonPath.read(created.getBody(), "$.data.id")).longValue();
        assertThat(((Number) JsonPath.read(created.getBody(), "$.data.orderId")).longValue()).isEqualTo(order.id());
        assertThat((String) JsonPath.read(created.getBody(), "$.data.orderNo")).isEqualTo(order.orderNo());
        assertThat(((Number) JsonPath.read(created.getBody(), "$.data.rating")).intValue()).isEqualTo(5);
        assertThat((String) JsonPath.read(created.getBody(), "$.data.content")).isEqualTo("味道很好，配送很快");
        assertThat((List<String>) JsonPath.read(created.getBody(), "$.data.dishNames")).contains(dishName);
        assertThat((String) JsonPath.read(created.getBody(), "$.data.userName")).isNotBlank();

        ResponseEntity<String> fetched = get(ORDERS + "/" + order.id() + "/review", customerToken);
        assertThat(fetched.getStatusCode().value()).isEqualTo(200);
        assertThat(codeOf(fetched)).isZero();
        assertThat(((Number) JsonPath.read(fetched.getBody(), "$.data.id")).longValue()).isEqualTo(reviewId);

        ResponseEntity<String> duplicate = postJson(ORDERS + "/" + order.id() + "/review", body, customerToken, null);
        assertThat(duplicate.getStatusCode().value()).isEqualTo(409);
        assertThat(codeOf(duplicate)).isEqualTo(40906);
        assertThat((String) JsonPath.read(duplicate.getBody(), "$.message")).contains("已评价");
    }

    @Test
    void reviewRequiresCompletedOrderAndOwnership() throws Exception {
        String merchantToken = login("merchant", "merchant123");
        long dishId = createDish(merchantToken, "未完成评价菜" + suffix(), "22.00", 10);
        String customerToken = login("customer", "customer123");
        ResponseEntity<String> created = postJson(ORDERS, orderBody(dishId, 1, "未支付评价"), customerToken,
                "review-unpaid-" + suffix());
        assertThat(codeOf(created)).as("create body=%s", created.getBody()).isZero();
        long unpaidOrderId = ((Number) JsonPath.read(created.getBody(), "$.data.id")).longValue();

        Map<String, Object> reviewBody = Map.of("rating", 5, "content", "还没做完就评价");
        ResponseEntity<String> notCompleted = postJson(ORDERS + "/" + unpaidOrderId + "/review", reviewBody,
                customerToken, null);
        assertThat(notCompleted.getStatusCode().value()).isEqualTo(409);
        assertThat(codeOf(notCompleted)).isEqualTo(40907);

        CompletedOrder order = completeTakeoutOrder(customerToken, merchantToken, dishId, 2, "越权评价链路");
        String otherToken = registerCustomer();
        ResponseEntity<String> otherCreate = postJson(ORDERS + "/" + order.id() + "/review", reviewBody, otherToken, null);
        assertThat(otherCreate.getStatusCode().value()).isEqualTo(403);
        assertThat(codeOf(otherCreate)).isEqualTo(40302);

        postJson(ORDERS + "/" + order.id() + "/review", reviewBody, customerToken, null);
        ResponseEntity<String> otherGet = get(ORDERS + "/" + order.id() + "/review", otherToken);
        assertThat(otherGet.getStatusCode().value()).isEqualTo(403);
        assertThat(codeOf(otherGet)).isEqualTo(40302);

        ResponseEntity<String> missing = get(ORDERS + "/999999999/review", customerToken);
        assertThat(missing.getStatusCode().value()).isEqualTo(404);
        assertThat(codeOf(missing)).isEqualTo(40401);
    }

    @Test
    void merchantReviewListFiltersByRatingAndRequiresMerchantRole() throws Exception {
        String merchantToken = login("merchant", "merchant123");
        long dishId = createDish(merchantToken, "商家评价菜" + suffix(), "26.00", 10);
        String customerToken = login("customer", "customer123");
        CompletedOrder order = completeTakeoutOrder(customerToken, merchantToken, dishId, 1, "商家评价列表");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("rating", 4);
        body.put("content", "评分四星" + suffix());
        ResponseEntity<String> created = postJson(ORDERS + "/" + order.id() + "/review", body, customerToken, null);
        long reviewId = ((Number) JsonPath.read(created.getBody(), "$.data.id")).longValue();

        ResponseEntity<String> rated = get("/api/v1/merchant/reviews?rating=4&page=1&size=50", merchantToken);
        assertThat(rated.getStatusCode().value()).isEqualTo(200);
        List<Number> ids = JsonPath.read(rated.getBody(), "$.data.list[*].id");
        assertThat(ids.stream().map(Number::longValue)).contains(reviewId);

        ResponseEntity<String> oneStar = get("/api/v1/merchant/reviews?rating=1&page=1&size=50", merchantToken);
        List<Number> oneStarIds = JsonPath.read(oneStar.getBody(), "$.data.list[*].id");
        assertThat(oneStarIds.stream().map(Number::longValue)).doesNotContain(reviewId);

        ResponseEntity<String> invalidRating = get("/api/v1/merchant/reviews?rating=6", merchantToken);
        assertThat(invalidRating.getStatusCode().value()).isEqualTo(400);
        assertThat(codeOf(invalidRating)).isEqualTo(40001);

        ResponseEntity<String> customerList = get("/api/v1/merchant/reviews", customerToken);
        assertThat(customerList.getStatusCode().value()).isEqualTo(403);
        assertThat(codeOf(customerList)).isEqualTo(40301);
    }

    private CompletedOrder completeTakeoutOrder(String customerToken, String merchantToken, long dishId,
                                                int quantity, String remark) throws Exception {
        ResponseEntity<String> created = postJson(ORDERS, orderBody(dishId, quantity, remark), customerToken,
                "review-order-" + suffix());
        assertThat(created.getStatusCode().value()).isEqualTo(200);
        assertThat(codeOf(created)).isZero();
        long orderId = ((Number) JsonPath.read(created.getBody(), "$.data.id")).longValue();
        String orderNo = JsonPath.read(created.getBody(), "$.data.orderNo");

        ResponseEntity<String> payment = postJson(PAYMENTS, Map.of("orderId", orderId, "orderType", "TAKEOUT"),
                customerToken, "review-pay-" + suffix());
        assertThat(codeOf(payment)).isZero();
        String payNo = JsonPath.read(payment.getBody(), "$.data.payNo");
        ResponseEntity<String> paid = postJson(PAYMENTS + "/" + payNo + "/mock-pay", null, customerToken,
                "review-mock-pay-" + suffix());
        assertThat(codeOf(paid)).isZero();
        assertThat((String) JsonPath.read(paid.getBody(), "$.data.status")).isEqualTo("SUCCESS");

        for (String action : List.of("accept", "prepare", "deliver", "complete")) {
            ResponseEntity<String> transition = postJson(MERCHANT_ORDERS + "/" + orderId + "/" + action,
                    null, merchantToken, null);
            assertThat(codeOf(transition)).as("action=%s body=%s", action, transition.getBody()).isZero();
        }
        ResponseEntity<String> detail = get(ORDERS + "/" + orderId, customerToken);
        assertThat((String) JsonPath.read(detail.getBody(), "$.data.status")).isEqualTo("COMPLETED");
        return new CompletedOrder(orderId, orderNo);
    }

    private Map<String, Object> orderBody(long dishId, int quantity, String remark) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("dishId", dishId);
        item.put("quantity", quantity);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("merchantId", MERCHANT_ID);
        body.put("addressId", ADDRESS_ID);
        body.put("items", List.of(item));
        body.put("remark", remark);
        return body;
    }

    private long createDish(String merchantToken, String name, String price, int stock) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("categoryId", 1);
        body.put("name", name);
        body.put("price", new BigDecimal(price));
        body.put("stock", stock);
        ResponseEntity<String> response = postJson("/api/v1/merchant/dishes", body, merchantToken, null);
        assertThat(codeOf(response)).isZero();
        return ((Number) JsonPath.read(response.getBody(), "$.data.id")).longValue();
    }

    private String registerCustomer() throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("username", "rv" + suffix());
        body.put("password", "pass1234");
        body.put("nickname", "评价越权顾客");
        ResponseEntity<String> response = postJson("/api/v1/auth/register", body, null, null);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        return JsonPath.read(response.getBody(), "$.data.token");
    }

    private String login(String username, String password) throws Exception {
        ResponseEntity<String> response = postJson("/api/v1/auth/login",
                Map.of("username", username, "password", password), null, null);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        return JsonPath.read(response.getBody(), "$.data.token");
    }

    private ResponseEntity<String> postJson(String path, Object body, String token, String idempotencyKey)
            throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            headers.setBearerAuth(token);
        }
        if (idempotencyKey != null) {
            headers.set("Idempotency-Key", idempotencyKey);
        }
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

    private record CompletedOrder(long id, String orderNo) {
    }
}
