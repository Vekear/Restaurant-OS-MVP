package com.restaurant.cart.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.restaurant.cart.CartItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Delete;

import java.util.Collection;

/** 购物车 Mapper。 */
@Mapper
public interface CartItemMapper extends BaseMapper<CartItem> {

    /** 下单后清理购物车中已下单菜品。 */
    @Delete("""
            <script>
            DELETE FROM cart_item
            WHERE user_id = #{userId} AND dish_id IN
            <foreach collection="dishIds" item="dishId" open="(" separator="," close=")">#{dishId}</foreach>
            </script>
            """)
    int deleteByUserAndDishIds(@Param("userId") Long userId,
                               @Param("dishIds") Collection<Long> dishIds);
}
