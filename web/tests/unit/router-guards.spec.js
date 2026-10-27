import { beforeEach, describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => ({
  auth: {
    isLoggedIn: false,
    user: null,
    role: null,
    homePath: '/',
    token: '',
    fetchProfile: vi.fn(),
    clearSession: vi.fn()
  }
}))

vi.mock('@/stores/auth', () => ({ useAuthStore: () => mocks.auth }))

import { createAuthBeforeEach, createTitleAfterEach } from '@/router/guards'

function route(overrides = {}) {
  return {
    fullPath: '/',
    query: {},
    meta: {},
    ...overrides
  }
}

beforeEach(() => {
  mocks.auth.isLoggedIn = false
  mocks.auth.user = null
  mocks.auth.role = null
  mocks.auth.homePath = '/'
  mocks.auth.fetchProfile.mockReset()
  mocks.auth.clearSession.mockReset()
})

describe('router guards 路由守卫', () => {
  it('未登录访问受保护页面跳转登录并携带 redirect', async () => {
    const guard = createAuthBeforeEach()
    const result = await guard(route({ fullPath: '/customer/orders', meta: { roles: ['CUSTOMER'] } }))
    expect(result).toEqual({ name: 'login', query: { redirect: '/customer/orders' } })
  })

  it('角色不匹配跳转 403 并记录来源路径', async () => {
    mocks.auth.isLoggedIn = true
    mocks.auth.user = { id: 1, role: 'CUSTOMER' }
    mocks.auth.role = 'CUSTOMER'

    const guard = createAuthBeforeEach()
    const result = await guard(route({ fullPath: '/admin/users', meta: { roles: ['ADMIN'] } }))
    expect(result).toEqual({ name: 'forbidden', query: { from: '/admin/users' } })
  })

  it('public 页面直接放行；guestOnly 且已登录跳回角色首页', async () => {
    const guard = createAuthBeforeEach()
    expect(await guard(route({ fullPath: '/menu', meta: { public: true } }))).toBe(true)

    mocks.auth.isLoggedIn = true
    mocks.auth.user = { id: 1, role: 'MERCHANT' }
    mocks.auth.role = 'MERCHANT'
    mocks.auth.homePath = '/merchant/dashboard'

    expect(await guard(route({ fullPath: '/login', meta: { public: true, guestOnly: true } }))).toBe(
      '/merchant/dashboard'
    )
    expect(
      await guard(
        route({ fullPath: '/register', query: { redirect: '/orders' }, meta: { public: true, guestOnly: true } })
      )
    ).toBe('/orders')
  })

  it('有 token 缺 user 时先拉取资料，失败则清会话并跳登录', async () => {
    mocks.auth.isLoggedIn = true
    mocks.auth.user = null
    mocks.auth.role = 'CUSTOMER'
    mocks.auth.fetchProfile.mockImplementation(async () => {
      mocks.auth.user = { id: 1, role: 'CUSTOMER' }
      return mocks.auth.user
    })

    const guard = createAuthBeforeEach()
    expect(await guard(route({ fullPath: '/menu', meta: { public: true } }))).toBe(true)
    expect(mocks.auth.fetchProfile).toHaveBeenCalledTimes(1)
  })

  it('afterEach 写入页面标题', () => {
    createTitleAfterEach()(route({ meta: { title: '订单管理' } }))
    expect(document.title).toContain('订单管理')
    expect(document.title).toContain('拾味点餐')
  })
})
