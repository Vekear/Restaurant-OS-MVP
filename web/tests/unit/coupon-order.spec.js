import { beforeEach, describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => ({
  get: vi.fn(() => Promise.resolve({})),
  post: vi.fn(() => Promise.resolve({})),
  put: vi.fn(() => Promise.resolve({})),
  patch: vi.fn(() => Promise.resolve({})),
  delete: vi.fn(() => Promise.resolve({}))
}))

vi.mock('@/api/http', () => ({ http: mocks, default: mocks }))

import { createOrder, getOrder } from '@/api/orders'
import { createPayment, mockPay } from '@/api/payments'
import { quoteCoupon } from '@/api/coupons'
import { createIdempotencyGuard } from '@/utils/idempotency'

/**
 * T59 回归：领券 → 带 userCouponId 试算 → 带券下单 → 模拟支付 0.01。
 * 这里锁定前端请求形状（路径 / 幂等头 / 券与应付金额字段），真实链路由 E2E 覆盖。
 */
describe('T59 带券下单与模拟支付请求形状', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('createOrder 携带用户券 id 与预期应付金额，并复用同一个幂等键', async () => {
    const guard = createIdempotencyGuard('checkout')
    const payload = {
      merchantId: 1,
      addressId: 3,
      items: [
        { dishId: 1, quantity: 2 },
        { dishId: 3, quantity: 2 },
        { dishId: 7, quantity: 1 }
      ],
      userCouponId: 5,
      remark: '少辣',
      expectedPayAmount: 115
    }
    const key = guard.keyFor(payload)
    expect(key.length).toBeGreaterThanOrEqual(8)

    await createOrder(payload, key)
    await createOrder(payload, guard.keyFor(payload))

    expect(mocks.post).toHaveBeenNthCalledWith(1, '/orders', payload, {
      silent: true,
      headers: { 'Idempotency-Key': key }
    })
    // 同一笔提交（内容未变）重试时复用幂等键
    expect(mocks.post).toHaveBeenNthCalledWith(2, '/orders', payload, {
      silent: true,
      headers: { 'Idempotency-Key': key }
    })
  })

  it('quote 用 user_coupon.id 试算，返回服务端抵扣后的应付金额', async () => {
    mocks.post.mockResolvedValueOnce({
      data: { totalAmount: 120, deliveryFee: 5, discountAmount: 10, payAmount: 115, userCoupon: { id: 5 } }
    })
    const quote = await quoteCoupon({ merchantId: 1, totalAmount: 120, userCouponId: 5 })

    expect(mocks.post).toHaveBeenCalledWith(
      '/coupons/quote',
      { merchantId: 1, totalAmount: 120, userCouponId: 5 },
      { silent: true }
    )
    expect(quote.data.payAmount).toBe(115)
  })

  it('支付单与模拟支付路径正确，且都带幂等键', async () => {
    await createPayment(2, 'TAKEOUT', 'pay-key-0001')
    await mockPay('P202610282000586929420', 'pay-key-0002')

    expect(mocks.post).toHaveBeenNthCalledWith(
      1,
      '/payments',
      { orderId: 2, orderType: 'TAKEOUT' },
      { silent: true, headers: { 'Idempotency-Key': 'pay-key-0001' } }
    )
    expect(mocks.post).toHaveBeenNthCalledWith(2, '/payments/P202610282000586929420/mock-pay', null, {
      silent: true,
      headers: { 'Idempotency-Key': 'pay-key-0002' }
    })
  })

  it('支付后订单详情按服务端字段展示：PAID + 实付 115 + 模拟扣款 0.01', async () => {
    mocks.get.mockResolvedValueOnce({
      data: {
        id: 2,
        status: 'PAID',
        totalAmount: 120,
        deliveryFee: 5,
        discountAmount: 10,
        payAmount: 115,
        payment: { status: 'SUCCESS', paidAmount: 0.01, orderAmount: 115 },
        canPay: false
      }
    })
    const detail = await getOrder(2)

    expect(mocks.get).toHaveBeenCalledWith('/orders/2', { silent: true })
    expect(detail.data).toMatchObject({
      status: 'PAID',
      payAmount: 115,
      payment: { status: 'SUCCESS', paidAmount: 0.01, orderAmount: 115 },
      canPay: false
    })
  })
})
