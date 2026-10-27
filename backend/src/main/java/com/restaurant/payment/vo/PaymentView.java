package com.restaurant.payment.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 支付单视图。 */
public record PaymentView(String payNo, Long orderId, String orderType, String orderNo,
                          BigDecimal orderAmount, BigDecimal paidAmount, String channel, String status,
                          String transactionNo, LocalDateTime paidAt, LocalDateTime callbackAt,
                          LocalDateTime expireAt, LocalDateTime createdAt) {
}
