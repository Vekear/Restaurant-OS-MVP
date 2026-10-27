package com.restaurant.order.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 下单明细项。 */
@Data
public class OrderItemRequest {

    @NotNull(message = "菜品 ID 不能为空")
    private Long dishId;

    @NotNull(message = "数量不能为空")
    @Min(value = 1, message = "数量至少为 1")
    @Max(value = 99, message = "数量最多为 99")
    private Integer quantity;
}
