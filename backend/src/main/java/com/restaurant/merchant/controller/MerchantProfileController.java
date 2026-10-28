package com.restaurant.merchant.controller;

import com.restaurant.common.constant.ApiConstants;
import com.restaurant.common.api.Result;
import com.restaurant.merchant.dto.BusinessStatusRequest;
import com.restaurant.merchant.dto.MerchantProfileUpdateRequest;
import com.restaurant.merchant.service.MerchantProfileService;
import com.restaurant.merchant.vo.BusinessStatusView;
import com.restaurant.merchant.vo.MerchantAdminView;
import com.restaurant.merchant.vo.MerchantProfileView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 商家店铺资料与营业状态（{@code docs/02-api.md §15.1–15.4}）。 */
@RestController
@RequestMapping(ApiConstants.API_V1 + "/merchant")
@RequiredArgsConstructor
@Tag(name = "商家-店铺", description = "店铺资料与营业状态")
@SecurityRequirement(name = "bearerAuth")
public class MerchantProfileController {

    private final MerchantProfileService merchantProfileService;

    @Operation(summary = "店铺资料")
    @GetMapping("/profile")
    public Result<MerchantProfileView> profile() {
        return Result.success(merchantProfileService.profile());
    }

    @Operation(summary = "更新店铺资料")
    @PutMapping("/profile")
    public Result<MerchantAdminView> updateProfile(@Valid @RequestBody MerchantProfileUpdateRequest request) {
        return Result.success(merchantProfileService.update(request));
    }

    @Operation(summary = "查询营业状态")
    @GetMapping("/business-status")
    public Result<BusinessStatusView> businessStatus() {
        return Result.success(new BusinessStatusView(merchantProfileService.businessStatus()));
    }

    @Operation(summary = "切换营业状态")
    @PutMapping("/business-status")
    public Result<BusinessStatusView> updateBusinessStatus(@Valid @RequestBody BusinessStatusRequest request) {
        return Result.success(new BusinessStatusView(merchantProfileService.updateBusinessStatus(request)));
    }
}
