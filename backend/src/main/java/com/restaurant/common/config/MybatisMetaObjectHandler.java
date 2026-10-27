package com.restaurant.common.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * MyBatis-Plus 自动填充处理器。
 *
 * <p>配合实体字段上的 {@code @TableField(fill = ...)} 生效（列名 {@code created_at} / {@code updated_at}，
 * 实体属性 {@code createdAt} / {@code updatedAt}）：
 * <ul>
 *   <li>insert：createdAt / updatedAt；</li>
 *   <li>update：updatedAt。</li>
 * </ul>
 * 使用 strict fill，只填充未显式赋值的字段，便于测试或数据初始化指定时间。
 */
@Component
public class MybatisMetaObjectHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        strictInsertFill(metaObject, "createdAt", LocalDateTime.class, now);
        strictInsertFill(metaObject, "updatedAt", LocalDateTime.class, now);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        strictUpdateFill(metaObject, "updatedAt", LocalDateTime.class, LocalDateTime.now());
    }
}
