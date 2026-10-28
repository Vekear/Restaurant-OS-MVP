package com.restaurant.stat.vo;

import java.math.BigDecimal;

/** 订单类型占比项（{@code RatioItem}）。 */
public record RatioItem(String orderType, long orderCount, BigDecimal orderAmount, BigDecimal percent) {
}
