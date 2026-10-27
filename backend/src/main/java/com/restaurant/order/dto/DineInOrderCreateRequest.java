package com.restaurant.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/** 堂食下单请求（{@code docs/02-api.md §11.1}）：桌台与商家来自 {@code X-Table-Session} 会话。 */
@Data
public class DineInOrderCreateRequest {

    @NotEmpty(message = "订单至少包含一个菜品")
    @Size(max = 50, message = "单次最多 50 个菜品")
    private List<@Valid OrderItemRequest> items;

    private Long userCouponId;

    @Size(max = 100, message = "备注不能超过 100 字")
    private String remark;

    private BigDecimal expectedPayAmount;
}
