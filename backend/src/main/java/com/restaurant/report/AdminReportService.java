package com.restaurant.report;

import com.restaurant.common.api.ErrorCode;
import com.restaurant.common.enums.OrderStatus;
import com.restaurant.common.exception.BizException;
import com.restaurant.common.util.DateRanges;
import com.restaurant.report.exporter.OrderExcelExporter;
import com.restaurant.report.exporter.SalesExcelExporter;
import com.restaurant.report.mapper.ReportMapper;
import com.restaurant.report.row.OrderExportRow;
import com.restaurant.report.vo.ExportFile;
import com.restaurant.report.vo.SalesExportData;
import com.restaurant.stat.mapper.StatMapper;
import com.restaurant.stat.row.HotDishRow;
import com.restaurant.stat.row.MerchantRankRow;
import com.restaurant.stat.row.RatioRow;
import com.restaurant.stat.row.TrendRow;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/** 管理端全量订单/统计导出（{@code docs/02-api.md §20.8/§20.19}）。 */
@Service
@RequiredArgsConstructor
public class AdminReportService {

    private static final int MAX_ROWS = 20_000;

    private final ReportMapper reportMapper;

    private final StatMapper statMapper;

    private final OrderExcelExporter orderExcelExporter;

    private final SalesExcelExporter salesExcelExporter;

    @Transactional(readOnly = true)
    public ExportFile exportOrders(Long merchantId, String orderType, String status, String startDate,
                                   String endDate, String keyword) {
        DateRanges.Range range = resolveRange(startDate, endDate);
        if (status != null && !status.isBlank() && !OrderStatus.isValid(status)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "非法的订单状态");
        }
        String resolvedKeyword = keyword == null || keyword.isBlank() ? null : keyword.trim();
        if (resolvedKeyword != null && resolvedKeyword.length() > 30) {
            throw new BizException(ErrorCode.PARAM_INVALID, "keyword 不能超过 30 字");
        }
        List<OrderExportRow> rows = reportMapper.selectOrderRows(merchantId,
                status == null || status.isBlank() ? null : status, resolvedKeyword,
                range.startTime(), range.endTime(), MAX_ROWS);
        return new ExportFile(filename("orders", range), orderExcelExporter.export(rows, true));
    }

    @Transactional(readOnly = true)
    public ExportFile exportStats(String startDate, String endDate) {
        DateRanges.Range range = resolveRange(startDate, endDate);
        SalesExportData data = buildStatsData(range);
        return new ExportFile(filename("stats", range), salesExcelExporter.adminStats(data));
    }

    private SalesExportData buildStatsData(DateRanges.Range range) {
        long orderCount = statMapper.countByStatus(null, range.startTime(), range.endTime(), "COMPLETED");
        BigDecimal revenue = nullSafe(statMapper.sumCompletedPayAmount(null, range.startTime(), range.endTime()));
        BigDecimal refund = nullSafe(statMapper.sumRefundPayAmount(null, range.startTime(), range.endTime()));
        BigDecimal avg = orderCount == 0 ? BigDecimal.ZERO
                : revenue.divide(BigDecimal.valueOf(orderCount), 2, RoundingMode.HALF_UP);
        List<TrendRow> trend = statMapper.selectTrend(null, range.startTime(), range.endTime());
        List<HotDishRow> hot = statMapper.selectHotDishesByName(range.startTime(), range.endTime(), 10);
        List<RatioRow> ratios = statMapper.selectTypeRatio(null, range.startTime(), range.endTime());
        List<MerchantRankRow> ranking = statMapper.selectMerchantRanking(range.startTime(), range.endTime(), 20);
        return new SalesExportData(range.start(), range.end(), orderCount, revenue, avg, refund,
                trend, hot, ratios, ranking);
    }

    private DateRanges.Range resolveRange(String startDate, String endDate) {
        DateRanges.Range defaults = new DateRanges.Range(DateRanges.today().minusDays(29), DateRanges.today());
        return DateRanges.resolve(DateRanges.parseOptional(startDate, "startDate"),
                DateRanges.parseOptional(endDate, "endDate"), defaults.start(), defaults.end(), 92);
    }

    private static String filename(String prefix, DateRanges.Range range) {
        return prefix + "-" + DateRanges.fileDate(range.start()) + "-" + DateRanges.fileDate(range.end()) + ".xlsx";
    }

    private static BigDecimal nullSafe(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value.setScale(2, RoundingMode.HALF_UP);
    }
}
