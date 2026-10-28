package com.restaurant.stat.vo;

import java.util.List;

/** 管理端热销菜品视图（只有 items）。 */
public record HotDishItemsView(List<HotDishView> items) {
}
