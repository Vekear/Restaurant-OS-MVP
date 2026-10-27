package com.restaurant.menu;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.restaurant.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 菜品 {@code dish}。
 *
 * <p>库存通过 {@code DishMapper} 的原子 UPDATE 调整（{@code stock = stock + delta}），
 * 绝对库存设置使用 {@code version} 乐观锁重试；菜单缓存由 Service 层注解失效。
 */
@Getter
@Setter
@TableName("dish")
public class Dish extends BaseEntity {

    private Long merchantId;

    private Long categoryId;

    private String name;

    private String description;

    private BigDecimal price;

    private BigDecimal originalPrice;

    private String imageUrl;

    private Integer stock;

    private Integer sales;

    /** ON_SALE / OFF_SHELF。 */
    private String status;

    private Integer sort;

    @TableLogic
    private Integer deleted;

    @Version
    private Integer version;
}
