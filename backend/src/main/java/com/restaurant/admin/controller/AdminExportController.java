package com.restaurant.admin.controller;

import com.restaurant.admin.service.AdminOrderService;
import com.restaurant.common.constant.ApiConstants;
import com.restaurant.report.AdminReportService;
import com.restaurant.report.ExcelResponse;
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

/** 管理端导出（{@code docs/02-api.md §20.8/§20.19}，同时兼容任务约定的别名路径）。 */
@RestController
@RequestMapping(ApiConstants.API_V1 + "/admin")
@RequiredArgsConstructor
@Tag(name = "管理-导出", description = "全量订单与平台统计 xlsx 导出")
@SecurityRequirement(name = "bearerAuth")
public class AdminExportController {

    private final AdminReportService adminReportService;

    @Operation(summary = "导出全量订单")
    @GetMapping("/orders/export")
    public ResponseEntity<byte[]> exportOrders(@RequestParam(required = false) Long merchantId,
                                               @RequestParam(required = false) String orderType,
                                               @RequestParam(required = false) String status,
                                               @RequestParam(required = false) String startDate,
                                               @RequestParam(required = false) String endDate,
                                               @RequestParam(required = false) String keyword) {
        ExportFile file = adminReportService.exportOrders(merchantId, orderType, status, startDate, endDate,
                keyword);
        return ExcelResponse.of(file);
    }

    @Operation(summary = "导出平台统计")
    @GetMapping({"/stats/export", "/reports/export"})
    public ResponseEntity<byte[]> exportStats(@RequestParam(required = false) String startDate,
                                              @RequestParam(required = false) String endDate) {
        ExportFile file = adminReportService.exportStats(startDate, endDate);
        return ExcelResponse.of(file);
    }
}
