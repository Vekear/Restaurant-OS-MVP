import { readStorage, writeStorage, removeStorage } from './storage'

/**
 * 堂食会话本地持久化（契约 §10：scan/bind 返回 DineInSessionView，sessionToken 用于
 * 后续堂食接口的 `X-Table-Session` 请求头）。
 *
 * 单独放在 utils 层，供 request.js 读取请求头，避免 store ↔ api 循环依赖。
 */
export const TABLE_SESSION_KEY = 'restaurant_mp_table_session_v1'

/** 会话视为有效的状态（ACTIVE；服务端过期时间另行判断） */
const ACTIVE_STATUS = 'ACTIVE'

export function getStoredTableSession() {
  const value = readStorage(TABLE_SESSION_KEY, null)
  if (!value || typeof value !== 'object') return null
  return value
}

export function saveTableSession(session) {
  if (!session || typeof session !== 'object') return
  writeStorage(TABLE_SESSION_KEY, session)
}

export function clearStoredTableSession() {
  removeStorage(TABLE_SESSION_KEY)
}

export function getTableSessionToken() {
  const session = getStoredTableSession()
  return session && session.sessionToken ? String(session.sessionToken) : ''
}

/** 会话是否仍可下单：ACTIVE 且未过期（expireAt 缺失时按有效处理，由服务端兜底） */
export function isTableSessionUsable(session) {
  if (!session || !session.sessionToken) return false
  if (session.status && session.status !== ACTIVE_STATUS) return false
  if (session.expireAt) {
    const expire = new Date(String(session.expireAt)).getTime()
    if (!Number.isNaN(expire) && expire <= Date.now()) return false
  }
  return true
}
