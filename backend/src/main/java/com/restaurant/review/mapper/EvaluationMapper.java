package com.restaurant.review.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.restaurant.review.Evaluation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** 评价 Mapper。 */
@Mapper
public interface EvaluationMapper extends BaseMapper<Evaluation> {

    /** 菜品评价总数：评价按订单关联菜品行统计，同一订单内同菜品多行只计一次。 */
    @Select("""
            SELECT COUNT(DISTINCT e.id)
            FROM evaluation e
            JOIN order_item oi ON oi.order_id = e.order_id
            WHERE oi.dish_id = #{dishId} AND e.status = 'ENABLED'
            """)
    long countEnabledByDish(@Param("dishId") Long dishId);

    /** 菜品评价分页（{@code createdAt DESC, id DESC} 稳定排序；DISTINCT 去重）。 */
    @Select("""
            SELECT DISTINCT e.*
            FROM evaluation e
            JOIN order_item oi ON oi.order_id = e.order_id
            WHERE oi.dish_id = #{dishId} AND e.status = 'ENABLED'
            ORDER BY e.created_at DESC, e.id DESC
            LIMIT #{offset}, #{size}
            """)
    List<Evaluation> selectEnabledPageByDish(@Param("dishId") Long dishId,
                                             @Param("offset") long offset,
                                             @Param("size") long size);
}
