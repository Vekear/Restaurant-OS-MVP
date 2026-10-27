package com.restaurant.menu.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/** 上下架请求（{@code docs/02-api.md §16.10}）。 */
@Data
public class DishStatusRequest {

    @NotBlank(message = "status 不能为空")
    @Pattern(regexp = "ON_SALE|OFF_SHELF", message = "status 只能为 ON_SALE/OFF_SHELF")
    private String status;
}
