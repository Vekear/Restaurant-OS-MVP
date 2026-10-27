package com.restaurant.common.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 业务实体公共字段基类（对应 T03 DDL 中每张业务表的 {@code id / created_at / updated_at}）。
 *
 * <p>列名 snake_case 由 MyBatis-Plus 驼峰映射转换；时间由
 * {@link com.restaurant.common.config.MybatisMetaObjectHandler} 自动填充。
 * 需要乐观锁的表在具体实体中额外声明 {@code @Version private Integer version;}。
 */
@Getter
@Setter
public abstract class BaseEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
