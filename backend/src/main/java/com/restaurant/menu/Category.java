package com.restaurant.menu;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.restaurant.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 菜品分类 {@code category}。
 *
 * <p>软删除列 {@code deleted} 由 MyBatis-Plus 逻辑删除接管（0 正常 / 1 已删除）；
 * 商家间以 {@code merchant_id} 隔离，唯一键为 {@code (merchant_id, name, deleted)}。
 */
@Getter
@Setter
@TableName("category")
public class Category extends BaseEntity {

    private Long merchantId;

    private String name;

    private Integer sort;

    /** ENABLED / DISABLED。 */
    private String status;

    @TableLogic
    private Integer deleted;

    @Version
    private Integer version;
}
