package com.restaurant.cart;

import com.restaurant.cart.mapper.CartItemMapper;
import com.restaurant.contract.CartClearPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;

/** {@link CartClearPort} 的购物车域实现。 */
@Component
@RequiredArgsConstructor
public class CartClearAdapter implements CartClearPort {

    private final CartItemMapper cartItemMapper;

    @Override
    public void clearDishes(Long userId, Collection<Long> dishIds) {
        if (dishIds == null || dishIds.isEmpty()) {
            return;
        }
        cartItemMapper.deleteByUserAndDishIds(userId, dishIds);
    }
}
