package com.restaurant.table.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 扫码开桌请求（{@code docs/02-api.md §10.1}），t 为二维码签名时间戳（秒，可选）。 */
@Data
public class DineInScanRequest {

    @NotBlank(message = "桌台参数不能为空")
    @Pattern(regexp = "\\d{1,20}", message = "桌台参数非法")
    private String p;

    @NotBlank(message = "二维码签名不能为空")
    @Size(min = 16, max = 256, message = "二维码签名非法")
    private String s;

    /** 签名时间戳（epoch 秒），用于时间窗校验。 */
    private Long t;
}
