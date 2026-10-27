package com.restaurant.menu.controller;

import com.restaurant.common.api.PageResult;
import com.restaurant.common.api.Result;
import com.restaurant.common.constant.ApiConstants;
import com.restaurant.menu.service.DishService;
import com.restaurant.menu.vo.DishSummaryView;
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

/**
 * 商家端低库存查询（{@code docs/02-api.md §16.13}）。
 */
@RestController
@RequestMapping(ApiConstants.API_V1 + "/merchant/inventory")
@RequiredArgsConstructor
@Validated
@Tag(name = "商家-库存")
@SecurityRequirement(name = "bearerAuth")
public class InventoryController {

    private final DishService dishService;

    @GetMapping("/low-stock")
    @Operation(summary = "低库存菜品分页")
    public Result<PageResult<DishSummaryView>> lowStock(
            @RequestParam(defaultValue = "5") @Min(0) @Max(9999) int threshold,
            @RequestParam(defaultValue = "1") @Min(1) @Max(10000) long page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) long size) {
        return Result.success(dishService.listLowStock(threshold, page, size));
    }
}
