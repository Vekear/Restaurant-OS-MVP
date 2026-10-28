package com.restaurant.stat.vo;

import java.time.LocalDate;
import java.util.List;

/** 商家看板热销列表响应。 */
public record HotDishesView(List<HotDishView> items, LocalDate startDate, LocalDate endDate) {
}
