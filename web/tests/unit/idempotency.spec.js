import { describe, expect, it } from 'vitest'
import {
  createIdempotencyGuard,
  createIdempotencyKey,
  requestSignature
} from '@/utils/idempotency'

describe('idempotency 幂等键工具（docs/02-api.md §0.5）', () => {
  it('生成的幂等键长度满足 8–64 字符', () => {
    const key = createIdempotencyKey('order')
    expect(typeof key).toBe('string')
    expect(key.length).toBeGreaterThanOrEqual(8)
    expect(key.length).toBeLessThanOrEqual(64)
    expect(createIdempotencyKey()).not.toBe(createIdempotencyKey())
  })

  it('同一请求体复用同一个 key，内容变化后换新 key', () => {
    const guard = createIdempotencyGuard('order')
    const payload = { merchantId: 1, items: [{ dishId: 2, quantity: 3 }] }
    const first = guard.keyFor(payload)
    expect(guard.keyFor(payload)).toBe(first)

    const second = guard.keyFor({ ...payload, remark: '不要辣' })
    expect(second).not.toBe(first)
  })

  it('reset 之后重新生成 key，签名与字段顺序无关', () => {
    const guard = createIdempotencyGuard('pay')
    const key = guard.keyFor({ a: 1, b: 2 })
    guard.reset()
    expect(guard.keyFor({ a: 1, b: 2 })).not.toBe(key)
    expect(requestSignature({ a: 1, b: 2 })).toBe(requestSignature({ b: 2, a: 1 }))
  })
})
