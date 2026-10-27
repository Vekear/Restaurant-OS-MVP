package com.restaurant.review.vo;

import java.time.LocalDateTime;
import java.util.List;

/** 评价视图。 */
public record ReviewView(Long id, Long orderId, String orderType, String orderNo, int rating,
                         String content, String imageUrls, List<String> dishNames, String reply,
                         LocalDateTime createdAt) {
}
