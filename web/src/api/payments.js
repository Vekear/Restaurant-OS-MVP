/**
 * 模拟支付接口（docs/02-api.md §12）
 * 金额语义：orderAmount = 订单真实应付金额；paidAmount = min(0.01, orderAmount)（演示扣款）。
 */
import { http } from './http'

/**
 * POST /payments —— 创建模拟支付单（幂等；已有 PENDING 单会直接返回）
 * @param {number} orderId
 * @param {'TAKEOUT'|'DINE_IN'} [orderType]
 * @param {string} [idempotencyKey]
 */
export function createPayment(orderId, orderType = 'TAKEOUT', idempotencyKey) {
  return http.post(
    '/payments',
    { orderId, orderType },
    {
      silent: true,
      headers: idempotencyKey ? { 'Idempotency-Key': idempotencyKey } : undefined
    }
  )
}

/** GET /payments/{payNo} */
export function getPayment(payNo) {
  return http.get(`/payments/${payNo}`, { silent: true })
}

/**
 * POST /payments/{payNo}/mock-pay —— 触发模拟支付成功（幂等）
 * @param {string} payNo
 * @param {string} [idempotencyKey]
 */
export function mockPay(payNo, idempotencyKey) {
  return http.post(`/payments/${payNo}/mock-pay`, null, {
    silent: true,
    headers: idempotencyKey ? { 'Idempotency-Key': idempotencyKey } : undefined
  })
}

export const paymentApi = {
  createPayment,
  getPayment,
  mockPay
}

export default paymentApi
