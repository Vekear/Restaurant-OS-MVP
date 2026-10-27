package com.restaurant.order;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.restaurant.menu.mapper.DishMapper;
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
 * 商家订单接口集成测试：接单→制作→配送→完成→评价全链路，拒单回补库存/退款，角色与非法流转校验。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class MerchantOrderApiIntegrationTest {

    private static final String MERCHANT_ORDERS = "/api/v1/merchant/orders";

    private static final String CUSTOMER_ORDERS = "/api/v1/orders";

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DishMapper dishMapper;

    @BeforeEach
    void clearRateLimitKeys() {
        Set<String> keys = stringRedisTemplate.keys("rate:*");
        if (keys != null && !keys.isEmpty()) {
            stringRedisTemplate.delete(keys);
        }
    }

    @Test
    void merchantAcceptsPreparesDeliversAndCustomerCompletesWithReview() throws Exception {
        String customerToken = login("customer", "customer123");
        String merchantToken = login("merchant", "merchant123");
        long dishId = createDish("全链路菜品" + suffix(), "22.00", 10);
        long orderId = createOrder(customerToken, dishId, 1);
        payOrder(customerToken, orderId);

        ResponseEntity<String> accepted = post("/api/v1/merchant/orders/" + orderId + "/accept", null, merchantToken);
        assertThat(accepted.getStatusCode().value()).isEqualTo(200);
        assertThat((String) JsonPath.read(accepted.getBody(), "$.data.status")).isEqualTo("ACCEPTED");

        ResponseEntity<String> preparing = post("/api/v1/merchant/orders/" + orderId + "/prepare", null, merchantToken);
        assertThat((String) JsonPath.read(preparing.getBody(), "$.data.status")).isEqualTo("MAKING");

        ResponseEntity<String> delivering = post("/api/v1/merchant/orders/" + orderId + "/deliver", null, merchantToken);
        assertThat((String) JsonPath.read(delivering.getBody(), "$.data.status")).isEqualTo("DELIVERING");

        ResponseEntity<String> completed = restTemplate.exchange(
                CUSTOMER_ORDERS + "/" + orderId + "/confirm-receipt", HttpMethod.POST,
                new HttpEntity<>(jsonHeaders(customerToken, null)), String.class);
        assertThat(completed.getStatusCode().value()).isEqualTo(200);
        assertThat((String) JsonPath.read(completed.getBody(), "$.data.status")).isEqualTo("COMPLETED");
        assertThat((Boolean) JsonPath.read(completed.getBody(), "$.data.canReview")).isTrue();

        Map<String, Object> review = new LinkedHashMap<>();
        review.put("rating", 5);
        review.put("content", "味道很好");
        ResponseEntity<String> reviewResponse = restTemplate.exchange(
                CUSTOMER_ORDERS + "/" + orderId + "/review", HttpMethod.POST,
                new HttpEntity<>(objectMapper.writeValueAsString(review), jsonHeaders(customerToken, null)),
                String.class);
        assertThat(reviewResponse.getStatusCode().value()).isEqualTo(200);
        assertThat(((Number) JsonPath.read(reviewResponse.getBody(), "$.data.rating")).intValue()).isEqualTo(5);
        assertThat((String) JsonPath.read(reviewResponse.getBody(), "$.data.orderNo")).isNotBlank();

        ResponseEntity<String> reviewDetail = restTemplate.exchange(
                CUSTOMER_ORDERS + "/" + orderId + "/review", HttpMethod.GET,
                new HttpEntity<>(jsonHeaders(customerToken, null)), String.class);
        assertThat(reviewDetail.getStatusCode().value()).isEqualTo(200);

        ResponseEntity<String> secondReview = restTemplate.exchange(
                CUSTOMER_ORDERS + "/" + orderId + "/review", HttpMethod.POST,
                new HttpEntity<>(objectMapper.writeValueAsString(review), jsonHeaders(customerToken, null)),
                String.class);
        assertThat(secondReview.getStatusCode().value()).isEqualTo(409);
        assertThat(codeOf(secondReview)).isEqualTo(40906);

        ResponseEntity<String> list = restTemplate.exchange(
                MERCHANT_ORDERS + "?statusGroup=FINISHED&page=1&size=50", HttpMethod.GET,
                new HttpEntity<>(jsonHeaders(merchantToken, null)), String.class);
        assertThat(list.getStatusCode().value()).isEqualTo(200);
        List<Number> ids = JsonPath.read(list.getBody(), "$.data.list[*].id");
        assertThat(ids.stream().map(Number::longValue)).contains(orderId);
    }

    @Test
    void merchantRejectRestoresStockAndRefundsMockPayment() throws Exception {
        String customerToken = login("customer", "customer123");
        String merchantToken = login("merchant", "merchant123");
        long dishId = createDish("拒单菜品" + suffix(), "25.00", 8);
        long orderId = createOrder(customerToken, dishId, 3);
        assertThat(dishMapper.selectById(dishId).getStock()).isEqualTo(5);
        String payNo = payOrder(customerToken, orderId);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("reason", "食材售罄");
        ResponseEntity<String> rejected = restTemplate.exchange(
                MERCHANT_ORDERS + "/" + orderId + "/reject", HttpMethod.POST,
                new HttpEntity<>(objectMapper.writeValueAsString(body), jsonHeaders(merchantToken, null)),
                String.class);
        assertThat(rejected.getStatusCode().value()).isEqualTo(200);
        assertThat((String) JsonPath.read(rejected.getBody(), "$.data.status")).isEqualTo("REJECTED");
        assertThat((String) JsonPath.read(rejected.getBody(), "$.data.cancelReason")).isEqualTo("食材售罄");
        assertThat(dishMapper.selectById(dishId).getStock()).isEqualTo(8);

        ResponseEntity<String> payment = restTemplate.exchange(
                "/api/v1/payments/" + payNo, HttpMethod.GET,
                new HttpEntity<>(jsonHeaders(customerToken, null)), String.class);
        assertThat((String) JsonPath.read(payment.getBody(), "$.data.status")).isEqualTo("REFUNDED");

        ResponseEntity<String> cancelAfterReject = post("/api/v1/orders/" + orderId + "/cancel",
                Map.of("reason", "晚了"), customerToken);
        assertThat(cancelAfterReject.getStatusCode().value()).isEqualTo(409);
        assertThat(codeOf(cancelAfterReject)).isEqualTo(40905);

        ResponseEntity<String> deliverAfterReject = post("/api/v1/merchant/orders/" + orderId + "/deliver",
                null, merchantToken);
        assertThat(deliverAfterReject.getStatusCode().value()).isEqualTo(409);
        assertThat(codeOf(deliverAfterReject)).isEqualTo(40905);
    }

    @Test
    void merchantEndpointsEnforceRoleAndOwnership() throws Exception {
        String customerToken = login("customer", "customer123");
        String merchantToken = login("merchant", "merchant123");
        long dishId = createDish("角色菜品" + suffix(), "25.00", 10);
        long unpaidOrderId = createOrder(customerToken, dishId, 1);

        ResponseEntity<String> customerAccept = post("/api/v1/merchant/orders/" + unpaidOrderId + "/accept",
                null, customerToken);
        assertThat(customerAccept.getStatusCode().value()).isEqualTo(403);
        assertThat(codeOf(customerAccept)).isEqualTo(40301);

        ResponseEntity<String> customerList = get(MERCHANT_ORDERS, customerToken);
        assertThat(customerList.getStatusCode().value()).isEqualTo(403);
        assertThat(codeOf(customerList)).isEqualTo(40301);

        ResponseEntity<String> acceptUnpaid = post("/api/v1/merchant/orders/" + unpaidOrderId + "/accept",
                null, merchantToken);
        assertThat(acceptUnpaid.getStatusCode().value()).isEqualTo(409);
        assertThat(codeOf(acceptUnpaid)).isEqualTo(40905);

        ResponseEntity<String> invalidReject = restTemplate.exchange(
                MERCHANT_ORDERS + "/" + unpaidOrderId + "/reject", HttpMethod.POST,
                new HttpEntity<>(objectMapper.writeValueAsString(Map.of("reason", "x")),
                        jsonHeaders(merchantToken, null)),
                String.class);
        assertThat(invalidReject.getStatusCode().value()).isEqualTo(400);
        assertThat(codeOf(invalidReject)).isEqualTo(40001);

        ResponseEntity<String> merchantConfirm = post("/api/v1/orders/" + unpaidOrderId + "/confirm-receipt",
                null, merchantToken);
        assertThat(merchantConfirm.getStatusCode().value()).isEqualTo(403);
        assertThat(codeOf(merchantConfirm)).isEqualTo(40302);
    }

    private String payOrder(String customerToken, long orderId) throws Exception {
        Map<String, Object> create = new LinkedHashMap<>();
        create.put("orderId", orderId);
        create.put("orderType", "TAKEOUT");
        ResponseEntity<String> payment = restTemplate.exchange(
                "/api/v1/payments", HttpMethod.POST,
                new HttpEntity<>(objectMapper.writeValueAsString(create),
                        jsonHeaders(customerToken, "merchant-pay-create-" + suffix())),
                String.class);
        assertThat(codeOf(payment)).isZero();
        String payNo = JsonPath.read(payment.getBody(), "$.data.payNo");
        ResponseEntity<String> paid = restTemplate.exchange(
                "/api/v1/payments/" + payNo + "/mock-pay", HttpMethod.POST,
                new HttpEntity<>(jsonHeaders(customerToken, "merchant-pay-mock-" + suffix())), String.class);
        assertThat(codeOf(paid)).isZero();
        assertThat((String) JsonPath.read(paid.getBody(), "$.data.status")).isEqualTo("SUCCESS");
        return payNo;
    }

    private long createOrder(String customerToken, long dishId, int quantity) throws Exception {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("dishId", dishId);
        item.put("quantity", quantity);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("merchantId", 1L);
        body.put("addressId", 1L);
        body.put("items", List.of(item));
        ResponseEntity<String> response = restTemplate.exchange(
                CUSTOMER_ORDERS, HttpMethod.POST,
                new HttpEntity<>(objectMapper.writeValueAsString(body),
                        jsonHeaders(customerToken, "merchant-order-" + suffix())),
                String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(codeOf(response)).isZero();
        return ((Number) JsonPath.read(response.getBody(), "$.data.id")).longValue();
    }

    private long createDish(String name, String price, int stock) throws Exception {
        String merchantToken = login("merchant", "merchant123");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("categoryId", 1);
        body.put("name", name);
        body.put("price", new BigDecimal(price));
        body.put("stock", stock);
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/merchant/dishes", HttpMethod.POST,
                new HttpEntity<>(objectMapper.writeValueAsString(body), jsonHeaders(merchantToken, null)),
                String.class);
        assertThat(codeOf(response)).isZero();
        return ((Number) JsonPath.read(response.getBody(), "$.data.id")).longValue();
    }

    private String login(String username, String password) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("username", username);
        body.put("password", password);
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/auth/login", HttpMethod.POST,
                new HttpEntity<>(objectMapper.writeValueAsString(body), jsonHeaders(null, null)),
                String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        return JsonPath.read(response.getBody(), "$.data.token");
    }

    private ResponseEntity<String> post(String path, Object body, String token) throws Exception {
        String payload = body == null ? "{}" : objectMapper.writeValueAsString(body);
        return restTemplate.exchange(path, HttpMethod.POST,
                new HttpEntity<>(payload, jsonHeaders(token, null)), String.class);
    }

    private ResponseEntity<String> get(String path, String token) {
        return restTemplate.exchange(path, HttpMethod.GET,
                new HttpEntity<>(jsonHeaders(token, null)), String.class);
    }

    private HttpHeaders jsonHeaders(String token, String idempotencyKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            headers.setBearerAuth(token);
        }
        if (idempotencyKey != null) {
            headers.set("Idempotency-Key", idempotencyKey);
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
