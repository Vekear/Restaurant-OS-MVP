package com.restaurant.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 更新收货地址请求（docs/02-api.md §5.4，全量字段）。 */
@Data
public class AddressUpdateRequest {

    @NotBlank(message = "收货人不能为空")
    @Size(min = 2, max = 20, message = "收货人长度需在 2-20 字")
    private String consignee;

    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    @NotBlank(message = "省份不能为空")
    @Size(max = 32, message = "省份不能超过 32 字")
    private String province;

    @NotBlank(message = "城市不能为空")
    @Size(max = 32, message = "城市不能超过 32 字")
    private String city;

    @NotBlank(message = "区县不能为空")
    @Size(max = 32, message = "区县不能超过 32 字")
    private String district;

    @NotBlank(message = "详细地址不能为空")
    @Size(min = 5, max = 120, message = "详细地址长度需在 5-120 字")
    private String detail;

    /** 是否设为默认地址；缺省时保留原值。 */
    private Boolean isDefault;
}
