package com.restaurant.contract;

import java.util.Collection;

/** 跨域端口：下单成功后清理购物车中已下单菜品（实现方：cart 域）。 */
public interface CartClearPort {

    void clearDishes(Long userId, Collection<Long> dishIds);
}
