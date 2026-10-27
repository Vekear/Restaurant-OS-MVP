package com.restaurant.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/** 外卖下单请求（POST /api/v1/orders）。 */
@Data
public class OrderCreateRequest {

    @NotNull(message = "商家 ID 不能为空")
    private Long merchantId;

    @NotNull(message = "收货地址不能为空")
    private Long addressId;

    @NotEmpty(message = "订单至少包含一个菜品")
    @Size(max = 50, message = "单次最多 50 个菜品")
    private List<@Valid OrderItemRequest> items;

    private Long userCouponId;

    @Size(max = 100, message = "备注最多 100 字")
    private String remark;

    /** 前端期望实付金额（可选）；与服务端计算不一致时返回 40005。 */
    private BigDecimal expectedPayAmount;
}
