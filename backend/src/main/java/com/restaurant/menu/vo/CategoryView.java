package com.restaurant.menu.vo;

import java.time.LocalDateTime;

/**
 * 分类视图（{@code docs/02-api.md §2.3}）。
 *
 * <p>公开端只返回 ENABLED 且 {@code dishCount} 仅统计可售菜品；商家端返回全部分类。
 */
public record CategoryView(Long id,
                           Long merchantId,
                           String name,
                           Integer sort,
                           String status,
                           long dishCount,
                           LocalDateTime createdAt) {
}
