package com.restaurant.review.controller;

import com.restaurant.common.constant.ApiConstants;
import com.restaurant.common.api.PageResult;
import com.restaurant.common.api.Result;
import com.restaurant.review.MerchantReviewService;
import com.restaurant.review.vo.ReviewView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 商家端评价（{@code docs/02-api.md §19.1}）。 */
@Validated
@RestController
@RequestMapping(ApiConstants.API_V1 + "/merchant/reviews")
@RequiredArgsConstructor
@Tag(name = "商家-评价", description = "查看本店顾客评价")
@SecurityRequirement(name = "bearerAuth")
public class MerchantReviewController {

    private final MerchantReviewService merchantReviewService;

    @Operation(summary = "评价列表")
    @GetMapping
    public Result<PageResult<ReviewView>> list(
            @RequestParam(required = false) Integer rating,
            @RequestParam(defaultValue = "1") @Min(1) @Max(10000) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int size) {
        return Result.success(merchantReviewService.list(rating, page, size));
    }
}
