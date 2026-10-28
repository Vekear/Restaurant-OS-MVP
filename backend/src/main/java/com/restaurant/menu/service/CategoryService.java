package com.restaurant.menu.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.restaurant.common.api.ErrorCode;
import com.restaurant.common.exception.BizException;
import com.restaurant.menu.Category;
import com.restaurant.menu.dto.CategoryCreateRequest;
import com.restaurant.menu.dto.CategoryUpdateRequest;
import com.restaurant.menu.mapper.CategoryMapper;
import com.restaurant.menu.mapper.DishMapper;
import com.restaurant.menu.vo.CategoryView;
import com.restaurant.menu.vo.DeletedView;
import com.restaurant.security.RoleGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 商家端分类管理（{@code docs/02-api.md §16.1–§16.4}）。
 *
 * <p>分类增删改在事务提交后失效公开菜单缓存（分类列表 + 该商家菜品分页 + 菜品详情），
 * 回滚时不失效；单实例 MVP 按 merchantId 精准失效。
 */
@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryMapper categoryMapper;

    private final DishMapper dishMapper;

    private final RoleGuard roleGuard;

    private final MenuCacheEvictor menuCacheEvictor;

    /** 商家端分类列表（含 DISABLED，dishCount 统计未删除菜品）。 */
    public List<CategoryView> listForMerchant() {
        Long merchantId = roleGuard.requireMerchantId();
        List<Category> categories = categoryMapper.selectList(Wrappers.<Category>lambdaQuery()
                .eq(Category::getMerchantId, merchantId)
                .orderByAsc(Category::getSort)
                .orderByAsc(Category::getId));
        Map<Long, Long> counts = categoryMapper.countDishesByCategory(merchantId).stream()
                .collect(Collectors.toMap(CategoryMapper.CategoryDishCount::getCategoryId,
                        CategoryMapper.CategoryDishCount::getDishCount));
        return categories.stream().map(category -> toView(category, counts.getOrDefault(category.getId(), 0L)))
                .toList();
    }

    /** 创建分类。 */
    @Transactional
    public CategoryView create(CategoryCreateRequest request) {
        Long merchantId = roleGuard.requireMerchantId();
        String name = request.getName().trim();
        ensureNameUnique(merchantId, name, null);
        Category category = new Category();
        category.setMerchantId(merchantId);
        category.setName(name);
        category.setSort(request.getSort() == null ? 0 : request.getSort());
        category.setStatus("ENABLED");
        category.setDeleted(0);
        categoryMapper.insert(category);
        menuCacheEvictor.evictCategoryAfterCommit(merchantId);
        return toView(category, 0L);
    }

    /** 更新分类（名称/排序/状态）。 */
    @Transactional
    public CategoryView update(Long id, CategoryUpdateRequest request) {
        Long merchantId = roleGuard.requireMerchantId();
        Category category = requireOwnedCategory(id, merchantId);
        if (request.getName() != null && !request.getName().isBlank()) {
            String name = request.getName().trim();
            ensureNameUnique(merchantId, name, id);
            category.setName(name);
        }
        if (request.getSort() != null) {
            category.setSort(request.getSort());
        }
        if (request.getStatus() != null) {
            category.setStatus(request.getStatus());
        }
        if (categoryMapper.updateById(category) == 0) {
            throw new BizException(ErrorCode.STATE_CONFLICT, "分类已被修改，请重试");
        }
        menuCacheEvictor.evictCategoryAfterCommit(merchantId);
        return toView(category, countDishes(category.getId()));
    }

    /** 删除分类：存在未删除菜品时拒绝（40915）。 */
    @Transactional
    public DeletedView delete(Long id) {
        Long merchantId = roleGuard.requireMerchantId();
        requireOwnedCategory(id, merchantId);
        if (countDishes(id) > 0) {
            throw new BizException(ErrorCode.CATEGORY_NOT_EMPTY);
        }
        categoryMapper.deleteById(id);
        menuCacheEvictor.evictCategoryAfterCommit(merchantId);
        return DeletedView.yes();
    }

    private Category requireOwnedCategory(Long id, Long merchantId) {
        Category category = categoryMapper.selectOne(Wrappers.<Category>lambdaQuery()
                .eq(Category::getId, id)
                .eq(Category::getMerchantId, merchantId));
        if (category == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "分类不存在");
        }
        return category;
    }

    private void ensureNameUnique(Long merchantId, String name, Long excludeId) {
        Long count = categoryMapper.selectCount(Wrappers.<Category>lambdaQuery()
                .eq(Category::getMerchantId, merchantId)
                .eq(Category::getName, name)
                .ne(excludeId != null, Category::getId, excludeId));
        if (count != null && count > 0) {
            throw new BizException(ErrorCode.UNIQUE_CONFLICT, "分类名称已存在");
        }
    }

    private long countDishes(Long categoryId) {
        Long count = dishMapper.selectCount(Wrappers.<com.restaurant.menu.Dish>lambdaQuery()
                .eq(com.restaurant.menu.Dish::getCategoryId, categoryId));
        return count == null ? 0L : count;
    }

    private CategoryView toView(Category category, long dishCount) {
        return new CategoryView(category.getId(), category.getMerchantId(), category.getName(),
                category.getSort(), category.getStatus(), dishCount, category.getCreatedAt());
    }
}
