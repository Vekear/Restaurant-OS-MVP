package com.restaurant.contract.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单事件（订单创建/状态变更/支付成功），由 order 域发布、ws/audit 监听。
 *
 * @param eventType  ORDER_CREATED / ORDER_STATUS_CHANGED / PAYMENT_SUCCEEDED / PAYMENT_REFUNDED
 * @param orderId    订单 ID
 * @param orderNo    订单号
 * @param orderType  TAKEOUT / DINE_IN
 * @param merchantId 商家 ID
 * @param status     订单状态
 * @param statusText 状态中文
 * @param payAmount  应付金额
 * @param paidAmount 模拟实扣金额（可为 null）
 * @param itemCount  明细行数（仅 ORDER_CREATED 填充）
 * @param tableId    桌台 ID（堂食）
 * @param tableNo    桌号（堂食）
 * @param at         事件时间
 * @param traceId    链路 ID
 */
public record OrderStatusChangedEvent(String eventType,
                                      Long orderId,
                                      String orderNo,
                                      String orderType,
                                      Long merchantId,
                                      String status,
                                      String statusText,
                                      BigDecimal payAmount,
                                      BigDecimal paidAmount,
                                      Integer itemCount,
                                      Long tableId,
                                      String tableNo,
                                      LocalDateTime at,
                                      String traceId) {
}
