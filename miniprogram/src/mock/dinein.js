import { MOCK_DISHES } from './menu'

/**
 * 演示模式（VITE_MOCK=true）下的堂食会话 / 订单 / 支付假数据。
 * 目的：无后端时也能完整走一遍「扫码/手输 → 下单 → 模拟支付 → 状态轮询」。
 * 与 docs/02-api.md §10–§12 的响应结构保持一致，便于切换真实接口。
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

/** 制作流转顺序（每次查询详情推进一步，模拟商家端操作） */
const FLOW = ['PAID', 'ACCEPTED', 'MAKING', 'SERVED', 'COMPLETED']
const ACTIVE_STATUSES = ['PENDING_PAYMENT', 'PAID', 'ACCEPTED', 'MAKING', 'SERVED']

const state = {
  session: null,
  orders: [],
  nextOrderId: 5001,
  nextSessionId: 9001,
  nextPayNo: 8001,
  progress: {}
}

function apiError(message, code, statusCode) {
  const error = new Error(message)
  error.name = 'ApiError'
  error.code = code
  error.statusCode = statusCode || 409
  error.type = 'business'
  return error
}

function nowIso(offsetMinutes = 0) {
  return new Date(Date.now() + offsetMinutes * 60000).toISOString()
}

function buildSession(tableId, tableNo, tableName) {
  const session = {
    tableId,
    tableNo,
    tableName: tableName || '',
    merchantId: 1,
    merchantName: '小馆食堂（演示店）',
    capacity: 4,
    sessionId: state.nextSessionId++,
    sessionToken: `mock_ts_${Date.now().toString(36)}`,
    status: 'ACTIVE',
    openedAt: nowIso(),
    expireAt: nowIso(120),
    currentOrderId: null,
    tableStatus: 'OCCUPIED'
  }
  state.session = session
  return session
}

function requireSession() {
  if (!state.session || state.session.status !== 'ACTIVE') {
    throw apiError('桌台会话无效或已关闭，请重新扫码', 40911, 409)
  }
  return state.session
}

function buildOrderItems(items) {
  return (items || []).map((line, index) => {
    const dish = MOCK_DISHES.find((item) => String(item.id) === String(line.dishId)) || {}
    const price = Number(dish.price || 0)
    const quantity = Number(line.quantity) || 1
    return {
      id: index + 1,
      dishId: dish.id || line.dishId,
      dishName: dish.name || '菜品',
      dishImage: dish.image || '',
      price,
      quantity,
      subtotal: Number((price * quantity).toFixed(2))
    }
  })
}

function currentOrder(id) {
  const order = state.orders.find((item) => String(item.id) === String(id))
  if (!order) throw apiError('订单不存在', 40401, 404)
  return order
}

function advance(order) {
  if (!ACTIVE_STATUSES.includes(order.status)) return
  const step = state.progress[order.id] || 0
  state.progress[order.id] = step + 1
  // 支付后每两次查询推进一步，便于演示状态流转
  if (step > 0 && step % 2 === 0) {
    const index = FLOW.indexOf(order.status)
    if (index >= 0 && index < FLOW.length - 1) {
      const next = FLOW[index + 1]
      order.statusLogs.push({
        fromStatus: order.status,
        toStatus: next,
        operatorRole: 'MERCHANT',
        operatorId: 2,
        remark: null,
        createdAt: nowIso()
      })
      order.status = next
      order.statusText = STATUS_TEXT[next]
      if (next === 'COMPLETED') order.finishedAt = nowIso()
    }
  }
}

export function mockScanSession({ p, s }) {
  if (s === 'bad' || s === 'expired') {
    throw apiError('桌台二维码无效或已失效，请让商家重新生成', 40004, 400)
  }
  if (String(p) === '99') {
    throw apiError('该桌台已被占用，请联系服务员', 40910, 409)
  }
  const tableId = Number(p)
  return buildSession(tableId, `A${String(tableId).padStart(2, '0')}`, '演示桌台')
}

export function mockBindSession({ tableNo }) {
  const no = String(tableNo || '').trim().toUpperCase()
  if (!no) throw apiError('请输入桌号', 40001, 400)
  if (no === 'A99') {
    throw apiError('该桌台已被占用，请联系服务员', 40910, 409)
  }
  const tableId = 10 + (Number(no.replace(/\D/g, '')) || 1)
  return buildSession(tableId, no, '演示桌台')
}

export function mockFetchSession() {
  if (!state.session || state.session.status !== 'ACTIVE') {
    throw apiError('当前没有进行中的堂食会话', 40401, 404)
  }
  return state.session
}

export function mockCloseSession() {
  const session = requireSession()
  const hasActiveOrder = state.orders.some(
    (order) => String(order.tableSessionId) === String(session.sessionId) && ACTIVE_STATUSES.includes(order.status)
  )
  if (hasActiveOrder) {
    throw apiError('还有进行中的订单，暂时无法结束用餐', 40916, 409)
  }
  state.session.status = 'CLOSED'
  state.session.tableStatus = 'FREE'
  return { closed: true, tableId: session.tableId, sessionId: session.sessionId, closedAt: nowIso() }
}

export function mockCreateOrder({ items, remark, expectedPayAmount }) {
  const session = requireSession()
  const lines = buildOrderItems(items)
  if (!lines.length) throw apiError('请选择菜品', 40001, 400)
  const totalAmount = Number(lines.reduce((sum, item) => sum + item.subtotal, 0).toFixed(2))
  const payAmount = totalAmount
  if (expectedPayAmount !== undefined && expectedPayAmount !== null) {
    if (Number(expectedPayAmount).toFixed(2) !== payAmount.toFixed(2)) {
      throw apiError('价格已变化，请返回购物车刷新后重试', 40005, 400)
    }
  }
  const id = state.nextOrderId++
  const order = {
    id,
    orderNo: `D${id}`,
    orderType: 'DINE_IN',
    status: 'PENDING_PAYMENT',
    statusText: STATUS_TEXT.PENDING_PAYMENT,
    payAmount,
    totalAmount,
    discountAmount: 0,
    deliveryFee: 0,
    itemCount: lines.reduce((sum, item) => sum + item.quantity, 0),
    tableNo: session.tableNo,
    createdAt: nowIso(),
    paidAt: null,
    finishedAt: null,
    userId: 1001,
    merchantId: session.merchantId,
    merchantName: session.merchantName,
    items: lines,
    tableId: session.tableId,
    tableSessionId: session.sessionId,
    remark: remark || '',
    cancelReason: null,
    timeoutAt: nowIso(15),
    payment: null,
    statusLogs: [
      {
        fromStatus: null,
        toStatus: 'PENDING_PAYMENT',
        operatorRole: 'CUSTOMER',
        operatorId: 1001,
        remark: null,
        createdAt: nowIso()
      }
    ],
    canPay: true,
    canCancel: true,
    canConfirmReceipt: false,
    canReview: false,
    reviewId: null
  }
  state.orders.unshift(order)
  session.currentOrderId = id
  return order
}

export function mockFetchOrder(id) {
  const order = currentOrder(id)
  advance(order)
  order.canPay = order.status === 'PENDING_PAYMENT'
  order.canCancel = ['PENDING_PAYMENT', 'PAID'].includes(order.status)
  return order
}

export function mockListOrders({ statusGroup = 'ALL' } = {}) {
  const list = state.orders.filter((order) => {
    if (statusGroup === 'ACTIVE') return ACTIVE_STATUSES.includes(order.status)
    if (statusGroup === 'FINISHED') return ['COMPLETED', 'CANCELLED', 'REJECTED'].includes(order.status)
    return true
  })
  return { list, total: list.length, page: 1, size: 20, pages: list.length ? 1 : 0 }
}

export function mockCancelOrder(id, reason) {
  const order = currentOrder(id)
  if (!['PENDING_PAYMENT', 'PAID'].includes(order.status)) {
    throw apiError('当前状态不可取消，请联系服务员', 40905, 409)
  }
  order.statusLogs.push({
    fromStatus: order.status,
    toStatus: 'CANCELLED',
    operatorRole: 'CUSTOMER',
    operatorId: 1001,
    remark: reason || null,
    createdAt: nowIso()
  })
  order.status = 'CANCELLED'
  order.statusText = STATUS_TEXT.CANCELLED
  order.cancelReason = reason || ''
  order.canPay = false
  order.canCancel = false
  return order
}

export function mockCreatePayment(orderId) {
  const order = currentOrder(orderId)
  if (order.status !== 'PENDING_PAYMENT') {
    throw apiError('订单状态已变化，请刷新后重试', 40905, 409)
  }
  const payNo = `P${state.nextPayNo++}`
  return {
    payNo,
    orderId: order.id,
    orderType: order.orderType,
    orderNo: order.orderNo,
    orderAmount: order.payAmount,
    paidAmount: Math.min(0.01, order.payAmount),
    channel: 'MOCK',
    mock: true,
    mockHint: `订单金额 ¥${order.payAmount.toFixed(2)}；模拟支付扣款 ¥0.01（演示环境）`,
    expireAt: order.timeoutAt
  }
}

export function mockPayOrder(payNo) {
  const id = Number(String(payNo).replace(/\D/g, '')) - 8000
  const order = state.orders.find((item) => item.id === id) || state.orders[0]
  if (!order) throw apiError('支付单不存在', 40401, 404)
  if (order.status === 'PENDING_PAYMENT') {
    order.statusLogs.push({
      fromStatus: 'PENDING_PAYMENT',
      toStatus: 'PAID',
      operatorRole: 'CUSTOMER',
      operatorId: 1001,
      remark: '模拟支付成功',
      createdAt: nowIso()
    })
    order.status = 'PAID'
    order.statusText = STATUS_TEXT.PAID
    order.paidAt = nowIso()
    order.canPay = false
    order.canCancel = true
  }
  const payment = {
    payNo,
    orderId: order.id,
    orderType: order.orderType,
    orderNo: order.orderNo,
    orderAmount: order.payAmount,
    paidAmount: Math.min(0.01, order.payAmount),
    channel: 'MOCK',
    status: 'SUCCESS',
    transactionNo: `MOCK${payNo}`,
    paidAt: nowIso(),
    callbackAt: nowIso(),
    expireAt: order.timeoutAt,
    createdAt: nowIso()
  }
  order.payment = payment
  return payment
}

export function statusTextOf(status) {
  return STATUS_TEXT[status] || status || ''
}
