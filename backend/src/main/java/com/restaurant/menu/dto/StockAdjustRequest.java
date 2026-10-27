package com.restaurant.menu.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 增量调整库存请求（{@code docs/02-api.md §16.12}，原子 UPDATE）。 */
@Data
public class StockAdjustRequest {

    @NotNull(message = "delta 不能为空")
    @Min(value = -99999, message = "delta 不能小于 -99999")
    @Max(value = 99999, message = "delta 不能大于 99999")
    private Integer delta;

    @Size(max = 100, message = "reason 不能超过 100")
    private String reason;
}
