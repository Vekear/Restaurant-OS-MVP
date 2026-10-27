package com.restaurant.menu.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 菜品详情视图（{@code docs/02-api.md §2.3}：DishSummaryView + 描述/评分/时间）。
 */
public record DishDetailView(Long id,
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
                             Integer sort,
                             String description,
                             BigDecimal ratingAvg,
                             long reviewCount,
                             LocalDateTime createdAt,
                             LocalDateTime updatedAt) {
}
