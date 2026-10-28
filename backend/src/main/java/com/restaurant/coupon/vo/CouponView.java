package com.restaurant.coupon.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 可领取优惠券模板视图（docs/02-api.md §2.5）。 */
public record CouponView(Long id,
                         String name,
                         String type,
                         BigDecimal thresholdAmount,
                         BigDecimal discountAmount,
                         LocalDateTime startAt,
                         LocalDateTime endAt,
                         Integer total,
                         Integer remaining,
                         String status,
                         Boolean claimed,
                         Boolean claimable,
                         Boolean usable) {
}
