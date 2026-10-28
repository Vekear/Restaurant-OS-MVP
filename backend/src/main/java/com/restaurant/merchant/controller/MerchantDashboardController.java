package com.restaurant.merchant.controller;

import com.restaurant.common.constant.ApiConstants;
import com.restaurant.common.api.Result;
import com.restaurant.merchant.MerchantGuard;
import com.restaurant.stat.MerchantStatService;
import com.restaurant.stat.vo.DashboardSummaryView;
import com.restaurant.stat.vo.HotDishesView;
import com.restaurant.stat.vo.RatioResult;
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

/** 商家看板（{@code docs/02-api.md §15.5–15.8}）。 */
@Validated
@RestController
@RequestMapping(ApiConstants.API_V1 + "/merchant/dashboard")
@RequiredArgsConstructor
@Tag(name = "商家-看板", description = "今日概览、趋势、热销、订单类型占比")
@SecurityRequirement(name = "bearerAuth")
public class MerchantDashboardController {

    private final MerchantStatService merchantStatService;

    private final MerchantGuard merchantGuard;

    @Operation(summary = "今日经营概览")
    @GetMapping("/summary")
    public Result<DashboardSummaryView> summary(@RequestParam(required = false) String date) {
        return Result.success(merchantStatService.summary(merchantGuard.requireActiveMerchantId(), date));
    }

    @Operation(summary = "营业趋势（默认近 7 天）")
    @GetMapping("/trend")
    public Result<TrendView> trend(@RequestParam(required = false) String startDate,
                                   @RequestParam(required = false) String endDate) {
        return Result.success(merchantStatService.trend(merchantGuard.requireActiveMerchantId(), startDate, endDate));
    }

    @Operation(summary = "热销菜品 Top")
    @GetMapping("/hot-dishes")
    public Result<HotDishesView> hotDishes(@RequestParam(required = false) String startDate,
                                           @RequestParam(required = false) String endDate,
                                           @RequestParam(defaultValue = "5") @Min(1) @Max(20) int limit) {
        return Result.success(merchantStatService.hotDishes(merchantGuard.requireActiveMerchantId(),
                startDate, endDate, limit));
    }

    @Operation(summary = "订单类型占比")
    @GetMapping("/order-type-ratio")
    public Result<RatioResult> orderTypeRatio(@RequestParam(required = false) String startDate,
                                              @RequestParam(required = false) String endDate) {
        return Result.success(merchantStatService.typeRatio(merchantGuard.requireActiveMerchantId(),
                startDate, endDate));
    }
}
