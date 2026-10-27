package com.restaurant.inventory.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/** 库存 Mapper（inventory 域独占 dish 库存列写入，避免跨域引用 menu mapper）。 */
@Mapper
public interface InventoryMapper {

    /**
     * 原子扣减库存并累加销量：{@code stock >= quantity} 才更新。
     *
     * @return 影响行数（0 表示库存不足/菜品不可售）
     */
    @Update("""
            UPDATE dish
            SET stock = stock - #{quantity}, sales = sales + #{quantity},
                version = version + 1, updated_at = CURRENT_TIMESTAMP(3)
            WHERE id = #{dishId} AND deleted = 0 AND status = 'ON_SALE' AND stock >= #{quantity}
            """)
    int deduct(@Param("dishId") Long dishId, @Param("quantity") int quantity);

    /** 回补库存（下单取消/拒单/超时时逐明细调用）。 */
    @Update("""
            UPDATE dish
            SET stock = stock + #{quantity}, version = version + 1, updated_at = CURRENT_TIMESTAMP(3)
            WHERE id = #{dishId}
            """)
    int restore(@Param("dishId") Long dishId, @Param("quantity") int quantity);
}
