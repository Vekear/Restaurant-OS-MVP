package com.restaurant.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 用户启用/禁用请求（{@code docs/02-api.md §20.2}）。 */
@Data
public class AdminUserStatusRequest {

    @NotBlank(message = "状态不能为空")
    @Pattern(regexp = "ENABLED|DISABLED", message = "状态只能为 ENABLED 或 DISABLED")
    private String status;

    @Size(max = 200, message = "原因不能超过 200 字")
    private String reason;
}
