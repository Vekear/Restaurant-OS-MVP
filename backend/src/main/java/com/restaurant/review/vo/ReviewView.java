package com.restaurant.review.vo;

import java.time.LocalDateTime;
import java.util.List;

/** 评价视图（顾客与商家端共用，{@code docs/02-api.md §2.3}）。 */
public record ReviewView(Long id, Long orderId, String orderType, String orderNo, Long userId, String userName,
                         int rating, String content, String imageUrls, List<String> dishNames, String reply,
                         LocalDateTime createdAt) {
}
