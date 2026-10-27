package com.restaurant.table.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 更新桌台请求（{@code docs/02-api.md §17.4}）。 */
@Data
public class TableUpdateRequest {

    @Size(min = 1, max = 20, message = "桌号长度需为 1-20")
    private String tableNo;

    @Size(max = 20, message = "桌台名称长度不能超过 20")
    private String name;

    @Min(value = 1, message = "容纳人数需为 1-20")
    @Max(value = 20, message = "容纳人数需为 1-20")
    private Integer capacity;

    @Size(max = 20, message = "区域长度不能超过 20")
    private String zone;
}
