/**
 * localStorage 安全读写（隐私模式 / 配额异常时不抛错）。
 */

export const STORAGE_KEYS = {
  token: 'restaurant.token',
  user: 'restaurant.user',
  sidebarCollapsed: 'restaurant.sidebarCollapsed'
}

export function readRaw(key) {
  try {
    return window.localStorage.getItem(key)
  } catch (error) {
    console.warn('[storage] 读取失败', key, error)
    return null
  }
}

export function writeRaw(key, value) {
  try {
    window.localStorage.setItem(key, value)
    return true
  } catch (error) {
    console.warn('[storage] 写入失败', key, error)
    return false
  }
}

export function removeRaw(key) {
  try {
    window.localStorage.removeItem(key)
  } catch (error) {
    console.warn('[storage] 删除失败', key, error)
  }
}

export function readJSON(key, fallback = null) {
  const raw = readRaw(key)
  if (!raw) return fallback
  try {
    return JSON.parse(raw)
  } catch (error) {
    console.warn('[storage] JSON 解析失败，已清理', key, error)
    removeRaw(key)
    return fallback
  }
}

export function writeJSON(key, value) {
  return writeRaw(key, JSON.stringify(value))
}

export function readBoolean(key, fallback = false) {
  const raw = readRaw(key)
  if (raw === null) return fallback
  return raw === 'true'
}

export function writeBoolean(key, value) {
  return writeRaw(key, value ? 'true' : 'false')
}
