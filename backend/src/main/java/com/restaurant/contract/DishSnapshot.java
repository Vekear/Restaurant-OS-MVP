package com.restaurant.contract;

import java.math.BigDecimal;

/**
 * 菜品下单快照（菜单域提供给订单/购物车域的只读视图）。
 *
 * @param id        菜品 ID
 * @param merchantId 所属商家 ID
 * @param name      菜品名称
 * @param imageUrl  菜品图片
 * @param price     当前售价
 * @param stock     当前库存
 * @param status    ON_SALE / OFF_SHELF
 */
public record DishSnapshot(Long id,
                           Long merchantId,
                           String name,
                           String imageUrl,
                           BigDecimal price,
                           Integer stock,
                           String status) {
}
