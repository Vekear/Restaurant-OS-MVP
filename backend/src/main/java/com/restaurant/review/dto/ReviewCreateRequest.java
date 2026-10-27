package com.restaurant.review.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 发表评价请求。 */
@Data
public class ReviewCreateRequest {

    @NotNull(message = "评分不能为空")
    @Min(value = 1, message = "评分需在 1-5 之间")
    @Max(value = 5, message = "评分需在 1-5 之间")
    private Integer rating;

    @Size(max = 200, message = "评价内容最多 200 字")
    private String content;
}
