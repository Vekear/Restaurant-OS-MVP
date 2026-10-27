package com.restaurant.payment.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 创建支付单请求。 */
@Data
public class PaymentCreateRequest {

    @NotNull(message = "订单 ID 不能为空")
    private Long orderId;

    private String orderType;
}
