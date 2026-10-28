package com.restaurant.coupon.vo;

import java.math.BigDecimal;
import java.util.List;

/** 金额试算结果（docs/02-api.md §2.5）。 */
public record QuoteView(Long merchantId,
                        BigDecimal totalAmount,
                        BigDecimal deliveryFee,
                        BigDecimal discountAmount,
                        BigDecimal payAmount,
                        BigDecimal minOrderAmount,
                        Boolean reachMinOrder,
                        UserCouponView userCoupon,
                        List<UserCouponView> usableCoupons) {
}
