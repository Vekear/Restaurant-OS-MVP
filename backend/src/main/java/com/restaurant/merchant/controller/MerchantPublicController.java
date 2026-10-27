package com.restaurant.merchant.controller;

import com.restaurant.common.api.Result;
import com.restaurant.common.constant.ApiConstants;
import com.restaurant.merchant.vo.MerchantPublicView;
import com.restaurant.menu.service.MenuQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 店铺公开信息（{@code docs/02-api.md §2.2}）。
 */
@RestController
@RequestMapping(ApiConstants.API_V1 + "/merchants")
@RequiredArgsConstructor
@Tag(name = "店铺公开信息")
public class MerchantPublicController {

    private final MenuQueryService menuQueryService;

    @GetMapping("/{id}")
    @Operation(summary = "店铺公开信息")
    public Result<MerchantPublicView> get(@PathVariable Long id) {
        return Result.success(menuQueryService.getMerchantPublic(id));
    }
}
