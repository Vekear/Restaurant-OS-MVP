package com.restaurant.order;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.restaurant.menu.mapper.DishMapper;
import com.restaurant.order.mapper.OrderMapper;
import com.restaurant.payment.Payment;
import com.restaurant.payment.mapper.PaymentMapper;
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
 * 幂等回放归属隔离集成测试（T58 回归，安全缺陷 H-02）。
 *
 * <p>缺陷背景：数据库 {@code idempotency_record} 只按 {@code idempotency_key} 查询，
 * 而 Redis sidecar 已按 {@code userId} 隔离；用户 B 复用用户 A 的 Idempotency-Key 时会命中
 * A 的 SUCCESS 记录并原样回放 A 的响应体（订单/支付 PII）。
 *
 * <p>本测试覆盖：跨用户复用同 key → 40904 且响应不含他人订单号/地址/备注；B 不产生订单或支付单；
 * 同用户重放行为不变（Idempotency-Replayed=true）；同用户异体同 key → 40904；匿名请求 401。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class IdempotencyOwnershipIntegrationTest {

    private static final String ORDERS = "/api/v1/orders";

    private static final String PAYMENTS = "/api/v1/payments";

    private static final long MERCHANT_ID = 1L;

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
    private PaymentMapper paymentMapper;

    @BeforeEach
    void clearRateLimitKeys() {
        Set<String> keys = stringRedisTemplate.keys("rate:*");
        if (keys != null && !keys.isEmpty()) {
            stringRedisTemplate.delete(keys);
        }
    }

    @Test
    void crossUserOrderReplayIsRejectedWithoutLeakingOwnerData() throws Exception {
        String merchantToken = login("merchant", "merchant123");
        long dishId = createDish(merchantToken, "幂等越权菜品" + suffix(), "20.00", 10);
        TestUser a = register();
        long addressA = createAddress(a.token, "私密收货人" + suffix(), "上海市浦东新区张江路-" + suffix());

        String key = "idem-cross-order-" + suffix();
        String secretRemark = "TOPSECRET-" + suffix();
        Map<String, Object> orderBody = orderBody(dishId, addressA, secretRemark);

        ResponseEntity<String> first = postJson(ORDERS, orderBody, a.token, key);
        assertThat(first.getStatusCode().value()).isEqualTo(200);
        assertThat(codeOf(first)).isZero();
        String orderNo = JsonPath.read(first.getBody(), "$.data.orderNo");
        long orderId = ((Number) JsonPath.read(first.getBody(), "$.data.id")).longValue();
        assertThat(dishMapper.selectById(dishId).getStock()).isEqualTo(9);

        // B 复用 A 的 key（请求体相同，最大化复现原始越权路径）
        TestUser b = register();
        ResponseEntity<String> crossUser = postJson(ORDERS, orderBody, b.token, key);
        assertThat(crossUser.getStatusCode().value()).isEqualTo(409);
        assertThat(codeOf(crossUser)).isEqualTo(40904);
        assertThat(crossUser.getHeaders().getFirst("Idempotency-Replayed")).isNull();
        assertThat(crossUser.getBody())
                .doesNotContain(orderNo)
                .doesNotContain(secretRemark)
                .doesNotContain("私密收货人");
        assertThat(orderMapper.selectCount(Wrappers.<Order>lambdaQuery().eq(Order::getUserId, b.userId)))
                .isZero();
        assertThat(dishMapper.selectById(dishId).getStock()).isEqualTo(9);

        // A 重放：行为不变，返回原单
        ResponseEntity<String> replay = postJson(ORDERS, orderBody, a.token, key);
        assertThat(replay.getStatusCode().value()).isEqualTo(200);
        assertThat(replay.getHeaders().getFirst("Idempotency-Replayed")).isEqualTo("true");
        assertThat(((Number) JsonPath.read(replay.getBody(), "$.data.id")).longValue()).isEqualTo(orderId);
        assertThat(orderMapper.selectCount(Wrappers.<Order>lambdaQuery().eq(Order::getOrderNo, orderNo)))
                .isEqualTo(1L);
        assertThat(dishMapper.selectById(dishId).getStock()).isEqualTo(9);

        // 同用户异体同 key：仍为幂等键冲突
        Map<String, Object> differentBody = orderBody(dishId, addressA, "改过的备注-" + suffix());
        ResponseEntity<String> sameUserConflict = postJson(ORDERS, differentBody, a.token, key);
        assertThat(sameUserConflict.getStatusCode().value()).isEqualTo(409);
        assertThat(codeOf(sameUserConflict)).isEqualTo(40904);

        // 匿名同 key：安全层 401，不触达幂等记录
        ResponseEntity<String> anonymous = postJson(ORDERS, orderBody, null, key);
        assertThat(anonymous.getStatusCode().value()).isEqualTo(401);
        assertThat(codeOf(anonymous)).isEqualTo(40101);
        assertThat(anonymous.getBody()).doesNotContain(orderNo);
        assertThat(orderMapper.selectCount(Wrappers.<Order>lambdaQuery().eq(Order::getOrderNo, orderNo)))
                .isEqualTo(1L);
    }

    @Test
    void crossUserPaymentAndMockPayReplayIsRejectedWithoutLeakingOwnerData() throws Exception {
        String merchantToken = login("merchant", "merchant123");
        long dishId = createDish(merchantToken, "幂等越权支付菜品" + suffix(), "20.00", 10);
        TestUser a = register();
        long addressA = createAddress(a.token, "支付顾客" + suffix(), "北京市朝阳区测试路-" + suffix());
        String orderKey = "idem-cross-pay-order-" + suffix();
        ResponseEntity<String> order = postJson(ORDERS, orderBody(dishId, addressA, "支付用订单"), a.token, orderKey);
        assertThat(order.getStatusCode().value()).isEqualTo(200);
        long orderId = ((Number) JsonPath.read(order.getBody(), "$.data.id")).longValue();
        String orderNo = JsonPath.read(order.getBody(), "$.data.orderNo");

        String paymentKey = "idem-cross-payment-" + suffix();
        Map<String, Object> paymentBody = Map.of("orderId", orderId);
        ResponseEntity<String> payment = postJson(PAYMENTS, paymentBody, a.token, paymentKey);
        assertThat(payment.getStatusCode().value()).isEqualTo(200);
        String payNo = JsonPath.read(payment.getBody(), "$.data.payNo");
        assertThat(JsonPath.<String>read(payment.getBody(), "$.data.orderNo")).isEqualTo(orderNo);

        TestUser b = register();
        ResponseEntity<String> crossUserPayment = postJson(PAYMENTS, paymentBody, b.token, paymentKey);
        assertThat(crossUserPayment.getStatusCode().value()).isEqualTo(409);
        assertThat(codeOf(crossUserPayment)).isEqualTo(40904);
        assertThat(crossUserPayment.getHeaders().getFirst("Idempotency-Replayed")).isNull();
        assertThat(crossUserPayment.getBody()).doesNotContain(payNo).doesNotContain(orderNo);
        assertThat(paymentMapper.selectCount(
                Wrappers.<Payment>lambdaQuery().eq(Payment::getUserId, b.userId))).isZero();

        // 同用户支付重放行为不变
        ResponseEntity<String> paymentReplay = postJson(PAYMENTS, paymentBody, a.token, paymentKey);
        assertThat(paymentReplay.getStatusCode().value()).isEqualTo(200);
        assertThat(paymentReplay.getHeaders().getFirst("Idempotency-Replayed")).isEqualTo("true");
        assertThat(JsonPath.<String>read(paymentReplay.getBody(), "$.data.payNo")).isEqualTo(payNo);

        // mock-pay 回放同样按用户隔离
        String mockPayKey = "idem-cross-mockpay-" + suffix();
        ResponseEntity<String> mockPay = postJson(PAYMENTS + "/" + payNo + "/mock-pay", null, a.token, mockPayKey);
        assertThat(mockPay.getStatusCode().value()).isEqualTo(200);
        assertThat(codeOf(mockPay)).isZero();

        ResponseEntity<String> crossUserMockPay = postJson(
                PAYMENTS + "/" + payNo + "/mock-pay", null, b.token, mockPayKey);
        assertThat(crossUserMockPay.getStatusCode().value()).isEqualTo(409);
        assertThat(codeOf(crossUserMockPay)).isEqualTo(40904);
        assertThat(crossUserMockPay.getBody()).doesNotContain(payNo).doesNotContain(orderNo);
    }

    private Map<String, Object> orderBody(long dishId, long addressId, String remark) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("dishId", dishId);
        item.put("quantity", 1);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("merchantId", MERCHANT_ID);
        body.put("addressId", addressId);
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

    private long createAddress(String token, String consignee, String detail) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("consignee", consignee);
        body.put("phone", "13800138000");
        body.put("province", "上海市");
        body.put("city", "上海市");
        body.put("district", "浦东新区");
        body.put("detail", detail);
        body.put("isDefault", true);
        ResponseEntity<String> response = postJson("/api/v1/addresses", body, token, null);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(codeOf(response)).isZero();
        return ((Number) JsonPath.read(response.getBody(), "$.data.id")).longValue();
    }

    private TestUser register() throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("username", "idem" + suffix());
        body.put("password", "pass1234");
        ResponseEntity<String> response = postJson("/api/v1/auth/register", body, null, null);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(codeOf(response)).isZero();
        return new TestUser(JsonPath.read(response.getBody(), "$.data.token"),
                ((Number) JsonPath.read(response.getBody(), "$.data.user.id")).longValue());
    }

    private String login(String username, String password) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("username", username);
        body.put("password", password);
        ResponseEntity<String> response = postJson("/api/v1/auth/login", body, null, null);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(codeOf(response)).isZero();
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

    private record TestUser(String token, long userId) {
    }

}
