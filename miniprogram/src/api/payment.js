import { http } from './request'
import { IS_MOCK, mockDelay } from '@/mock'
import { mockCreatePayment, mockPayOrder } from '@/mock/dinein'
import { createIdempotencyKey } from '@/utils/id'

/**
 * 模拟支付接口（契约 docs/02-api.md §12）。
 * - POST /payments                { orderId, orderType }  创建支付单（Idempotency-Key）
 * - GET  /payments/{payNo}                                查询支付单
 * - POST /payments/{payNo}/mock-pay                       发起模拟支付（Idempotency-Key）
 *
 * 金额口径（brief §4.3）：页面展示 `orderAmount` 为订单真实金额；
 * 实际扣款 `paidAmount = min(0.01, orderAmount)`，由后端计算并回传。
 */

/** PaymentView / PaymentCreateView 归一化（字段见契约 §2.6） */
export function normalizePayment(raw) {
  const item = raw || {}
  return {
    payNo: item.payNo || '',
    orderId: item.orderId !== undefined ? item.orderId : null,
    orderType: item.orderType || 'DINE_IN',
    orderNo: item.orderNo || '',
    orderAmount: Number(item.orderAmount || 0),
    paidAmount: Number(item.paidAmount || 0),
    channel: item.channel || 'MOCK',
    status: item.status || 'PENDING',
    transactionNo: item.transactionNo || '',
    paidAt: item.paidAt || '',
    callbackAt: item.callbackAt || '',
    expireAt: item.expireAt || '',
    createdAt: item.createdAt || '',
    mock: item.mock === true,
    mockHint: item.mockHint || ''
  }
}

export function createPayment({ orderId, orderType = 'DINE_IN' }, idempotencyKey) {
  if (IS_MOCK) {
    return mockDelay(mockCreatePayment(orderId), 320).then(normalizePayment)
  }
  return http
    .post(
      '/payments',
      { orderId, orderType },
      {
        idempotencyKey: idempotencyKey || createIdempotencyKey('pay'),
        silent: true,
        loading: true,
        loadingText: '创建支付单'
      }
    )
    .then(normalizePayment)
}

export function fetchPayment(payNo) {
  if (IS_MOCK) {
    return mockDelay(mockPayOrder(payNo), 200).then(normalizePayment)
  }
  return http.get(`/payments/${payNo}`, {}, { silent: true }).then(normalizePayment)
}

/** 发起模拟支付：后端生成 transactionNo 并自打回调，返回 SUCCESS 的 PaymentView */
export function mockPay(payNo) {
  if (IS_MOCK) {
    return mockDelay(mockPayOrder(payNo), 420).then(normalizePayment)
  }
  return http
    .post(
      `/payments/${payNo}/mock-pay`,
      {},
      {
        idempotencyKey: createIdempotencyKey('mockpay'),
        silent: true,
        loading: true,
        loadingText: '支付处理中'
      }
    )
    .then(normalizePayment)
}
