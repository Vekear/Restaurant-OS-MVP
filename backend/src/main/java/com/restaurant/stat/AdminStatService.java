package com.restaurant.stat;

import com.restaurant.common.api.ErrorCode;
import com.restaurant.common.config.AppProperties;
import com.restaurant.common.exception.BizException;
import com.restaurant.common.util.DateRanges;
import com.restaurant.stat.mapper.StatMapper;
import com.restaurant.stat.row.HotDishRow;
import com.restaurant.stat.row.MerchantRankRow;
import com.restaurant.stat.row.RatioRow;
import com.restaurant.stat.vo.HotDishItemsView;
import com.restaurant.stat.vo.HotDishView;
import com.restaurant.stat.vo.RatioItem;
import com.restaurant.stat.vo.RatioItemsView;
import com.restaurant.stat.vo.StatsOverviewView;
import com.restaurant.stat.vo.TrendView;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

/**
 * 管理后台统计（{@code docs/02-api.md §20.15–20.18}）。
 *
 * <p>默认近 30 天；趋势补零、金额两位小数；热销按菜品名跨店合并。
 */
@Service
@RequiredArgsConstructor
public class AdminStatService {

    private final StatMapper statMapper;

    private final AppProperties appProperties;

    /** 统计总览（默认近 30 天）。 */
    public StatsOverviewView overview(String startParam, String endParam) {
        DateRanges.Range range = resolveRange(startParam, endParam);
        long orderCount = statMapper.countByStatus(null, range.startTime(), range.endTime(), "COMPLETED");
        BigDecimal revenue = MerchantStatService.scale(
                statMapper.sumCompletedPayAmount(null, range.startTime(), range.endTime()));
        BigDecimal avg = orderCount == 0 ? MerchantStatService.money(BigDecimal.ZERO)
                : revenue.divide(BigDecimal.valueOf(orderCount), 2, RoundingMode.HALF_UP);
        BigDecimal refundAmount = MerchantStatService.scale(
                statMapper.sumRefundPayAmount(null, range.startTime(), range.endTime()));
        long userCount = statMapper.countUsers();
        long newUserCount = statMapper.countNewUsers(range.startTime(), range.endTime());
        long merchantCount = statMapper.countApprovedMerchants();
        return new StatsOverviewView(range.start(), range.end(), orderCount, revenue, avg, refundAmount,
                userCount, newUserCount, merchantCount);
    }

    /** 平台趋势（默认近 30 天）。 */
    public TrendView trend(String startParam, String endParam) {
        DateRanges.Range range = resolveRange(startParam, endParam);
        return MerchantStatService.buildTrend(
                statMapper.selectTrend(null, range.startTime(), range.endTime()), range);
    }

    /** 平台订单类型占比。 */
    public RatioItemsView typeRatio(String startParam, String endParam) {
        DateRanges.Range range = resolveRange(startParam, endParam);
        List<RatioRow> rows = statMapper.selectTypeRatio(null, range.startTime(), range.endTime());
        return new RatioItemsView(MerchantStatService.buildRatioItems(rows));
    }

    /** 平台热销菜品（默认近 30 天，limit 1–50，默认 10）。 */
    public HotDishItemsView hotDishes(String startParam, String endParam, int limit) {
        int resolvedLimit = limit <= 0 ? 10 : limit;
        if (resolvedLimit > 50) {
            throw new BizException(ErrorCode.PARAM_INVALID, "limit 取值范围为 1-50");
        }
        DateRanges.Range range = resolveRange(startParam, endParam);
        List<HotDishRow> rows = statMapper.selectHotDishesByName(range.startTime(), range.endTime(), resolvedLimit);
        return new HotDishItemsView(MerchantStatService.toHotDishViews(rows));
    }

    /** 商家排行（统计导出用，取前 20）。 */
    public List<MerchantRankRow> merchantRanking(DateRanges.Range range) {
        return statMapper.selectMerchantRanking(range.startTime(), range.endTime(), 20);
    }

    /** 解析统计区间（默认近 30 天，跨度 ≤ app.report.max-range-days）。 */
    public DateRanges.Range resolveRange(String startParam, String endParam) {
        LocalDate today = DateRanges.today();
        LocalDate start = DateRanges.parseOptional(startParam, "startDate");
        LocalDate end = DateRanges.parseOptional(endParam, "endDate");
        return DateRanges.resolve(start, end, today.minusDays(29), today,
                appProperties.getReport().getMaxRangeDays());
    }

    /** 商家占比项构造复用（对商家服务保持包内可见）。 */
    static List<RatioItem> ratioItems(List<RatioRow> rows) {
        return MerchantStatService.buildRatioItems(rows);
    }
}
