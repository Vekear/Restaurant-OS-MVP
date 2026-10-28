package com.restaurant.ws;

import com.restaurant.order.Order;
import com.restaurant.order.mapper.OrderMapper;
import com.restaurant.security.JwtTokenProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * T34：WebSocket STOMP 鉴权与订阅 ACL 集成测试（真实随机端口 + 真实 Spring 上下文）。
 *
 * <p>覆盖 docs/05-security.md H-01 的核心场景：
 * <ul>
 *   <li>匿名/无效 token 的 CONNECT 被 ERROR 拒绝并断开；</li>
 *   <li>CUSTOMER 不能订阅 admin / 商家 / 他人订单频道；</li>
 *   <li>MERCHANT 只能订阅自己商家的频道与订单；</li>
 *   <li>ADMIN 可订阅 admin 与任意商家/订单频道；</li>
 *   <li>CUSTOMER 可订阅自己的订单频道；</li>
 *   <li>桌台频道按桌台所属商家校验；</li>
 *   <li>未列明目的地（/queue、/app、/topic/unknown）默认拒绝；客户端直接 SEND 也被拒绝。</li>
 * </ul>
 *
 * <p>「订阅成功」通过 {@link SimpMessagingTemplate} 轮询向该 destination 推送探针并收到
 * MESSAGE 来证明，避免依赖 SimpleBroker 不支持的 RECEIPT。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class WebSocketAuthIntegrationTest {

    private static final long TIMEOUT_SECONDS = 10;

    @LocalServerPort
    private int port;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    private final AtomicInteger subscriptionSeq = new AtomicInteger();

    private final List<StompSession> sessions = new ArrayList<>();

    private final List<Long> createdOrderIds = new ArrayList<>();

    private WebSocketStompClient stompClient;

    private String adminToken;
    private String merchantToken;
    private String merchant2Token;
    private String customerToken;
    private String otherCustomerToken;
    private Long ownOrderId;
    private Long otherOrderId;

    @BeforeEach
    void setUp() {
        stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        stompClient.start();
        adminToken = token(1L, "admin", "ADMIN", null);
        merchantToken = token(2L, "merchant", "MERCHANT", 1L);
        // 仅用于验证「他店商家」分支：claim 中的 merchantId=2（种子库只有 merchant 1）。
        merchant2Token = token(2L, "merchant", "MERCHANT", 2L);
        customerToken = token(3L, "customer", "CUSTOMER", null);
        otherCustomerToken = token(1L, "other-customer", "CUSTOMER", null);
        ownOrderId = createOrder(3L, 1L);
        otherOrderId = createOrder(1L, 1L);
    }

    @AfterEach
    void tearDown() {
        for (StompSession session : sessions) {
            try {
                if (session.isConnected()) {
                    session.disconnect();
                }
            } catch (org.springframework.messaging.MessagingException ignored) {
                // 被 ACL 拒绝的会话已由服务端关闭，忽略重复断开。
            }
        }
        sessions.clear();
        createdOrderIds.forEach(orderMapper::deleteById);
        createdOrderIds.clear();
        if (stompClient != null) {
            stompClient.stop();
        }
    }

    // ------------------------------------------------------------------
    // CONNECT
    // ------------------------------------------------------------------

    @Test
    void anonymousConnectIsRejected() throws Exception {
        RecordingHandler handler = open(null);

        ExecutionException failure = org.junit.jupiter.api.Assertions.assertThrows(ExecutionException.class,
                () -> handler.connectFuture.get(TIMEOUT_SECONDS, TimeUnit.SECONDS),
                "匿名 CONNECT 不应成功");
        assertThat(failure).isNotNull();
        StompHeaders error = handler.error.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertThat(error.getFirst("message")).contains("WS_UNAUTHORIZED").contains("CONNECT");
        assertThat(handler.connected).isNotDone();
    }

    @Test
    void invalidTokenConnectIsRejected() throws Exception {
        RecordingHandler handler = open("this-is-not-a-valid-jwt");

        org.junit.jupiter.api.Assertions.assertThrows(ExecutionException.class,
                () -> handler.connectFuture.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        StompHeaders error = handler.error.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertThat(error.getFirst("message")).contains("WS_UNAUTHORIZED").contains("token 无效");
    }

    // ------------------------------------------------------------------
    // 订阅 ACL
    // ------------------------------------------------------------------

    @Test
    void customerCannotSubscribeAdminTopic() throws Exception {
        ConnectedClient client = connect(customerToken);
        assertSubscribeRejected(client, "/topic/admin/orders", "WS_FORBIDDEN");
    }

    @Test
    void customerCannotSubscribeMerchantTopic() throws Exception {
        ConnectedClient client = connect(customerToken);
        assertSubscribeRejected(client, "/topic/merchant/1/orders", "WS_FORBIDDEN");
    }

    @Test
    void merchantCanSubscribeOwnShopOrders() throws Exception {
        ConnectedClient client = connect(merchantToken);
        assertSubscribedWithProbe(client, "/topic/merchant/1/orders");
        // 与 web 前端 stores/merchant.js 的订阅路径保持一致。
        assertThat(client.handler.error).isNotDone();
    }

    @Test
    void merchantCannotSubscribeOtherShopOrders() throws Exception {
        ConnectedClient client = connect(merchant2Token);
        assertSubscribeRejected(client, "/topic/merchant/1/orders", "WS_FORBIDDEN");
    }

    @Test
    void merchantCanSubscribeOwnShopTables() throws Exception {
        ConnectedClient client = connect(merchantToken);
        assertSubscribedWithProbe(client, "/topic/merchant/1/tables");
    }

    @Test
    void adminCanSubscribeAdminAndAnyMerchantTopic() throws Exception {
        ConnectedClient adminClient = connect(adminToken);
        assertSubscribedWithProbe(adminClient, "/topic/admin/orders");

        ConnectedClient adminClient2 = connect(adminToken);
        assertSubscribedWithProbe(adminClient2, "/topic/merchant/99/orders");
    }

    @Test
    void customerCanSubscribeOwnOrderOnly() throws Exception {
        ConnectedClient own = connect(customerToken);
        assertSubscribedWithProbe(own, "/topic/orders/" + ownOrderId);

        ConnectedClient other = connect(customerToken);
        assertSubscribeRejected(other, "/topic/orders/" + otherOrderId, "WS_FORBIDDEN");

        ConnectedClient unknown = connect(customerToken);
        assertSubscribeRejected(unknown, "/topic/orders/999999999", "WS_NOT_FOUND");
    }

    @Test
    void otherCustomerTokenCannotSubscribeOrder() throws Exception {
        ConnectedClient client = connect(otherCustomerToken);
        assertSubscribeRejected(client, "/topic/orders/" + ownOrderId, "WS_FORBIDDEN");
    }

    @Test
    void merchantOrderTopicFollowsOwnership() throws Exception {
        ConnectedClient own = connect(merchantToken);
        assertSubscribedWithProbe(own, "/topic/orders/" + ownOrderId);

        ConnectedClient other = connect(merchant2Token);
        assertSubscribeRejected(other, "/topic/orders/" + ownOrderId, "WS_FORBIDDEN");
    }

    @Test
    void adminCanSubscribeAnyOrder() throws Exception {
        ConnectedClient client = connect(adminToken);
        assertSubscribedWithProbe(client, "/topic/orders/" + otherOrderId);
    }

    @Test
    void tableTopicChecksTableOwner() throws Exception {
        // 种子桌台 1 属于 merchant 1。
        assertSubscribedWithProbe(connect(merchantToken), "/topic/table/1");
        assertSubscribeRejected(connect(merchant2Token), "/topic/table/1", "WS_FORBIDDEN");
        assertSubscribeRejected(connect(customerToken), "/topic/table/1", "WS_FORBIDDEN");
        assertSubscribeRejected(connect(merchantToken), "/topic/table/999999999", "WS_NOT_FOUND");
        assertSubscribedWithProbe(connect(adminToken), "/topic/table/1");
    }

    @Test
    void unknownDestinationsAreDeniedByDefault() throws Exception {
        assertSubscribeRejected(connect(customerToken), "/queue/anything", "WS_FORBIDDEN");
        assertSubscribeRejected(connect(customerToken), "/app/echo", "WS_FORBIDDEN");
        assertSubscribeRejected(connect(customerToken), "/topic/anything", "WS_FORBIDDEN");
        assertSubscribeRejected(connect(merchantToken), "/topic/merchant/1/other", "WS_FORBIDDEN");
    }

    @Test
    void userDestinationAllowedForAuthenticatedUser() throws Exception {
        ConnectedClient client = connect(customerToken);
        RecordingPayloadHandler frameHandler = subscribe(client, "/user/queue/reply");
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS);
        while (System.nanoTime() < deadline) {
            messagingTemplate.convertAndSendToUser("3", "/queue/reply", "probe-user");
            if (frameHandler.poll(250) != null) {
                assertThat(client.handler.error).isNotDone();
                return;
            }
        }
        fail("已认证用户订阅 /user/queue/reply 后未收到用户目的地推送");
    }

    @Test
    void clientSendFramesAreDenied() throws Exception {
        ConnectedClient client = connect(customerToken);
        client.session.send("/topic/orders/" + ownOrderId, "forged".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        StompHeaders error = client.handler.error.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertThat(error.getFirst("message")).contains("WS_FORBIDDEN");
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private String token(Long userId, String username, String role, Long merchantId) {
        return jwtTokenProvider.issue(userId, username, role, merchantId).token();
    }

    private Long createOrder(Long userId, Long merchantId) {
        Order order = new Order();
        order.setOrderNo("WSTEST" + System.nanoTime());
        order.setUserId(userId);
        order.setMerchantId(merchantId);
        order.setOrderType("TAKEOUT");
        order.setStatus("PENDING_PAYMENT");
        order.setTotalAmount(new BigDecimal("1.00"));
        order.setDiscountAmount(BigDecimal.ZERO);
        order.setDeliveryFee(BigDecimal.ZERO);
        order.setPayAmount(new BigDecimal("1.00"));
        orderMapper.insert(order);
        createdOrderIds.add(order.getId());
        return order.getId();
    }

    private String url() {
        return "ws://127.0.0.1:" + port + "/ws";
    }

    private RecordingHandler open(String token) {
        RecordingHandler handler = new RecordingHandler();
        StompHeaders connectHeaders = new StompHeaders();
        if (token != null) {
            connectHeaders.add("Authorization", "Bearer " + token);
        }
        handler.connectFuture = stompClient.connectAsync(url(), new WebSocketHttpHeaders(), connectHeaders, handler);
        return handler;
    }

    private ConnectedClient connect(String token) throws Exception {
        RecordingHandler handler = open(token);
        StompSession session = handler.connectFuture.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        sessions.add(session);
        return new ConnectedClient(session, handler);
    }

    private RecordingPayloadHandler subscribe(ConnectedClient client, String destination) {
        RecordingPayloadHandler frameHandler = new RecordingPayloadHandler();
        StompHeaders headers = new StompHeaders();
        headers.setDestination(destination);
        headers.setId("sub-" + subscriptionSeq.incrementAndGet());
        client.session.subscribe(headers, frameHandler);
        return frameHandler;
    }

    /** 订阅应成功：反复向 destination 推送探针，直到收到 MESSAGE。 */
    private void assertSubscribedWithProbe(ConnectedClient client, String destination) throws Exception {
        RecordingPayloadHandler frameHandler = subscribe(client, destination);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS);
        while (System.nanoTime() < deadline) {
            if (client.handler.error.isDone()) {
                fail("订阅 " + destination + " 被拒绝: "
                        + client.handler.error.get().getFirst("message"));
            }
            messagingTemplate.convertAndSend(destination, "probe");
            if (frameHandler.poll(250) != null) {
                return;
            }
        }
        fail("订阅 " + destination + " 未收到探针推送");
    }

    /** 订阅应被拒绝：服务端回 ERROR 帧（随后关闭连接）。 */
    private void assertSubscribeRejected(ConnectedClient client, String destination, String expectedCode)
            throws Exception {
        subscribe(client, destination);
        StompHeaders error = client.handler.error.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertThat(error.getFirst("message"))
                .as("订阅 %s 的 ERROR message", destination)
                .contains(expectedCode);
    }

    private record ConnectedClient(StompSession session, RecordingHandler handler) {
    }

    /** STOMP 会话级 handler：记录 CONNECTED / ERROR / transport error。 */
    private static final class RecordingHandler extends StompSessionHandlerAdapter {

        private CompletableFuture<StompSession> connectFuture;

        private final CompletableFuture<StompSession> connected = new CompletableFuture<>();

        private final CompletableFuture<StompHeaders> error = new CompletableFuture<>();

        private final CompletableFuture<Throwable> transportError = new CompletableFuture<>();

        @Override
        public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
            connected.complete(session);
        }

        @Override
        public void handleFrame(StompHeaders headers, Object payload) {
            // 会话级 handler 的 handleFrame 仅在服务端回 ERROR（或未知帧）时被调用；
            // 正常 MESSAGE 会路由到各订阅自己的 StompFrameHandler。
            error.complete(headers);
        }

        @Override
        public void handleException(StompSession session, StompCommand command, StompHeaders headers,
                                    byte[] payload, Throwable exception) {
            if (StompCommand.ERROR.equals(command)) {
                error.complete(headers);
            }
        }

        @Override
        public void handleTransportError(StompSession session, Throwable exception) {
            transportError.complete(exception);
        }
    }

    /** 订阅帧 handler：只记录收到的 MESSAGE 数量，payload 类型用 byte[] 避免 JSON 转换依赖。 */
    private static final class RecordingPayloadHandler implements StompFrameHandler {

        private final BlockingQueue<Object> payloads = new LinkedBlockingQueue<>();

        @Override
        public Type getPayloadType(StompHeaders headers) {
            return byte[].class;
        }

        @Override
        public void handleFrame(StompHeaders headers, Object payload) {
            payloads.add(payload == null ? new byte[0] : payload);
        }

        Object poll(long millis) throws InterruptedException {
            return payloads.poll(millis, TimeUnit.MILLISECONDS);
        }
    }
}
