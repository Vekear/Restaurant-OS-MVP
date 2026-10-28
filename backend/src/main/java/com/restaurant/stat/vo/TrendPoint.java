package com.restaurant.stat.vo;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 趋势点（自然日，补零）。 */
public record TrendPoint(LocalDate date, long orderCount, BigDecimal revenue) {
}
