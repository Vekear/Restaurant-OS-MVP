package com.restaurant.table.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 手输桌号开桌请求（{@code docs/02-api.md §10.2}）。 */
@Data
public class DineInBindRequest {

    @NotBlank(message = "桌号不能为空")
    @Size(min = 1, max = 20, message = "桌号长度需为 1-20")
    private String tableNo;

    /** 可选二维码签名（扫码带入时校验签名）。 */
    @Size(min = 16, max = 256, message = "二维码签名非法")
    private String s;

    /** 签名时间戳（epoch 秒，可选）。 */
    private Long t;
}
