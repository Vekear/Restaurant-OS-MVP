package com.restaurant.contract;

/** 跨域端口：菜品库存原子扣减与回补（实现方：inventory 域）。 */
public interface InventoryPort {

    /**
     * 原子扣减库存并累加销量；库存不足或不满足售卖条件时抛 40901。
     *
     * @param dishId   菜品 ID
     * @param quantity 扣减数量
     */
    void deduct(Long dishId, int quantity);

    /**
     * 回补库存（幂等由调用方状态机保证只调用一次）。
     *
     * @param dishId   菜品 ID
     * @param quantity 回补数量
     */
    void restore(Long dishId, int quantity);
}
