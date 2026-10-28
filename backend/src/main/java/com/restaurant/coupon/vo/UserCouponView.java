package com.restaurant.coupon.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 用户已领取优惠券视图（docs/02-api.md §2.5）。 */
public record UserCouponView(Long id,
                             Long couponId,
                             String name,
                             String type,
                             BigDecimal thresholdAmount,
                             BigDecimal discountAmount,
                             String status,
                             LocalDateTime receivedAt,
                             LocalDateTime usedAt,
                             LocalDateTime expireAt,
                             Long orderId,
                             Boolean usable,
                             String reason) {
}
