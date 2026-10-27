package com.restaurant.auth.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 修改个人资料请求（{@code docs/02-api.md §4.6}）。
 *
 * <p>字段为 null 表示不修改；phone 传空字符串表示清空；avatarUrl 只允许本地 /files/** 或 https:// 地址。
 */
@Data
public class UpdateProfileRequest {

    @Size(min = 1, max = 20, message = "昵称长度需为 1-20 位")
    private String nickname;

    @Pattern(regexp = "^$|^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    @Size(max = 500, message = "头像地址不能超过 500 字符")
    @Pattern(regexp = "^(/files/.*|https://.*)$", message = "头像地址必须以 /files/ 或 https:// 开头")
    private String avatarUrl;
}
