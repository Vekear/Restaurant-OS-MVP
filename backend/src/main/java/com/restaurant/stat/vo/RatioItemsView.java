package com.restaurant.stat.vo;

import java.util.List;

/** 管理后台订单类型占比响应（仅 items）。 */
public record RatioItemsView(List<RatioItem> items) {
}
