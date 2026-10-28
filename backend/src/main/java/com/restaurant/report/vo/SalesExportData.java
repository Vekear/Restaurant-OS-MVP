package com.restaurant.report.vo;

import com.restaurant.stat.row.HotDishRow;
import com.restaurant.stat.row.MerchantRankRow;
import com.restaurant.stat.row.RatioRow;
import com.restaurant.stat.row.TrendRow;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** 销售/统计报表导出数据。 */
public record SalesExportData(LocalDate startDate,
                              LocalDate endDate,
                              long orderCount,
                              BigDecimal revenue,
                              BigDecimal avgOrderAmount,
                              BigDecimal refundAmount,
                              List<TrendRow> trend,
                              List<HotDishRow> hotDishes,
                              List<RatioRow> ratios,
                              List<MerchantRankRow> merchantRanking) {
}
