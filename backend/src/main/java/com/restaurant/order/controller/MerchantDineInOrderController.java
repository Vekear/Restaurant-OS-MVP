package com.restaurant.order.controller;

import com.restaurant.common.api.PageResult;
import com.restaurant.common.api.Result;
import com.restaurant.common.config.OpenApiConfig;
import com.restaurant.common.constant.ApiConstants;
import com.restaurant.order.MerchantDineInOrderService;
import com.restaurant.order.vo.OrderDetailView;
import com.restaurant.order.vo.OrderView;
import com.restaurant.security.RoleGuard;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 商家堂食订单（{@code docs/02-api.md §18.8–§18.12}）。 */
@Validated
@RestController
@RequestMapping(ApiConstants.API_V1 + "/merchant/dine-in/orders")
@RequiredArgsConstructor
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH_SCHEME)
@Tag(name = "订单-商家堂食")
public class MerchantDineInOrderController {

    private final MerchantDineInOrderService merchantDineInOrderService;

    private final RoleGuard roleGuard;

    @GetMapping
    @Operation(summary = "商家堂食订单列表")
    public Result<PageResult<OrderView>> list(
            @RequestParam(required = false, defaultValue = "ACTIVE") String statusGroup,
            @RequestParam(required = false) String status,
            @RequestParam(required = false, defaultValue = "1") @Min(1) @Max(10000) int page,
            @RequestParam(required = false, defaultValue = "10") @Min(1) @Max(50) int size) {
        return Result.success(merchantDineInOrderService.list(roleGuard.requireMerchantId(),
                statusGroup, status, page, size));
    }

    @GetMapping("/{id}")
    @Operation(summary = "堂食订单详情")
    public Result<OrderDetailView> detail(@PathVariable Long id) {
        return Result.success(merchantDineInOrderService.detail(roleGuard.requireMerchantId(), id));
    }

    @PostMapping("/{id}/accept")
    @Operation(summary = "接单 PAID→ACCEPTED")
    public Result<OrderDetailView> accept(@PathVariable Long id) {
        return Result.success(merchantDineInOrderService.accept(roleGuard.requireMerchantId(), id));
    }

    @PostMapping("/{id}/prepare")
    @Operation(summary = "开始制作 ACCEPTED→MAKING")
    public Result<OrderDetailView> prepare(@PathVariable Long id) {
        return Result.success(merchantDineInOrderService.prepare(roleGuard.requireMerchantId(), id));
    }

    @PostMapping("/{id}/serve")
    @Operation(summary = "出餐 MAKING→SERVED")
    public Result<OrderDetailView> serve(@PathVariable Long id) {
        return Result.success(merchantDineInOrderService.serve(roleGuard.requireMerchantId(), id));
    }

    @PostMapping("/{id}/complete")
    @Operation(summary = "完成 SERVED→COMPLETED（同一事务释放桌台）")
    public Result<OrderDetailView> complete(@PathVariable Long id) {
        return Result.success(merchantDineInOrderService.complete(roleGuard.requireMerchantId(), id));
    }
}
