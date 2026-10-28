package com.restaurant.user.vo;

import java.time.LocalDateTime;

/** 收货地址视图（docs/02-api.md §2.4）。 */
public record AddressView(Long id,
                          String consignee,
                          String phone,
                          String province,
                          String city,
                          String district,
                          String detail,
                          Boolean isDefault,
                          LocalDateTime createdAt,
                          LocalDateTime updatedAt) {
}
