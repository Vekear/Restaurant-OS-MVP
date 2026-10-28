package com.restaurant.admin.controller;

import com.restaurant.common.constant.ApiConstants;
import com.restaurant.common.api.Result;
import com.restaurant.stat.AdminStatService;
import com.restaurant.stat.vo.HotDishItemsView;
import com.restaurant.stat.vo.RatioItemsView;
import com.restaurant.stat.vo.StatsOverviewView;
import com.restaurant.stat.vo.TrendView;
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

/** 管理后台统计（{@code docs/02-api.md §20.15–20.18}）。 */
@Validated
@RestController
@RequestMapping(ApiConstants.API_V1 + "/admin/stats")
@RequiredArgsConstructor
@Tag(name = "管理-统计", description = "平台概览、趋势、订单类型占比、热销菜品")
@SecurityRequirement(name = "bearerAuth")
public class AdminStatsController {

    private final AdminStatService adminStatService;

    @Operation(summary = "平台概览（默认近 30 天）")
    @GetMapping("/overview")
    public Result<StatsOverviewView> overview(@RequestParam(required = false) String startDate,
                                              @RequestParam(required = false) String endDate) {
        return Result.success(adminStatService.overview(startDate, endDate));
    }

    @Operation(summary = "平台趋势")
    @GetMapping("/trend")
    public Result<TrendView> trend(@RequestParam(required = false) String startDate,
                                   @RequestParam(required = false) String endDate) {
        return Result.success(adminStatService.trend(startDate, endDate));
    }

    @Operation(summary = "订单类型占比")
    @GetMapping("/order-type-ratio")
    public Result<RatioItemsView> orderTypeRatio(@RequestParam(required = false) String startDate,
                                                 @RequestParam(required = false) String endDate) {
        return Result.success(adminStatService.typeRatio(startDate, endDate));
    }

    @Operation(summary = "热销菜品")
    @GetMapping("/hot-dishes")
    public Result<HotDishItemsView> hotDishes(@RequestParam(required = false) String startDate,
                                           @RequestParam(required = false) String endDate,
                                           @RequestParam(defaultValue = "10") @Min(1) @Max(50) int limit) {
        return Result.success(adminStatService.hotDishes(startDate, endDate, limit));
    }
}
