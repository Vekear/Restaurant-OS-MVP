package com.restaurant.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/** 优惠券状态切换（{@code docs/02-api.md §20.14}）。 */
@Data
public class CouponStatusRequest {

    @NotBlank(message = "状态不能为空")
    @Pattern(regexp = "ENABLED|DISABLED", message = "状态只能为 ENABLED 或 DISABLED")
    private String status;
}
