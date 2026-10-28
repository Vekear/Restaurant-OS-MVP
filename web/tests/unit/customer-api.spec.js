import { describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => ({
  get: vi.fn(() => Promise.resolve({})),
  post: vi.fn(() => Promise.resolve({})),
  put: vi.fn(() => Promise.resolve({})),
  patch: vi.fn(() => Promise.resolve({})),
  delete: vi.fn(() => Promise.resolve({}))
}))

vi.mock('@/api/http', () => ({ http: mocks, default: mocks }))

import {
  createAddress,
  deleteAddress,
  getAddress,
  listAddresses,
  updateAddress
} from '@/api/addresses'
import {
  addCartItem,
  clearCart,
  getCart,
  removeCartItem,
  updateCartItem
} from '@/api/cart'
import { claimCoupon, listCoupons, listMyCoupons, quoteCoupon } from '@/api/coupons'

describe('顾客端接口路径与 docs/02-api.md 一致', () => {
  it('地址 §5：list/create/get/update/delete 使用 /addresses', async () => {
    const payload = { consignee: '李小明', phone: '13900000003' }
    await listAddresses()
    await createAddress(payload)
    await getAddress(7)
    await updateAddress(7, payload)
    await deleteAddress(7)

    expect(mocks.get).toHaveBeenNthCalledWith(1, '/addresses', { silent: true })
    expect(mocks.post).toHaveBeenCalledWith('/addresses', payload, { silent: true })
    expect(mocks.get).toHaveBeenNthCalledWith(2, '/addresses/7', { silent: true })
    expect(mocks.put).toHaveBeenCalledWith('/addresses/7', payload, { silent: true })
    expect(mocks.delete).toHaveBeenCalledWith('/addresses/7', { silent: true })
  })

  it('购物车 §7：写操作命中 /cart/items 并返回服务端视图', async () => {
    await getCart()
    await addCartItem(12, 3)
    await updateCartItem(12, 2)
    await removeCartItem(12)
    await clearCart()

    expect(mocks.get).toHaveBeenCalledWith('/cart', { silent: true })
    expect(mocks.post).toHaveBeenCalledWith('/cart/items', { dishId: 12, quantity: 3 }, { silent: true })
    expect(mocks.patch).toHaveBeenCalledWith('/cart/items/12', { quantity: 2 }, { silent: true })
    expect(mocks.delete).toHaveBeenNthCalledWith(1, '/cart/items/12', { silent: true })
    expect(mocks.delete).toHaveBeenNthCalledWith(2, '/cart', { silent: true })
  })

  it('优惠券 §8：列表/领取/我的券/试算', async () => {
    const quotePayload = { merchantId: 1, totalAmount: 64, userCouponId: 1 }
    await listCoupons({ amount: 64, page: 1, size: 5 })
    await claimCoupon(1)
    await listMyCoupons({ status: 'UNUSED', amount: 64 })
    await quoteCoupon(quotePayload)

    expect(mocks.get).toHaveBeenNthCalledWith(1, '/coupons', {
      params: { amount: 64, page: 1, size: 5 },
      silent: true
    })
    expect(mocks.post).toHaveBeenNthCalledWith(1, '/coupons/1/claim', null, { silent: true })
    expect(mocks.get).toHaveBeenNthCalledWith(2, '/coupons/mine', {
      params: { status: 'UNUSED', amount: 64 },
      silent: true
    })
    expect(mocks.post).toHaveBeenNthCalledWith(2, '/coupons/quote', quotePayload, { silent: true })
  })
})
