package com.restaurant.merchant.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 管理后台商家视图（{@code docs/02-api.md §2.9 AdminMerchantView}，供管理端与商家资料更新响应复用）。 */
public record MerchantAdminView(Long id,
                                Long userId,
                                String ownerUsername,
                                String name,
                                String phone,
                                String address,
                                String logoUrl,
                                BigDecimal deliveryFee,
                                BigDecimal minOrderAmount,
                                String businessStatus,
                                String auditStatus,
                                String status,
                                long orderCount,
                                LocalDateTime createdAt) {
}
