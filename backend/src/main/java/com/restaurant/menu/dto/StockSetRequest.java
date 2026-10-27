package com.restaurant.menu.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 设置库存请求（{@code docs/02-api.md §16.11}，乐观锁）。 */
@Data
public class StockSetRequest {

    @NotNull(message = "库存不能为空")
    @Min(value = 0, message = "库存不能小于 0")
    @Max(value = 99999, message = "库存不能大于 99999")
    private Integer stock;
}
