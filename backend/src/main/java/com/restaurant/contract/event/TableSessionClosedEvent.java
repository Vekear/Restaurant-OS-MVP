package com.restaurant.contract.event;

import java.time.LocalDateTime;

/**
 * 桌台会话关闭事件（table → ws）。
 *
 * @param tableId    桌台 ID
 * @param tableNo    桌号
 * @param merchantId 商家 ID
 * @param sessionId  会话 ID
 * @param reason     关闭原因（CLOSED / EXPIRED / ORDER_COMPLETED / ORDER_CANCELLED）
 * @param at         发生时间
 * @param traceId    链路 ID
 */
public record TableSessionClosedEvent(Long tableId,
                                      String tableNo,
                                      Long merchantId,
                                      Long sessionId,
                                      String reason,
                                      LocalDateTime at,
                                      String traceId) {
}
