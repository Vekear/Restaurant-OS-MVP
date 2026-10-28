package com.restaurant.stat.vo;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 管理后台统计总览（{@code StatsOverviewView}）。 */
public record StatsOverviewView(LocalDate startDate,
                                LocalDate endDate,
                                long orderCount,
                                BigDecimal revenue,
                                BigDecimal avgOrderAmount,
                                BigDecimal refundAmount,
                                long userCount,
                                long newUserCount,
                                long merchantCount) {
}
