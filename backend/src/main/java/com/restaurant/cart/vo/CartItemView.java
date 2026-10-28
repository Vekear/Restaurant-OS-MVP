package com.restaurant.cart.vo;

import java.math.BigDecimal;

/** 购物车明细视图（docs/02-api.md §2.4）。 */
public record CartItemView(Long dishId,
                           String dishName,
                           String dishImage,
                           BigDecimal price,
                           int quantity,
                           Integer stock,
                           boolean available,
                           BigDecimal subtotal) {
}
