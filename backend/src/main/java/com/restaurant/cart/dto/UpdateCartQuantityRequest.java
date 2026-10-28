package com.restaurant.cart.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 修改购物车数量请求（docs/02-api.md §7.3）。 */
@Data
public class UpdateCartQuantityRequest {

    @NotNull(message = "数量不能为空")
    @Min(value = 1, message = "数量需在 1-99 之间")
    @Max(value = 99, message = "数量需在 1-99 之间")
    private Integer quantity;
}
