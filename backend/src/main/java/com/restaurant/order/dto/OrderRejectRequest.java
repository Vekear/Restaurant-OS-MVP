package com.restaurant.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 商家拒单请求。 */
@Data
public class OrderRejectRequest {

    @NotBlank(message = "拒单原因不能为空")
    @Size(min = 2, max = 100, message = "拒单原因长度需在 2-100 字")
    private String reason;
}
