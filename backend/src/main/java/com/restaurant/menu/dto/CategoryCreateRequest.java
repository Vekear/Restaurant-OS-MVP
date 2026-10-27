package com.restaurant.menu.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 商家创建分类请求（{@code docs/02-api.md §16.2}）。 */
@Data
public class CategoryCreateRequest {

    @NotBlank(message = "分类名称不能为空")
    @Size(min = 1, max = 20, message = "分类名称长度需为 1-20")
    private String name;

    @Min(value = 0, message = "sort 不能小于 0")
    @Max(value = 999, message = "sort 不能大于 999")
    private Integer sort;
}
