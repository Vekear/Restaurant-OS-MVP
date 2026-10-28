package com.restaurant.admin.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 优惠券创建请求（{@code docs/02-api.md §20.10}）。 */
@Data
public class CouponCreateRequest {

    @NotBlank(message = "优惠券名称不能为空")
    @Size(min = 2, max = 50, message = "优惠券名称长度需在 2-50 字")
    private String name;

    @NotNull(message = "使用门槛不能为空")
    @DecimalMin(value = "0.01", message = "使用门槛不能小于 0.01")
    @DecimalMax(value = "99999.99", message = "使用门槛不能超过 99999.99")
    @Digits(integer = 5, fraction = 2, message = "使用门槛最多两位小数")
    private BigDecimal thresholdAmount;

    @NotNull(message = "优惠金额不能为空")
    @DecimalMin(value = "0.01", message = "优惠金额不能小于 0.01")
    @DecimalMax(value = "99999.99", message = "优惠金额不能超过 99999.99")
    @Digits(integer = 5, fraction = 2, message = "优惠金额最多两位小数")
    private BigDecimal discountAmount;

    @NotNull(message = "发行量不能为空")
    @Min(value = 1, message = "发行量不能小于 1")
    @Max(value = 1_000_000, message = "发行量不能超过 1000000")
    private Integer total;

    @NotNull(message = "开始时间不能为空")
    private LocalDateTime startAt;

    @NotNull(message = "结束时间不能为空")
    private LocalDateTime endAt;
}
