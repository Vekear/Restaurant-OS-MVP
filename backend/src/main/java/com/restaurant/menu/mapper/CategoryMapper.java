package com.restaurant.menu.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.restaurant.menu.Category;
import lombok.Data;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 分类 Mapper：附带按分类聚合的菜品数量（避免 N+1）。
 */
@Mapper
public interface CategoryMapper extends BaseMapper<Category> {

    /** 商家端：分类下未删除菜品数。 */
    @Select("""
            SELECT category_id AS categoryId, COUNT(*) AS dishCount
            FROM dish
            WHERE merchant_id = #{merchantId} AND deleted = 0
            GROUP BY category_id
            """)
    List<CategoryDishCount> countDishesByCategory(@Param("merchantId") Long merchantId);

    /** 公开端：分类下可售（ON_SALE）菜品数。 */
    @Select("""
            SELECT category_id AS categoryId, COUNT(*) AS dishCount
            FROM dish
            WHERE merchant_id = #{merchantId} AND deleted = 0 AND status = 'ON_SALE'
            GROUP BY category_id
            """)
    List<CategoryDishCount> countSellableDishesByCategory(@Param("merchantId") Long merchantId);

    /** 聚合结果行。 */
    @Data
    class CategoryDishCount {

        private Long categoryId;

        private Long dishCount;
    }
}
