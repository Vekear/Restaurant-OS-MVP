package com.restaurant.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.restaurant.common.config.AppProperties;
import com.restaurant.contract.event.PaymentSucceededEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
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
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 支付接口集成测试：创建支付单幂等、mock-pay（回调降级）、回调验签/时钟偏移/金额/nonce 重放、
 * 已取消订单回调置 REFUNDED。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(PaymentApiIntegrationTest.EventConfig.class)
class PaymentApiIntegrationTest {

    private static final String PAYMENTS = "/api/v1/payments";

    private static final long MERCHANT_ID = 1L;

    private static final long ADDRESS_ID = 1L;

    private static final DateTimeFormatter SECONDS = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AppProperties appProperties;

    @Autowired
    private PaymentEventCounter paymentEventCounter;

    @BeforeEach
    void clearRateLimitKeys() {
        paymentEventCounter.reset();
        Set<String> keys = stringRedisTemplate.keys("rate:*");
        if (keys != null && !keys.isEmpty()) {
            stringRedisTemplate.delete(keys);
        }
    }

    @Test
    void createPaymentMockPayAndReplayAreIdempotent() throws Exception {
        String customerToken = login("customer", "customer123");
        long dishId = createDish("支付菜品" + suffix(), "20.00", 10);
        long orderId = createOrder(customerToken, dishId, 2);
        String createKey = "pay-create-" + suffix();
        Map<String, Object> createBody = paymentBody(orderId);

        ResponseEntity<String> created = postJson(PAYMENTS, createBody, customerToken, createKey);
        assertThat(created.getStatusCode().value()).isEqualTo(200);
        assertThat(codeOf(created)).isZero();
        assertThat(((Number) JsonPath.read(created.getBody(), "$.data.orderAmount")).doubleValue()).isEqualTo(45.00);
        assertThat(((Number) JsonPath.read(created.getBody(), "$.data.paidAmount")).doubleValue()).isEqualTo(0.01);
        assertThat((String) JsonPath.read(created.getBody(), "$.data.channel")).isEqualTo("MOCK");
        assertThat((Boolean) JsonPath.read(created.getBody(), "$.data.mock")).isTrue();
        assertThat((String) JsonPath.read(created.getBody(), "$.data.mockHint")).contains("0.01");
        String payNo = JsonPath.read(created.getBody(), "$.data.payNo");

        ResponseEntity<String> replay = postJson(PAYMENTS, createBody, customerToken, createKey);
        assertThat(replay.getHeaders().getFirst("Idempotency-Replayed")).isEqualTo("true");
        assertThat((String) JsonPath.read(replay.getBody(), "$.data.payNo")).isEqualTo(payNo);

        ResponseEntity<String> paid = postJson(PAYMENTS + "/" + payNo + "/mock-pay", null,
                customerToken, "pay-mock-" + suffix());
        assertThat(paid.getStatusCode().value()).isEqualTo(200);
        assertThat(codeOf(paid)).isZero();
        assertThat((String) JsonPath.read(paid.getBody(), "$.data.status")).isEqualTo("SUCCESS");
        assertThat(((Number) JsonPath.read(paid.getBody(), "$.data.paidAmount")).doubleValue()).isEqualTo(0.01);

        ResponseEntity<String> detail = get("/api/v1/orders/" + orderId, customerToken);
        assertThat((String) JsonPath.read(detail.getBody(), "$.data.status")).isEqualTo("PAID");
        assertThat((String) JsonPath.read(detail.getBody(), "$.data.payment.status")).isEqualTo("SUCCESS");

        ResponseEntity<String> mockAgain = postJson(PAYMENTS + "/" + payNo + "/mock-pay", null,
                customerToken, "pay-mock-again-" + suffix());
        assertThat((String) JsonPath.read(mockAgain.getBody(), "$.data.status")).isEqualTo("SUCCESS");

        ResponseEntity<String> alreadyPaid = postJson(PAYMENTS, createBody, customerToken, "pay-create-2-" + suffix());
        assertThat(alreadyPaid.getStatusCode().value()).isEqualTo(409);
        assertThat(codeOf(alreadyPaid)).isEqualTo(40913);
    }

    @Test
    void callbackRejectsBadSignatureClockSkewAndWrongAmount() throws Exception {
        String customerToken = login("customer", "customer123");
        long dishId = createDish("回调菜品" + suffix(), "15.00", 10);
        long orderId = createOrder(customerToken, dishId, 2);
        String payNo = createPayment(customerToken, orderId);

        String body = callbackBody(payNo, "TX" + suffix(), "0.01", "SUCCESS");
        long now = System.currentTimeMillis();

        assertThat(callback(body, now, nonce(), "deadbeef")).satisfies(response -> {
            assertThat(response.getStatusCode().value()).isEqualTo(400);
            assertThat(codeOf(response)).isEqualTo(40001);
        });

        long stale = now - 10 * 60 * 1000L;
        String staleSignature = MockPayGatewayClient.hmacSha256Hex(
                appProperties.getPayment().getCallbackSecret(), stale + "\n" + nonce() + "\n" + body);
        String staleNonce = "stale" + suffix();
        String sig = MockPayGatewayClient.hmacSha256Hex(
                appProperties.getPayment().getCallbackSecret(), stale + "\n" + staleNonce + "\n" + body);
        ResponseEntity<String> skew = callback(body, stale, staleNonce, sig);
        assertThat(skew.getStatusCode().value()).isEqualTo(400);
        assertThat(codeOf(skew)).isEqualTo(40001);

        String wrongAmountBody = callbackBody(payNo, "TX" + suffix(), "1.00", "SUCCESS");
        String wrongNonce = nonce();
        long ts = System.currentTimeMillis();
        String wrongSig = MockPayGatewayClient.hmacSha256Hex(
                appProperties.getPayment().getCallbackSecret(), ts + "\n" + wrongNonce + "\n" + wrongAmountBody);
        ResponseEntity<String> mismatch = callback(wrongAmountBody, ts, wrongNonce, wrongSig);
        assertThat(mismatch.getStatusCode().value()).isEqualTo(400);
        assertThat(codeOf(mismatch)).isEqualTo(40005);
        assertThat(staleSignature).isNotBlank();
    }

    @Test
    void callbackSucceedsSetsOrderPaidAndNonceCannotBeReplayed() throws Exception {
        String customerToken = login("customer", "customer123");
        long dishId = createDish("回调成功菜品" + suffix(), "25.00", 10);
        long orderId = createOrder(customerToken, dishId, 1);
        String payNo = createPayment(customerToken, orderId);

        String transactionNo = "TXOK" + suffix();
        String body = callbackBody(payNo, transactionNo, "0.01", "SUCCESS");
        long ts = System.currentTimeMillis();
        String usedNonce = nonce();
        String signature = MockPayGatewayClient.hmacSha256Hex(
                appProperties.getPayment().getCallbackSecret(), ts + "\n" + usedNonce + "\n" + body);

        ResponseEntity<String> ok = callback(body, ts, usedNonce, signature);
        assertThat(ok.getStatusCode().value()).isEqualTo(200);
        assertThat((Boolean) JsonPath.read(ok.getBody(), "$.data.received")).isTrue();
        assertThat((String) JsonPath.read(ok.getBody(), "$.data.status")).isEqualTo("SUCCESS");

        ResponseEntity<String> detail = get("/api/v1/orders/" + orderId, customerToken);
        assertThat((String) JsonPath.read(detail.getBody(), "$.data.status")).isEqualTo("PAID");
        assertThat((String) JsonPath.read(detail.getBody(), "$.data.payment.transactionNo")).isEqualTo(transactionNo);
        assertThat(((Number) JsonPath.read(detail.getBody(), "$.data.payment.paidAmount")).doubleValue())
                .isEqualTo(0.01);

        ResponseEntity<String> replayNonce = callback(body, ts, usedNonce, signature);
        assertThat(replayNonce.getStatusCode().value()).isEqualTo(400);
        assertThat(codeOf(replayNonce)).isEqualTo(40001);

        String retryNonce = nonce();
        String retrySignature = MockPayGatewayClient.hmacSha256Hex(
                appProperties.getPayment().getCallbackSecret(), ts + "\n" + retryNonce + "\n" + body);
        ResponseEntity<String> idempotent = callback(body, ts, retryNonce, retrySignature);
        assertThat(idempotent.getStatusCode().value()).isEqualTo(200);
        assertThat((String) JsonPath.read(idempotent.getBody(), "$.data.status")).isEqualTo("SUCCESS");
    }

    @Test
    void callbackForCancelledOrderMarksPaymentRefunded() throws Exception {
        String customerToken = login("customer", "customer123");
        long dishId = createDish("取消支付菜品" + suffix(), "24.00", 10);
        long orderId = createOrder(customerToken, dishId, 1);
        String payNo = createPayment(customerToken, orderId);

        ResponseEntity<String> cancelled = postJson("/api/v1/orders/" + orderId + "/cancel",
                Map.of("reason", "不想要了"), customerToken, null);
        assertThat((String) JsonPath.read(cancelled.getBody(), "$.data.status")).isEqualTo("CANCELLED");

        String body = callbackBody(payNo, "TXREFUND" + suffix(), "0.01", "SUCCESS");
        long ts = System.currentTimeMillis();
        String usedNonce = nonce();
        String signature = MockPayGatewayClient.hmacSha256Hex(
                appProperties.getPayment().getCallbackSecret(), ts + "\n" + usedNonce + "\n" + body);
        ResponseEntity<String> response = callback(body, ts, usedNonce, signature);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat((String) JsonPath.read(response.getBody(), "$.data.status")).isEqualTo("REFUNDED");

        ResponseEntity<String> payment = get(PAYMENTS + "/" + payNo, customerToken);
        assertThat((String) JsonPath.read(payment.getBody(), "$.data.status")).isEqualTo("REFUNDED");
    }

    /** M-2：金额校验失败不消耗 nonce，修正报文后可用同一 nonce 重试成功。 */
    @Test
    void failedCallbackValidationDoesNotConsumeNonce() throws Exception {
        String customerToken = login("customer", "customer123");
        long dishId = createDish("nonce重试菜" + suffix(), "28.00", 10);
        long orderId = createOrder(customerToken, dishId, 1);
        String payNo = createPayment(customerToken, orderId);

        long ts = System.currentTimeMillis();
        String nonce = nonce();
        String wrongBody = callbackBody(payNo, "TXBAD" + suffix(), "1.00", "SUCCESS");
        String wrongSignature = MockPayGatewayClient.hmacSha256Hex(
                appProperties.getPayment().getCallbackSecret(), ts + "\n" + nonce + "\n" + wrongBody);
        ResponseEntity<String> rejected = callback(wrongBody, ts, nonce, wrongSignature);
        assertThat(rejected.getStatusCode().value()).isEqualTo(400);
        assertThat(codeOf(rejected)).isEqualTo(40005);

        String goodBody = callbackBody(payNo, "TXGOOD" + suffix(), "0.01", "SUCCESS");
        String goodSignature = MockPayGatewayClient.hmacSha256Hex(
                appProperties.getPayment().getCallbackSecret(), ts + "\n" + nonce + "\n" + goodBody);
        ResponseEntity<String> ok = callback(goodBody, ts, nonce, goodSignature);
        assertThat(ok.getStatusCode().value()).isEqualTo(200);
        assertThat((String) JsonPath.read(ok.getBody(), "$.data.status")).isEqualTo("SUCCESS");
        assertThat((String) JsonPath.read(get("/api/v1/orders/" + orderId, customerToken).getBody(),
                "$.data.status")).isEqualTo("PAID");
    }

    /** M-1：并发双回调只有一个能把支付单从 PENDING 推进到 SUCCESS，只发一次 PaymentSucceededEvent。 */
    @Test
    void concurrentCallbacksPublishPaymentSucceededOnlyOnce() throws Exception {
        String customerToken = login("customer", "customer123");
        long dishId = createDish("并发回调菜" + suffix(), "33.00", 10);
        long orderId = createOrder(customerToken, dishId, 1);
        String payNo = createPayment(customerToken, orderId);
        paymentEventCounter.reset();

        long ts = System.currentTimeMillis();
        String bodyA = callbackBody(payNo, "TXA" + suffix(), "0.01", "SUCCESS");
        String nonceA = nonce();
        String signatureA = MockPayGatewayClient.hmacSha256Hex(
                appProperties.getPayment().getCallbackSecret(), ts + "\n" + nonceA + "\n" + bodyA);
        String bodyB = callbackBody(payNo, "TXB" + suffix(), "0.01", "SUCCESS");
        String nonceB = nonce();
        String signatureB = MockPayGatewayClient.hmacSha256Hex(
                appProperties.getPayment().getCallbackSecret(), ts + "\n" + nonceB + "\n" + bodyB);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        Callable<ResponseEntity<String>> first = () -> {
            start.await(5, TimeUnit.SECONDS);
            return callback(bodyA, ts, nonceA, signatureA);
        };
        Callable<ResponseEntity<String>> second = () -> {
            start.await(5, TimeUnit.SECONDS);
            return callback(bodyB, ts, nonceB, signatureB);
        };
        List<Future<ResponseEntity<String>>> futures = List.of(pool.submit(first), pool.submit(second));
        start.countDown();
        for (Future<ResponseEntity<String>> future : futures) {
            ResponseEntity<String> response = future.get(30, TimeUnit.SECONDS);
            assertThat(response.getStatusCode().value()).as("body=%s", response.getBody()).isEqualTo(200);
        }
        pool.shutdown();

        assertThat(paymentEventCounter.count()).as("并发回调只能发布一次 PaymentSucceededEvent").isEqualTo(1);
        assertThat((String) JsonPath.read(get("/api/v1/orders/" + orderId, customerToken).getBody(),
                "$.data.status")).isEqualTo("PAID");
        ResponseEntity<String> payment = get(PAYMENTS + "/" + payNo, customerToken);
        assertThat((String) JsonPath.read(payment.getBody(), "$.data.status")).isEqualTo("SUCCESS");
    }

    @Test
    void paymentForMissingOrderOrUnknownPayNoReturns404() throws Exception {
        String customerToken = login("customer", "customer123");
        ResponseEntity<String> missingOrder = postJson(PAYMENTS, paymentBody(999999999L), customerToken,
                "pay-missing-" + suffix());
        assertThat(missingOrder.getStatusCode().value()).isEqualTo(404);
        assertThat(codeOf(missingOrder)).isEqualTo(40401);

        ResponseEntity<String> unknownPayNo = get(PAYMENTS + "/PAY-UNKNOWN", customerToken);
        assertThat(unknownPayNo.getStatusCode().value()).isEqualTo(404);
        assertThat(codeOf(unknownPayNo)).isEqualTo(40401);
    }

    @TestConfiguration
    static class EventConfig {

        @Bean
        PaymentEventCounter paymentEventCounter() {
            return new PaymentEventCounter();
        }
    }

    static class PaymentEventCounter {

        private final AtomicInteger count = new AtomicInteger();

        @EventListener
        public void onPaymentSucceeded(PaymentSucceededEvent event) {
            count.incrementAndGet();
        }

        int count() {
            return count.get();
        }

        void reset() {
            count.set(0);
        }
    }

    private String createPayment(String customerToken, long orderId) throws Exception {
        ResponseEntity<String> created = postJson(PAYMENTS, paymentBody(orderId), customerToken,
                "pay-create-" + suffix());
        assertThat(codeOf(created)).isZero();
        return JsonPath.read(created.getBody(), "$.data.payNo");
    }

    private Map<String, Object> paymentBody(long orderId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("orderId", orderId);
        body.put("orderType", "TAKEOUT");
        return body;
    }

    private String callbackBody(String payNo, String transactionNo, String paidAmount, String status) {
        String paidAt = OffsetDateTime.now(ZoneId.of("Asia/Shanghai")).toString();
        return "{\"payNo\":\"" + payNo + "\",\"transactionNo\":\"" + transactionNo
                + "\",\"paidAmount\":" + paidAmount + ",\"paidAt\":\"" + paidAt
                + "\",\"status\":\"" + status + "\"}";
    }

    private ResponseEntity<String> callback(String body, long timestamp, String nonce, String signature)
            throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Pay-Timestamp", Long.toString(timestamp));
        headers.set("X-Pay-Nonce", nonce);
        headers.set("X-Pay-Signature", signature);
        HttpEntity<String> entity = new HttpEntity<>(body, headers);
        return restTemplate.exchange(PAYMENTS + "/mock/callback", HttpMethod.POST, entity, String.class);
    }

    private long createOrder(String customerToken, long dishId, int quantity) throws Exception {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("dishId", dishId);
        item.put("quantity", quantity);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("merchantId", MERCHANT_ID);
        body.put("addressId", ADDRESS_ID);
        body.put("items", List.of(item));
        ResponseEntity<String> response = postJson("/api/v1/orders", body, customerToken,
                "order-pay-" + suffix());
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
        ResponseEntity<String> response = postJson("/api/v1/merchant/dishes", body, merchantToken, null);
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

    private static String nonce() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }
}
