package com.restaurant.order.controller;

import com.restaurant.common.api.PageResult;
import com.restaurant.common.api.Result;
import com.restaurant.common.config.OpenApiConfig;
import com.restaurant.common.constant.ApiConstants;
import com.restaurant.common.idempotency.IdempotencyService;
import com.restaurant.common.idempotency.IdempotentResult;
import com.restaurant.order.DineInOrderService;
import com.restaurant.order.OrderService;
import com.restaurant.order.dto.DineInOrderCreateRequest;
import com.restaurant.order.dto.OrderCancelRequest;
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

/** 顾客堂食订单（{@code docs/02-api.md §11}）：下单需 Idempotency-Key + X-Table-Session。 */
@Validated
@RestController
@RequestMapping(ApiConstants.API_V1 + "/dine-in/orders")
@RequiredArgsConstructor
@Tag(name = "订单-堂食")
public class DineInOrderController {

    private final DineInOrderService dineInOrderService;

    private final OrderService orderService;

    private final IdempotencyService idempotencyService;

    private final RoleGuard roleGuard;

    @PostMapping
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH_SCHEME)
    @Operation(summary = "堂食下单（需 Idempotency-Key 与 X-Table-Session）")
    public ResponseEntity<?> create(
            @RequestHeader(value = ApiConstants.HEADER_IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @RequestHeader(value = ApiConstants.HEADER_TABLE_SESSION, required = false) String sessionToken,
            @Valid @RequestBody DineInOrderCreateRequest request) {
        Long userId = roleGuard.currentUserId();
        IdempotentResult<OrderDetailView> result = idempotencyService.execute(idempotencyKey,
                "POST /api/v1/dine-in/orders", userId, request,
                () -> orderService.createDineIn(userId, sessionToken, request));
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
    @Operation(summary = "我的堂食订单列表")
    public Result<PageResult<OrderView>> list(
            @RequestParam(required = false, defaultValue = "ALL") String statusGroup,
            @RequestParam(required = false) String status,
            @RequestParam(required = false, defaultValue = "1") @Min(1) @Max(10000) int page,
            @RequestParam(required = false, defaultValue = "10") @Min(1) @Max(50) int size) {
        return Result.success(dineInOrderService.list(roleGuard.currentUserId(), statusGroup, status, page, size));
    }

    @GetMapping("/{id}")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH_SCHEME)
    @Operation(summary = "堂食订单详情")
    public Result<OrderDetailView> detail(@PathVariable Long id) {
        return Result.success(dineInOrderService.detail(roleGuard.currentUserId(), id));
    }

    @PostMapping("/{id}/cancel")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH_SCHEME)
    @Operation(summary = "取消堂食订单并释放桌台")
    public Result<OrderDetailView> cancel(@PathVariable Long id,
                                          @Valid @RequestBody(required = false) OrderCancelRequest request) {
        String reason = request == null ? null : request.getReason();
        return Result.success(dineInOrderService.cancel(roleGuard.currentUserId(), id, reason));
    }
}
