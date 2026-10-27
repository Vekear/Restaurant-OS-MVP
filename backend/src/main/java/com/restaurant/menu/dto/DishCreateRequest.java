package com.restaurant.menu.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/** 商家创建菜品请求（{@code docs/02-api.md §16.7}）。 */
@Data
public class DishCreateRequest {

    @NotNull(message = "分类不能为空")
    private Long categoryId;

    @NotBlank(message = "菜品名称不能为空")
    @Size(min = 2, max = 50, message = "菜品名称长度需为 2-50")
    private String name;

    @NotNull(message = "价格不能为空")
    @DecimalMin(value = "0.01", message = "价格不能低于 0.01")
    @DecimalMax(value = "9999.99", message = "价格不能高于 9999.99")
    @Digits(integer = 4, fraction = 2, message = "价格最多两位小数")
    private BigDecimal price;

    @DecimalMin(value = "0.01", message = "原价不能低于 0.01")
    @DecimalMax(value = "9999.99", message = "原价不能高于 9999.99")
    @Digits(integer = 4, fraction = 2, message = "原价最多两位小数")
    private BigDecimal originalPrice;

    @Size(max = 500, message = "图片 URL 不能超过 500")
    @Pattern(regexp = "^(/files/.*|https://.*)?$", message = "图片 URL 必须为 /files/** 或 https:// 开头")
    private String imageUrl;

    @Size(max = 500, message = "描述不能超过 500")
    private String description;

    @NotNull(message = "库存不能为空")
    @Min(value = 0, message = "库存不能小于 0")
    @Max(value = 99999, message = "库存不能大于 99999")
    private Integer stock;

    @Min(value = 0, message = "sort 不能小于 0")
    @Max(value = 999, message = "sort 不能大于 999")
    private Integer sort;

    @Pattern(regexp = "ON_SALE|OFF_SHELF", message = "status 只能为 ON_SALE/OFF_SHELF")
    private String status;
}
