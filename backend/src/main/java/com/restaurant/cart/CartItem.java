package com.restaurant.cart;

import com.baomidou.mybatisplus.annotation.TableName;
import com.restaurant.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/** 购物车明细 {@code cart_item}（unique(user_id, dish_id)）。 */
@Getter
@Setter
@TableName("cart_item")
public class CartItem extends BaseEntity {

    private Long userId;

    private Long dishId;

    private Integer quantity;
}
