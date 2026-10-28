package com.restaurant.stat;

import com.restaurant.common.api.ErrorCode;
import com.restaurant.common.config.AppProperties;
import com.restaurant.common.exception.BizException;
import com.restaurant.common.util.DateRanges;
import com.restaurant.stat.mapper.StatMapper;
import com.restaurant.stat.row.HotDishRow;
import com.restaurant.stat.row.RatioRow;
import com.restaurant.stat.row.TrendRow;
import com.restaurant.stat.vo.DashboardSummaryView;
import com.restaurant.stat.vo.HotDishView;
import com.restaurant.stat.vo.HotDishesView;
import com.restaurant.stat.vo.RatioItem;
import com.restaurant.stat.vo.RatioResult;
import com.restaurant.stat.vo.TrendPoint;
import com.restaurant.stat.vo.TrendView;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 商家看板统计（{@code docs/02-api.md §15.5–15.8}）。
 *
 * <p>口径：Asia/Shanghai 自然日；营业额与热销金额只统计 {@code COMPLETED} 订单，金额取 {@code pay_amount}。
 */
@Service
@RequiredArgsConstructor
public class MerchantStatService {

    private static final String COMPLETED = "COMPLETED";

    private static final String CANCELLED = "CANCELLED";

    private static final String PAID = "PAID";

    private static final String MAKING = "MAKING";

    private final StatMapper statMapper;

    private final AppProperties appProperties;

    /** 当日汇总。{@code date} 缺省今天，不得晚于今天。 */
    public DashboardSummaryView summary(Long merchantId, String dateParam) {
        LocalDate today = DateRanges.today();
        LocalDate date = DateRanges.parseOptional(dateParam, "date");
        if (date == null) {
            date = today;
        }
        if (date.isAfter(today)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "date 不能晚于今天");
        }
        DateRanges.Range range = new DateRanges.Range(date, date);
        long orderCount = statMapper.countAll(merchantId, range.startTime(), range.endTime());
        long completedCount = statMapper.countByStatus(merchantId, range.startTime(), range.endTime(), COMPLETED);
        BigDecimal revenue = scale(statMapper.sumCompletedPayAmount(merchantId, range.startTime(), range.endTime()));
        BigDecimal avg = completedCount == 0 ? money(BigDecimal.ZERO)
                : revenue.divide(BigDecimal.valueOf(completedCount), 2, RoundingMode.HALF_UP);
        long pendingAccept = statMapper.countByStatus(merchantId, range.startTime(), range.endTime(), PAID);
        long making = statMapper.countByStatus(merchantId, range.startTime(), range.endTime(), MAKING);
        long cancelled = statMapper.countByStatus(merchantId, range.startTime(), range.endTime(), CANCELLED);
        BigDecimal refundAmount = scale(statMapper.sumRefundPayAmount(merchantId, range.startTime(), range.endTime()));
        long lowStock = statMapper.countLowStock(merchantId, appProperties.getStock().getWarnThreshold());
        return new DashboardSummaryView(date, orderCount, revenue, avg, pendingAccept, making, completedCount,
                cancelled, refundAmount, lowStock);
    }

    /** 趋势（默认最近 7 天，自然日补零）。 */
    public TrendView trend(Long merchantId, String startParam, String endParam) {
        DateRanges.Range range = resolveRange(startParam, endParam, 7);
        return buildTrend(statMapper.selectTrend(merchantId, range.startTime(), range.endTime()), range);
    }

    /** 热销菜品（默认最近 7 天，limit 1–20）。 */
    public HotDishesView hotDishes(Long merchantId, String startParam, String endParam, int limit) {
        if (limit < 1 || limit > 20) {
            throw new BizException(ErrorCode.PARAM_INVALID, "limit 取值范围为 1-20");
        }
        DateRanges.Range range = resolveRange(startParam, endParam, 7);
        List<HotDishRow> rows = statMapper.selectHotDishes(merchantId, range.startTime(), range.endTime(), limit);
        return new HotDishesView(toHotDishViews(rows), range.start(), range.end());
    }

    /** 订单类型占比（默认最近 7 天，外卖/堂食两项）。 */
    public RatioResult typeRatio(Long merchantId, String startParam, String endParam) {
        DateRanges.Range range = resolveRange(startParam, endParam, 7);
        List<RatioRow> rows = statMapper.selectTypeRatio(merchantId, range.startTime(), range.endTime());
        List<RatioItem> items = buildRatioItems(rows);
        long total = items.stream().mapToLong(RatioItem::orderCount).sum();
        return new RatioResult(items, total);
    }

    private DateRanges.Range resolveRange(String startParam, String endParam, int defaultDays) {
        LocalDate today = DateRanges.today();
        LocalDate start = DateRanges.parseOptional(startParam, "startDate");
        LocalDate end = DateRanges.parseOptional(endParam, "endDate");
        return DateRanges.resolve(start, end, today.minusDays(defaultDays - 1L), today,
                appProperties.getReport().getMaxRangeDays());
    }

    static TrendView buildTrend(List<TrendRow> rows, DateRanges.Range range) {
        Map<String, TrendRow> byDate = new LinkedHashMap<>();
        for (TrendRow row : rows) {
            byDate.put(row.getDate(), row);
        }
        List<TrendPoint> points = new ArrayList<>();
        long totalCount = 0L;
        BigDecimal totalRevenue = money(BigDecimal.ZERO);
        LocalDate cursor = range.start();
        while (!cursor.isAfter(range.end())) {
            String key = cursor.format(DateRanges.DATE_FORMAT);
            TrendRow row = byDate.get(key);
            long count = row == null || row.getOrderCount() == null ? 0L : row.getOrderCount();
            BigDecimal revenue = row == null ? money(BigDecimal.ZERO) : scale(row.getRevenue());
            points.add(new TrendPoint(cursor, count, revenue));
            totalCount += count;
            totalRevenue = totalRevenue.add(revenue);
            cursor = cursor.plusDays(1);
        }
        return new TrendView(range.start(), range.end(), totalCount, money(totalRevenue), points);
    }

    static List<HotDishView> toHotDishViews(List<HotDishRow> rows) {
        List<HotDishView> items = new ArrayList<>();
        for (HotDishRow row : rows) {
            items.add(new HotDishView(row.getDishId(), row.getDishName(),
                    row.getQuantity() == null ? 0L : row.getQuantity(), scale(row.getAmount())));
        }
        return items;
    }

    static List<RatioItem> buildRatioItems(List<RatioRow> rows) {
        Map<String, RatioRow> byType = new LinkedHashMap<>();
        for (RatioRow row : rows) {
            byType.put(row.getOrderType(), row);
        }
        long totalCount = rows.stream().mapToLong(row -> row.getOrderCount() == null ? 0L : row.getOrderCount()).sum();
        List<RatioItem> items = new ArrayList<>();
        for (String type : Arrays.asList("TAKEOUT", "DINE_IN")) {
            RatioRow row = byType.get(type);
            long count = row == null || row.getOrderCount() == null ? 0L : row.getOrderCount();
            BigDecimal amount = row == null ? money(BigDecimal.ZERO) : scale(row.getOrderAmount());
            BigDecimal percent = totalCount == 0 ? money(BigDecimal.ZERO)
                    : BigDecimal.valueOf(count).multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(totalCount), 2, RoundingMode.HALF_UP);
            items.add(new RatioItem(type, count, amount, percent));
        }
        return items;
    }

    static BigDecimal money(BigDecimal value) {
        return value == null ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : value.setScale(2, RoundingMode.HALF_UP);
    }

    static BigDecimal scale(BigDecimal value) {
        return money(value);
    }
}
