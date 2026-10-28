package com.restaurant.menu.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.restaurant.common.api.ErrorCode;
import com.restaurant.common.api.PageResult;
import com.restaurant.common.exception.BizException;
import com.restaurant.common.util.CacheKeys;
import com.restaurant.merchant.Merchant;
import com.restaurant.merchant.mapper.MerchantMapper;
import com.restaurant.merchant.vo.MerchantPublicView;
import com.restaurant.menu.Category;
import com.restaurant.menu.Dish;
import com.restaurant.menu.dto.DishQuery;
import com.restaurant.menu.mapper.CategoryMapper;
import com.restaurant.menu.mapper.DishMapper;
import com.restaurant.menu.vo.CategoryView;
import com.restaurant.menu.vo.DishDetailView;
import com.restaurant.menu.vo.DishSummaryView;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 公开菜单查询（{@code docs/02-api.md §6}、{@code docs/01-architecture.md §13}）。
 *
 * <p>缓存策略：分类/菜品分页/菜品详情/店铺信息均 10 分钟 TTL，key 由 {@code CacheKeys} 常量拼接；
 * 商家端任何写操作（库存/菜品/分类）在事务提交后通过 {@link MenuCacheEvictor} 按 merchantId/dishId 精准失效，
 * 回滚时不失效，避免缓存与数据库不一致。
 */
@Service
@RequiredArgsConstructor
public class MenuQueryService {

    private final MerchantMapper merchantMapper;

    private final CategoryMapper categoryMapper;

    private final DishMapper dishMapper;

    /** 公开分类（仅 ENABLED，dishCount 只统计可售菜品）。 */
    @Cacheable(cacheNames = CacheKeys.MENU_CATEGORIES, key = "#merchantId")
    public List<CategoryView> listPublicCategories(Long merchantId) {
        requireAvailableMerchant(merchantId);
        List<Category> categories = categoryMapper.selectList(Wrappers.<Category>lambdaQuery()
                .eq(Category::getMerchantId, merchantId)
                .eq(Category::getStatus, "ENABLED")
                .orderByAsc(Category::getSort)
                .orderByAsc(Category::getId));
        Map<Long, Long> counts = categoryMapper.countSellableDishesByCategory(merchantId).stream()
                .collect(Collectors.toMap(CategoryMapper.CategoryDishCount::getCategoryId,
                        CategoryMapper.CategoryDishCount::getDishCount));
        return categories.stream()
                .map(category -> new CategoryView(category.getId(), category.getMerchantId(), category.getName(),
                        category.getSort(), category.getStatus(), counts.getOrDefault(category.getId(), 0L),
                        category.getCreatedAt()))
                .toList();
    }

    /** 公开菜品分页（仅 ON_SALE，缺省按 sort ASC, id ASC）。 */
    @Cacheable(cacheNames = CacheKeys.MENU_DISHES, key = "#query.cacheKey()")
    public PageResult<DishSummaryView> listPublicDishes(DishQuery query) {
        requireAvailableMerchant(query.getMerchantId());
        var wrapper = Wrappers.<Dish>lambdaQuery()
                .eq(Dish::getMerchantId, query.getMerchantId())
                .eq(Dish::getStatus, "ON_SALE")
                .eq(query.getCategoryId() != null, Dish::getCategoryId, query.getCategoryId());
        if (StringUtils.hasText(query.getKeyword())) {
            String keyword = query.getKeyword().trim();
            wrapper.and(w -> w.like(Dish::getName, keyword).or().like(Dish::getDescription, keyword));
        }
        applySort(wrapper, query.getSort());
        Page<Dish> page = dishMapper.selectPage(new Page<>(query.getPage(), query.getSize()), wrapper);
        Map<Long, String> categoryNames = loadCategoryNames(page.getRecords().stream()
                .map(Dish::getCategoryId).filter(Objects::nonNull).distinct().toList());
        List<DishSummaryView> views = page.getRecords().stream()
                .map(dish -> toSummary(dish, categoryNames.get(dish.getCategoryId())))
                .toList();
        return PageResult.of(views, page.getTotal(), page.getCurrent(), page.getSize());
    }

    /** 公开菜品详情（OFF_SHELF / 已删除 → 40401）。 */
    @Cacheable(cacheNames = CacheKeys.MENU_DISH, key = "#dishId")
    public DishDetailView getPublicDishDetail(Long dishId) {
        Dish dish = dishMapper.selectById(dishId);
        if (dish == null || !"ON_SALE".equals(dish.getStatus())) {
            throw new BizException(ErrorCode.NOT_FOUND, "菜品不存在或已下架");
        }
        Category category = dish.getCategoryId() == null ? null : categoryMapper.selectById(dish.getCategoryId());
        DishMapper.RatingAggregate rating = dishMapper.selectRatingAggregate(dishId);
        BigDecimal ratingAvg = rating == null ? null : rating.getRatingAvg();
        long reviewCount = rating == null || rating.getReviewCount() == null ? 0L : rating.getReviewCount();
        return new DishDetailView(dish.getId(), dish.getName(), dish.getCategoryId(),
                category == null ? null : category.getName(), dish.getPrice(), dish.getOriginalPrice(),
                dish.getImageUrl(), dish.getStock(), dish.getSales(), dish.getStatus(), isSoldOut(dish),
                dish.getSort(), dish.getDescription(), ratingAvg, reviewCount, dish.getCreatedAt(),
                dish.getUpdatedAt());
    }

    /** 店铺公开信息（仅审核通过且启用）。 */
    @Cacheable(cacheNames = CacheKeys.MENU_MERCHANT, key = "#merchantId")
    public MerchantPublicView getMerchantPublic(Long merchantId) {
        Merchant merchant = requireAvailableMerchant(merchantId);
        return new MerchantPublicView(merchant.getId(), merchant.getName(), merchant.getDescription(),
                merchant.getLogoUrl(), merchant.getPhone(), merchant.getAddress(), merchant.getDeliveryFee(),
                merchant.getMinOrderAmount(), merchant.getBusinessStatus());
    }

    private Merchant requireAvailableMerchant(Long merchantId) {
        Merchant merchant = merchantMapper.selectById(merchantId);
        if (merchant == null
                || !"APPROVED".equals(merchant.getAuditStatus())
                || !"ENABLED".equals(merchant.getStatus())) {
            throw new BizException(ErrorCode.NOT_FOUND, "商家不存在或不可用");
        }
        return merchant;
    }

    private Map<Long, String> loadCategoryNames(Collection<Long> categoryIds) {
        if (categoryIds.isEmpty()) {
            return Map.of();
        }
        return categoryMapper.selectByIds(categoryIds).stream()
                .collect(Collectors.toMap(Category::getId, Category::getName));
    }

    private void applySort(com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Dish> wrapper,
                           String sort) {
        switch (sort == null ? "default" : sort) {
            case "price_asc" -> wrapper.orderByAsc(Dish::getPrice).orderByAsc(Dish::getId);
            case "price_desc" -> wrapper.orderByDesc(Dish::getPrice).orderByAsc(Dish::getId);
            case "sales_desc" -> wrapper.orderByDesc(Dish::getSales).orderByAsc(Dish::getId);
            default -> wrapper.orderByAsc(Dish::getSort).orderByAsc(Dish::getId);
        }
    }

    private DishSummaryView toSummary(Dish dish, String categoryName) {
        return new DishSummaryView(dish.getId(), dish.getName(), dish.getCategoryId(), categoryName,
                dish.getPrice(), dish.getOriginalPrice(), dish.getImageUrl(), dish.getStock(), dish.getSales(),
                dish.getStatus(), isSoldOut(dish), dish.getSort());
    }

    private boolean isSoldOut(Dish dish) {
        return dish.getStock() == null || dish.getStock() <= 0;
    }
}
