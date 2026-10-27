package com.restaurant.contract;

import java.util.Collection;
import java.util.Map;

/** 跨域端口：按 {@code dishId} 批量读取菜品快照（实现方：menu 域）。 */
public interface DishSnapshotPort {

    /**
     * 批量读取菜品快照。
     *
     * @param dishIds 菜品 ID 集合
     * @return dishId → 快照；不存在的 ID 不出现在结果中
     */
    Map<Long, DishSnapshot> loadAll(Collection<Long> dishIds);
}
