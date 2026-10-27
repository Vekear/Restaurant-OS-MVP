package com.restaurant.order;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.restaurant.common.enums.OrderStatus;
import com.restaurant.menu.mapper.DishMapper;
import com.restaurant.order.mapper.OrderMapper;
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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 订单接口集成测试（真实 Tomcat + MySQL restaurant_mvp_test + Redis db15）。
 *
 * <p>覆盖 G2 门禁：下单幂等、并发扣库存不超卖、取消/超时回补库存、非法状态流转拒绝。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class OrderApiIntegrationTest {

    private static final String ORDERS = "/api/v1/orders";

    private static final long MERCHANT_ID = 1L;

    private static final long ADDRESS_ID = 1L;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private DishMapper dishMapper;

    @Autowired
    private OrderTimeoutService orderTimeoutService;

    @BeforeEach
    void clearRateLimitKeys() {
        Set<String> keys = stringRedisTemplate.keys("rate:*");
        if (keys != null && !keys.isEmpty()) {
            stringRedisTemplate.delete(keys);
        }
    }

    @Test
    void createOrderIsIdempotentAndRejectsSameKeyWithDifferentBody() throws Exception {
        String merchantToken = login("merchant", "merchant123");
        long dishId = createDish(merchantToken, "幂等菜品" + suffix(), "20.00", 10);
        String customerToken = login("customer", "customer123");
        String key = "order-idem-" + suffix();
        Map<String, Object> body = orderBody(dishId, 2, "第一次下单");

        ResponseEntity<String> first = postJson(ORDERS, body, customerToken, key);
        assertThat(first.getStatusCode().value()).isEqualTo(200);
        assertThat(codeOf(first)).isZero();
        assertThat((String) JsonPath.read(first.getBody(), "$.data.status")).isEqualTo("PENDING_PAYMENT");
        assertThat(((Number) JsonPath.read(first.getBody(), "$.data.payAmount")).doubleValue()).isEqualTo(45.00);
        assertThat((Boolean) JsonPath.read(first.getBody(), "$.data.canPay")).isTrue();
        String orderNo = JsonPath.read(first.getBody(), "$.data.orderNo");
        long orderId = ((Number) JsonPath.read(first.getBody(), "$.data.id")).longValue();

        ResponseEntity<String> replay = postJson(ORDERS, body, customerToken, key);
        assertThat(replay.getStatusCode().value()).isEqualTo(200);
        assertThat(replay.getHeaders().getFirst("Idempotency-Replayed")).isEqualTo("true");
        assertThat(((Number) JsonPath.read(replay.getBody(), "$.data.id")).longValue()).isEqualTo(orderId);
        assertThat(orderMapper.selectCount(Wrappers.<Order>lambdaQuery().eq(Order::getOrderNo, orderNo)))
                .isEqualTo(1L);
        assertThat(dishMapper.selectById(dishId).getStock()).isEqualTo(8);

        Map<String, Object> differentBody = orderBody(dishId, 2, "改了备注");
        ResponseEntity<String> conflict = postJson(ORDERS, differentBody, customerToken, key);
        assertThat(conflict.getStatusCode().value()).isEqualTo(409);
        assertThat(codeOf(conflict)).isEqualTo(40904);
        assertThat(dishMapper.selectById(dishId).getStock()).isEqualTo(8);
    }

    @Test
    void concurrentOrdersNeverOversell() throws Exception {
        String merchantToken = login("merchant", "merchant123");
        long dishId = createDish(merchantToken, "并发菜品" + suffix(), "30.00", 5);
        String customerToken = login("customer", "customer123");

        int attempts = 8;
        ExecutorService pool = Executors.newFixedThreadPool(attempts);
        CountDownLatch start = new CountDownLatch(1);
        List<Callable<ResponseEntity<String>>> tasks = new ArrayList<>();
        for (int i = 0; i < attempts; i++) {
            final String key = "order-concurrent-" + i + "-" + suffix();
            tasks.add(() -> {
                start.await(5, TimeUnit.SECONDS);
                return postJson(ORDERS, orderBody(dishId, 1, "并发下单"), customerToken, key);
            });
        }
        List<Future<ResponseEntity<String>>> futures = new ArrayList<>();
        for (Callable<ResponseEntity<String>> task : tasks) {
            futures.add(pool.submit(task));
        }
        start.countDown();
        int success = 0;
        int insufficient = 0;
        for (Future<ResponseEntity<String>> future : futures) {
            ResponseEntity<String> response = future.get(30, TimeUnit.SECONDS);
            int code = codeOf(response);
            if (code == 0) {
                success++;
            } else if (code == 40901) {
                assertThat(response.getStatusCode().value()).isEqualTo(409);
                insufficient++;
            }
        }
        pool.shutdown();
        assertThat(success).isEqualTo(5);
        assertThat(insufficient).isEqualTo(3);
        assertThat(dishMapper.selectById(dishId).getStock()).isZero();
    }

    @Test
    void cancelRestoresStockAndIllegalTransitionsAreRejected() throws Exception {
        String merchantToken = login("merchant", "merchant123");
        String dishName = "取消菜品" + suffix();
        long dishId = createDish(merchantToken, dishName, "12.00", 5);
        String customerToken = login("customer", "customer123");
        ResponseEntity<String> created = postJson(ORDERS, orderBody(dishId, 2, "待取消订单"),
                customerToken, "order-cancel-" + suffix());
        assertThat(codeOf(created)).isZero();
        long orderId = ((Number) JsonPath.read(created.getBody(), "$.data.id")).longValue();
        assertThat(dishMapper.selectById(dishId).getStock()).isEqualTo(3);

        ResponseEntity<String> accepted = postJson("/api/v1/merchant/orders/" + orderId + "/accept", null,
                merchantToken, null);
        assertThat(accepted.getStatusCode().value()).isEqualTo(409);
        assertThat(codeOf(accepted)).isEqualTo(40905);

        ResponseEntity<String> cancelled = postJson(ORDERS + "/" + orderId + "/cancel",
                Map.of("reason", "不想要了"), customerToken, null);
        assertThat(cancelled.getStatusCode().value()).isEqualTo(200);
        assertThat((String) JsonPath.read(cancelled.getBody(), "$.data.status")).isEqualTo("CANCELLED");
        assertThat(dishMapper.selectById(dishId).getStock()).isEqualTo(5);

        ResponseEntity<String> cancelAgain = postJson(ORDERS + "/" + orderId + "/cancel", null,
                customerToken, null);
        assertThat(cancelAgain.getStatusCode().value()).isEqualTo(409);
        assertThat(codeOf(cancelAgain)).isEqualTo(40905);

        ResponseEntity<String> acceptAfterCancel = postJson("/api/v1/merchant/orders/" + orderId + "/accept",
                null, merchantToken, null);
        assertThat(acceptAfterCancel.getStatusCode().value()).isEqualTo(409);
        assertThat(codeOf(acceptAfterCancel)).isEqualTo(40905);
    }

    @Test
    void timeoutTaskCancelsUnpaidOrderAndRestoresStock() throws Exception {
        String merchantToken = login("merchant", "merchant123");
        long dishId = createDish(merchantToken, "超时菜品" + suffix(), "25.00", 5);
        String customerToken = login("customer", "customer123");
        ResponseEntity<String> created = postJson(ORDERS, orderBody(dishId, 1, "超时单"),
                customerToken, "order-timeout-" + suffix());
        assertThat(codeOf(created)).isZero();
        long orderId = ((Number) JsonPath.read(created.getBody(), "$.data.id")).longValue();
        assertThat(dishMapper.selectById(dishId).getStock()).isEqualTo(4);

        orderMapper.update(null, Wrappers.<Order>lambdaUpdate()
                .eq(Order::getId, orderId)
                .set(Order::getTimeoutAt, LocalDateTime.now().minusMinutes(1)));
        int cancelled = orderTimeoutService.cancelTimedOut(200);
        assertThat(cancelled).isGreaterThanOrEqualTo(1);

        Order stored = orderMapper.selectById(orderId);
        assertThat(stored.getStatus()).isEqualTo(OrderStatus.CANCELLED.name());
        assertThat(stored.getCancelReason()).isEqualTo("PAYMENT_TIMEOUT");
        assertThat(dishMapper.selectById(dishId).getStock()).isEqualTo(5);

        assertThat(orderTimeoutService.cancelTimedOut(200)).isZero();
    }

    @Test
    void orderListAndDetailAreScopedToCurrentCustomer() throws Exception {
        String merchantToken = login("merchant", "merchant123");
        String dishName = "列表菜品" + suffix();
        long dishId = createDish(merchantToken, dishName, "20.00", 10);
        String customerToken = login("customer", "customer123");
        ResponseEntity<String> created = postJson(ORDERS, orderBody(dishId, 1, "列表订单"),
                customerToken, "order-list-" + suffix());
        long orderId = ((Number) JsonPath.read(created.getBody(), "$.data.id")).longValue();

        ResponseEntity<String> list = get("/api/v1/orders?statusGroup=ACTIVE&page=1&size=50", customerToken);
        assertThat(list.getStatusCode().value()).isEqualTo(200);
        List<Number> ids = JsonPath.read(list.getBody(), "$.data.list[*].id");
        assertThat(ids.stream().map(Number::longValue)).contains(orderId);

        ResponseEntity<String> detail = get(ORDERS + "/" + orderId, customerToken);
        assertThat(detail.getStatusCode().value()).isEqualTo(200);
        assertThat(((Number) JsonPath.read(detail.getBody(), "$.data.itemCount")).intValue()).isEqualTo(1);
        assertThat((String) JsonPath.read(detail.getBody(), "$.data.items[0].dishName")).isEqualTo(dishName);
        assertThat((String) JsonPath.read(detail.getBody(), "$.data.consignee")).isEqualTo("李小明");

        ResponseEntity<String> merchantDetail = get(ORDERS + "/" + orderId, merchantToken);
        assertThat(merchantDetail.getStatusCode().value()).isEqualTo(403);
        assertThat(codeOf(merchantDetail)).isEqualTo(40302);
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
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(codeOf(response)).isZero();
        return ((Number) JsonPath.read(response.getBody(), "$.data.id")).longValue();
    }

    private String login(String username, String password) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("username", username);
        body.put("password", password);
        ResponseEntity<String> response = postJson("/api/v1/auth/login", body, null, null);
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
}
