package com.restaurant.stat.vo;

import java.util.List;

/** 商家看板订单类型占比。 */
public record RatioResult(List<RatioItem> items, long totalOrderCount) {
}
