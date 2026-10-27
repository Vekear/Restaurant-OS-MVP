import { http } from './request'
import { IS_MOCK, mockDelay } from '@/mock'
import {
  mockScanSession,
  mockBindSession,
  mockFetchSession,
  mockCloseSession
} from '@/mock/dinein'

/**
 * 堂食会话接口（契约 docs/02-api.md §10）。
 * - POST /dine-in/scan        { p, s }           扫码绑定
 * - POST /dine-in/bind        { tableNo, s? }    手输桌号兜底
 * - GET  /dine-in/session                        当前会话（自动带 X-Table-Session）
 * - POST /dine-in/session/close                  结束用餐
 */

/** DineInSessionView 归一化（字段见契约 §2.7） */
export function normalizeSession(raw) {
  const item = raw || {}
  return {
    tableId: item.tableId !== undefined ? item.tableId : null,
    tableNo: item.tableNo || '',
    tableName: item.tableName || '',
    merchantId: item.merchantId !== undefined ? item.merchantId : null,
    merchantName: item.merchantName || '',
    capacity: Number(item.capacity || 0),
    sessionId: item.sessionId !== undefined ? item.sessionId : null,
    sessionToken: item.sessionToken || '',
    status: item.status || 'ACTIVE',
    openedAt: item.openedAt || '',
    expireAt: item.expireAt || '',
    currentOrderId: item.currentOrderId !== undefined ? item.currentOrderId : null,
    tableStatus: item.tableStatus || 'OCCUPIED'
  }
}

/** 扫码绑定桌台 */
export function scanTable({ p, s }) {
  if (IS_MOCK) {
    return mockDelay(mockScanSession({ p, s }), 320).then(normalizeSession)
  }
  return http
    .post('/dine-in/scan', { p: String(p), s: String(s) }, { silent: true })
    .then(normalizeSession)
}

/** 手输桌号绑定（可选携带签名） */
export function bindTable({ tableNo, s }) {
  const body = { tableNo: String(tableNo || '').trim().toUpperCase() }
  if (s) body.s = String(s)
  if (IS_MOCK) {
    return mockDelay(mockBindSession(body), 320).then(normalizeSession)
  }
  return http.post('/dine-in/bind', body, { silent: true }).then(normalizeSession)
}

/** 查询当前会话（无会话时后端返回 40401） */
export function fetchTableSession() {
  if (IS_MOCK) {
    return mockDelay(mockFetchSession(), 160).then(normalizeSession)
  }
  return http.get('/dine-in/session', {}, { silent: true }).then(normalizeSession)
}

/** 结束用餐（存在进行中订单时后端返回 40916） */
export function closeTableSession() {
  if (IS_MOCK) {
    return mockDelay(mockCloseSession(), 260)
  }
  return http.post('/dine-in/session/close', {}, { silent: true })
}

const FRIENDLY_MESSAGES = {
  40004: '二维码无效或已失效，请让商家重新生成二维码',
  40303: '该商家暂不可用，请联系服务员',
  40401: '没有找到桌台或会话，请重新扫码',
  40910: '该桌台正在使用中，请联系服务员安排',
  40911: '桌台会话已失效，请重新扫码入座',
  40916: '还有进行中的订单，暂时无法结束用餐',
  40917: '您在其他桌台有进行中的订单，请先完成或取消',
  40905: '订单已超时或已取消，请重新下单',
  40901: '部分菜品库存不足，请调整后重试',
  40908: '商家休息中，暂不接单',
  40913: '该订单已完成支付，请查看订单状态'
}

/** 把后端错误码转成用户可读文案（保留服务端 message 优先） */
export function friendlyMessage(error, fallback = '操作失败，请稍后重试') {
  if (!error) return fallback
  const code = Number(error.code)
  if (code && FRIENDLY_MESSAGES[code]) return FRIENDLY_MESSAGES[code]
  return error.message || fallback
}
