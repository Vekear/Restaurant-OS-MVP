/**
 * 商家分类 / 菜品 / 库存接口（docs/02-api.md §16）
 * 删除均为逻辑删除：订单快照保留，购物车对应行 available=false。
 */
import { http } from './http'

/* ==================== 分类 ==================== */

/** GET /merchant/categories → CategoryView[]（含 DISABLED，按 sort ASC, id ASC） */
export function listCategories() {
  return http.get('/merchant/categories', { silent: true })
}

/**
 * POST /merchant/categories
 * @param {{ name:string, sort?:number }} payload
 */
export function createCategory(payload) {
  return http.post('/merchant/categories', payload, { silent: true })
}

/**
 * PUT /merchant/categories/{id}
 * @param {number} id
 * @param {{ name?:string, sort?:number, status?:'ENABLED'|'DISABLED' }} payload
 */
export function updateCategory(id, payload) {
  return http.put(`/merchant/categories/${id}`, payload, { silent: true })
}

/** DELETE /merchant/categories/{id} —— 分类下存在未删除菜品 → 40915 */
export function deleteCategory(id) {
  return http.delete(`/merchant/categories/${id}`, { silent: true })
}

/* ==================== 菜品 ==================== */

/**
 * GET /merchant/dishes
 * @param {{ categoryId?:number, keyword?:string, status?:'ON_SALE'|'OFF_SHELF',
 *           page?:number, size?:number }} params
 */
export function listDishes(params = {}) {
  return http.get('/merchant/dishes', { params, silent: true })
}

/** GET /merchant/dishes/{id} → DishDetailView */
export function getDish(id) {
  return http.get(`/merchant/dishes/${id}`, { silent: true })
}

/**
 * POST /merchant/dishes
 * @param {{ categoryId:number, name:string, price:number, originalPrice?:number,
 *           imageUrl?:string, description?:string, stock:number, sort?:number,
 *           status?:'ON_SALE'|'OFF_SHELF' }} payload
 */
export function createDish(payload) {
  return http.post('/merchant/dishes', payload, { silent: true })
}

/**
 * PUT /merchant/dishes/{id} —— 全量更新；改价不影响已下单快照
 * @param {number} id
 * @param {object} payload 同 createDish
 */
export function updateDish(id, payload) {
  return http.put(`/merchant/dishes/${id}`, payload, { silent: true })
}

/** DELETE /merchant/dishes/{id} —— 逻辑删除 */
export function deleteDish(id) {
  return http.delete(`/merchant/dishes/${id}`, { silent: true })
}

/**
 * PATCH /merchant/dishes/{id}/status —— 上下架，立即失效菜单缓存
 * @param {number} id
 * @param {'ON_SALE'|'OFF_SHELF'} status
 */
export function updateDishStatus(id, status) {
  return http.patch(`/merchant/dishes/${id}/status`, { status }, { silent: true })
}

/**
 * PUT /merchant/dishes/{id}/stock —— 直接设置库存
 * @param {number} id
 * @param {number} stock
 */
export function updateDishStock(id, stock) {
  return http.put(`/merchant/dishes/${id}/stock`, { stock }, { silent: true })
}

/**
 * POST /merchant/dishes/{id}/stock/adjust —— 增减库存（负数为扣减，库存不足 → 40901）
 * @param {number} id
 * @param {{ delta:number, reason?:string }} payload
 */
export function adjustDishStock(id, payload) {
  return http.post(`/merchant/dishes/${id}/stock/adjust`, payload, { silent: true })
}

/**
 * GET /merchant/inventory/low-stock
 * @param {{ threshold?:number, page?:number, size?:number }} params
 */
export function listLowStockDishes(params = {}) {
  return http.get('/merchant/inventory/low-stock', { params, silent: true })
}

export const merchantDishApi = {
  listCategories,
  createCategory,
  updateCategory,
  deleteCategory,
  listDishes,
  getDish,
  createDish,
  updateDish,
  deleteDish,
  updateDishStatus,
  updateDishStock,
  adjustDishStock,
  listLowStockDishes
}

export default merchantDishApi
