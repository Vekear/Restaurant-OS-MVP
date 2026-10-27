package com.restaurant.merchant.vo;

import java.math.BigDecimal;

/**
 * 店铺公开信息（{@code docs/02-api.md §2.2}）。
 *
 * <p>仅 {@code auditStatus=APPROVED && status=ENABLED} 的商家可见，否则 40401。
 */
public record MerchantPublicView(Long id,
                                 String name,
                                 String description,
                                 String logoUrl,
                                 String phone,
                                 String address,
                                 BigDecimal deliveryFee,
                                 BigDecimal minOrderAmount,
                                 String businessStatus) {
}
