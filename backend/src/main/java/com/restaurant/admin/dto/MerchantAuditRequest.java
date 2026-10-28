package com.restaurant.admin.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 商家审核/启用禁用请求（{@code docs/02-api.md §20.5}）。 */
@Data
public class MerchantAuditRequest {

    @Pattern(regexp = "APPROVED|REJECTED", message = "审核状态只能为 APPROVED 或 REJECTED")
    private String auditStatus;

    @Pattern(regexp = "ENABLED|DISABLED", message = "状态只能为 ENABLED 或 DISABLED")
    private String status;

    @Size(max = 200, message = "原因不能超过 200 字")
    private String reason;
}
