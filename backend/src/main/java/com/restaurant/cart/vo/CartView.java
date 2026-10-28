package com.restaurant.cart.vo;

import java.math.BigDecimal;
import java.util.List;

/** 购物车视图（docs/02-api.md §2.4，所有购物车接口返回最新视图）。 */
public record CartView(List<CartItemView> items,
                       int totalQuantity,
                       BigDecimal totalAmount,
                       int invalidCount,
                       int maxItems) {
}
