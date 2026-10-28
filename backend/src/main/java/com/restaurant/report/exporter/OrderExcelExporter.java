package com.restaurant.report.exporter;

import com.restaurant.common.api.ErrorCode;
import com.restaurant.common.enums.OrderStatus;
import com.restaurant.common.exception.BizException;
import com.restaurant.report.row.OrderExportRow;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** 订单明细 Excel（POI SXSSF；{@code docs/02-api.md §19.2/§20.8}）。 */
@Component
public class OrderExcelExporter {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public byte[] export(List<OrderExportRow> rows, boolean withMerchant) {
        try (SXSSFWorkbook workbook = new SXSSFWorkbook(200);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("订单");
            CellStyle header = ExcelSupport.headerStyle(workbook);
            CellStyle money = ExcelSupport.moneyStyle(workbook);
            CellStyle dateTime = ExcelSupport.dateTimeStyle(workbook);
            List<String> headers = new ArrayList<>(List.of("订单号", "下单时间", "订单类型", "状态"));
            if (withMerchant) {
                headers.add("商家");
            }
            headers.addAll(List.of("收货人", "联系电话", "收货地址", "菜品明细",
                    "商品金额", "配送费", "优惠金额", "订单实付金额", "模拟扣款金额", "支付状态"));
            Row headerRow = sheet.createRow(0);
            ExcelSupport.writeHeader(headerRow, header, headers.toArray(String[]::new));

            int rowIndex = 1;
            for (OrderExportRow data : rows) {
                Row row = sheet.createRow(rowIndex++);
                int col = 0;
                ExcelSupport.writeText(row, col++, data.getOrderNo());
                var timeCell = row.createCell(col++);
                if (data.getCreatedAt() != null) {
                    timeCell.setCellValue(data.getCreatedAt().format(TIME_FORMAT));
                }
                ExcelSupport.writeText(row, col++, "DINE_IN".equals(data.getOrderType()) ? "堂食" : "外卖");
                ExcelSupport.writeText(row, col++, OrderStatus.textOf(data.getStatus()));
                if (withMerchant) {
                    ExcelSupport.writeText(row, col++, data.getMerchantName());
                }
                ExcelSupport.writeText(row, col++, data.getConsignee());
                ExcelSupport.writeText(row, col++, data.getConsigneePhone());
                ExcelSupport.writeText(row, col++, data.getDeliveryAddress());
                ExcelSupport.writeText(row, col++, data.getDishSummary());
                ExcelSupport.writeMoney(row, col++, money, data.getTotalAmount());
                ExcelSupport.writeMoney(row, col++, money, data.getDeliveryFee());
                ExcelSupport.writeMoney(row, col++, money, data.getDiscountAmount());
                ExcelSupport.writeMoney(row, col++, money, data.getPayAmount());
                ExcelSupport.writeMoney(row, col++, money, data.getMockPaidAmount());
                ExcelSupport.writeText(row, col, paymentStatusText(data.getPaymentStatus()));
            }
            ExcelSupport.autoSize(sheet, headers.size());
            workbook.write(out);
            workbook.dispose();
            return out.toByteArray();
        } catch (IOException e) {
            throw new BizException(ErrorCode.STORAGE_ERROR, "订单导出失败，请重试");
        }
    }

    private static String paymentStatusText(String status) {
        if (status == null) {
            return "无支付";
        }
        return switch (status) {
            case "SUCCESS" -> "支付成功";
            case "REFUNDED" -> "已退款";
            case "FAILED" -> "支付失败";
            default -> "待支付";
        };
    }
}
