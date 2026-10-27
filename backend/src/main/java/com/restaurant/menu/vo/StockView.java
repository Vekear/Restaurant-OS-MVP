package com.restaurant.menu.vo;

/**
 * 库存操作结果（{@code docs/02-api.md §16.11/§16.12}）。
 *
 * @param dishId   菜品 ID
 * @param stock    调整后库存
 * @param lowStock 是否低于预警阈值（默认 stock ≤ 5）
 */
public record StockView(Long dishId, Integer stock, boolean lowStock) {
}
