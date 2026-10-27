package com.restaurant.payment.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 创建支付单视图（前端据此展示模拟支付金额）。 */
public record PaymentCreateView(String payNo, Long orderId, String orderType, String orderNo,
                                BigDecimal orderAmount, BigDecimal paidAmount, String channel,
                                boolean mock, String mockHint, LocalDateTime expireAt) {
}
