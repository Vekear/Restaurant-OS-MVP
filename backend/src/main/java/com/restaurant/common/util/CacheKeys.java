package com.restaurant.common.util;

/**
 * Redis key / Spring Cache 名称常量（{@code docs/01-architecture.md §13}）。
 *
 * <p>规则 R7：缓存读写只出现在 Service 层，key 统一由本类生成，禁止在业务代码中拼字符串。
 */
public final class CacheKeys {

    private CacheKeys() {
    }

    // ---------- Spring Cache 名称 ----------
    public static final String MENU_CATEGORIES = "menu:categories";

    public static final String MENU_DISHES = "menu:dishes";

    public static final String MENU_DISH = "menu:dish";

    public static final String MENU_MERCHANT = "menu:merchant";

    // ---------- 限流（固定窗口，单位：分钟） ----------
    private static final String RATE_LOGIN_PREFIX = "rate:login:";

    private static final String RATE_REGISTER_PREFIX = "rate:register:";

    private static final String RATE_WECHAT_PREFIX = "rate:wechat:";

    private static final String RATE_ORDER_PREFIX = "rate:order:";

    private static final String RATE_PAY_PREFIX = "rate:pay:";

    private static final String RATE_API_PREFIX = "rate:api:";

    // ---------- 幂等 / 鉴权 / 会话 ----------
    private static final String IDEMPOTENCY_PREFIX = "idem:";

    private static final String JWT_BLACKLIST_PREFIX = "jwt:blacklist:";

    private static final String WS_PRESENCE_PREFIX = "ws:presence:";

    private static final String TABLE_SIGNATURE_PREFIX = "table:sig:";

    public static String rateLoginKey(String ip) {
        return RATE_LOGIN_PREFIX + ip;
    }

    /** 顾客注册限流 key：按客户端 IP 计，与登录刻意使用不同前缀避免互相消耗配额。 */
    public static String rateRegisterKey(String ip) {
        return RATE_REGISTER_PREFIX + ip;
    }

    /** 微信小程序登录限流 key：按客户端 IP 计，与账号密码登录独立计数。 */
    public static String rateWechatKey(String ip) {
        return RATE_WECHAT_PREFIX + ip;
    }

    public static String rateOrderKey(Long userId) {
        return RATE_ORDER_PREFIX + userId;
    }

    public static String ratePayKey(Long userId) {
        return RATE_PAY_PREFIX + userId;
    }

    public static String rateApiKey(Long userId, String method, String path) {
        return RATE_API_PREFIX + userId + ":" + method + ":" + path;
    }

    public static String idempotencyReplayKey(Long userId, String idempotencyKey) {
        return IDEMPOTENCY_PREFIX + userId + ":" + idempotencyKey;
    }

    public static String jwtBlacklistKey(String jti) {
        return JWT_BLACKLIST_PREFIX + jti;
    }

    public static String wsPresenceKey(Long userId) {
        return WS_PRESENCE_PREFIX + userId;
    }

    public static String tableSignatureKey(Long tableId, int qrVersion) {
        return TABLE_SIGNATURE_PREFIX + tableId + ":" + qrVersion;
    }
}
