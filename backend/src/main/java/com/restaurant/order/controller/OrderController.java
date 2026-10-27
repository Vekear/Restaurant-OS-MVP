package com.restaurant.order.controller;

import com.restaurant.common.api.PageResult;
import com.restaurant.common.api.Result;
import com.restaurant.common.constant.ApiConstants;
import com.restaurant.common.idempotency.IdempotencyService;
import com.restaurant.common.idempotency.IdempotentResult;
import com.restaurant.common.config.OpenApiConfig;
import com.restaurant.order.OrderQueryService;
import com.restaurant.order.OrderService;
import com.restaurant.order.dto.OrderCancelRequest;
import com.restaurant.order.dto.OrderCreateRequest;
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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 顾客订单接口（下单幂等、列表、详情、取消、确认收货）。 */
@Validated
@RestController
@RequestMapping(ApiConstants.API_V1 + "/orders")
@RequiredArgsConstructor
@Tag(name = "订单-顾客")
public class OrderController {

    private final OrderService orderService;

    private final OrderQueryService orderQueryService;

    private final IdempotencyService idempotencyService;

    private final RoleGuard roleGuard;

    @PostMapping
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH_SCHEME)
    @Operation(summary = "创建外卖订单（需 Idempotency-Key）")
    public ResponseEntity<?> create(
            @RequestHeader(value = ApiConstants.HEADER_IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @Valid @RequestBody OrderCreateRequest request) {
        Long userId = roleGuard.currentUserId();
        IdempotentResult<OrderDetailView> result = idempotencyService.execute(idempotencyKey,
                "POST /api/v1/orders", userId, request, () -> orderService.create(userId, request));
        if (result.replayed()) {
            return ResponseEntity.ok()
                    .header(ApiConstants.HEADER_IDEMPOTENCY_REPLAYED, "true")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(result.replayBody());
        }
        return ResponseEntity.ok(Result.success(result.data()));
    }

    @GetMapping
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH_SCHEME)
    @Operation(summary = "我的订单列表")
    public Result<PageResult<OrderView>> list(
            @RequestParam(required = false, defaultValue = "ALL") String statusGroup,
            @RequestParam(required = false) String status,
            @RequestParam(required = false, defaultValue = "1") @Min(1) @Max(10000) int page,
            @RequestParam(required = false, defaultValue = "10") @Min(1) @Max(50) int size) {
        return Result.success(orderQueryService.list(roleGuard.currentUserId(), statusGroup, status, page, size));
    }

    @GetMapping("/{id}")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH_SCHEME)
    @Operation(summary = "订单详情")
    public Result<OrderDetailView> detail(@PathVariable Long id) {
        return Result.success(orderQueryService.detail(roleGuard.currentUserId(), id));
    }

    @PostMapping("/{id}/cancel")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH_SCHEME)
    @Operation(summary = "取消订单（待支付/待接单）")
    public Result<OrderDetailView> cancel(@PathVariable Long id,
                                          @Valid @RequestBody(required = false) OrderCancelRequest request) {
        String reason = request == null ? null : request.getReason();
        return Result.success(orderService.cancel(roleGuard.currentUserId(), id, reason));
    }

    @PostMapping("/{id}/confirm-receipt")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH_SCHEME)
    @Operation(summary = "确认收货")
    public Result<OrderDetailView> confirmReceipt(@PathVariable Long id) {
        return Result.success(orderService.confirmReceipt(roleGuard.currentUserId(), id));
    }
}
