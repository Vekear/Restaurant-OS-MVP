package com.restaurant.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 账号密码登录请求（{@code docs/02-api.md §4.1}）。
 *
 * <p>用户名 4–20 位字母/数字/下划线；密码 6–32 位，服务端不做 trim。
 */
@Data
public class LoginRequest {

    @NotBlank(message = "用户名不能为空")
    @Pattern(regexp = "^[A-Za-z0-9_]{4,20}$", message = "用户名需为 4-20 位字母、数字或下划线")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 32, message = "密码长度需为 6-32 位")
    private String password;
}
