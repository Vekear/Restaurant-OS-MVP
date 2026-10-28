package com.restaurant.merchant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/** 营业状态切换请求。 */
@Data
public class BusinessStatusRequest {

    @NotBlank(message = "营业状态不能为空")
    @Pattern(regexp = "OPEN|CLOSED", message = "营业状态只能为 OPEN 或 CLOSED")
    private String businessStatus;
}
