package com.restaurant.admin.controller;

import com.restaurant.admin.service.AdminOrderService;
import com.restaurant.admin.vo.AdminOrderView;
import com.restaurant.common.constant.ApiConstants;
import com.restaurant.common.api.PageResult;
import com.restaurant.common.api.Result;
import com.restaurant.order.vo.OrderDetailView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 管理后台全量订单（{@code docs/02-api.md §20.6–20.7}）。 */
@Validated
@RestController
@RequestMapping(ApiConstants.API_V1 + "/admin/orders")
@RequiredArgsConstructor
@Tag(name = "管理-订单", description = "全量订单查询与详情")
@SecurityRequirement(name = "bearerAuth")
public class AdminOrderController {

    private final AdminOrderService adminOrderService;

    @Operation(summary = "全量订单列表")
    @GetMapping
    public Result<PageResult<AdminOrderView>> list(@RequestParam(required = false) Long merchantId,
                                                   @RequestParam(required = false) String orderType,
                                                   @RequestParam(required = false) String status,
                                                   @RequestParam(required = false) String startDate,
                                                   @RequestParam(required = false) String endDate,
                                                   @RequestParam(required = false) String keyword,
                                                   @RequestParam(defaultValue = "1") @Min(1) @Max(10000) int page,
                                                   @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size) {
        return Result.success(adminOrderService.list(merchantId, orderType, status, startDate, endDate,
                keyword, page, size));
    }

    @Operation(summary = "订单详情")
    @GetMapping("/{id}")
    public Result<OrderDetailView> detail(@PathVariable Long id) {
        return Result.success(adminOrderService.detail(id));
    }
}
