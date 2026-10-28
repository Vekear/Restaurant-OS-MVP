package com.restaurant.admin.controller;

import com.restaurant.admin.dto.CouponCreateRequest;
import com.restaurant.admin.dto.CouponStatusRequest;
import com.restaurant.admin.dto.CouponUpdateRequest;
import com.restaurant.admin.service.AdminCouponService;
import com.restaurant.admin.vo.CouponView;
import com.restaurant.common.constant.ApiConstants;
import com.restaurant.common.api.PageResult;
import com.restaurant.common.api.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 管理后台优惠券（{@code docs/02-api.md §20.9–20.14}）。 */
@Validated
@RestController
@RequestMapping(ApiConstants.API_V1 + "/admin/coupons")
@RequiredArgsConstructor
@Tag(name = "管理-优惠券", description = "优惠券 CRUD 与状态切换")
@SecurityRequirement(name = "bearerAuth")
public class AdminCouponController {

    private final AdminCouponService adminCouponService;

    @Operation(summary = "优惠券列表")
    @GetMapping
    public Result<PageResult<CouponView>> list(@RequestParam(required = false) String status,
                                               @RequestParam(required = false) String keyword,
                                               @RequestParam(defaultValue = "1") @Min(1) @Max(10000) int page,
                                               @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size) {
        return Result.success(adminCouponService.list(status, keyword, page, size));
    }

    @Operation(summary = "优惠券详情")
    @GetMapping("/{id}")
    public Result<CouponView> get(@PathVariable Long id) {
        return Result.success(adminCouponService.get(id));
    }

    @Operation(summary = "创建优惠券")
    @PostMapping
    public Result<CouponView> create(@Valid @RequestBody CouponCreateRequest request) {
        return Result.success(adminCouponService.create(request));
    }

    @Operation(summary = "更新优惠券")
    @PutMapping("/{id}")
    public Result<CouponView> update(@PathVariable Long id, @Valid @RequestBody CouponUpdateRequest request) {
        return Result.success(adminCouponService.update(id, request));
    }

    @Operation(summary = "删除优惠券")
    @DeleteMapping("/{id}")
    public Result<Map<String, Boolean>> delete(@PathVariable Long id) {
        return Result.success(adminCouponService.delete(id));
    }

    @Operation(summary = "切换优惠券状态")
    @PatchMapping("/{id}/status")
    public Result<CouponView> updateStatus(@PathVariable Long id,
                                           @Valid @RequestBody CouponStatusRequest request) {
        return Result.success(adminCouponService.updateStatus(id, request));
    }
}
