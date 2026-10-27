package com.restaurant.menu.controller;

import com.restaurant.common.api.Result;
import com.restaurant.common.constant.ApiConstants;
import com.restaurant.menu.dto.CategoryCreateRequest;
import com.restaurant.menu.dto.CategoryUpdateRequest;
import com.restaurant.menu.service.CategoryService;
import com.restaurant.menu.vo.CategoryView;
import com.restaurant.menu.vo.DeletedView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 商家端分类管理（{@code docs/02-api.md §16.1–§16.4}）。
 */
@RestController
@RequestMapping(ApiConstants.API_V1 + "/merchant/categories")
@RequiredArgsConstructor
@Tag(name = "商家-分类管理")
@SecurityRequirement(name = "bearerAuth")
public class MerchantCategoryController {

    private final CategoryService categoryService;

    @GetMapping
    @Operation(summary = "分类列表（含停用）")
    public Result<List<CategoryView>> list() {
        return Result.success(categoryService.listForMerchant());
    }

    @PostMapping
    @Operation(summary = "创建分类")
    public Result<CategoryView> create(@Valid @RequestBody CategoryCreateRequest request) {
        return Result.success(categoryService.create(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新分类")
    public Result<CategoryView> update(@PathVariable Long id,
                                       @Valid @RequestBody CategoryUpdateRequest request) {
        return Result.success(categoryService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除分类")
    public Result<DeletedView> delete(@PathVariable Long id) {
        return Result.success(categoryService.delete(id));
    }
}
