package com.restaurant.order.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/** 用户取消订单请求。 */
@Data
public class OrderCancelRequest {

    @Size(max = 100, message = "取消原因最多 100 字")
    private String reason;
}
