package com.restaurant.stat.row;

import lombok.Data;

import java.math.BigDecimal;

/** 订单类型占比聚合行。 */
@Data
public class RatioRow {

    private String orderType;

    private Long orderCount;

    private BigDecimal orderAmount;
}
