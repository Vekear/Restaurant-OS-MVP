package com.restaurant.merchant.controller;

import com.restaurant.common.constant.ApiConstants;
import com.restaurant.report.ExcelResponse;
import com.restaurant.report.MerchantReportService;
import com.restaurant.report.vo.ExportFile;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 商家导出（{@code docs/02-api.md §19.2–19.3}，同时兼容任务约定的别名路径）。 */
@RestController
@RequestMapping(ApiConstants.API_V1 + "/merchant")
@RequiredArgsConstructor
@Tag(name = "商家-导出", description = "订单明细与销售报表 xlsx 导出")
@SecurityRequirement(name = "bearerAuth")
public class MerchantExportController {

    private final MerchantReportService merchantReportService;

    @Operation(summary = "导出订单明细")
    @GetMapping({"/export/orders", "/orders/export"})
    public ResponseEntity<byte[]> exportOrders(@RequestParam(required = false) String startDate,
                                               @RequestParam(required = false) String endDate,
                                               @RequestParam(required = false) String status) {
        ExportFile file = merchantReportService.exportOrders(startDate, endDate, status);
        return ExcelResponse.of(file);
    }

    @Operation(summary = "导出销售报表")
    @GetMapping({"/export/sales", "/reports/export"})
    public ResponseEntity<byte[]> exportSales(@RequestParam(required = false) String startDate,
                                              @RequestParam(required = false) String endDate) {
        ExportFile file = merchantReportService.exportSales(startDate, endDate);
        return ExcelResponse.of(file);
    }
}
