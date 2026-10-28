package com.restaurant.stat.vo;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 商家看板当日汇总（{@code docs/02-api.md §2.9 DashboardSummaryView}）。 */
public record DashboardSummaryView(LocalDate date,
                                   long orderCount,
                                   BigDecimal revenue,
                                   BigDecimal avgOrderAmount,
                                   long pendingAcceptCount,
                                   long makingCount,
                                   long completedCount,
                                   long cancelCount,
                                   BigDecimal refundAmount,
                                   long lowStockCount) {
}
