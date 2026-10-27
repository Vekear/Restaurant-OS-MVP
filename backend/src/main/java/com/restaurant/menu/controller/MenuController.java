package com.restaurant.menu.controller;

import com.restaurant.common.api.PageResult;
import com.restaurant.common.api.Result;
import com.restaurant.common.config.AppProperties;
import com.restaurant.common.constant.ApiConstants;
import com.restaurant.menu.dto.DishQuery;
import com.restaurant.menu.service.MenuQueryService;
import com.restaurant.menu.vo.CategoryView;
import com.restaurant.menu.vo.DishDetailView;
import com.restaurant.menu.vo.DishSummaryView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 公开菜单接口（{@code docs/02-api.md §6}），无需登录。
 */
@RestController
@RequestMapping(ApiConstants.API_V1 + "/menu")
@RequiredArgsConstructor
@Tag(name = "公开菜单")
public class MenuController {

    private final MenuQueryService menuQueryService;

    private final AppProperties appProperties;

    @GetMapping("/categories")
    @Operation(summary = "公开分类列表")
    public Result<List<CategoryView>> categories(@RequestParam(required = false) Long merchantId) {
        return Result.success(menuQueryService.listPublicCategories(resolveMerchantId(merchantId)));
    }

    @GetMapping("/dishes")
    @Operation(summary = "公开菜品分页")
    public Result<PageResult<DishSummaryView>> dishes(@Valid DishQuery query) {
        query.setMerchantId(resolveMerchantId(query.getMerchantId()));
        return Result.success(menuQueryService.listPublicDishes(query));
    }

    @GetMapping("/dishes/{id}")
    @Operation(summary = "公开菜品详情")
    public Result<DishDetailView> dishDetail(@PathVariable Long id) {
        return Result.success(menuQueryService.getPublicDishDetail(id));
    }

    private Long resolveMerchantId(Long merchantId) {
        return merchantId == null ? appProperties.getDefaultMerchantId() : merchantId;
    }
}
