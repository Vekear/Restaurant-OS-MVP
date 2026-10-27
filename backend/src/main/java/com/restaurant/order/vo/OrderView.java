package com.restaurant.order.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 订单列表视图。 */
public record OrderView(Long id, String orderNo, String orderType, String status, String statusText,
                        BigDecimal payAmount, BigDecimal totalAmount, BigDecimal discountAmount,
                        BigDecimal deliveryFee, int itemCount, String tableNo,
                        LocalDateTime createdAt, LocalDateTime paidAt, LocalDateTime finishedAt) {
}
