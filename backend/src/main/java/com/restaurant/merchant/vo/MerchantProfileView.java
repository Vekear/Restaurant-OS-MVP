package com.restaurant.merchant.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 商家端店铺资料（{@code docs/02-api.md §2.2 MerchantProfileView}）。 */
public record MerchantProfileView(Long id,
                                  Long userId,
                                  String ownerUsername,
                                  String name,
                                  String description,
                                  String logoUrl,
                                  String phone,
                                  String address,
                                  BigDecimal deliveryFee,
                                  BigDecimal minOrderAmount,
                                  String businessStatus,
                                  String auditStatus,
                                  String status,
                                  LocalDateTime createdAt) {
}
