package com.restaurant.admin.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 管理后台订单视图（{@code docs/02-api.md §2.9 AdminOrderView}）。 */
public record AdminOrderView(Long id, String orderNo, String orderType, String status, String statusText,
                             BigDecimal payAmount, BigDecimal totalAmount, BigDecimal discountAmount,
                             BigDecimal deliveryFee, int itemCount, String tableNo, LocalDateTime createdAt,
                             LocalDateTime paidAt, LocalDateTime finishedAt,
                             Long merchantId, String merchantName, String username) {
}
