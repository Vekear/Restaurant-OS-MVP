import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { STORAGE_KEYS } from '@/utils/storage'

const mocks = vi.hoisted(() => ({
  getCart: vi.fn(),
  addCartItem: vi.fn(),
  updateCartItem: vi.fn(),
  removeCartItem: vi.fn(),
  clearCart: vi.fn()
}))

vi.mock('@/api/cart', () => ({ cartApi: mocks }))

import { useCartStore } from '@/stores/cart'

function cartView(overrides = {}) {
  return {
    items: [
      { dishId: 1, dishName: '辣椒炒肉', price: 28, quantity: 2, subtotal: 56, available: true },
      { dishId: 2, dishName: '小炒黄牛肉', price: 38, quantity: 1, subtotal: 38, available: true }
    ],
    totalQuantity: 3,
    totalAmount: 94,
    invalidCount: 0,
    maxItems: 50,
    ...overrides
  }
}

function loginAsCustomer() {
  localStorage.setItem(STORAGE_KEYS.token, 'fake-token')
  localStorage.setItem(
    STORAGE_KEYS.user,
    JSON.stringify({ id: 9, username: 'customer', nickname: '顾客', role: 'CUSTOMER' })
  )
}

beforeEach(() => {
  localStorage.clear()
  mocks.getCart.mockReset().mockResolvedValue(cartView())
  mocks.addCartItem.mockReset().mockResolvedValue(cartView())
  mocks.updateCartItem.mockReset().mockResolvedValue(cartView())
  mocks.removeCartItem.mockReset().mockResolvedValue(cartView({ items: [], totalQuantity: 0, totalAmount: 0 }))
  mocks.clearCart.mockReset().mockResolvedValue(cartView({ items: [], totalQuantity: 0, totalAmount: 0 }))
  setActivePinia(createPinia())
})

describe('cart store 购物车', () => {
  it('fetchCart 应用服务端返回的数量与金额', async () => {
    loginAsCustomer()
    setActivePinia(createPinia())
    const cart = useCartStore()
    await cart.fetchCart({ force: true })

    expect(mocks.getCart).toHaveBeenCalledTimes(1)
    expect(cart.items).toHaveLength(2)
    expect(cart.totalQuantity).toBe(3)
    expect(cart.totalAmount).toBe(94)
    expect(cart.isEmpty).toBe(false)
    expect(cart.isReady).toBe(true)
  })

  it('addItem 与 updateQuantity 复用接口结果并暴露数量查询', async () => {
    loginAsCustomer()
    setActivePinia(createPinia())
    const cart = useCartStore()

    await cart.addItem(1, 2)
    expect(mocks.addCartItem).toHaveBeenCalledWith(1, 2)
    expect(cart.quantityOf(1)).toBe(2)
    expect(cart.totalAmount).toBe(94)

    await cart.updateQuantity(2, 5)
    expect(mocks.updateCartItem).toHaveBeenCalledWith(2, 5)
  })

  it('removeItem / clear 后购物车为空', async () => {
    loginAsCustomer()
    setActivePinia(createPinia())
    const cart = useCartStore()
    await cart.fetchCart({ force: true })

    await cart.removeItem(1)
    expect(mocks.removeCartItem).toHaveBeenCalledWith(1)
    expect(cart.items).toHaveLength(0)

    await cart.clear()
    expect(mocks.clearCart).toHaveBeenCalledTimes(1)
    expect(cart.totalQuantity).toBe(0)
  })

  it('金额与数量以服务端 CartView 为准，不按 items 本地重算', async () => {
    loginAsCustomer()
    mocks.getCart.mockResolvedValue(cartView({ totalQuantity: 7, totalAmount: 999 }))
    setActivePinia(createPinia())
    const cart = useCartStore()

    await cart.fetchCart({ force: true })

    expect(cart.totalQuantity).toBe(7)
    expect(cart.totalAmount).toBe(999)
    expect(cart.estimatedTotal).toBe(999)
  })

  it('未登录时 addItem 抛出 NEED_LOGIN 且不请求接口', async () => {
    const cart = useCartStore()
    await expect(cart.addItem(1, 1)).rejects.toMatchObject({ code: 'NEED_LOGIN' })
    expect(mocks.addCartItem).not.toHaveBeenCalled()

    await cart.fetchCart({ force: true })
    expect(mocks.getCart).not.toHaveBeenCalled()
    expect(cart.isEmpty).toBe(true)
  })
})
