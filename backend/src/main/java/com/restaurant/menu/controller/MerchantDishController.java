package com.restaurant.menu.controller;

import com.restaurant.common.api.PageResult;
import com.restaurant.common.api.Result;
import com.restaurant.common.constant.ApiConstants;
import com.restaurant.menu.dto.DishCreateRequest;
import com.restaurant.menu.dto.DishStatusRequest;
import com.restaurant.menu.dto.DishUpdateRequest;
import com.restaurant.menu.dto.StockAdjustRequest;
import com.restaurant.menu.dto.StockSetRequest;
import com.restaurant.menu.service.DishService;
import com.restaurant.menu.vo.DeletedView;
import com.restaurant.menu.vo.DishDetailView;
import com.restaurant.menu.vo.DishSummaryView;
import com.restaurant.menu.vo.StockView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 商家端菜品与库存（{@code docs/02-api.md §16.5–§16.12}）。
 */
@RestController
@RequestMapping(ApiConstants.API_V1 + "/merchant/dishes")
@RequiredArgsConstructor
@Validated
@Tag(name = "商家-菜品管理")
@SecurityRequirement(name = "bearerAuth")
public class MerchantDishController {

    private final DishService dishService;

    @GetMapping
    @Operation(summary = "菜品分页（含下架）")
    public Result<PageResult<DishSummaryView>> list(
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) @Size(max = 30, message = "keyword 不能超过 30") String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") @Min(1) @Max(10000) long page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) long size) {
        return Result.success(dishService.listForMerchant(categoryId, keyword, status, page, size));
    }

    @GetMapping("/{id}")
    @Operation(summary = "菜品详情")
    public Result<DishDetailView> detail(@PathVariable Long id) {
        return Result.success(dishService.detail(id));
    }

    @PostMapping
    @Operation(summary = "创建菜品")
    public Result<DishDetailView> create(@Valid @RequestBody DishCreateRequest request) {
        return Result.success(dishService.create(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新菜品")
    public Result<DishDetailView> update(@PathVariable Long id,
                                         @Valid @RequestBody DishUpdateRequest request) {
        return Result.success(dishService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除菜品（逻辑删除）")
    public Result<DeletedView> delete(@PathVariable Long id) {
        return Result.success(dishService.delete(id));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "上下架")
    public Result<DishDetailView> changeStatus(@PathVariable Long id,
                                               @Valid @RequestBody DishStatusRequest request) {
        return Result.success(dishService.changeStatus(id, request));
    }

    @PutMapping("/{id}/stock")
    @Operation(summary = "设置库存（乐观锁）")
    public Result<StockView> setStock(@PathVariable Long id,
                                      @Valid @RequestBody StockSetRequest request) {
        return Result.success(dishService.setStock(id, request));
    }

    @PostMapping("/{id}/stock/adjust")
    @Operation(summary = "增量调整库存（原子 UPDATE）")
    public Result<StockView> adjustStock(@PathVariable Long id,
                                         @Valid @RequestBody StockAdjustRequest request) {
        return Result.success(dishService.adjustStock(id, request));
    }
}
