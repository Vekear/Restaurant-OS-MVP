import { http } from './request'
import { IS_MOCK, mockDelay } from '@/mock'
import {
  mockCreateOrder,
  mockFetchOrder,
  mockListOrders,
  mockCancelOrder,
  statusTextOf
} from '@/mock/dinein'
import { normalizePayment } from './payment'
import { createIdempotencyKey } from '@/utils/id'

/**
 * 堂食订单接口（契约 docs/02-api.md §11）。
 * - POST /dine-in/orders                 创建（X-Table-Session 必填 + Idempotency-Key）
 * - GET  /dine-in/orders?statusGroup=    我的堂食订单分页
 * - GET  /dine-in/orders/{id}            详情（含时间线 / 支付）
 * - POST /dine-in/orders/{id}/cancel     取消
 */

const STATUS_TEXT = {
  PENDING_PAYMENT: '待支付',
  PAID: '待接单',
  ACCEPTED: '已接单',
  MAKING: '制作中',
  SERVED: '已出餐',
  COMPLETED: '已完成',
  CANCELLED: '已取消',
  REJECTED: '已拒单'
}

const ACTIVE_STATUSES = ['PENDING_PAYMENT', 'PAID', 'ACCEPTED', 'MAKING', 'SERVED']

export function isActiveStatus(status) {
  return ACTIVE_STATUSES.indexOf(status) !== -1
}

export function statusLabel(status, statusText) {
  return statusText || STATUS_TEXT[status] || status || '未知状态'
}

export function normalizeOrderItem(raw) {
  const item = raw || {}
  return {
    id: item.id !== undefined ? item.id : null,
    dishId: item.dishId !== undefined ? item.dishId : null,
    dishName: item.dishName || item.name || '',
    dishImage: item.dishImage || item.imageUrl || '',
    price: Number(item.price || 0),
    quantity: Number(item.quantity || 0),
    subtotal: Number(item.subtotal || 0)
  }
}

export function normalizeStatusLog(raw) {
  const log = raw || {}
  return {
    fromStatus: log.fromStatus || null,
    toStatus: log.toStatus || '',
    operatorRole: log.operatorRole || '',
    operatorId: log.operatorId !== undefined ? log.operatorId : null,
    remark: log.remark || '',
    createdAt: log.createdAt || ''
  }
}

/** OrderView / OrderDetailView 归一化（字段见契约 §2.6） */
export function normalizeOrder(raw) {
  const item = raw || {}
  const status = item.status || ''
  return {
    id: item.id !== undefined ? item.id : null,
    orderNo: item.orderNo || '',
    orderType: item.orderType || 'DINE_IN',
    status,
    statusText: statusLabel(status, item.statusText),
    payAmount: Number(item.payAmount || 0),
    totalAmount: Number(item.totalAmount || 0),
    discountAmount: Number(item.discountAmount || 0),
    deliveryFee: Number(item.deliveryFee || 0),
    itemCount: Number(item.itemCount || 0),
    tableNo: item.tableNo || '',
    createdAt: item.createdAt || '',
    paidAt: item.paidAt || '',
    finishedAt: item.finishedAt || '',
    merchantName: item.merchantName || '',
    items: Array.isArray(item.items) ? item.items.map(normalizeOrderItem) : [],
    remark: item.remark || '',
    cancelReason: item.cancelReason || '',
    timeoutAt: item.timeoutAt || '',
    payment: item.payment ? normalizePayment(item.payment) : null,
    statusLogs: Array.isArray(item.statusLogs) ? item.statusLogs.map(normalizeStatusLog) : [],
    canPay: item.canPay === true || status === 'PENDING_PAYMENT',
    canCancel: item.canCancel === true || status === 'PENDING_PAYMENT' || status === 'PAID',
    isActive: isActiveStatus(status)
  }
}

/**
 * 创建堂食订单。
 * @param {{items:Array<{dishId:number,quantity:number}>, remark?:string, expectedPayAmount?:number, userCouponId?:number}} payload
 * @param {string} [idempotencyKey] 重试时复用同一 key，避免重复下单
 */
export function createDineInOrder(payload, idempotencyKey) {
  const body = {
    items: (payload.items || []).map((line) => ({
      dishId: line.dishId !== undefined ? line.dishId : line.id,
      quantity: line.quantity
    })),
    remark: payload.remark || '',
    userCouponId: payload.userCouponId !== undefined ? payload.userCouponId : null,
    expectedPayAmount: payload.expectedPayAmount
  }
  if (IS_MOCK) {
    return mockDelay(mockCreateOrder(body), 360).then(normalizeOrder)
  }
  return http
    .post('/dine-in/orders', body, {
      idempotencyKey: idempotencyKey || createIdempotencyKey('order'),
      silent: true,
      loading: true,
      loadingText: '提交订单'
    })
    .then(normalizeOrder)
}

export function fetchDineInOrder(orderId) {
  if (IS_MOCK) {
    return mockDelay(mockFetchOrder(orderId), 200).then(normalizeOrder)
  }
  return http.get(`/dine-in/orders/${orderId}`, {}, { silent: true }).then(normalizeOrder)
}

export function fetchDineInOrders({ statusGroup = 'ALL', page = 1, size = 20 } = {}) {
  if (IS_MOCK) {
    return mockDelay(mockListOrders({ statusGroup }), 220).then((pageData) => ({
      list: (pageData.list || []).map(normalizeOrder),
      total: Number(pageData.total || 0),
      page: Number(pageData.page || 1),
      size: Number(pageData.size || size),
      pages: Number(pageData.pages || 0)
    }))
  }
  return http
    .get('/dine-in/orders', { statusGroup, page, size }, { silent: true })
    .then((payload) => {
      const list = Array.isArray(payload) ? payload : (payload && payload.list) || []
      return {
        list: list.map(normalizeOrder),
        total: Number((payload && payload.total) || list.length),
        page: Number((payload && payload.page) || page),
        size: Number((payload && payload.size) || size),
        pages: Number((payload && payload.pages) || 0)
      }
    })
}

export function cancelDineInOrder(orderId, reason) {
  if (IS_MOCK) {
    return mockDelay(mockCancelOrder(orderId, reason), 260).then(normalizeOrder)
  }
  return http
    .post(`/dine-in/orders/${orderId}/cancel`, { reason: reason || '' }, { silent: true, loading: true })
    .then(normalizeOrder)
}

export function mockStatusText(status) {
  return statusTextOf(status)
}
