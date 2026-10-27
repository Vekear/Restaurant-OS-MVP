package com.restaurant.menu.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.restaurant.menu.Dish;
import lombok.Data;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;

/**
 * 菜品 Mapper：库存原子调整 + 评价聚合。
 */
@Mapper
public interface DishMapper extends BaseMapper<Dish> {

    /**
     * 原子调整库存：{@code stock = stock + delta}，扣减后为负则不更新（0 行）。
     * 同时自增 {@code version}，保证并发下与绝对库存设置互斥。
     */
    @Update("""
            UPDATE dish
            SET stock = stock + #{delta}, version = version + 1, updated_at = CURRENT_TIMESTAMP(3)
            WHERE id = #{dishId} AND merchant_id = #{merchantId} AND deleted = 0
              AND stock + #{delta} >= 0
            """)
    int adjustStock(@Param("dishId") Long dishId,
                    @Param("merchantId") Long merchantId,
                    @Param("delta") int delta);

    /**
     * 乐观锁设置绝对库存：仅当 {@code version} 与读取时一致才更新，0 行表示并发冲突。
     */
    @Update("""
            UPDATE dish
            SET stock = #{stock}, version = version + 1, updated_at = CURRENT_TIMESTAMP(3)
            WHERE id = #{dishId} AND merchant_id = #{merchantId} AND deleted = 0
              AND version = #{version}
            """)
    int setStock(@Param("dishId") Long dishId,
                 @Param("merchantId") Long merchantId,
                 @Param("stock") int stock,
                 @Param("version") int version);

    /** 菜品评分聚合（评价按订单关联菜品行统计）。 */
    @Select("""
            SELECT AVG(e.rating) AS ratingAvg, COUNT(*) AS reviewCount
            FROM evaluation e
            JOIN order_item oi ON oi.order_id = e.order_id
            WHERE oi.dish_id = #{dishId} AND e.status = 'ENABLED'
            """)
    RatingAggregate selectRatingAggregate(@Param("dishId") Long dishId);

    /** 评分聚合结果行。 */
    @Data
    class RatingAggregate {

        private BigDecimal ratingAvg;

        private Long reviewCount;
    }
}
