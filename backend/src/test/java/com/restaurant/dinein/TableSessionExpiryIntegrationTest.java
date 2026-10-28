package com.restaurant.dinein;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.restaurant.table.TableInfo;
import com.restaurant.table.TableSession;
import com.restaurant.table.mapper.TableInfoMapper;
import com.restaurant.table.mapper.TableSessionMapper;
import com.restaurant.contract.event.TableSessionClosedEvent;
import com.restaurant.table.service.TableReleaseService;
import com.restaurant.table.service.TableSessionCleanupService;
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
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.EventListener;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
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
 * 桌台会话过期释放集成测试（T45，修 T31 缺口）。
 *
 * <p>覆盖：惰性过期（requireActive / findActiveByUser）释放桌台、过期桌台重新扫码/绑桌成功、
 * 并发双过期只释放一次、清理任务释放与幂等、旧版 EXPIRED 残留补救、有进行中订单跳过。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class TableSessionExpiryIntegrationTest {

    private static final List<TableSessionClosedEvent> CLOSED_EVENTS = new CopyOnWriteArrayList<>();

    @TestConfiguration
    static class EventCaptureConfig {

        @Bean
        EventCapture tableSessionClosedCapture() {
            return new EventCapture();
        }
    }

    static class EventCapture {

        @EventListener
        public void onTableSessionClosed(TableSessionClosedEvent event) {
            CLOSED_EVENTS.add(event);
        }
    }

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
    private TableReleaseService tableReleaseService;


    @BeforeEach
    void clearRateLimitKeys() {
        CLOSED_EVENTS.clear();
        Set<String> keys = stringRedisTemplate.keys("rate:*");
        if (keys != null && !keys.isEmpty()) {
            stringRedisTemplate.delete(keys);
        }
    }

    @Test
    void lazyExpiryOnSessionQueryReleasesTableAndRescanSucceeds() throws Exception {
        String merchantToken = login("merchant", "merchant123");
        CreatedTable table = createTable(merchantToken);
        String customerToken = login("customer", "customer123");
        String sessionToken = scan(table, customerToken);
        TableSession session = sessionByToken(sessionToken);
        forceExpire(session.getId());

        ResponseEntity<String> expired = get(DINE_IN + "/session", customerToken, sessionToken);
        assertThat(expired.getStatusCode().value()).isEqualTo(409);
        assertThat(codeOf(expired)).isEqualTo(40911);
        assertReleased(table.id(), session.getId());
        assertThat(closedEventCount(session.getId())).isEqualTo(1);

        // T31 缺陷：过期后桌台仍 OCCUPIED，重扫返回 40910；现在应成功占桌。
        ResponseEntity<String> rescan = scanRaw(table, customerToken);
        assertThat(rescan.getStatusCode().value()).isEqualTo(200);
        assertThat(codeOf(rescan)).isZero();
        assertThat((String) JsonPath.read(rescan.getBody(), "$.data.tableStatus")).isEqualTo("OCCUPIED");
        TableInfo occupied = tableInfoMapper.selectById(table.id());
        assertThat(occupied.getStatus()).isEqualTo("OCCUPIED");
        assertThat(occupied.getCurrentSessionId()).isNotNull().isNotEqualTo(session.getId());
    }

    @Test
    void lazyExpiryOnFindActiveByUserReleasesTable() throws Exception {
        String merchantToken = login("merchant", "merchant123");
        CreatedTable table = createTable(merchantToken);
        String customerToken = login("customer", "customer123");
        String sessionToken = scan(table, customerToken);
        TableSession session = sessionByToken(sessionToken);
        forceExpire(session.getId());

        ResponseEntity<String> lookup = get(DINE_IN + "/session", customerToken, null);
        assertThat(lookup.getStatusCode().value()).isEqualTo(404);
        assertThat(codeOf(lookup)).isEqualTo(40401);
        assertReleased(table.id(), session.getId());
    }

    @Test
    void anotherCustomerCanScanExpiredTableInsteadOf40910() throws Exception {
        String merchantToken = login("merchant", "merchant123");
        CreatedTable table = createTable(merchantToken);
        String firstCustomer = login("customer", "customer123");
        String sessionToken = scan(table, firstCustomer);
        TableSession session = sessionByToken(sessionToken);
        forceExpire(session.getId());

        String secondCustomer = registerCustomer();
        ResponseEntity<String> scanned = scanRaw(table, secondCustomer);
        assertThat(codeOf(scanned)).isZero();
        assertThat((String) JsonPath.read(scanned.getBody(), "$.data.tableStatus")).isEqualTo("OCCUPIED");
        assertReleasedSessionOnly(session.getId());
        TableInfo stored = tableInfoMapper.selectById(table.id());
        assertThat(stored.getStatus()).isEqualTo("OCCUPIED");
        assertThat(stored.getCurrentSessionId()).isNotEqualTo(session.getId());
    }

    @Test
    void concurrentExpiryReleasesOnceAndIsIdempotent() throws Exception {
        String merchantToken = login("merchant", "merchant123");
        CreatedTable table = createTable(merchantToken);
        String customerToken = login("customer", "customer123");
        String sessionToken = scan(table, customerToken);
        TableSession session = sessionByToken(sessionToken);
        forceExpire(session.getId());
        int sessionVersion = session.getVersion();
        int tableVersion = tableInfoMapper.selectById(table.id()).getVersion();

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Callable<Boolean>> tasks = List.of(
                () -> {
                    start.await(5, TimeUnit.SECONDS);
                    return tableReleaseService.releaseExpired(table.id(), session.getId());
                },
                () -> {
                    start.await(5, TimeUnit.SECONDS);
                    return tableReleaseService.releaseExpired(table.id(), session.getId());
                });
        List<Future<Boolean>> futures = new ArrayList<>();
        for (Callable<Boolean> task : tasks) {
            futures.add(pool.submit(task));
        }
        start.countDown();
        int released = 0;
        for (Future<Boolean> future : futures) {
            if (Boolean.TRUE.equals(future.get(30, TimeUnit.SECONDS))) {
                released++;
            }
        }
        pool.shutdown();
        assertThat(released).as("并发双过期只能有一次真正生效").isEqualTo(1);
        assertThat(closedEventCount(session.getId())).as("并发双过期只发布一次关闭事件").isEqualTo(1);

        assertReleased(table.id(), session.getId());
        assertThat(tableSessionMapper.selectById(session.getId()).getVersion()).isEqualTo(sessionVersion + 1);
        assertThat(tableInfoMapper.selectById(table.id()).getVersion()).isEqualTo(tableVersion + 1);
        assertThat(tableReleaseService.releaseExpired(table.id(), session.getId())).isFalse();
    }

    @Test
    void cleanupReleasesExpiredSessionAndRepairsLegacyExpiredRow() throws Exception {
        String merchantToken = login("merchant", "merchant123");
        CreatedTable table = createTable(merchantToken);
        String customerToken = login("customer", "customer123");
        String sessionToken = scan(table, customerToken);
        TableSession session = sessionByToken(sessionToken);
        forceExpire(session.getId());

        int closed = tableSessionCleanupService.cleanupExpiredSessions();
        assertThat(closed).isGreaterThanOrEqualTo(1);
        assertReleased(table.id(), session.getId());
        assertThat(tableSessionCleanupService.cleanupExpiredSessions()).isZero();

        // 模拟旧版本缺陷遗留：会话被置 EXPIRED，但桌台仍 OCCUPIED 且引用该会话。
        CreatedTable legacyTable = createTable(merchantToken);
        String legacySessionToken = scan(legacyTable, customerToken);
        TableSession legacy = sessionByToken(legacySessionToken);
        forceExpire(legacy.getId());
        tableSessionMapper.update(null, Wrappers.<TableSession>lambdaUpdate()
                .eq(TableSession::getId, legacy.getId())
                .set(TableSession::getStatus, "EXPIRED")
                .set(TableSession::getClosedAt, LocalDateTime.now().minusMinutes(5)));
        assertThat(tableInfoMapper.selectById(legacyTable.id()).getStatus()).isEqualTo("OCCUPIED");

        int repaired = tableSessionCleanupService.cleanupExpiredSessions();
        assertThat(repaired).isGreaterThanOrEqualTo(1);
        assertReleased(legacyTable.id(), legacy.getId());
    }

    @Test
    void cleanupSkipsSessionWithActiveOrder() throws Exception {
        String merchantToken = login("merchant", "merchant123");
        CreatedTable table = createTable(merchantToken);
        String customerToken = login("customer", "customer123");
        String sessionToken = scan(table, customerToken);
        TableSession session = sessionByToken(sessionToken);
        long dishId = createDish(merchantToken, "过期清理测试菜" + suffix(), "12.00", 5);
        ResponseEntity<String> order = postJson(DINE_IN + "/orders",
                dineInOrderBody(dishId), customerToken, "expire-order-" + suffix(),
                Map.of("X-Table-Session", sessionToken));
        assertThat(codeOf(order)).isZero();
        long orderId = ((Number) JsonPath.read(order.getBody(), "$.data.id")).longValue();
        forceExpire(session.getId());

        tableSessionCleanupService.cleanupExpiredSessions();
        assertThat(tableSessionMapper.selectById(session.getId()).getStatus()).isEqualTo("ACTIVE");
        assertThat(tableInfoMapper.selectById(table.id()).getStatus()).isEqualTo("OCCUPIED");

        ResponseEntity<String> cancelled = postJson(DINE_IN + "/orders/" + orderId + "/cancel",
                Map.of("reason", "测试结束"), customerToken, null, Map.of());
        assertThat(codeOf(cancelled)).isZero();
        assertReleased(table.id(), session.getId());
    }

    private static long closedEventCount(long sessionId) {
        return CLOSED_EVENTS.stream().filter(event -> sessionId == event.sessionId()).count();
    }

    private void assertReleased(long tableId, long sessionId) {
        TableInfo stored = tableInfoMapper.selectById(tableId);
        assertThat(stored.getStatus()).isEqualTo("FREE");
        assertThat(stored.getCurrentSessionId()).isNull();
        TableSession session = tableSessionMapper.selectById(sessionId);
        assertThat(session.getStatus()).isEqualTo("CLOSED");
        assertThat(session.getClosedAt()).isNotNull();
    }

    private void assertReleasedSessionOnly(long sessionId) {
        assertThat(tableSessionMapper.selectById(sessionId).getStatus()).isEqualTo("CLOSED");
    }

    private TableSession sessionByToken(String sessionToken) {
        return tableSessionMapper.selectOne(Wrappers.<TableSession>lambdaQuery()
                .eq(TableSession::getSessionToken, sessionToken));
    }

    private TableSession forceExpire(long sessionId) {
        tableSessionMapper.update(null, Wrappers.<TableSession>lambdaUpdate()
                .eq(TableSession::getId, sessionId)
                .set(TableSession::getExpireAt, LocalDateTime.now().minusMinutes(5)));
        return tableSessionMapper.selectById(sessionId);
    }

    private CreatedTable createTable(String merchantToken) throws Exception {
        String tableNo = "A" + suffix();
        ResponseEntity<String> response = postJson(TABLES,
                Map.of("tableNo", tableNo, "name", "过期测试桌", "capacity", 4), merchantToken, null, Map.of());
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(codeOf(response)).isZero();
        long id = ((Number) JsonPath.read(response.getBody(), "$.data.id")).longValue();
        String qrUrl = JsonPath.read(response.getBody(), "$.data.qrUrl");
        return new CreatedTable(id, tableNo, qrUrl);
    }

    private String scan(CreatedTable table, String customerToken) throws Exception {
        ResponseEntity<String> response = scanRaw(table, customerToken);
        assertThat(codeOf(response)).isZero();
        return JsonPath.read(response.getBody(), "$.data.sessionToken");
    }

    private ResponseEntity<String> scanRaw(CreatedTable table, String customerToken) throws Exception {
        Map<String, String> params = qrParams(table.qrUrl());
        return postJson(DINE_IN + "/scan",
                scanBody(table.id(), params.get("s"), Long.parseLong(params.get("t"))), customerToken, null, Map.of());
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

    private Map<String, Object> dineInOrderBody(long dishId) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("dishId", dishId);
        item.put("quantity", 1);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("items", List.of(item));
        body.put("remark", "过期清理测试");
        return body;
    }

    private String registerCustomer() throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("username", "u" + suffix());
        body.put("password", "pass1234");
        body.put("nickname", "过期测试顾客");
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

    private Map<String, String> qrParams(String qrUrl) {
        Map<String, String> params = new HashMap<>();
        String query = qrUrl.substring(qrUrl.indexOf('?') + 1);
        for (String pair : query.split("&")) {
            int index = pair.indexOf('=');
            params.put(pair.substring(0, index), URLDecoder.decode(pair.substring(index + 1), StandardCharsets.UTF_8));
        }
        return params;
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
