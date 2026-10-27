package com.restaurant.system.vo;

import java.math.BigDecimal;
import java.util.List;

/**
 * 公开配置视图（{@code docs/02-api.md §2.2}），前端启动时拉取一次。
 *
 * <p>{@code storage} 为后端当前生效的文件存储模式（local 降级后同样返回 local + backend）；
 * {@code mockPayment.chargeAmount} 是模拟支付真实扣款金额，订单金额仍按实展示。
 */
public record PublicConfigView(String appName,
                               String currency,
                               String timezone,
                               Long defaultMerchantId,
                               MockPayment mockPayment,
                               Order order,
                               Table table,
                               Storage storage,
                               Websocket websocket) {

    /** 模拟支付说明。 */
    public record MockPayment(boolean enabled, BigDecimal chargeAmount, String currency, String hint) {
    }

    /** 订单配置。 */
    public record Order(int paymentTimeoutMinutes) {
    }

    /** 堂食配置（MVP 允许手输桌号）。 */
    public record Table(int sessionExpireMinutes, boolean allowManualInput) {
    }

    /** 文件存储配置。 */
    public record Storage(String type, String uploadMode, int maxSizeMb, List<String> allowedTypes) {
    }

    /** WebSocket 地址（原生不可用时按 pollIntervalSeconds 轮询）。 */
    public record Websocket(String stompPath, String nativePath, int pollIntervalSeconds) {
    }
}
