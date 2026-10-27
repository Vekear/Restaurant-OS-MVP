package com.restaurant.menu.adapter;

import com.restaurant.contract.DishSnapshot;
import com.restaurant.contract.DishSnapshotPort;
import com.restaurant.menu.Dish;
import com.restaurant.menu.mapper.DishMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** {@link DishSnapshotPort} 的菜单域实现。 */
@Component
@RequiredArgsConstructor
public class DishSnapshotAdapter implements DishSnapshotPort {

    private final DishMapper dishMapper;

    @Override
    public Map<Long, DishSnapshot> loadAll(Collection<Long> dishIds) {
        Map<Long, DishSnapshot> result = new HashMap<>();
        if (dishIds == null || dishIds.isEmpty()) {
            return result;
        }
        List<Dish> dishes = dishMapper.selectBatchIds(dishIds);
        for (Dish dish : dishes) {
            result.put(dish.getId(), new DishSnapshot(dish.getId(), dish.getMerchantId(), dish.getName(),
                    dish.getImageUrl(), dish.getPrice(), dish.getStock(), dish.getStatus()));
        }
        return result;
    }
}
