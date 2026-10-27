/**
 * 商家订单接口（docs/02-api.md §18）
 * 所有动作走 OrderStateMachine.transition()：非法状态/跳步 → 40905，跨店 → 40302。
 * 外卖：PAID →(accept)→ ACCEPTED →(prepare)→ MAKING →(deliver)→ DELIVERING →(complete)→ COMPLETED
 * 堂食：ACCEPTED →(prepare)→ MAKING →(serve)→ SERVED →(complete)→ COMPLETED（同事务关会话 + 释放桌台）
 */
import { http } from './http'

/* ==================== 外卖 ==================== */

/**
 * GET /merchant/orders
 * @param {{ statusGroup?:'ALL'|'ACTIVE'|'FINISHED', status?:string,
 *           page?:number, size?:number }} params
 */
export function listTakeoutOrders(params = {}) {
  return http.get('/merchant/orders', { params, silent: true })
}

/** GET /merchant/orders/{id} → OrderDetailView */
export function getTakeoutOrder(id) {
  return http.get(`/merchant/orders/${id}`, { silent: true })
}

/** POST /merchant/orders/{id}/accept —— PAID → ACCEPTED */
export function acceptTakeoutOrder(id) {
  return http.post(`/merchant/orders/${id}/accept`, null, { silent: true })
}

/**
 * POST /merchant/orders/{id}/reject —— PAID → REJECTED（回补库存与券、模拟退款）
 * @param {number} id
 * @param {string} reason 2–100 字符
 */
export function rejectTakeoutOrder(id, reason) {
  return http.post(`/merchant/orders/${id}/reject`, { reason }, { silent: true })
}

/** POST /merchant/orders/{id}/prepare —— ACCEPTED → MAKING */
export function prepareTakeoutOrder(id) {
  return http.post(`/merchant/orders/${id}/prepare`, null, { silent: true })
}

/** POST /merchant/orders/{id}/deliver —— MAKING → DELIVERING（仅外卖） */
export function deliverTakeoutOrder(id) {
  return http.post(`/merchant/orders/${id}/deliver`, null, { silent: true })
}

/** POST /merchant/orders/{id}/complete —— DELIVERING → COMPLETED */
export function completeTakeoutOrder(id) {
  return http.post(`/merchant/orders/${id}/complete`, null, { silent: true })
}

/* ==================== 堂食 ==================== */

/**
 * GET /merchant/dine-in/orders
 * @param {{ statusGroup?:'ALL'|'ACTIVE'|'FINISHED', status?:string,
 *           page?:number, size?:number }} params
 */
export function listDineInOrders(params = {}) {
  return http.get('/merchant/dine-in/orders', { params, silent: true })
}

/** GET /merchant/dine-in/orders/{id} */
export function getDineInOrder(id) {
  return http.get(`/merchant/dine-in/orders/${id}`, { silent: true })
}

/** POST /merchant/dine-in/orders/{id}/prepare —— ACCEPTED → MAKING */
export function prepareDineInOrder(id) {
  return http.post(`/merchant/dine-in/orders/${id}/prepare`, null, { silent: true })
}

/** POST /merchant/dine-in/orders/{id}/serve —— MAKING → SERVED */
export function serveDineInOrder(id) {
  return http.post(`/merchant/dine-in/orders/${id}/serve`, null, { silent: true })
}

/** POST /merchant/dine-in/orders/{id}/complete —— SERVED → COMPLETED（三写同一事务） */
export function completeDineInOrder(id) {
  return http.post(`/merchant/dine-in/orders/${id}/complete`, null, { silent: true })
}

/** 详情加载函数：按订单类型分派 */
export function getMerchantOrder(orderType, id) {
  return orderType === 'DINE_IN' ? getDineInOrder(id) : getTakeoutOrder(id)
}

/** 列表加载函数：按订单类型分派 */
export function listMerchantOrders(orderType, params = {}) {
  return orderType === 'DINE_IN' ? listDineInOrders(params) : listTakeoutOrders(params)
}

export const merchantOrderApi = {
  listTakeoutOrders,
  getTakeoutOrder,
  acceptTakeoutOrder,
  rejectTakeoutOrder,
  prepareTakeoutOrder,
  deliverTakeoutOrder,
  completeTakeoutOrder,
  listDineInOrders,
  getDineInOrder,
  prepareDineInOrder,
  serveDineInOrder,
  completeDineInOrder,
  getMerchantOrder,
  listMerchantOrders
}

export default merchantOrderApi
