package com.restaurant.contract;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 支付单快照（JSON 形状与 docs/02-api.md §2.6 PaymentView 完全一致）。
 *
 * <p>订单详情装配、支付接口均复用它，避免跨域暴露 payment 实体。
 */
public record PaymentSnapshot(String payNo,
                              Long orderId,
                              String orderType,
                              String orderNo,
                              BigDecimal orderAmount,
                              BigDecimal paidAmount,
                              String channel,
                              String status,
                              String transactionNo,
                              LocalDateTime paidAt,
                              LocalDateTime callbackAt,
                              LocalDateTime expireAt,
                              LocalDateTime createdAt) {
}
