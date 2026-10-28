package com.restaurant.coupon.controller;

import com.restaurant.common.api.PageResult;
import com.restaurant.common.api.Result;
import com.restaurant.common.constant.ApiConstants;
import com.restaurant.coupon.dto.CouponQuoteRequest;
import com.restaurant.coupon.service.CustomerCouponService;
import com.restaurant.coupon.vo.CouponView;
import com.restaurant.coupon.vo.QuoteView;
import com.restaurant.coupon.vo.UserCouponView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

/** 顾客优惠券（docs/02-api.md §8）。 */
@RestController
@RequestMapping(ApiConstants.API_V1 + "/coupons")
@RequiredArgsConstructor
@Validated
@Tag(name = "优惠券-顾客", description = "可领取券列表、领取、我的券与金额试算")
@SecurityRequirement(name = "bearerAuth")
public class CouponController {

    private final CustomerCouponService customerCouponService;

    @GetMapping
    @Operation(summary = "可领取优惠券分页", description = "amount 传入时计算 usable")
    public Result<PageResult<CouponView>> list(
            @RequestParam(required = false)
            @DecimalMin(value = "0.00", message = "amount 不能为负数")
            @DecimalMax(value = "99999.99", message = "amount 超出范围") BigDecimal amount,
            @RequestParam(defaultValue = "1") @Min(1) @Max(10000) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int size) {
        return Result.success(customerCouponService.list(amount, page, size));
    }

    @PostMapping("/{couponId}/claim")
    @Operation(summary = "领取优惠券（原子扣减，重复领取 40912）")
    public Result<UserCouponView> claim(@PathVariable Long couponId) {
        return Result.success(customerCouponService.claim(couponId));
    }

    @GetMapping("/mine")
    @Operation(summary = "我的优惠券", description = "status 缺省 UNUSED；amount 传入时计算 usable/reason")
    public Result<PageResult<UserCouponView>> mine(
            @RequestParam(required = false) String status,
            @RequestParam(required = false)
            @DecimalMin(value = "0.00", message = "amount 不能为负数")
            @DecimalMax(value = "99999.99", message = "amount 超出范围") BigDecimal amount,
            @RequestParam(defaultValue = "1") @Min(1) @Max(10000) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int size) {
        return Result.success(customerCouponService.mine(status, amount, page, size));
    }

    @PostMapping("/quote")
    @Operation(summary = "金额试算（服务端计算优惠额与应付额）")
    public Result<QuoteView> quote(@Valid @RequestBody CouponQuoteRequest request) {
        return Result.success(customerCouponService.quote(request));
    }
}
