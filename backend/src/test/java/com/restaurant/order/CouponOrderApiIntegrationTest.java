package com.restaurant.order;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.restaurant.coupon.UserCoupon;
import com.restaurant.coupon.mapper.UserCouponMapper;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 下单用券集成测试（T56 回归）。
 *
 * <p>缺陷背景：{@code CouponPortAdapter.consume} 过去固定把 userId 传 null，SQL 条件
 * {@code AND user_id = NULL} 永远不成立 → 用券下单必然 40912。本测试覆盖：
 * 用券下单成功且金额与 /coupons/quote 一致、券置 USED、支付单金额正确；重复/无效/他人券 40912；
 * 无券下单不受影响。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class CouponOrderApiIntegrationTest {

    private static final String ORDERS = "/api/v1/orders";

    private static final String COUPONS = "/api/v1/coupons";

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
    private UserCouponMapper userCouponMapper;

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
    void couponOrderConsumesCouponAndMatchesQuote() throws Exception {
        String adminToken = login("admin", "admin123");
        String merchantToken = login("merchant", "merchant123");
        long dishId = createDish(merchantToken, "用券菜品" + suffix(), "32.00", 20);
        long couponId = createCoupon(adminToken, "满50减10-" + suffix(), "50.00", "10.00", 10);

        String customerToken = register();
        long addressId = createAddress(customerToken);
        long userCouponId = claim(customerToken, couponId);

        // 购物车/试算金额：32*2=64，满 50 减 10，配送费 5 → 59
        Map<String, Object> quoteBody = new LinkedHashMap<>();
        quoteBody.put("merchantId", MERCHANT_ID);
        quoteBody.put("totalAmount", new BigDecimal("64.00"));
        quoteBody.put("userCouponId", userCouponId);
        ResponseEntity<String> quote = postJson(COUPONS + "/quote", quoteBody, customerToken, null);
        assertThat(quote.getStatusCode().value()).isEqualTo(200);
        assertThat(codeOf(quote)).isZero();
        double quotePayAmount = decimalAt(quote, "$.data.payAmount");
        assertThat(decimalAt(quote, "$.data.discountAmount")).isEqualTo(10.00);
        assertThat(decimalAt(quote, "$.data.deliveryFee")).isEqualTo(5.00);
        assertThat(quotePayAmount).isEqualTo(59.00);

        Map<String, Object> orderBody = orderBody(dishId, addressId, 2, userCouponId);
        ResponseEntity<String> created = postJson(ORDERS, orderBody, customerToken, "coupon-order-" + suffix());
        assertThat(created.getStatusCode().value()).isEqualTo(200);
        assertThat(codeOf(created)).isZero();
        long orderId = ((Number) JsonPath.read(created.getBody(), "$.data.id")).longValue();
        assertThat(decimalAt(created, "$.data.totalAmount")).isEqualTo(64.00);
        assertThat(decimalAt(created, "$.data.discountAmount")).isEqualTo(10.00);
        assertThat(decimalAt(created, "$.data.payAmount")).isEqualTo(quotePayAmount);

        // 券已核销并绑定该订单
        UserCoupon used = userCouponMapper.selectById(userCouponId);
        assertThat(used.getStatus()).isEqualTo("USED");
        assertThat(used.getOrderId()).isEqualTo(orderId);
        assertThat(used.getUsedAt()).isNotNull();

        // 订单金额与库存
        Order order = orderMapper.selectById(orderId);
        assertThat(order.getUserCouponId()).isEqualTo(userCouponId);
        assertThat(order.getDiscountAmount()).isEqualByComparingTo("10.00");
        assertThat(order.getPayAmount()).isEqualByComparingTo("59.00");
        assertThat(dishMapper.selectById(dishId).getStock()).isEqualTo(18);

        // 支付单金额 = 订单应付金额（模拟扣款仍为 0.01）
        Map<String, Object> payBody = new LinkedHashMap<>();
        payBody.put("orderId", orderId);
        ResponseEntity<String> payment = postJson("/api/v1/payments", payBody, customerToken, "coupon-pay-" + suffix());
        assertThat(payment.getStatusCode().value()).isEqualTo(200);
        assertThat(codeOf(payment)).isZero();
        assertThat(decimalAt(payment, "$.data.orderAmount")).isEqualTo(59.00);
        assertThat(decimalAt(payment, "$.data.paidAmount")).isEqualTo(0.01);
    }

    @Test
    void consumedOrForeignOrUnknownCouponIsRejected() throws Exception {
        String adminToken = login("admin", "admin123");
        String merchantToken = login("merchant", "merchant123");
        long dishId = createDish(merchantToken, "重复用券菜品" + suffix(), "30.00", 10);
        long couponId = createCoupon(adminToken, "满50减10-" + suffix(), "50.00", "10.00", 10);

        String customerToken = register();
        long addressId = createAddress(customerToken);
        long userCouponId = claim(customerToken, couponId);
        Map<String, Object> body = orderBody(dishId, addressId, 2, userCouponId);

        ResponseEntity<String> first = postJson(ORDERS, body, customerToken, "coupon-dup-1-" + suffix());
        assertThat(first.getStatusCode().value()).isEqualTo(200);
        long firstOrderId = ((Number) JsonPath.read(first.getBody(), "$.data.id")).longValue();
        assertThat(dishMapper.selectById(dishId).getStock()).isEqualTo(8);
        long orderCountAfterFirst = countOrders(userCouponMapper.selectById(userCouponId).getUserId());

        // 同一张已核销券再次下单 → 40912，订单/库存/券状态不变
        ResponseEntity<String> reused = postJson(ORDERS, body, customerToken, "coupon-dup-2-" + suffix());
        assertThat(reused.getStatusCode().value()).isEqualTo(409);
        assertThat(codeOf(reused)).isEqualTo(40912);
        assertThat(dishMapper.selectById(dishId).getStock()).isEqualTo(8);
        assertThat(countOrders(userCouponMapper.selectById(userCouponId).getUserId()))
                .isEqualTo(orderCountAfterFirst);
        assertThat(userCouponMapper.selectById(userCouponId).getOrderId()).isEqualTo(firstOrderId);

        // 不存在的券 → 40912
        Map<String, Object> unknown = orderBody(dishId, addressId, 1, 999999999L);
        ResponseEntity<String> unknownCoupon = postJson(ORDERS, unknown, customerToken, "coupon-unknown-" + suffix());
        assertThat(unknownCoupon.getStatusCode().value()).isEqualTo(409);
        assertThat(codeOf(unknownCoupon)).isEqualTo(40912);

        // 他人券 → 40912（券归属校验）
        String otherToken = register();
        long otherAddressId = createAddress(otherToken);
        long otherCouponId = claim(otherToken, couponId);
        Map<String, Object> foreign = orderBody(dishId, addressId, 1, otherCouponId);
        ResponseEntity<String> foreignCoupon = postJson(ORDERS, foreign, customerToken, "coupon-foreign-" + suffix());
        assertThat(foreignCoupon.getStatusCode().value()).isEqualTo(409);
        assertThat(codeOf(foreignCoupon)).isEqualTo(40912);
        assertThat(userCouponMapper.selectById(otherCouponId).getStatus()).isEqualTo("UNUSED");
        assertThat(otherAddressId).isPositive();
    }

    @Test
    void orderWithoutCouponStillWorks() throws Exception {
        String merchantToken = login("merchant", "merchant123");
        long dishId = createDish(merchantToken, "无券菜品" + suffix(), "28.00", 10);
        String customerToken = register();
        long addressId = createAddress(customerToken);

        ResponseEntity<String> created = postJson(ORDERS,
                orderBody(dishId, addressId, 1, null), customerToken, "no-coupon-" + suffix());
        assertThat(created.getStatusCode().value()).isEqualTo(200);
        assertThat(codeOf(created)).isZero();
        assertThat(decimalAt(created, "$.data.discountAmount")).isZero();
        // 28 + 配送费 5 = 33
        assertThat(decimalAt(created, "$.data.payAmount")).isEqualTo(33.00);
        long orderId = ((Number) JsonPath.read(created.getBody(), "$.data.id")).longValue();
        assertThat(orderMapper.selectById(orderId).getUserCouponId()).isNull();
        assertThat(dishMapper.selectById(dishId).getStock()).isEqualTo(9);
    }

    private long countOrders(long userId) {
        return orderMapper.selectCount(Wrappers.<Order>lambdaQuery().eq(Order::getUserId, userId));
    }

    private Map<String, Object> orderBody(long dishId, long addressId, int quantity, Long userCouponId) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("dishId", dishId);
        item.put("quantity", quantity);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("merchantId", MERCHANT_ID);
        body.put("addressId", addressId);
        body.put("items", List.of(item));
        if (userCouponId != null) {
            body.put("userCouponId", userCouponId);
        }
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

    private long createCoupon(String adminToken, String name, String threshold, String discount, int total)
            throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name);
        body.put("thresholdAmount", new BigDecimal(threshold));
        body.put("discountAmount", new BigDecimal(discount));
        body.put("total", total);
        body.put("startAt", "2026-01-01T00:00:00");
        body.put("endAt", "2030-12-31T23:59:59");
        ResponseEntity<String> response = postJson("/api/v1/admin/coupons", body, adminToken, null);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(codeOf(response)).isZero();
        return ((Number) JsonPath.read(response.getBody(), "$.data.id")).longValue();
    }

    private long claim(String customerToken, long couponId) throws Exception {
        ResponseEntity<String> response = postJson(COUPONS + "/" + couponId + "/claim", null, customerToken, null);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(codeOf(response)).isZero();
        assertThat((String) JsonPath.read(response.getBody(), "$.data.status")).isEqualTo("UNUSED");
        return ((Number) JsonPath.read(response.getBody(), "$.data.id")).longValue();
    }

    private long createAddress(String customerToken) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("consignee", "用券顾客");
        body.put("phone", "13800138000");
        body.put("province", "上海市");
        body.put("city", "上海市");
        body.put("district", "浦东新区");
        body.put("detail", "张江路 88 号 3 号楼 501");
        body.put("isDefault", true);
        ResponseEntity<String> response = postJson("/api/v1/addresses", body, customerToken, null);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(codeOf(response)).isZero();
        return ((Number) JsonPath.read(response.getBody(), "$.data.id")).longValue();
    }

    private String register() throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("username", "co" + suffix());
        body.put("password", "pass1234");
        ResponseEntity<String> response = postJson("/api/v1/auth/register", body, null, null);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(codeOf(response)).isZero();
        return JsonPath.read(response.getBody(), "$.data.token");
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

    private double decimalAt(ResponseEntity<String> response, String path) {
        Number value = JsonPath.read(response.getBody(), path);
        return value == null ? -1 : value.doubleValue();
    }

    private static String suffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 6);
    }
}
