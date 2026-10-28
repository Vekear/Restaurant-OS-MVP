package com.restaurant.merchant.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/** 商家资料更新请求（{@code docs/02-api.md §15.2}）。 */
@Data
public class MerchantProfileUpdateRequest {

    @NotBlank(message = "店铺名称不能为空")
    @Size(min = 2, max = 50, message = "店铺名称长度需在 2-50 字")
    private String name;

    @Size(max = 500, message = "店铺简介不能超过 500 字")
    private String description;

    @NotBlank(message = "联系电话不能为空")
    @Size(min = 7, max = 20, message = "联系电话长度需在 7-20 位")
    @Pattern(regexp = "^[0-9-]+$", message = "联系电话只能包含数字和 -")
    private String phone;

    @NotBlank(message = "店铺地址不能为空")
    @Size(min = 5, max = 120, message = "店铺地址长度需在 5-120 字")
    private String address;

    @Size(max = 500, message = "logoUrl 不能超过 500 字符")
    @Pattern(regexp = "^(|/files/.*|https://.*)$", message = "logoUrl 必须以 /files/ 或 https:// 开头")
    private String logoUrl;

    @NotNull(message = "配送费不能为空")
    @DecimalMin(value = "0.00", message = "配送费不能小于 0")
    @DecimalMax(value = "100.00", message = "配送费不能超过 100")
    @Digits(integer = 3, fraction = 2, message = "配送费最多两位小数")
    private BigDecimal deliveryFee;

    @NotNull(message = "起送价不能为空")
    @DecimalMin(value = "0.00", message = "起送价不能小于 0")
    @DecimalMax(value = "1000.00", message = "起送价不能超过 1000")
    @Digits(integer = 4, fraction = 2, message = "起送价最多两位小数")
    private BigDecimal minOrderAmount;
}
