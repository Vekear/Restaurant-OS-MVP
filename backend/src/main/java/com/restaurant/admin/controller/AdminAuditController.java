package com.restaurant.admin.controller;

import com.restaurant.admin.service.AdminAuditService;
import com.restaurant.admin.vo.AuditLogView;
import com.restaurant.common.constant.ApiConstants;
import com.restaurant.common.api.PageResult;
import com.restaurant.common.api.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 管理后台审计日志（{@code docs/02-api.md §20.20}）。 */
@Validated
@RestController
@RequestMapping(ApiConstants.API_V1 + "/admin/audit-logs")
@RequiredArgsConstructor
@Tag(name = "管理-审计日志", description = "审计日志查询")
@SecurityRequirement(name = "bearerAuth")
public class AdminAuditController {

    private final AdminAuditService adminAuditService;

    @Operation(summary = "审计日志列表")
    @GetMapping
    public Result<PageResult<AuditLogView>> list(@RequestParam(required = false) String action,
                                                 @RequestParam(required = false) Long userId,
                                                 @RequestParam(required = false) String startDate,
                                                 @RequestParam(required = false) String endDate,
                                                 @RequestParam(defaultValue = "1") @Min(1) @Max(10000) int page,
                                                 @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size) {
        return Result.success(adminAuditService.list(action, userId, startDate, endDate, page, size));
    }
}
