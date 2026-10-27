package com.restaurant.order.vo;

import com.restaurant.payment.vo.PaymentView;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** 订单详情视图（列表字段 + 明细 + 支付 + 时间线 + 操作按钮）。 */
public record OrderDetailView(Long id, String orderNo, String orderType, String status, String statusText,
                              BigDecimal payAmount, BigDecimal totalAmount, BigDecimal discountAmount,
                              BigDecimal deliveryFee, int itemCount, String tableNo,
                              LocalDateTime createdAt, LocalDateTime paidAt, LocalDateTime finishedAt,
                              Long userId, Long merchantId, String merchantName,
                              List<OrderItemView> items, String consignee, String consigneePhone,
                              String deliveryAddress, Long tableId, Long tableSessionId, String remark,
                              String cancelReason, LocalDateTime timeoutAt, PaymentView payment,
                              List<StatusLogView> statusLogs, boolean canPay, boolean canCancel,
                              boolean canConfirmReceipt, boolean canReview, Long reviewId) {
}
