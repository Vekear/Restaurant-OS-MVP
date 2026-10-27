package com.restaurant.review.controller;

import com.restaurant.common.api.Result;
import com.restaurant.common.config.OpenApiConfig;
import com.restaurant.common.constant.ApiConstants;
import com.restaurant.review.ReviewService;
import com.restaurant.review.dto.ReviewCreateRequest;
import com.restaurant.review.vo.ReviewView;
import com.restaurant.security.RoleGuard;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 订单评价接口（映射在 /api/v1/orders/{orderId}/review 下，实现位于 review 域）。 */
@RestController
@RequestMapping(ApiConstants.API_V1 + "/orders/{orderId}/review")
@RequiredArgsConstructor
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH_SCHEME)
@Tag(name = "评价")
public class ReviewController {

    private final ReviewService reviewService;

    private final RoleGuard roleGuard;

    @PostMapping
    @Operation(summary = "发表评价（已完成订单，一单一评）")
    public Result<ReviewView> create(@PathVariable Long orderId, @Valid @RequestBody ReviewCreateRequest request) {
        return Result.success(reviewService.create(roleGuard.currentUserId(), orderId, request));
    }

    @GetMapping
    @Operation(summary = "查看订单评价")
    public Result<ReviewView> get(@PathVariable Long orderId) {
        return Result.success(reviewService.get(roleGuard.currentUserId(), orderId));
    }
}
