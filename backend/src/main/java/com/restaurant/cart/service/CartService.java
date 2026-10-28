package com.restaurant.cart.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.restaurant.cart.CartItem;
import com.restaurant.cart.dto.AddCartItemRequest;
import com.restaurant.cart.dto.UpdateCartQuantityRequest;
import com.restaurant.cart.mapper.CartItemMapper;
import com.restaurant.cart.vo.CartItemView;
import com.restaurant.cart.vo.CartView;
import com.restaurant.common.api.ErrorCode;
import com.restaurant.common.config.AppProperties;
import com.restaurant.common.enums.Role;
import com.restaurant.common.exception.BizException;
import com.restaurant.contract.MerchantPort;
import com.restaurant.menu.Dish;
import com.restaurant.menu.mapper.DishMapper;
import com.restaurant.security.RoleGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 购物车服务（docs/02-api.md §7）。
 *
 * <p>同一菜品自动合并；库存/下架/行数上限在加购与改数量时校验；
 * 失效行保留在购物车中（available=false），但不计入应付总额。
 */
@Service
@RequiredArgsConstructor
public class CartService {

    private static final int MAX_QUANTITY = 99;

    private final CartItemMapper cartItemMapper;
    private final DishMapper dishMapper;
    private final MerchantPort merchantPort;
    private final RoleGuard roleGuard;
    private final AppProperties appProperties;

    @Transactional(readOnly = true)
    public CartView get() {
        return buildView(requireCustomer());
    }

    @Transactional
    public CartView add(AddCartItemRequest request) {
        Long userId = requireCustomer();
        Dish dish = requireOrderableDish(request.getDishId());
        int quantity = request.getQuantity() == null ? 1 : request.getQuantity();
        CartItem existing = findItem(userId, dish.getId());
        if (existing == null) {
            int lineCount = countLines(userId);
            int maxItems = appProperties.getCart().getMaxItems();
            if (lineCount >= maxItems) {
                throw new BizException(ErrorCode.CART_FULL, "购物车已满（最多 " + maxItems + " 种菜品）");
            }
            validateQuantity(dish, quantity);
            CartItem item = new CartItem();
            item.setUserId(userId);
            item.setDishId(dish.getId());
            item.setQuantity(quantity);
            try {
                cartItemMapper.insert(item);
            } catch (DuplicateKeyException ex) {
                CartItem concurrent = findItem(userId, dish.getId());
                if (concurrent == null) {
                    throw ex;
                }
                mergeQuantity(concurrent, dish, quantity);
            }
        } else {
            mergeQuantity(existing, dish, quantity);
        }
        return buildView(userId);
    }

    @Transactional
    public CartView updateQuantity(Long dishId, UpdateCartQuantityRequest request) {
        Long userId = requireCustomer();
        CartItem item = findItem(userId, dishId);
        if (item == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "购物车中不存在该菜品");
        }
        Dish dish = dishMapper.selectById(dishId);
        if (dish != null) {
            validateStock(dish, request.getQuantity());
        }
        item.setQuantity(request.getQuantity());
        cartItemMapper.updateById(item);
        return buildView(userId);
    }

    @Transactional
    public CartView deleteItem(Long dishId) {
        Long userId = requireCustomer();
        cartItemMapper.delete(Wrappers.<CartItem>lambdaQuery()
                .eq(CartItem::getUserId, userId)
                .eq(CartItem::getDishId, dishId));
        return buildView(userId);
    }

    @Transactional
    public CartView clear() {
        Long userId = requireCustomer();
        cartItemMapper.delete(Wrappers.<CartItem>lambdaQuery().eq(CartItem::getUserId, userId));
        return buildView(userId);
    }

    private void mergeQuantity(CartItem item, Dish dish, int added) {
        int merged = (item.getQuantity() == null ? 0 : item.getQuantity()) + added;
        if (merged > MAX_QUANTITY) {
            throw new BizException(ErrorCode.PARAM_INVALID, "同一菜品数量不能超过 " + MAX_QUANTITY);
        }
        validateStock(dish, merged);
        item.setQuantity(merged);
        cartItemMapper.updateById(item);
    }

    private void validateQuantity(Dish dish, int quantity) {
        if (quantity > MAX_QUANTITY) {
            throw new BizException(ErrorCode.PARAM_INVALID, "同一菜品数量不能超过 " + MAX_QUANTITY);
        }
        validateStock(dish, quantity);
    }

    private void validateStock(Dish dish, int quantity) {
        int stock = dish.getStock() == null ? 0 : dish.getStock();
        if (quantity > stock) {
            throw new BizException(ErrorCode.STOCK_INSUFFICIENT,
                    "库存不足：" + dish.getName() + " 仅剩 " + stock + " 份");
        }
    }

    private Dish requireOrderableDish(Long dishId) {
        Dish dish = dishMapper.selectById(dishId);
        if (dish == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "菜品不存在或已删除");
        }
        if (!"ON_SALE".equals(dish.getStatus())) {
            throw new BizException(ErrorCode.STATE_CONFLICT, "菜品已下架");
        }
        MerchantPort.MerchantSnapshot merchant = merchantPort.get(dish.getMerchantId());
        if (merchant == null
                || !"APPROVED".equals(merchant.auditStatus())
                || !"ENABLED".equals(merchant.status())
                || !"OPEN".equals(merchant.businessStatus())) {
            throw new BizException(ErrorCode.STATE_CONFLICT, "商家暂不可下单");
        }
        return dish;
    }

    private CartItem findItem(Long userId, Long dishId) {
        return cartItemMapper.selectOne(Wrappers.<CartItem>lambdaQuery()
                .eq(CartItem::getUserId, userId)
                .eq(CartItem::getDishId, dishId));
    }

    private int countLines(Long userId) {
        Long count = cartItemMapper.selectCount(Wrappers.<CartItem>lambdaQuery()
                .eq(CartItem::getUserId, userId));
        return count == null ? 0 : count.intValue();
    }

    private CartView buildView(Long userId) {
        List<CartItem> items = cartItemMapper.selectList(Wrappers.<CartItem>lambdaQuery()
                .eq(CartItem::getUserId, userId)
                .orderByAsc(CartItem::getId));
        Map<Long, Dish> dishes = loadDishes(items.stream().map(CartItem::getDishId).toList());
        List<CartItemView> views = new ArrayList<>(items.size());
        int totalQuantity = 0;
        int invalidCount = 0;
        BigDecimal totalAmount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        for (CartItem item : items) {
            int quantity = item.getQuantity() == null ? 0 : item.getQuantity();
            totalQuantity += quantity;
            Dish dish = dishes.get(item.getDishId());
            boolean available = dish != null
                    && "ON_SALE".equals(dish.getStatus())
                    && dish.getStock() != null
                    && dish.getStock() >= quantity;
            BigDecimal subtotal = dish == null || dish.getPrice() == null
                    ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                    : dish.getPrice().multiply(BigDecimal.valueOf(quantity)).setScale(2, RoundingMode.HALF_UP);
            if (!available) {
                invalidCount++;
            } else {
                totalAmount = totalAmount.add(subtotal);
            }
            views.add(new CartItemView(item.getDishId(),
                    dish == null ? null : dish.getName(),
                    dish == null ? null : dish.getImageUrl(),
                    dish == null ? null : dish.getPrice(),
                    quantity,
                    dish == null ? null : dish.getStock(),
                    available,
                    subtotal));
        }
        return new CartView(views, totalQuantity, totalAmount, invalidCount,
                appProperties.getCart().getMaxItems());
    }

    private Map<Long, Dish> loadDishes(Collection<Long> dishIds) {
        if (dishIds.isEmpty()) {
            return Map.of();
        }
        return dishMapper.selectBatchIds(dishIds.stream().distinct().toList()).stream()
                .collect(Collectors.toMap(Dish::getId, Function.identity()));
    }

    private Long requireCustomer() {
        return roleGuard.requireAnyRole(Role.CUSTOMER).getId();
    }
}
