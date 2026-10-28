package com.restaurant.cart.controller;

import com.restaurant.cart.dto.AddCartItemRequest;
import com.restaurant.cart.dto.UpdateCartQuantityRequest;
import com.restaurant.cart.service.CartService;
import com.restaurant.cart.vo.CartView;
import com.restaurant.common.api.Result;
import com.restaurant.common.constant.ApiConstants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 购物车（docs/02-api.md §7）。 */
@RestController
@RequestMapping(ApiConstants.API_V1 + "/cart")
@RequiredArgsConstructor
@Tag(name = "购物车-顾客", description = "购物车查看、加购合并、改数量、删除与清空")
@SecurityRequirement(name = "bearerAuth")
public class CartController {

    private final CartService cartService;

    @GetMapping
    @Operation(summary = "购物车", description = "失效行以 available=false 返回，不计入 totalAmount")
    public Result<CartView> get() {
        return Result.success(cartService.get());
    }

    @PostMapping("/items")
    @Operation(summary = "加入购物车（同菜品合并数量）")
    public Result<CartView> add(@Valid @RequestBody AddCartItemRequest request) {
        return Result.success(cartService.add(request));
    }

    @PatchMapping("/items/{dishId}")
    @Operation(summary = "修改数量")
    public Result<CartView> updateQuantity(@PathVariable Long dishId,
                                           @Valid @RequestBody UpdateCartQuantityRequest request) {
        return Result.success(cartService.updateQuantity(dishId, request));
    }

    @DeleteMapping("/items/{dishId}")
    @Operation(summary = "删除某行（幂等）")
    public Result<CartView> deleteItem(@PathVariable Long dishId) {
        return Result.success(cartService.deleteItem(dishId));
    }

    @DeleteMapping
    @Operation(summary = "清空购物车")
    public Result<CartView> clear() {
        return Result.success(cartService.clear());
    }
}
