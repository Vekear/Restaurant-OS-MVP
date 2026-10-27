package com.restaurant.contract.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 支付成功事件：payment 域发布，order 域监听后将订单由 PENDING_PAYMENT 流转为 PAID
 * （{@code docs/01-architecture.md §11.3} 唯一入口）。
 *
 * @param paymentId     支付单 ID
 * @param payNo         支付单号
 * @param orderId       订单 ID
 * @param orderNo       订单号
 * @param paidAmount    模拟实扣金额
 * @param paidAt        支付时间
 * @param transactionNo 模拟网关交易号
 */
public record PaymentSucceededEvent(Long paymentId,
                                    String payNo,
                                    Long orderId,
                                    String orderNo,
                                    BigDecimal paidAmount,
                                    LocalDateTime paidAt,
                                    String transactionNo) {
}
