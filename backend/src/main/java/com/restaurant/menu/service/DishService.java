package com.restaurant.menu.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.restaurant.common.api.ErrorCode;
import com.restaurant.common.api.PageResult;
import com.restaurant.common.config.AppProperties;
import com.restaurant.common.exception.BizException;
import com.restaurant.common.util.CacheKeys;
import com.restaurant.menu.Category;
import com.restaurant.menu.Dish;
import com.restaurant.menu.dto.DishCreateRequest;
import com.restaurant.menu.dto.DishStatusRequest;
import com.restaurant.menu.dto.DishUpdateRequest;
import com.restaurant.menu.dto.StockAdjustRequest;
import com.restaurant.menu.dto.StockSetRequest;
import com.restaurant.menu.mapper.CategoryMapper;
import com.restaurant.menu.mapper.DishMapper;
import com.restaurant.menu.vo.DeletedView;
import com.restaurant.menu.vo.DishDetailView;
import com.restaurant.menu.vo.DishSummaryView;
import com.restaurant.menu.vo.StockView;
import com.restaurant.security.RoleGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 商家端菜品与库存（{@code docs/02-api.md §16.5–§16.13}）。
 *
 * <p>库存唯一写入口：{@code adjustStock} 使用原子 UPDATE，{@code setStock} 使用乐观锁重试；
 * 所有写操作都会立即失效公开菜单缓存（分类数量/分页结果/详情）。
 */
@Service
@RequiredArgsConstructor
public class DishService {

    private static final int STOCK_SET_MAX_ATTEMPTS = 3;

    private final DishMapper dishMapper;

    private final CategoryMapper categoryMapper;

    private final RoleGuard roleGuard;

    private final AppProperties appProperties;

    /** 商家端菜品分页（含下架，按 sort ASC, id ASC）。 */
    public PageResult<DishSummaryView> listForMerchant(Long categoryId, String keyword, String status,
                                                       long page, long size) {
        Long merchantId = roleGuard.requireMerchantId();
        var wrapper = Wrappers.<Dish>lambdaQuery()
                .eq(Dish::getMerchantId, merchantId)
                .eq(categoryId != null, Dish::getCategoryId, categoryId)
                .eq(StringUtils.hasText(status), Dish::getStatus, status);
        if (StringUtils.hasText(keyword)) {
            String trimmed = keyword.trim();
            wrapper.and(w -> w.like(Dish::getName, trimmed).or().like(Dish::getDescription, trimmed));
        }
        wrapper.orderByAsc(Dish::getSort).orderByAsc(Dish::getId);
        Page<Dish> result = dishMapper.selectPage(new Page<>(page, size), wrapper);
        Map<Long, String> categoryNames = loadCategoryNames(result.getRecords().stream()
                .map(Dish::getCategoryId).filter(Objects::nonNull).distinct().toList());
        List<DishSummaryView> views = result.getRecords().stream()
                .map(dish -> toSummary(dish, categoryNames.get(dish.getCategoryId())))
                .toList();
        return PageResult.of(views, result.getTotal(), result.getCurrent(), result.getSize());
    }

    /** 商家端菜品详情。 */
    public DishDetailView detail(Long id) {
        Dish dish = requireOwnedDish(id, roleGuard.requireMerchantId());
        return toDetail(dish);
    }

    /** 创建菜品。 */
    @Transactional
    @CacheEvict(cacheNames = {CacheKeys.MENU_CATEGORIES, CacheKeys.MENU_DISHES, CacheKeys.MENU_DISH},
            allEntries = true)
    public DishDetailView create(DishCreateRequest request) {
        Long merchantId = roleGuard.requireMerchantId();
        requireOwnedCategory(request.getCategoryId(), merchantId);
        validateOriginalPrice(request.getPrice(), request.getOriginalPrice());
        Dish dish = new Dish();
        dish.setMerchantId(merchantId);
        applyFields(dish, request.getCategoryId(), request.getName(), request.getPrice(),
                request.getOriginalPrice(), request.getImageUrl(), request.getDescription(),
                request.getStock(), request.getSort(), request.getStatus());
        dish.setSales(0);
        dishMapper.insert(dish);
        return toDetail(dish);
    }

    /** 全量更新菜品（改价不影响已下单快照）。 */
    @Transactional
    @CacheEvict(cacheNames = {CacheKeys.MENU_CATEGORIES, CacheKeys.MENU_DISHES, CacheKeys.MENU_DISH},
            allEntries = true)
    public DishDetailView update(Long id, DishUpdateRequest request) {
        Long merchantId = roleGuard.requireMerchantId();
        Dish dish = requireOwnedDish(id, merchantId);
        requireOwnedCategory(request.getCategoryId(), merchantId);
        validateOriginalPrice(request.getPrice(), request.getOriginalPrice());
        applyFields(dish, request.getCategoryId(), request.getName(), request.getPrice(),
                request.getOriginalPrice(), request.getImageUrl(), request.getDescription(),
                request.getStock(), request.getSort(), request.getStatus());
        if (dishMapper.updateById(dish) == 0) {
            throw new BizException(ErrorCode.STATE_CONFLICT, "菜品已被修改，请重试");
        }
        return toDetail(dish);
    }

    /** 逻辑删除菜品（订单明细快照保留）。 */
    @Transactional
    @CacheEvict(cacheNames = {CacheKeys.MENU_CATEGORIES, CacheKeys.MENU_DISHES, CacheKeys.MENU_DISH},
            allEntries = true)
    public DeletedView delete(Long id) {
        Dish dish = requireOwnedDish(id, roleGuard.requireMerchantId());
        dishMapper.deleteById(dish.getId());
        return DeletedView.yes();
    }

    /** 上下架：立即失效菜单缓存。 */
    @Transactional
    @CacheEvict(cacheNames = {CacheKeys.MENU_CATEGORIES, CacheKeys.MENU_DISHES, CacheKeys.MENU_DISH},
            allEntries = true)
    public DishDetailView changeStatus(Long id, DishStatusRequest request) {
        Dish dish = requireOwnedDish(id, roleGuard.requireMerchantId());
        dish.setStatus(request.getStatus());
        if (dishMapper.updateById(dish) == 0) {
            throw new BizException(ErrorCode.STATE_CONFLICT, "菜品已被修改，请重试");
        }
        return toDetail(dish);
    }

    /** 绝对库存设置（乐观锁，冲突重试 3 次）。 */
    @CacheEvict(cacheNames = {CacheKeys.MENU_CATEGORIES, CacheKeys.MENU_DISHES, CacheKeys.MENU_DISH},
            allEntries = true)
    public StockView setStock(Long id, StockSetRequest request) {
        Long merchantId = roleGuard.requireMerchantId();
        for (int attempt = 0; attempt < STOCK_SET_MAX_ATTEMPTS; attempt++) {
            Dish dish = requireOwnedDish(id, merchantId);
            int version = dish.getVersion() == null ? 0 : dish.getVersion();
            if (dishMapper.setStock(id, merchantId, request.getStock(), version) > 0) {
                return stockView(id, request.getStock());
            }
        }
        throw new BizException(ErrorCode.STATE_CONFLICT, "库存已被其他操作修改，请重试");
    }

    /** 增量库存调整（原子 UPDATE；扣减为负 → 40901）。 */
    @CacheEvict(cacheNames = {CacheKeys.MENU_CATEGORIES, CacheKeys.MENU_DISHES, CacheKeys.MENU_DISH},
            allEntries = true)
    public StockView adjustStock(Long id, StockAdjustRequest request) {
        Long merchantId = roleGuard.requireMerchantId();
        if (request.getDelta() == 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "delta 不能为 0");
        }
        requireOwnedDish(id, merchantId);
        if (dishMapper.adjustStock(id, merchantId, request.getDelta()) == 0) {
            throw new BizException(ErrorCode.STOCK_INSUFFICIENT, "库存不足，无法扣减");
        }
        Dish updated = dishMapper.selectById(id);
        int stock = updated == null || updated.getStock() == null ? 0 : updated.getStock();
        return stockView(id, stock);
    }

    /** 低库存菜品（stock <= threshold，按 stock ASC, id ASC）。 */
    public PageResult<DishSummaryView> listLowStock(int threshold, long page, long size) {
        Long merchantId = roleGuard.requireMerchantId();
        Page<Dish> result = dishMapper.selectPage(new Page<>(page, size), Wrappers.<Dish>lambdaQuery()
                .eq(Dish::getMerchantId, merchantId)
                .le(Dish::getStock, threshold)
                .orderByAsc(Dish::getStock)
                .orderByAsc(Dish::getId));
        Map<Long, String> categoryNames = loadCategoryNames(result.getRecords().stream()
                .map(Dish::getCategoryId).filter(Objects::nonNull).distinct().toList());
        List<DishSummaryView> views = result.getRecords().stream()
                .map(dish -> toSummary(dish, categoryNames.get(dish.getCategoryId())))
                .toList();
        return PageResult.of(views, result.getTotal(), result.getCurrent(), result.getSize());
    }

    private void applyFields(Dish dish, Long categoryId, String name, BigDecimal price,
                             BigDecimal originalPrice, String imageUrl, String description,
                             Integer stock, Integer sort, String status) {
        dish.setCategoryId(categoryId);
        dish.setName(name.trim());
        dish.setPrice(price);
        dish.setOriginalPrice(originalPrice);
        dish.setImageUrl(StringUtils.hasText(imageUrl) ? imageUrl.trim() : null);
        dish.setDescription(description);
        dish.setStock(stock);
        if (sort != null) {
            dish.setSort(sort);
        } else if (dish.getSort() == null) {
            dish.setSort(0);
        }
        if (status != null) {
            dish.setStatus(status);
        } else if (dish.getStatus() == null) {
            dish.setStatus("ON_SALE");
        }
    }

    private void validateOriginalPrice(BigDecimal price, BigDecimal originalPrice) {
        if (originalPrice != null && price != null && originalPrice.compareTo(price) < 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "原价不能低于现价");
        }
    }

    private Dish requireOwnedDish(Long id, Long merchantId) {
        Dish dish = dishMapper.selectOne(Wrappers.<Dish>lambdaQuery()
                .eq(Dish::getId, id)
                .eq(Dish::getMerchantId, merchantId));
        if (dish == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "菜品不存在");
        }
        return dish;
    }

    private Category requireOwnedCategory(Long categoryId, Long merchantId) {
        Category category = categoryMapper.selectOne(Wrappers.<Category>lambdaQuery()
                .eq(Category::getId, categoryId)
                .eq(Category::getMerchantId, merchantId));
        if (category == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "分类不存在");
        }
        return category;
    }

    private Map<Long, String> loadCategoryNames(List<Long> categoryIds) {
        if (categoryIds.isEmpty()) {
            return Map.of();
        }
        return categoryMapper.selectByIds(categoryIds).stream()
                .collect(Collectors.toMap(Category::getId, Category::getName));
    }

    private DishDetailView toDetail(Dish dish) {
        Category category = dish.getCategoryId() == null ? null : categoryMapper.selectById(dish.getCategoryId());
        DishMapper.RatingAggregate rating = dishMapper.selectRatingAggregate(dish.getId());
        BigDecimal ratingAvg = rating == null ? null : rating.getRatingAvg();
        long reviewCount = rating == null || rating.getReviewCount() == null ? 0L : rating.getReviewCount();
        return new DishDetailView(dish.getId(), dish.getName(), dish.getCategoryId(),
                category == null ? null : category.getName(), dish.getPrice(), dish.getOriginalPrice(),
                dish.getImageUrl(), dish.getStock(), dish.getSales(), dish.getStatus(),
                dish.getStock() == null || dish.getStock() <= 0, dish.getSort(), dish.getDescription(),
                ratingAvg, reviewCount, dish.getCreatedAt(), dish.getUpdatedAt());
    }

    private DishSummaryView toSummary(Dish dish, String categoryName) {
        return new DishSummaryView(dish.getId(), dish.getName(), dish.getCategoryId(), categoryName,
                dish.getPrice(), dish.getOriginalPrice(), dish.getImageUrl(), dish.getStock(), dish.getSales(),
                dish.getStatus(), dish.getStock() == null || dish.getStock() <= 0, dish.getSort());
    }

    private StockView stockView(Long dishId, int stock) {
        return new StockView(dishId, stock, stock <= appProperties.getStock().getWarnThreshold());
    }
}
