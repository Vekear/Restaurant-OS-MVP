package com.restaurant.common.constant;

/**
 * 跨模块共享的协议常量（路径、请求头、WebSocket topic）。
 *
 * <p>Controller/Service 中禁止散落字符串字面量，统一引用本类。
 */
public final class ApiConstants {

    private ApiConstants() {
    }

    // ---------- HTTP ----------
    /** 统一 API 前缀。 */
    public static final String API_V1 = "/api/v1";

    /** 幂等键请求头（下单/支付/堂食下单必填，8–64 字符）。 */
    public static final String HEADER_IDEMPOTENCY_KEY = "Idempotency-Key";

    /** 幂等重放响应头。 */
    public static final String HEADER_IDEMPOTENCY_REPLAYED = "Idempotency-Replayed";

    /** 堂食会话凭据请求头。 */
    public static final String HEADER_TABLE_SESSION = "X-Table-Session";

    /** 限流响应头（秒）。 */
    public static final String HEADER_RETRY_AFTER = "Retry-After";

    public static final int IDEMPOTENCY_KEY_MIN_LENGTH = 8;

    public static final int IDEMPOTENCY_KEY_MAX_LENGTH = 64;

    // ---------- WebSocket ----------
    /** STOMP 端点（纯 WebSocket，无 SockJS）；实时通道唯一入口（ADR-0014）。 */
    public static final String WS_ENDPOINT = "/ws";

    /** 订阅前缀。 */
    public static final String WS_TOPIC_PREFIX = "/topic";

    public static final String WS_QUEUE_PREFIX = "/queue";

    /** 应用端点前缀（@MessageMapping）。 */
    public static final String WS_APPLICATION_PREFIX = "/app";

    public static final String WS_USER_PREFIX = "/user";

    /** /topic/orders/{orderId}。 */
    public static final String WS_TOPIC_ORDERS = WS_TOPIC_PREFIX + "/orders";

    /** /topic/merchant/{merchantId}/orders。 */
    public static final String WS_TOPIC_MERCHANT_ORDERS = WS_TOPIC_PREFIX + "/merchant";

    /** /topic/table/{tableId}。 */
    public static final String WS_TOPIC_TABLE = WS_TOPIC_PREFIX + "/table";

    /** /topic/admin/orders。 */
    public static final String WS_TOPIC_ADMIN_ORDERS = WS_TOPIC_PREFIX + "/admin/orders";
}
