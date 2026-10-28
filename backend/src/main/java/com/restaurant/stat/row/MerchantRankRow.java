package com.restaurant.stat.row;

import lombok.Data;

import java.math.BigDecimal;

/** 商家排行聚合行（管理后台导出）。 */
@Data
public class MerchantRankRow {

    private Long merchantId;

    private String merchantName;

    private Long orderCount;

    private BigDecimal revenue;
}
