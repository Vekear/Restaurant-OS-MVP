/**
 * 幂等键工具（docs/02-api.md §0.5）
 * 同一次业务提交的所有重试必须复用同一个 key；请求体变化时应更换 key（否则服务端返回 40904）。
 */

let sequence = 0

/** 生成 8–64 字符的幂等键（优先 UUID v4） */
export function createIdempotencyKey(prefix = 'web') {
  if (globalThis.crypto?.randomUUID) return globalThis.crypto.randomUUID()
  sequence += 1
  return `${prefix}-${Date.now()}-${sequence}-${Math.random().toString(16).slice(2, 10)}`
}

/**
 * 按请求内容生成稳定签名，用于判断「是否同一笔提交」。
 * 注意：仅用于前端决定是否续用幂等键，不参与签名/安全校验。
 * @param {unknown} value
 */
export function requestSignature(value) {
  try {
    return JSON.stringify(value, Object.keys(value || {}).sort())
  } catch {
    return String(value)
  }
}

/**
 * 幂等键管理器：内容不变则复用，内容变化则换新。
 * @param {string} prefix
 */
export function createIdempotencyGuard(prefix = 'web') {
  let currentKey = ''
  let currentSignature = ''

  return {
    /** 取得本次提交应使用的 key */
    keyFor(payload) {
      const signature = requestSignature(payload)
      if (!currentKey || signature !== currentSignature) {
        currentKey = createIdempotencyKey(prefix)
        currentSignature = signature
      }
      return currentKey
    },
    /** 业务成功/失败终态后调用，下一次提交换新 key */
    reset() {
      currentKey = ''
      currentSignature = ''
    }
  }
}
