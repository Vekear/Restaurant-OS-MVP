/**
 * 外卖订单接口（docs/02-api.md §9）
 * 下单 / 支付创建 / 模拟支付均要求 Idempotency-Key：由调用方传入，保证同一次提交重试复用同一个 key。
 */
import { http } from './http'

/**
 * POST /orders —— 创建外卖订单（幂等）
 * @param {{ merchantId:number, addressId:number, items:{dishId:number,quantity:number}[],
 *           userCouponId?:number, remark?:string, expectedPayAmount?:number }} payload
 * @param {string} idempotencyKey
 */
export function createOrder(payload, idempotencyKey) {
  return http.post('/orders', payload, {
    silent: true,
    headers: idempotencyKey ? { 'Idempotency-Key': idempotencyKey } : undefined
  })
}

/**
 * GET /orders —— 我的外卖订单分页
 * @param {{ statusGroup?: 'ALL'|'ACTIVE'|'FINISHED', status?: string, page?: number, size?: number }} params
 */
export function listOrders(params = {}) {
  return http.get('/orders', { params, silent: true })
}

/** GET /orders/{id} —— 订单详情（items/payment/statusLogs/canX） */
export function getOrder(id) {
  return http.get(`/orders/${id}`, { silent: true })
}

/**
 * POST /orders/{id}/cancel
 * @param {number} id
 * @param {string} [reason]
 */
export function cancelOrder(id, reason) {
  return http.post(`/orders/${id}/cancel`, { reason }, { silent: true })
}

/** POST /orders/{id}/confirm-receipt —— DELIVERING → COMPLETED */
export function confirmReceipt(id) {
  return http.post(`/orders/${id}/confirm-receipt`, null, { silent: true })
}

/**
 * POST /orders/{id}/review —— 评价（每单一次）
 * @param {number} id
 * @param {{ rating:number, content?:string }} payload
 */
export function reviewOrder(id, payload) {
  return http.post(`/orders/${id}/review`, payload, { silent: true })
}

/** GET /orders/{id}/review */
export function getOrderReview(id) {
  return http.get(`/orders/${id}/review`, { silent: true })
}

export const orderApi = {
  createOrder,
  listOrders,
  getOrder,
  cancelOrder,
  confirmReceipt,
  reviewOrder,
  getOrderReview
}

export default orderApi
