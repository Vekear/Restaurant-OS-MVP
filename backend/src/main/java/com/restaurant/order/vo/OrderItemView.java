package com.restaurant.order.vo;

import java.math.BigDecimal;

/** 订单明细视图。 */
public record OrderItemView(Long id, Long dishId, String dishName, String dishImage,
                            BigDecimal price, Integer quantity, BigDecimal subtotal) {
}
