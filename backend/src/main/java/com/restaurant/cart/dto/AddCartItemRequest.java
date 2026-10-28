package com.restaurant.cart.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 加入购物车请求（docs/02-api.md §7.2）。 */
@Data
public class AddCartItemRequest {

    @NotNull(message = "菜品 ID 不能为空")
    private Long dishId;

    /** 数量，缺省 1。 */
    @Min(value = 1, message = "数量需在 1-99 之间")
    @Max(value = 99, message = "数量需在 1-99 之间")
    private Integer quantity = 1;
}
