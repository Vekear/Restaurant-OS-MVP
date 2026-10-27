/**
 * 幂等键生成：写操作（下单、支付）必须携带 Idempotency-Key（8–64 字符）。
 * 同一用户操作在重试时应复用同一个 key（由页面持有），避免重复下单/重复扣款。
 */
export function createIdempotencyKey(prefix = 'idem') {
  const time = Date.now().toString(36)
  const random = Math.random().toString(36).slice(2, 10)
  return `${prefix}-${time}-${random}`
}
