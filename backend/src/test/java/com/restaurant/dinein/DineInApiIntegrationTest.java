package com.restaurant.dinein;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.restaurant.common.enums.OrderStatus;
import com.restaurant.contract.event.TableSessionClosedEvent;
import com.restaurant.table.TableInfo;
import com.restaurant.table.TableSession;
import com.restaurant.table.mapper.TableInfoMapper;
import com.restaurant.table.mapper.TableSessionMapper;
import com.restaurant.table.service.TableSessionCleanupService;
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
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 堂食桌台集成测试（真实 Tomcat + MySQL restaurant_mvp_test + Redis db15）。
 *
 * <p>覆盖 G2 门禁：篡改二维码签名拒绝、签名过期拒绝、并发开桌仅一人成功、
 * 出餐→完成释放桌台、取消释放桌台、有进行中订单拒绝关台、过期会话清理。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(DineInApiIntegrationTest.EventConfig.class)
class DineInApiIntegrationTest {

    private static final String TABLES = "/api/v1/merchant/tables";

    private static final String DINE_IN = "/api/v1/dine-in";

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TableInfoMapper tableInfoMapper;

    @Autowired
    private TableSessionMapper tableSessionMapper;

    @Autowired
    private TableSessionCleanupService tableSessionCleanupService;

    @Autowired
    private SessionEvents sessionEvents;

    @BeforeEach
    void clearRateLimitKeys() {
        sessionEvents.clear();
        Set<String> keys = stringRedisTemplate.keys("rate:*");
        if (keys != null && !keys.isEmpty()) {
            stringRedisTemplate.delete(keys);
        }
    }

    @Test
    void qrSignatureRejectsTamperedAndExpiredTokens() throws Exception {
        String merchantToken = login("merchant", "merchant123");
        CreatedTable table = createTable(merchantToken);
        String customerToken = login("customer", "customer123");

        Map<String, String> params = qrParams(table.qrUrl());
        assertThat(params.get("p")).isEqualTo(String.valueOf(table.id()));

        String tampered = tamper(params.get("s"));
        ResponseEntity<String> tamperedResponse = postJson(DINE_IN + "/scan",
                scanBody(table.id(), tampered, Long.parseLong(params.get("t"))), customerToken, null, Map.of());
        assertThat(tamperedResponse.getStatusCode().value()).isEqualTo(400);
        assertThat(codeOf(tamperedResponse)).isEqualTo(40004);

        long expiredAt = Instant.now().minusSeconds(366L * 24 * 3600).getEpochSecond();
        ResponseEntity<String> expiredResponse = postJson(DINE_IN + "/scan",
                scanBody(table.id(), params.get("s"), expiredAt), customerToken, null, Map.of());
        assertThat(expiredResponse.getStatusCode().value()).isEqualTo(400);
        assertThat(codeOf(expiredResponse)).isEqualTo(40004);
        assertThat((String) JsonPath.read(expiredResponse.getBody(), "$.message")).contains("过期");

        ResponseEntity<String> validResponse = postJson(DINE_IN + "/scan",
                scanBody(table.id(), params.get("s"), Long.parseLong(params.get("t"))), customerToken, null, Map.of());
        assertThat(codeOf(validResponse)).isZero();
        assertThat((String) JsonPath.read(validResponse.getBody(), "$.data.status")).isEqualTo("ACTIVE");
        assertThat((String) JsonPath.read(validResponse.getBody(), "$.data.tableStatus")).isEqualTo("OCCUPIED");
        String sessionToken = JsonPath.read(validResponse.getBody(), "$.data.sessionToken");

        ResponseEntity<String> session = get(DINE_IN + "/session", customerToken, sessionToken);
        assertThat(codeOf(session)).isZero();
        assertThat(((Number) JsonPath.read(session.getBody(), "$.data.tableId")).longValue())
                .isEqualTo(table.id());

        ResponseEntity<String> closed = postJson(DINE_IN + "/session/close", null, customerToken, null,
                Map.of("X-Table-Session", sessionToken));
        assertThat(codeOf(closed)).isZero();
        assertThat((Boolean) JsonPath.read(closed.getBody(), "$.data.closed")).isTrue();
        assertThat(tableInfoMapper.selectById(table.id()).getStatus()).isEqualTo("FREE");

        ResponseEntity<String> newQr = postJson(TABLES + "/" + table.id() + "/qr", null, merchantToken, null, Map.of());
        assertThat(codeOf(newQr)).isZero();
        String newSignature = JsonPath.read(newQr.getBody(), "$.data.qrUrl");
        Map<String, String> newParams = qrParams(newSignature);
        assertThat(newParams.get("s")).isNotEqualTo(params.get("s"));

        ResponseEntity<String> oldSignature = postJson(DINE_IN + "/scan",
                scanBody(table.id(), params.get("s"), Long.parseLong(params.get("t"))), customerToken, null, Map.of());
        assertThat(oldSignature.getStatusCode().value()).isEqualTo(400);
        assertThat(codeOf(oldSignature)).isEqualTo(40004);

        ResponseEntity<String> currentSignature = postJson(DINE_IN + "/scan",
                scanBody(table.id(), newParams.get("s"), Long.parseLong(newParams.get("t"))),
                customerToken, null, Map.of());
        assertThat(codeOf(currentSignature)).isZero();
    }

    @Test
    void concurrentBindOnlyOneCustomerSucceeds() throws Exception {
        String merchantToken = login("merchant", "merchant123");
        CreatedTable table = createTable(merchantToken);
        String customerOne = login("customer", "customer123");
        String customerTwo = registerCustomer();

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Callable<ResponseEntity<String>>> tasks = new ArrayList<>();
        for (String token : List.of(customerOne, customerTwo)) {
            tasks.add(() -> {
                start.await(5, TimeUnit.SECONDS);
                return postJson(DINE_IN + "/bind", Map.of("tableNo", table.tableNo()), token, null, Map.of());
            });
        }
        List<Future<ResponseEntity<String>>> futures = new ArrayList<>();
        for (Callable<ResponseEntity<String>> task : tasks) {
            futures.add(pool.submit(task));
        }
        start.countDown();
        int success = 0;
        int occupied = 0;
        List<String> observed = new ArrayList<>();
        for (Future<ResponseEntity<String>> future : futures) {
            ResponseEntity<String> response = future.get(30, TimeUnit.SECONDS);
            int code = codeOf(response);
            observed.add(response.getStatusCode().value() + ":" + code + ":" + response.getBody());
            if (code == 0) {
                success++;
            } else if (code == 40910) {
                assertThat(response.getStatusCode().value()).isEqualTo(409);
                occupied++;
            }
        }
        pool.shutdown();
        assertThat(success).as("responses=%s", observed).isEqualTo(1);
        assertThat(occupied).as("responses=%s", observed).isEqualTo(1);

        TableInfo stored = tableInfoMapper.selectById(table.id());
        assertThat(stored.getStatus()).isEqualTo("OCCUPIED");
        assertThat(stored.getCurrentSessionId()).isNotNull();
        long activeSessions = tableSessionMapper.selectCount(Wrappers.<TableSession>lambdaQuery()
                .eq(TableSession::getTableId, table.id())
                .eq(TableSession::getStatus, "ACTIVE"));
        assertThat(activeSessions).isEqualTo(1L);
    }

    @Test
    void dineInOrderFlowReleasesTableOnComplete() throws Exception {
        String merchantToken = login("merchant", "merchant123");
        CreatedTable table = createTable(merchantToken);
        String customerToken = login("customer", "customer123");
        String sessionToken = scan(table, customerToken);
        long dishId = createDish(merchantToken, "堂食鱼香肉丝" + suffix(), "30.00", 10);

        ResponseEntity<String> order = postJson(DINE_IN + "/orders",
                dineInOrderBody(dishId, 2, "堂食不要辣"), customerToken,
                "dinein-order-" + suffix(), Map.of("X-Table-Session", sessionToken));
        assertThat(codeOf(order)).isZero();
        assertThat((String) JsonPath.read(order.getBody(), "$.data.orderType")).isEqualTo("DINE_IN");
        assertThat((String) JsonPath.read(order.getBody(), "$.data.tableNo")).isEqualTo(table.tableNo());
        assertThat(((Number) JsonPath.read(order.getBody(), "$.data.deliveryFee")).doubleValue()).isZero();
        assertThat(((Number) JsonPath.read(order.getBody(), "$.data.payAmount")).doubleValue()).isEqualTo(60.00);
        long orderId = ((Number) JsonPath.read(order.getBody(), "$.data.id")).longValue();

        ResponseEntity<String> payment = postJson("/api/v1/payments",
                Map.of("orderId", orderId, "orderType", "DINE_IN"), customerToken, "pay-" + suffix(), Map.of());
        assertThat(codeOf(payment)).isZero();
        String payNo = JsonPath.read(payment.getBody(), "$.data.payNo");
        ResponseEntity<String> paid = postJson("/api/v1/payments/" + payNo + "/mock-pay", null,
                customerToken, "mock-pay-" + suffix(), Map.of());
        assertThat(codeOf(paid)).isZero();
        assertThat((String) JsonPath.read(paid.getBody(), "$.data.status")).isEqualTo("SUCCESS");

        ResponseEntity<String> accepted = postJson("/api/v1/merchant/dine-in/orders/" + orderId + "/accept",
                null, merchantToken, null, Map.of());
        assertThat(codeOf(accepted)).isZero();
        assertThat((String) JsonPath.read(accepted.getBody(), "$.data.status")).isEqualTo("ACCEPTED");
        ResponseEntity<String> preparing = postJson("/api/v1/merchant/dine-in/orders/" + orderId + "/prepare",
                null, merchantToken, null, Map.of());
        assertThat((String) JsonPath.read(preparing.getBody(), "$.data.status")).isEqualTo("MAKING");
        ResponseEntity<String> served = postJson("/api/v1/merchant/dine-in/orders/" + orderId + "/serve",
                null, merchantToken, null, Map.of());
        assertThat((String) JsonPath.read(served.getBody(), "$.data.status")).isEqualTo("SERVED");
        ResponseEntity<String> completed = postJson("/api/v1/merchant/dine-in/orders/" + orderId + "/complete",
                null, merchantToken, null, Map.of());
        assertThat(codeOf(completed)).isZero();
        assertThat((String) JsonPath.read(completed.getBody(), "$.data.status")).isEqualTo("COMPLETED");

        TableInfo released = tableInfoMapper.selectById(table.id());
        assertThat(released.getStatus()).isEqualTo("FREE");
        assertThat(released.getCurrentSessionId()).isNull();
        TableSession session = tableSessionMapper.selectOne(Wrappers.<TableSession>lambdaQuery()
                .eq(TableSession::getSessionToken, sessionToken));
        assertThat(session.getStatus()).isEqualTo("CLOSED");
        assertThat(session.getClosedAt()).isNotNull();

        ResponseEntity<String> merchantList = get("/api/v1/merchant/dine-in/orders?statusGroup=FINISHED", merchantToken, null);
        assertThat(merchantList.getStatusCode().value()).isEqualTo(200);
        List<Number> ids = JsonPath.read(merchantList.getBody(), "$.data.list[*].id");
        assertThat(ids.stream().map(Number::longValue)).contains(orderId);
    }

    @Test
    void cancelDineInOrderReleasesTableAndRestoresStock() throws Exception {
        String merchantToken = login("merchant", "merchant123");
        CreatedTable table = createTable(merchantToken);
        String customerToken = login("customer", "customer123");
        String sessionToken = scan(table, customerToken);
        long dishId = createDish(merchantToken, "堂食回锅肉" + suffix(), "24.00", 5);

        ResponseEntity<String> order = postJson(DINE_IN + "/orders",
                dineInOrderBody(dishId, 2, "稍后取消"), customerToken,
                "dinein-cancel-" + suffix(), Map.of("X-Table-Session", sessionToken));
        assertThat(codeOf(order)).isZero();
        long orderId = ((Number) JsonPath.read(order.getBody(), "$.data.id")).longValue();

        ResponseEntity<String> closeWithOrder = postJson(DINE_IN + "/session/close", null, customerToken,
                null, Map.of("X-Table-Session", sessionToken));
        assertThat(closeWithOrder.getStatusCode().value()).isEqualTo(409);
        assertThat(codeOf(closeWithOrder)).isEqualTo(40916);

        ResponseEntity<String> cancelled = postJson(DINE_IN + "/orders/" + orderId + "/cancel",
                Map.of("reason", "不要了"), customerToken, null, Map.of());
        assertThat(codeOf(cancelled)).isZero();
        assertThat((String) JsonPath.read(cancelled.getBody(), "$.data.status")).isEqualTo("CANCELLED");

        TableInfo released = tableInfoMapper.selectById(table.id());
        assertThat(released.getStatus()).isEqualTo("FREE");
        assertThat(released.getCurrentSessionId()).isNull();
        TableSession session = tableSessionMapper.selectOne(Wrappers.<TableSession>lambdaQuery()
                .eq(TableSession::getSessionToken, sessionToken));
        assertThat(session.getStatus()).isEqualTo("CLOSED");
        assertThat(restTemplate.exchange("/api/v1/menu/dishes/" + dishId, HttpMethod.GET,
                new HttpEntity<>(new HttpHeaders()), String.class).getStatusCode().value()).isEqualTo(200);
    }

    /**
     * H-1：堂食订单被商家拒单时，与完成订单同语义释放桌台（会话 CLOSED、桌台 FREE、
     * 发布 TABLE_SESSION_CLOSED），并保持拒单的退款/库存回补语义。
     */
    @Test
    void merchantRejectDineInOrderReleasesTableAndRefunds() throws Exception {
        String merchantToken = login("merchant", "merchant123");
        CreatedTable table = createTable(merchantToken);
        String customerToken = login("customer", "customer123");
        String sessionToken = scan(table, customerToken);
        long dishId = createDish(merchantToken, "堂食拒单菜" + suffix(), "20.00", 6);

        ResponseEntity<String> order = postJson(DINE_IN + "/orders",
                dineInOrderBody(dishId, 2, "拒单测试"), customerToken,
                "dinein-reject-" + suffix(), Map.of("X-Table-Session", sessionToken));
        assertThat(codeOf(order)).isZero();
        long orderId = ((Number) JsonPath.read(order.getBody(), "$.data.id")).longValue();

        ResponseEntity<String> payment = postJson("/api/v1/payments",
                Map.of("orderId", orderId, "orderType", "DINE_IN"), customerToken, "pay-" + suffix(), Map.of());
        assertThat(codeOf(payment)).isZero();
        String payNo = JsonPath.read(payment.getBody(), "$.data.payNo");
        ResponseEntity<String> paid = postJson("/api/v1/payments/" + payNo + "/mock-pay", null,
                customerToken, "mock-pay-" + suffix(), Map.of());
        assertThat(codeOf(paid)).isZero();
        assertThat((String) JsonPath.read(paid.getBody(), "$.data.status")).isEqualTo("SUCCESS");

        ResponseEntity<String> rejected = postJson("/api/v1/merchant/orders/" + orderId + "/reject",
                Map.of("reason", "食材售罄"), merchantToken, null, Map.of());
        assertThat(rejected.getStatusCode().value()).isEqualTo(200);
        assertThat(codeOf(rejected)).isZero();
        assertThat((String) JsonPath.read(rejected.getBody(), "$.data.status")).isEqualTo("REJECTED");

        TableInfo released = tableInfoMapper.selectById(table.id());
        assertThat(released.getStatus()).isEqualTo("FREE");
        assertThat(released.getCurrentSessionId()).isNull();
        TableSession session = tableSessionMapper.selectOne(Wrappers.<TableSession>lambdaQuery()
                .eq(TableSession::getSessionToken, sessionToken));
        assertThat(session.getStatus()).isEqualTo("CLOSED");
        assertThat(session.getClosedAt()).isNotNull();
        assertThat(sessionEvents.closed()).anyMatch(event ->
                Long.valueOf(table.id()).equals(event.tableId())
                        && session.getId().equals(event.sessionId()));

        ResponseEntity<String> oldSession = get(DINE_IN + "/session", customerToken, sessionToken);
        assertThat(oldSession.getStatusCode().value()).isEqualTo(409);
        assertThat(codeOf(oldSession)).isEqualTo(40911);

        ResponseEntity<String> paymentDetail = get("/api/v1/payments/" + payNo, customerToken, null);
        assertThat((String) JsonPath.read(paymentDetail.getBody(), "$.data.status")).isEqualTo("REFUNDED");

        ResponseEntity<String> dish = get("/api/v1/menu/dishes/" + dishId, null, null);
        assertThat(((Number) JsonPath.read(dish.getBody(), "$.data.stock")).intValue()).isEqualTo(6);
    }

    @Test
    void cleanupExpiresIdleSessionAndReleasesTable() throws Exception {
        String merchantToken = login("merchant", "merchant123");
        CreatedTable table = createTable(merchantToken);
        String customerToken = login("customer", "customer123");
        String sessionToken = scan(table, customerToken);
        TableSession session = tableSessionMapper.selectOne(Wrappers.<TableSession>lambdaQuery()
                .eq(TableSession::getSessionToken, sessionToken));
        tableSessionMapper.update(null, Wrappers.<TableSession>lambdaUpdate()
                .eq(TableSession::getId, session.getId())
                .set(TableSession::getExpireAt, LocalDateTime.now().minusMinutes(1)));

        int closed = tableSessionCleanupService.cleanupExpiredSessions();
        assertThat(closed).isGreaterThanOrEqualTo(1);
        assertThat(tableInfoMapper.selectById(table.id()).getStatus()).isEqualTo("FREE");
        assertThat(tableSessionMapper.selectById(session.getId()).getStatus()).isEqualTo("CLOSED");

        assertThat(tableSessionCleanupService.cleanupExpiredSessions()).isZero();
    }

    @TestConfiguration
    static class EventConfig {

        @Bean
        SessionEvents sessionEvents() {
            return new SessionEvents();
        }
    }

    static class SessionEvents {

        private final List<TableSessionClosedEvent> closed = new CopyOnWriteArrayList<>();

        @EventListener
        public void onClosed(TableSessionClosedEvent event) {
            closed.add(event);
        }

        List<TableSessionClosedEvent> closed() {
            return closed;
        }

        void clear() {
            closed.clear();
        }
    }

    private CreatedTable createTable(String merchantToken) throws Exception {        String tableNo = "A" + suffix();
        ResponseEntity<String> response = postJson(TABLES, Map.of("tableNo", tableNo, "name", "测试桌", "capacity", 4),
                merchantToken, null, Map.of());
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(codeOf(response)).isZero();
        long id = ((Number) JsonPath.read(response.getBody(), "$.data.id")).longValue();
        String qrUrl = JsonPath.read(response.getBody(), "$.data.qrUrl");
        assertThat((String) JsonPath.read(response.getBody(), "$.data.qrImageUrl")).startsWith("/files/");
        return new CreatedTable(id, tableNo, qrUrl);
    }

    private String scan(CreatedTable table, String customerToken) throws Exception {
        Map<String, String> params = qrParams(table.qrUrl());
        ResponseEntity<String> response = postJson(DINE_IN + "/scan",
                scanBody(table.id(), params.get("s"), Long.parseLong(params.get("t"))), customerToken, null, Map.of());
        assertThat(codeOf(response)).isZero();
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
        body.put("username", "u" + suffix());
        body.put("password", "pass1234");
        body.put("nickname", "并发顾客");
        ResponseEntity<String> response = postJson("/api/v1/auth/register", body, null, null, Map.of());
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        return JsonPath.read(response.getBody(), "$.data.token");
    }

    private Map<String, Object> scanBody(long tableId, String signature, long timestamp) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("p", String.valueOf(tableId));
        body.put("s", signature);
        body.put("t", timestamp);
        return body;
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

    private Map<String, String> qrParams(String qrUrl) {
        Map<String, String> params = new HashMap<>();
        String query = qrUrl.substring(qrUrl.indexOf('?') + 1);
        for (String pair : query.split("&")) {
            int index = pair.indexOf('=');
            params.put(pair.substring(0, index), URLDecoder.decode(pair.substring(index + 1), StandardCharsets.UTF_8));
        }
        return params;
    }

    private static String tamper(String signature) {
        char last = signature.charAt(signature.length() - 1);
        char replacement = last == 'A' ? 'B' : 'A';
        return signature.substring(0, signature.length() - 1) + replacement;
    }

    private String login(String username, String password) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("username", username);
        body.put("password", password);
        ResponseEntity<String> response = postJson("/api/v1/auth/login", body, null, null, Map.of());
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

    private ResponseEntity<String> get(String path, String token, String tableSession) {
        HttpHeaders headers = new HttpHeaders();
        if (token != null) {
            headers.setBearerAuth(token);
        }
        if (tableSession != null) {
            headers.set("X-Table-Session", tableSession);
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

    private record CreatedTable(long id, String tableNo, String qrUrl) {
    }
}
