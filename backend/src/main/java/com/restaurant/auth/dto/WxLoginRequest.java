package com.restaurant.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 小程序微信登录请求：前端 {@code wx.login} 得到的临时 code（{@code docs/02-api.md §4.3}）。 */
@Data
public class WxLoginRequest {

    @NotBlank(message = "code 不能为空")
    @Size(min = 1, max = 128, message = "code 长度需为 1-128 位")
    private String code;
}
