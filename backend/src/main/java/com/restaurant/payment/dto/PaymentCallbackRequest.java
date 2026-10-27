package com.restaurant.payment.dto;

import java.math.BigDecimal;

/** 模拟支付网关回调体（POST /api/v1/payments/mock/callback）。 */
public record PaymentCallbackRequest(String payNo, String transactionNo, BigDecimal paidAmount,
                                     String paidAt, String status) {
}
