package com.restaurant.stat.row;

import lombok.Data;

import java.math.BigDecimal;

/** 趋势聚合行。 */
@Data
public class TrendRow {

    private String date;

    private Long orderCount;

    private BigDecimal revenue;
}
