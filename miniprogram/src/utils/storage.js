/**
 * 本地存储薄封装：统一 try/catch，避免小程序存储异常中断页面逻辑。
 */

export function readStorage(key, fallback = null) {
  try {
    const value = uni.getStorageSync(key)
    if (value === '' || value === undefined || value === null) return fallback
    return value
  } catch (error) {
    console.warn('[storage] 读取失败', key, error)
    return fallback
  }
}

export function writeStorage(key, value) {
  try {
    uni.setStorageSync(key, value)
    return true
  } catch (error) {
    console.warn('[storage] 写入失败', key, error)
    return false
  }
}

export function removeStorage(key) {
  try {
    uni.removeStorageSync(key)
  } catch (error) {
    console.warn('[storage] 删除失败', key, error)
  }
}
