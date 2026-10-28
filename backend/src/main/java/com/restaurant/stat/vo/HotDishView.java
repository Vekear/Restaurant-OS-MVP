package com.restaurant.stat.vo;

import java.math.BigDecimal;

/** 热销菜品（{@code HotDishView}）。 */
public record HotDishView(Long dishId, String dishName, long quantity, BigDecimal amount) {
}
