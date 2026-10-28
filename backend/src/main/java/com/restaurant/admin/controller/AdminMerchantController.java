package com.restaurant.admin.controller;

import com.restaurant.admin.dto.MerchantAuditRequest;
import com.restaurant.admin.service.AdminMerchantService;
import com.restaurant.common.constant.ApiConstants;
import com.restaurant.common.api.PageResult;
import com.restaurant.common.api.Result;
import com.restaurant.merchant.vo.MerchantAdminView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 管理后台商家管理（{@code docs/02-api.md §20.3–20.5}）。 */
@Validated
@RestController
@RequestMapping(ApiConstants.API_V1 + "/admin/merchants")
@RequiredArgsConstructor
@Tag(name = "管理-商家", description = "商家列表、详情、审核与启用禁用")
@SecurityRequirement(name = "bearerAuth")
public class AdminMerchantController {

    private final AdminMerchantService adminMerchantService;

    @Operation(summary = "商家列表")
    @GetMapping
    public Result<PageResult<MerchantAdminView>> list(@RequestParam(required = false) String keyword,
                                                      @RequestParam(required = false) String auditStatus,
                                                      @RequestParam(required = false) String status,
                                                      @RequestParam(defaultValue = "1") @Min(1) @Max(10000) int page,
                                                      @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size) {
        return Result.success(adminMerchantService.list(keyword, auditStatus, status, page, size));
    }

    @Operation(summary = "商家详情")
    @GetMapping("/{id}")
    public Result<MerchantAdminView> get(@PathVariable Long id) {
        return Result.success(adminMerchantService.get(id));
    }

    @Operation(summary = "商家审核/启用禁用")
    @PatchMapping("/{id}/status")
    public Result<MerchantAdminView> updateStatus(@PathVariable Long id,
                                                  @Valid @RequestBody MerchantAuditRequest request) {
        return Result.success(adminMerchantService.updateStatus(id, request));
    }
}
