package com.restaurant.report;

import com.restaurant.common.api.ErrorCode;
import com.restaurant.common.enums.OrderStatus;
import com.restaurant.common.exception.BizException;
import com.restaurant.common.util.DateRanges;
import com.restaurant.merchant.MerchantGuard;
import com.restaurant.report.exporter.OrderExcelExporter;
import com.restaurant.report.exporter.SalesExcelExporter;
import com.restaurant.report.mapper.ReportMapper;
import com.restaurant.report.row.OrderExportRow;
import com.restaurant.report.vo.ExportFile;
import com.restaurant.report.vo.SalesExportData;
import com.restaurant.stat.mapper.StatMapper;
import com.restaurant.stat.row.HotDishRow;
import com.restaurant.stat.row.RatioRow;
import com.restaurant.stat.row.TrendRow;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/** 商家订单/销售导出（{@code docs/02-api.md §19.2–19.3}）。 */
@Service
@RequiredArgsConstructor
public class MerchantReportService {

    /** 单次导出行数上限（导出为异步外的保护阈值）。 */
    private static final int MAX_ROWS = 20_000;

    private final ReportMapper reportMapper;

    private final StatMapper statMapper;

    private final MerchantGuard merchantGuard;

    private final OrderExcelExporter orderExcelExporter;

    private final SalesExcelExporter salesExcelExporter;

    @Transactional(readOnly = true)
    public ExportFile exportOrders(String startDate, String endDate, String status) {
        Long merchantId = merchantGuard.requireActiveMerchantId();
        DateRanges.Range range = resolveRange(startDate, endDate);
        if (status != null && !status.isBlank() && !OrderStatus.isValid(status)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "非法的订单状态");
        }
        List<OrderExportRow> rows = reportMapper.selectOrderRows(merchantId,
                status == null || status.isBlank() ? null : status, null,
                range.startTime(), range.endTime(), MAX_ROWS);
        return new ExportFile(filename("orders", range), orderExcelExporter.export(rows, false));
    }

    @Transactional(readOnly = true)
    public ExportFile exportSales(String startDate, String endDate) {
        Long merchantId = merchantGuard.requireActiveMerchantId();
        DateRanges.Range range = resolveRange(startDate, endDate);
        SalesExportData data = buildSalesData(merchantId, range);
        return new ExportFile(filename("sales", range), salesExcelExporter.merchantSales(data));
    }

    private SalesExportData buildSalesData(Long merchantId, DateRanges.Range range) {
        long orderCount = statMapper.countByStatus(merchantId, range.startTime(), range.endTime(), "COMPLETED");
        BigDecimal revenue = nullSafe(statMapper.sumCompletedPayAmount(merchantId, range.startTime(), range.endTime()));
        BigDecimal refund = nullSafe(statMapper.sumRefundPayAmount(merchantId, range.startTime(), range.endTime()));
        BigDecimal avg = orderCount == 0 ? BigDecimal.ZERO
                : revenue.divide(BigDecimal.valueOf(orderCount), 2, RoundingMode.HALF_UP);
        List<TrendRow> trend = statMapper.selectTrend(merchantId, range.startTime(), range.endTime());
        List<HotDishRow> hot = statMapper.selectHotDishes(merchantId, range.startTime(), range.endTime(), 10);
        List<RatioRow> ratios = statMapper.selectTypeRatio(merchantId, range.startTime(), range.endTime());
        return new SalesExportData(range.start(), range.end(), orderCount, revenue, avg, refund,
                trend, hot, ratios, List.of());
    }

    private DateRanges.Range resolveRange(String startDate, String endDate) {
        DateRanges.Range defaults = new DateRanges.Range(DateRanges.today().minusDays(6), DateRanges.today());
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
