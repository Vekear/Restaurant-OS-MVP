package com.restaurant.order.controller;

import com.restaurant.common.api.PageResult;
import com.restaurant.common.api.Result;
import com.restaurant.common.config.OpenApiConfig;
import com.restaurant.common.constant.ApiConstants;
import com.restaurant.order.MerchantOrderService;
import com.restaurant.order.dto.OrderRejectRequest;
import com.restaurant.order.vo.OrderDetailView;
import com.restaurant.order.vo.OrderView;
import com.restaurant.security.RoleGuard;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 商家订单接口：接单/拒单/制作/配送/完成，均经状态机 + 角色校验。 */
@Validated
@RestController
@RequestMapping(ApiConstants.API_V1 + "/merchant/orders")
@RequiredArgsConstructor
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH_SCHEME)
@Tag(name = "订单-商家")
public class MerchantOrderController {

    private final MerchantOrderService merchantOrderService;

    private final RoleGuard roleGuard;

    @GetMapping
    @Operation(summary = "商家外卖订单列表")
    public Result<PageResult<OrderView>> list(
            @RequestParam(required = false, defaultValue = "ACTIVE") String statusGroup,
            @RequestParam(required = false) String status,
            @RequestParam(required = false, defaultValue = "1") @Min(1) @Max(10000) int page,
            @RequestParam(required = false, defaultValue = "10") @Min(1) @Max(50) int size) {
        return Result.success(merchantOrderService.list(roleGuard.requireMerchantId(), statusGroup, status, page, size));
    }

    @GetMapping("/{id}")
    @Operation(summary = "商家订单详情")
    public Result<OrderDetailView> detail(@PathVariable Long id) {
        return Result.success(merchantOrderService.detail(roleGuard.requireMerchantId(), id));
    }

    @PostMapping("/{id}/accept")
    @Operation(summary = "接单 PAID→ACCEPTED")
    public Result<OrderDetailView> accept(@PathVariable Long id) {
        return Result.success(merchantOrderService.accept(roleGuard.requireMerchantId(), id));
    }

    @PostMapping("/{id}/reject")
    @Operation(summary = "拒单 PAID→REJECTED（回补库存/券并模拟退款）")
    public Result<OrderDetailView> reject(@PathVariable Long id, @Valid @RequestBody OrderRejectRequest request) {
        return Result.success(merchantOrderService.reject(roleGuard.requireMerchantId(), id, request.getReason()));
    }

    @PostMapping("/{id}/prepare")
    @Operation(summary = "开始制作 ACCEPTED→MAKING")
    public Result<OrderDetailView> prepare(@PathVariable Long id) {
        return Result.success(merchantOrderService.prepare(roleGuard.requireMerchantId(), id));
    }

    @PostMapping("/{id}/deliver")
    @Operation(summary = "开始配送 MAKING→DELIVERING")
    public Result<OrderDetailView> deliver(@PathVariable Long id) {
        return Result.success(merchantOrderService.deliver(roleGuard.requireMerchantId(), id));
    }

    @PostMapping("/{id}/complete")
    @Operation(summary = "完成订单 DELIVERING→COMPLETED")
    public Result<OrderDetailView> complete(@PathVariable Long id) {
        return Result.success(merchantOrderService.complete(roleGuard.requireMerchantId(), id));
    }
}
