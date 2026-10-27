/**
 * 购物车接口（docs/02-api.md §7）
 * 服务端持久化；所有写接口都返回最新 CartView，前端直接用返回值刷新 store。
 */
import { http } from './http'

/** GET /cart */
export function getCart() {
  return http.get('/cart', { silent: true })
}

/**
 * POST /cart/items
 * @param {number} dishId
 * @param {number} [quantity]
 */
export function addCartItem(dishId, quantity = 1) {
  return http.post('/cart/items', { dishId, quantity }, { silent: true })
}

/**
 * PATCH /cart/items/{dishId}
 * @param {number} dishId
 * @param {number} quantity
 */
export function updateCartItem(dishId, quantity) {
  return http.patch(`/cart/items/${dishId}`, { quantity }, { silent: true })
}

/** DELETE /cart/items/{dishId}（幂等） */
export function removeCartItem(dishId) {
  return http.delete(`/cart/items/${dishId}`, { silent: true })
}

/** DELETE /cart（清空） */
export function clearCart() {
  return http.delete('/cart', { silent: true })
}

export const cartApi = {
  getCart,
  addCartItem,
  updateCartItem,
  removeCartItem,
  clearCart
}

export default cartApi
