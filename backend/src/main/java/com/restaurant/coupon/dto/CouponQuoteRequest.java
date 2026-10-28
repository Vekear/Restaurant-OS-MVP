package com.restaurant.coupon.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/** 金额试算请求（docs/02-api.md §8.4）。 */
@Data
public class CouponQuoteRequest {

    @NotNull(message = "商家 ID 不能为空")
    private Long merchantId;

    @NotNull(message = "订单商品金额不能为空")
    @DecimalMin(value = "0.01", message = "金额需在 0.01-99999.99 之间")
    @DecimalMax(value = "99999.99", message = "金额需在 0.01-99999.99 之间")
    @Digits(integer = 5, fraction = 2, message = "金额最多保留 2 位小数")
    private BigDecimal totalAmount;

    /** 可选：指定使用的用户优惠券 ID。 */
    private Long userCouponId;
}
