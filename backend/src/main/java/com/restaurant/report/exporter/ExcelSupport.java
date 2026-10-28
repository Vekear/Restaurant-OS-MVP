package com.restaurant.report.exporter;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Excel 导出公共样式与单元格写入。 */
public final class ExcelSupport {

    public static final String CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private ExcelSupport() {
    }

    public static CellStyle headerStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setBorderBottom(BorderStyle.THIN);
        return style;
    }

    public static CellStyle moneyStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setDataFormat(workbook.createDataFormat().getFormat("0.00"));
        return style;
    }

    public static CellStyle integerStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setDataFormat(workbook.createDataFormat().getFormat("0"));
        return style;
    }

    public static CellStyle dateTimeStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setDataFormat(workbook.createDataFormat().getFormat("yyyy-mm-dd hh:mm:ss"));
        return style;
    }

    public static void writeHeader(Row row, CellStyle style, String... headers) {
        for (int i = 0; i < headers.length; i++) {
            var cell = row.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(style);
        }
    }

    public static void writeMoney(Row row, int index, CellStyle style, BigDecimal value) {
        var cell = row.createCell(index);
        if (value != null) {
            cell.setCellValue(value.setScale(2, RoundingMode.HALF_UP).doubleValue());
        }
        cell.setCellStyle(style);
    }

    public static void writeText(Row row, int index, String value) {
        row.createCell(index).setCellValue(value == null ? "" : value);
    }

    public static void writeLong(Row row, int index, CellStyle style, long value) {
        var cell = row.createCell(index);
        cell.setCellValue(value);
        cell.setCellStyle(style);
    }

    public static void autoSize(Sheet sheet, int columns) {
        for (int i = 0; i < columns; i++) {
            sheet.setColumnWidth(i, 20 * 256);
        }
    }
}
