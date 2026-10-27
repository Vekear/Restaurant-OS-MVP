package com.restaurant.menu.vo;

import java.math.BigDecimal;

/**
 * 菜品列表视图（{@code docs/02-api.md §2.3}）；{@code soldOut = stock <= 0}。
 */
public record DishSummaryView(Long id,
                              String name,
                              Long categoryId,
                              String categoryName,
                              BigDecimal price,
                              BigDecimal originalPrice,
                              String imageUrl,
                              Integer stock,
                              Integer sales,
                              String status,
                              boolean soldOut,
                              Integer sort) {
}
