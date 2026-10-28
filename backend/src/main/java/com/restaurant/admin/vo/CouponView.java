package com.restaurant.admin.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 优惠券视图（管理后台）。 */
public record CouponView(Long id, String name, String type, BigDecimal thresholdAmount,
                         BigDecimal discountAmount, int total, int remaining, LocalDateTime startAt,
                         LocalDateTime endAt, String status, LocalDateTime createdAt) {
}
