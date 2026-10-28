package com.restaurant.menu;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.restaurant.order.OrderItem;
import com.restaurant.order.mapper.OrderItemMapper;
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
 * 公开菜品评价接口集成测试（真实 Tomcat + MySQL + Redis）。
 *
 * <p>覆盖 docs/02-api.md §6.4 端点 #17 {@code GET /api/v1/menu/dishes/{id}/reviews}：
 * 匿名访问、默认分页 size=20、稳定排序、size 上限 40001、菜品不存在/已删除 40401、
 * OFF_SHELF 可查、仅统计包含该菜品的 ENABLED 评价、同单同菜品多行去重。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class DishReviewApiIntegrationTest {

    private static final String ORDERS = "/api/v1/orders";

    private static final String PAYMENTS = "/api/v1/payments";

    private static final String MERCHANT_ORDERS = "/api/v1/merchant/orders";

    private static final String MERCHANT_DISHES = "/api/v1/merchant/dishes";

    private static final String PUBLIC_DISHES = "/api/v1/menu/dishes";

    private static final long MERCHANT_ID = 1L;

    private static final long ADDRESS_ID = 1L;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OrderItemMapper orderItemMapper;

    @BeforeEach
    void clearRateLimitKeys() {
        Set<String> keys = stringRedisTemplate.keys("rate:*");
        if (keys != null && !keys.isEmpty()) {
            stringRedisTemplate.delete(keys);
        }
    }

    @Test
    void anonymousListingUsesDefaultPagingAndNewestFirst() throws Exception {
        String merchantToken = login("merchant", "merchant123");
        String dishName = "公开评价菜" + suffix();
        long dishId = createDish(merchantToken, dishName, "28.00", 10);
        String customerToken = login("customer", "customer123");

        CompletedOrder firstOrder = completeTakeoutOrder(customerToken, merchantToken,
                Map.of(dishId, 1), "评价分页第一单");
        long firstReviewId = createReview(customerToken, firstOrder.id(), 5, "第一次评价");
        CompletedOrder secondOrder = completeTakeoutOrder(customerToken, merchantToken,
                Map.of(dishId, 1), "评价分页第二单");
        long secondReviewId = createReview(customerToken, secondOrder.id(), 4, "第二次评价");

        ResponseEntity<String> anonymous = get(PUBLIC_DISHES + "/" + dishId + "/reviews", null);
        assertThat(anonymous.getStatusCode().value()).isEqualTo(200);
        assertThat(codeOf(anonymous)).isZero();
        assertThat(((Number) JsonPath.read(anonymous.getBody(), "$.data.page")).intValue()).isEqualTo(1);
        assertThat(((Number) JsonPath.read(anonymous.getBody(), "$.data.size")).intValue()).isEqualTo(20);
        assertThat(((Number) JsonPath.read(anonymous.getBody(), "$.data.total")).intValue()).isEqualTo(2);
        assertThat(((Number) JsonPath.read(anonymous.getBody(), "$.data.pages")).intValue()).isEqualTo(1);
        List<Number> ids = JsonPath.read(anonymous.getBody(), "$.data.list[*].id");
        assertThat(ids).extracting(Number::longValue).containsExactly(secondReviewId, firstReviewId);
        assertThat((String) JsonPath.read(anonymous.getBody(), "$.data.list[0].orderNo"))
                .isEqualTo(secondOrder.orderNo());
        assertThat(((Number) JsonPath.read(anonymous.getBody(), "$.data.list[0].userId")).longValue())
                .isPositive();
        assertThat((String) JsonPath.read(anonymous.getBody(), "$.data.list[0].userName")).isNotBlank();
        assertThat((List<String>) JsonPath.read(anonymous.getBody(), "$.data.list[0].dishNames"))
                .contains(dishName);
        assertThat((String) JsonPath.read(anonymous.getBody(), "$.data.list[0].createdAt")).isNotBlank();
        assertThat(anonymous.getBody()).doesNotContain("consignee").doesNotContain("phone");

        ResponseEntity<String> secondPage = get(PUBLIC_DISHES + "/" + dishId + "/reviews?page=2&size=1", null);
        assertThat(secondPage.getStatusCode().value()).isEqualTo(200);
        assertThat(((Number) JsonPath.read(secondPage.getBody(), "$.data.page")).intValue()).isEqualTo(2);
        assertThat(((Number) JsonPath.read(secondPage.getBody(), "$.data.size")).intValue()).isEqualTo(1);
        assertThat(((Number) JsonPath.read(secondPage.getBody(), "$.data.total")).intValue()).isEqualTo(2);
        List<Number> pageIds = JsonPath.read(secondPage.getBody(), "$.data.list[*].id");
        assertThat(pageIds).extracting(Number::longValue).containsExactly(firstReviewId);

        ResponseEntity<String> tooLarge = get(PUBLIC_DISHES + "/" + dishId + "/reviews?size=51", null);
        assertThat(tooLarge.getStatusCode().value()).isEqualTo(400);
        assertThat(codeOf(tooLarge)).isEqualTo(40001);

        ResponseEntity<String> invalidPage = get(PUBLIC_DISHES + "/" + dishId + "/reviews?page=0", null);
        assertThat(invalidPage.getStatusCode().value()).isEqualTo(400);
        assertThat(codeOf(invalidPage)).isEqualTo(40001);
    }

    @Test
    void offShelfDishReviewsRemainPublic() throws Exception {
        String merchantToken = login("merchant", "merchant123");
        String dishName = "下架评价菜" + suffix();
        long dishId = createDish(merchantToken, dishName, "25.00", 10);
        String customerToken = login("customer", "customer123");
        CompletedOrder order = completeTakeoutOrder(customerToken, merchantToken,
                Map.of(dishId, 1), "下架前评价");
        long reviewId = createReview(customerToken, order.id(), 5, "下架后仍应可见");

        ResponseEntity<String> offShelf = exchange(MERCHANT_DISHES + "/" + dishId + "/status",
                HttpMethod.PATCH, Map.of("status", "OFF_SHELF"), merchantToken, null);
        assertThat(codeOf(offShelf)).isZero();

        ResponseEntity<String> reviews = get(PUBLIC_DISHES + "/" + dishId + "/reviews", null);
        assertThat(reviews.getStatusCode().value()).isEqualTo(200);
        assertThat(codeOf(reviews)).isZero();
        assertThat(((Number) JsonPath.read(reviews.getBody(), "$.data.total")).intValue()).isEqualTo(1);
        List<Number> ids = JsonPath.read(reviews.getBody(), "$.data.list[*].id");
        assertThat(ids).extracting(Number::longValue).containsExactly(reviewId);
    }

    @Test
    void missingOrDeletedDishReturnsNotFound() throws Exception {
        ResponseEntity<String> missing = get(PUBLIC_DISHES + "/999999999/reviews", null);
        assertThat(missing.getStatusCode().value()).isEqualTo(404);
        assertThat(codeOf(missing)).isEqualTo(40401);

        String merchantToken = login("merchant", "merchant123");
        long dishId = createDish(merchantToken, "删除评价菜" + suffix(), "21.00", 10);
        String customerToken = login("customer", "customer123");
        CompletedOrder order = completeTakeoutOrder(customerToken, merchantToken,
                Map.of(dishId, 1), "删除前评价");
        createReview(customerToken, order.id(), 3, "删除菜品后不可查");

        ResponseEntity<String> deletedDish = exchange(MERCHANT_DISHES + "/" + dishId,
                HttpMethod.DELETE, null, merchantToken, null);
        assertThat(codeOf(deletedDish)).isZero();

        ResponseEntity<String> afterDelete = get(PUBLIC_DISHES + "/" + dishId + "/reviews", null);
        assertThat(afterDelete.getStatusCode().value()).isEqualTo(404);
        assertThat(codeOf(afterDelete)).isEqualTo(40401);
    }

    @Test
    void onlyReviewsWhoseOrdersContainDishAreReturnedAndDuplicatesCollapsed() throws Exception {
        String merchantToken = login("merchant", "merchant123");
        String dishAName = "关联菜A" + suffix();
        String dishBName = "关联菜B" + suffix();
        long dishA = createDish(merchantToken, dishAName, "30.00", 10);
        long dishB = createDish(merchantToken, dishBName, "12.00", 10);
        String customerToken = login("customer", "customer123");

        Map<Long, Integer> onlyA = new LinkedHashMap<>();
        onlyA.put(dishA, 1);
        CompletedOrder orderA = completeTakeoutOrder(customerToken, merchantToken, onlyA, "只含A");
        long reviewA = createReview(customerToken, orderA.id(), 5, "只含A的评价");

        Map<Long, Integer> both = new LinkedHashMap<>();
        both.put(dishA, 1);
        both.put(dishB, 1);
        CompletedOrder orderAB = completeTakeoutOrder(customerToken, merchantToken, both, "含A和B");
        long reviewAB = createReview(customerToken, orderAB.id(), 4, "含A和B的评价");

        ResponseEntity<String> listA = get(PUBLIC_DISHES + "/" + dishA + "/reviews", null);
        assertThat(((Number) JsonPath.read(listA.getBody(), "$.data.total")).intValue()).isEqualTo(2);
        List<Number> idsA = JsonPath.read(listA.getBody(), "$.data.list[*].id");
        assertThat(idsA).extracting(Number::longValue).containsExactly(reviewAB, reviewA);

        ResponseEntity<String> listB = get(PUBLIC_DISHES + "/" + dishB + "/reviews", null);
        assertThat(((Number) JsonPath.read(listB.getBody(), "$.data.total")).intValue()).isEqualTo(1);
        List<Number> idsB = JsonPath.read(listB.getBody(), "$.data.list[*].id");
        assertThat(idsB).extracting(Number::longValue).containsExactly(reviewAB);

        OrderItem duplicate = new OrderItem();
        duplicate.setOrderId(orderA.id());
        duplicate.setDishId(dishA);
        duplicate.setDishName(dishAName);
        duplicate.setPrice(new BigDecimal("30.00"));
        duplicate.setQuantity(1);
        duplicate.setSubtotal(new BigDecimal("30.00"));
        orderItemMapper.insert(duplicate);

        ResponseEntity<String> afterDuplicate = get(PUBLIC_DISHES + "/" + dishA + "/reviews", null);
        assertThat(((Number) JsonPath.read(afterDuplicate.getBody(), "$.data.total")).intValue())
                .as("同一订单同菜品多行不应重复计数")
                .isEqualTo(2);
        List<Number> deduped = JsonPath.read(afterDuplicate.getBody(), "$.data.list[*].id");
        assertThat(deduped).extracting(Number::longValue).containsExactly(reviewAB, reviewA);
    }

    private long createReview(String customerToken, long orderId, int rating, String content) throws Exception {
        ResponseEntity<String> response = exchange(ORDERS + "/" + orderId + "/review", HttpMethod.POST,
                Map.of("rating", rating, "content", content), customerToken, null);
        assertThat(codeOf(response)).as("create review body=%s", response.getBody()).isZero();
        return ((Number) JsonPath.read(response.getBody(), "$.data.id")).longValue();
    }

    private CompletedOrder completeTakeoutOrder(String customerToken, String merchantToken,
                                                Map<Long, Integer> dishQuantities, String remark) throws Exception {
        List<Map<String, Object>> items = dishQuantities.entrySet().stream()
                .map(entry -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("dishId", entry.getKey());
                    item.put("quantity", entry.getValue());
                    return item;
                })
                .toList();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("merchantId", MERCHANT_ID);
        body.put("addressId", ADDRESS_ID);
        body.put("items", items);
        body.put("remark", remark);
        ResponseEntity<String> created = exchange(ORDERS, HttpMethod.POST, body, customerToken,
                "dish-review-order-" + suffix());
        assertThat(created.getStatusCode().value()).as("create order body=%s", created.getBody()).isEqualTo(200);
        assertThat(codeOf(created)).isZero();
        long orderId = ((Number) JsonPath.read(created.getBody(), "$.data.id")).longValue();
        String orderNo = JsonPath.read(created.getBody(), "$.data.orderNo");

        ResponseEntity<String> payment = exchange(PAYMENTS, HttpMethod.POST,
                Map.of("orderId", orderId, "orderType", "TAKEOUT"), customerToken,
                "dish-review-pay-" + suffix());
        assertThat(codeOf(payment)).as("payment body=%s", payment.getBody()).isZero();
        String payNo = JsonPath.read(payment.getBody(), "$.data.payNo");
        ResponseEntity<String> paid = exchange(PAYMENTS + "/" + payNo + "/mock-pay", HttpMethod.POST,
                null, customerToken, "dish-review-mock-pay-" + suffix());
        assertThat(codeOf(paid)).as("mock pay body=%s", paid.getBody()).isZero();

        for (String action : List.of("accept", "prepare", "deliver", "complete")) {
            ResponseEntity<String> transition = exchange(MERCHANT_ORDERS + "/" + orderId + "/" + action,
                    HttpMethod.POST, null, merchantToken, null);
            assertThat(codeOf(transition)).as("action=%s body=%s", action, transition.getBody()).isZero();
        }
        return new CompletedOrder(orderId, orderNo);
    }

    private long createDish(String merchantToken, String name, String price, int stock) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("categoryId", 1);
        body.put("name", name);
        body.put("price", new BigDecimal(price));
        body.put("stock", stock);
        ResponseEntity<String> response = exchange(MERCHANT_DISHES, HttpMethod.POST, body, merchantToken, null);
        assertThat(codeOf(response)).as("create dish body=%s", response.getBody()).isZero();
        return ((Number) JsonPath.read(response.getBody(), "$.data.id")).longValue();
    }

    private String login(String username, String password) throws Exception {
        ResponseEntity<String> response = exchange("/api/v1/auth/login", HttpMethod.POST,
                Map.of("username", username, "password", password), null, null);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        return JsonPath.read(response.getBody(), "$.data.token");
    }

    private ResponseEntity<String> get(String path, String token) {
        return exchange(path, HttpMethod.GET, null, token, null);
    }

    private ResponseEntity<String> exchange(String path, HttpMethod method, Object body, String token,
                                            String idempotencyKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            headers.setBearerAuth(token);
        }
        if (idempotencyKey != null) {
            headers.set("Idempotency-Key", idempotencyKey);
        }
        try {
            String payload = body == null ? null : objectMapper.writeValueAsString(body);
            return restTemplate.exchange(path, method, new HttpEntity<>(payload, headers), String.class);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
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
