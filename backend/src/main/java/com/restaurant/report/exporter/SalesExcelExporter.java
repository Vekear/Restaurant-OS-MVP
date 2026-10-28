package com.restaurant.report.exporter;

import com.restaurant.common.api.ErrorCode;
import com.restaurant.common.exception.BizException;
import com.restaurant.report.vo.SalesExportData;
import com.restaurant.stat.row.HotDishRow;
import com.restaurant.stat.row.MerchantRankRow;
import com.restaurant.stat.row.RatioRow;
import com.restaurant.stat.row.TrendRow;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** 销售/统计报表 Excel（POI SXSSF；{@code docs/02-api.md §19.3/§20.19}）。 */
@Component
public class SalesExcelExporter {

    private static final DateTimeFormatter FILE_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    /** 商家销售报表：汇总 / 趋势 / 热销 / 订单类型 4 个 sheet。 */
    public byte[] merchantSales(SalesExportData data) {
        try (SXSSFWorkbook workbook = new SXSSFWorkbook(200);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            CellStyle header = ExcelSupport.headerStyle(workbook);
            CellStyle money = ExcelSupport.moneyStyle(workbook);
            CellStyle integer = ExcelSupport.integerStyle(workbook);
            summarySheet(workbook, header, money, integer, data, "汇总");
            trendSheet(workbook, header, money, integer, data);
            hotSheet(workbook, header, money, integer, data);
            ratioSheet(workbook, header, money, integer, data);
            workbook.write(out);
            workbook.dispose();
            return out.toByteArray();
        } catch (IOException e) {
            throw new BizException(ErrorCode.STORAGE_ERROR, "销售报表导出失败，请重试");
        }
    }

    /** 管理端统计报表：平台汇总 / 趋势 / 订单类型 / 热销菜品 / 商家排行 5 个 sheet。 */
    public byte[] adminStats(SalesExportData data) {
        try (SXSSFWorkbook workbook = new SXSSFWorkbook(200);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            CellStyle header = ExcelSupport.headerStyle(workbook);
            CellStyle money = ExcelSupport.moneyStyle(workbook);
            CellStyle integer = ExcelSupport.integerStyle(workbook);
            summarySheet(workbook, header, money, integer, data, "平台汇总");
            trendSheet(workbook, header, money, integer, data);
            ratioSheet(workbook, header, money, integer, data);
            hotSheet(workbook, header, money, integer, data);
            merchantRankSheet(workbook, header, money, integer, data);
            workbook.write(out);
            workbook.dispose();
            return out.toByteArray();
        } catch (IOException e) {
            throw new BizException(ErrorCode.STORAGE_ERROR, "统计报表导出失败，请重试");
        }
    }

    private void summarySheet(SXSSFWorkbook workbook, CellStyle header, CellStyle money, CellStyle integer,
                              SalesExportData data, String name) {
        Sheet sheet = workbook.createSheet(name);
        Row title = sheet.createRow(0);
        ExcelSupport.writeHeader(title, header, "指标", "数值");
        int rowIndex = 1;
        rowIndex = metricRow(sheet, rowIndex, "统计区间",
                data.startDate().format(FILE_DATE) + "-" + data.endDate().format(FILE_DATE));
        rowIndex = metricRow(sheet, rowIndex, "订单数", String.valueOf(data.orderCount()));
        rowIndex = moneyMetricRow(sheet, rowIndex, "营业额", data.revenue(), money);
        rowIndex = moneyMetricRow(sheet, rowIndex, "客单价", data.avgOrderAmount(), money);
        moneyMetricRow(sheet, rowIndex, "退款金额", data.refundAmount(), money);
        ExcelSupport.autoSize(sheet, 2);
    }

    private int metricRow(Sheet sheet, int rowIndex, String name, String value) {
        Row row = sheet.createRow(rowIndex);
        ExcelSupport.writeText(row, 0, name);
        ExcelSupport.writeText(row, 1, value);
        return rowIndex + 1;
    }

    private int moneyMetricRow(Sheet sheet, int rowIndex, String name, BigDecimal value, CellStyle style) {
        Row row = sheet.createRow(rowIndex);
        ExcelSupport.writeText(row, 0, name);
        ExcelSupport.writeMoney(row, 1, style, value);
        return rowIndex + 1;
    }

    private void trendSheet(SXSSFWorkbook workbook, CellStyle header, CellStyle money, CellStyle integer,
                            SalesExportData data) {
        Sheet sheet = workbook.createSheet("趋势");
        ExcelSupport.writeHeader(sheet.createRow(0), header, "日期", "订单数", "营业额");
        int rowIndex = 1;
        for (TrendRow row : safe(data.trend())) {
            Row target = sheet.createRow(rowIndex++);
            ExcelSupport.writeText(target, 0, row.getDate());
            ExcelSupport.writeLong(target, 1, integer, nullSafe(row.getOrderCount()));
            ExcelSupport.writeMoney(target, 2, money, row.getRevenue());
        }
        ExcelSupport.autoSize(sheet, 3);
    }

    private void hotSheet(SXSSFWorkbook workbook, CellStyle header, CellStyle money, CellStyle integer,
                          SalesExportData data) {
        Sheet sheet = workbook.createSheet("热销");
        ExcelSupport.writeHeader(sheet.createRow(0), header, "菜品", "销量", "金额");
        int rowIndex = 1;
        for (HotDishRow row : safe(data.hotDishes())) {
            Row target = sheet.createRow(rowIndex++);
            ExcelSupport.writeText(target, 0, row.getDishName());
            ExcelSupport.writeLong(target, 1, integer, nullSafe(row.getQuantity()));
            ExcelSupport.writeMoney(target, 2, money, row.getAmount());
        }
        ExcelSupport.autoSize(sheet, 3);
    }

    private void ratioSheet(SXSSFWorkbook workbook, CellStyle header, CellStyle money, CellStyle integer,
                            SalesExportData data) {
        Sheet sheet = workbook.createSheet("订单类型");
        ExcelSupport.writeHeader(sheet.createRow(0), header, "类型", "订单数", "金额", "占比(%)");
        long total = safe(data.ratios()).stream().mapToLong(r -> nullSafe(r.getOrderCount())).sum();
        int rowIndex = 1;
        for (RatioRow row : safe(data.ratios())) {
            Row target = sheet.createRow(rowIndex++);
            ExcelSupport.writeText(target, 0, "DINE_IN".equals(row.getOrderType()) ? "堂食" : "外卖");
            ExcelSupport.writeLong(target, 1, integer, nullSafe(row.getOrderCount()));
            ExcelSupport.writeMoney(target, 2, money, row.getOrderAmount());
            BigDecimal percent = total == 0 ? BigDecimal.ZERO
                    : BigDecimal.valueOf(nullSafe(row.getOrderCount()) * 100.0 / total)
                    .setScale(2, RoundingMode.HALF_UP);
            ExcelSupport.writeMoney(target, 3, money, percent);
        }
        ExcelSupport.autoSize(sheet, 4);
    }

    private void merchantRankSheet(SXSSFWorkbook workbook, CellStyle header, CellStyle money, CellStyle integer,
                                   SalesExportData data) {
        Sheet sheet = workbook.createSheet("商家排行");
        ExcelSupport.writeHeader(sheet.createRow(0), header, "商家", "订单数", "营业额");
        int rowIndex = 1;
        for (MerchantRankRow row : safe(data.merchantRanking())) {
            Row target = sheet.createRow(rowIndex++);
            ExcelSupport.writeText(target, 0, row.getMerchantName());
            ExcelSupport.writeLong(target, 1, integer, nullSafe(row.getOrderCount()));
            ExcelSupport.writeMoney(target, 2, money, row.getRevenue());
        }
        ExcelSupport.autoSize(sheet, 3);
    }

    private static <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }

    private static long nullSafe(Long value) {
        return value == null ? 0L : value;
    }
}
