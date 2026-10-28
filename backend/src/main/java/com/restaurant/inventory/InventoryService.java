package com.restaurant.inventory;

import com.restaurant.common.api.ErrorCode;
import com.restaurant.common.exception.BizException;
import com.restaurant.contract.InventoryPort;
import com.restaurant.inventory.mapper.InventoryMapper;
import com.restaurant.menu.service.MenuCacheEvictor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** {@link InventoryPort} 的库存域实现：扣减不足抛 40901，回补幂等由订单状态机保证。 */
@Service
@RequiredArgsConstructor
public class InventoryService implements InventoryPort {

    private final InventoryMapper inventoryMapper;

    private final MenuCacheEvictor menuCacheEvictor;

    @Override
    public void deduct(Long dishId, int quantity) {
        int rows = inventoryMapper.deduct(dishId, quantity);
        if (rows == 0) {
            throw new BizException(ErrorCode.STOCK_INSUFFICIENT, "库存不足，无法扣减");
        }
        evictMenuCache(dishId);
    }

    @Override
    public void restore(Long dishId, int quantity) {
        inventoryMapper.restore(dishId, quantity);
        evictMenuCache(dishId);
    }

    /** 库存变更事务提交后失效公开菜单缓存（回滚时不失效，避免缓存与库不一致）。 */
    private void evictMenuCache(Long dishId) {
        menuCacheEvictor.evictInventoryAfterCommit(inventoryMapper.selectMerchantId(dishId), dishId);
    }
}
