package com.restaurant.stat.vo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** 趋势视图（{@code TrendView}）。 */
public record TrendView(LocalDate startDate,
                        LocalDate endDate,
                        long totalOrderCount,
                        BigDecimal totalRevenue,
                        List<TrendPoint> points) {
}
