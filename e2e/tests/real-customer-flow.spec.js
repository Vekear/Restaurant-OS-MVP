import { expect, test } from '@playwright/test'
import {
  API,
  backendBaseURL,
  seedRealSession,
  apiLogin,
  authHeaders,
  isRealMode,
  pickSellableDish,
} from '../support/real-stack.js'

/**
 * T49 / T42 顾客真实链路：地址、购物车、优惠券（隔离栈 18084 + restaurant_mvp_t49）。
 *
 * 只写测试数据，不修改产品代码；地址与购物车在用例内清理，优惠券按真实业务领取
 * （同一顾客模板只能领一次，因此新注册一个随机顾客验证领取链路）。
 */
const REAL = isRealMode()

async function api(request, token, method, path, data) {
  const response = await request.fetch(`${backendBaseURL()}${API}${path}`, {
    method,
    headers: authHeaders(token),
    data,
  })
  const contentType = response.headers()['content-type'] || ''
  const body = contentType.includes('application/json') ? await response.json() : await response.text()
  return { response, body }
}

async function freshLogin(request, username, password) {
  for (let attempt = 1; attempt <= 4; attempt += 1) {
    const response = await request.post(`${backendBaseURL()}${API}/auth/login`, { data: { username, password } })
    if (response.status() === 200) return (await response.json()).data
    if (response.status() !== 429 || attempt === 4) {
      throw new Error(`freshLogin ${username} HTTP ${response.status()}: ${await response.text()}`)
    }
    await new Promise((resolve) => setTimeout(resolve, 11_000))
  }
  throw new Error('unreachable')
}

test.describe('真实后端 · 顾客地址/购物车/优惠券链路', () => {
  test.skip(!REAL, 'mock 驱动模式：真实顾客链路用例只在 E2E_MOCK=0 时运行')

  test('地址 CRUD：列表 → 新增 → 详情 → 修改 → 删除 → 详情 404', async ({ request }) => {
    const { token } = await apiLogin(request, 'customer', 'customer123')

    const list = await api(request, token, 'GET', '/addresses')
    expect(list.response.status()).toBe(200)
    expect(list.body.code).toBe(0)
    expect(Array.isArray(list.body.data)).toBe(true)

    const created = await api(request, token, 'POST', '/addresses', {
      consignee: 'T49临时',
      phone: '13900004950',
      province: '上海市',
      city: '上海市',
      district: '徐汇区',
      detail: 'T49 临时地址 49 号',
      isDefault: false,
    })
    expect(created.response.status()).toBe(200)
    const addressId = created.body.data.id
    expect(addressId).toBeGreaterThan(0)

    const detail = await api(request, token, 'GET', `/addresses/${addressId}`)
    expect(detail.body.data.consignee).toBe('T49临时')

    const updated = await api(request, token, 'PUT', `/addresses/${addressId}`, {
      consignee: 'T49临时改',
      phone: '13900004950',
      province: '上海市',
      city: '上海市',
      district: '徐汇区',
      detail: 'T49 临时地址 50 号',
      isDefault: false,
    })
    expect(updated.response.status()).toBe(200)
    expect(updated.body.data.consignee).toBe('T49临时改')
    expect(updated.body.data.detail).toContain('50')

    const deleted = await api(request, token, 'DELETE', `/addresses/${addressId}`)
    expect(deleted.response.status()).toBe(200)

    const gone = await api(request, token, 'GET', `/addresses/${addressId}`)
    expect(gone.response.status()).toBe(404)
    expect(gone.body.code).toBe(40401)
  })

  test('购物车：API 增改删与 /cart 页面真实渲染', async ({ request, page }) => {
    const { token, user } = await apiLogin(request, 'customer', 'customer123')
    await api(request, token, 'DELETE', '/cart')

    const dish = await pickSellableDish(request, { minStock: 3 })
    const added = await api(request, token, 'POST', '/cart/items', { dishId: dish.id, quantity: 2 })
    expect(added.response.status()).toBe(200)
    expect(added.body.data.totalQuantity).toBe(2)
    expect(added.body.data.items.some((item) => item.dishId === dish.id)).toBe(true)

    const patched = await api(request, token, 'PATCH', `/cart/items/${dish.id}`, { quantity: 3 })
    expect(patched.body.data.totalQuantity).toBe(3)

    await seedRealSession(page, { token, user })
    await page.goto('/cart', { waitUntil: 'domcontentloaded' })
    await expect(page.locator('.cart-item__name').filter({ hasText: dish.name }).first()).toBeVisible()
    await expect(page.locator('.cart-page__header .page-subtitle')).toContainText('3')

    const removed = await api(request, token, 'DELETE', `/cart/items/${dish.id}`)
    expect(removed.response.status()).toBe(200)
    expect(removed.body.data.totalQuantity).toBe(0)

    await page.reload({ waitUntil: 'domcontentloaded' })
    await expect(page.locator('.el-empty__description')).toContainText('购物车还空着')
    await api(request, token, 'DELETE', '/cart')
  })

  test('优惠券：模板 → 新顾客领取 → 我的券 → 试算抵扣', async ({ request }) => {
    const username = `t49c${Date.now().toString(36).slice(-6)}${Math.floor(Math.random() * 1000)}`
    const phone = `139${String(Math.floor(Math.random() * 1e8)).padStart(8, '0')}`
    const registered = await request.post(`${backendBaseURL()}${API}/auth/register`, {
      data: { username, nickname: 'T49券顾客', phone, password: 't49-test-pass-123' },
    })
    expect(registered.status()).toBe(200)
    const session = await freshLogin(request, username, 't49-test-pass-123')

    const templates = await api(request, session.token, 'GET', '/coupons?page=1&size=5')
    expect(templates.response.status()).toBe(200)
    const claimable = (templates.body.data.list || []).find((item) => item.claimable)
    expect(claimable, '种子优惠券应至少有一张可领取').toBeTruthy()

    const claimed = await api(request, session.token, 'POST', `/coupons/${claimable.id}/claim`)
    expect(claimed.response.status()).toBe(200)
    expect(claimed.body.data.id).toBeGreaterThan(0)

    const mine = await api(request, session.token, 'GET', '/coupons/mine?status=UNUSED&page=1&size=10')
    expect(mine.body.data.list.some((item) => item.id === claimed.body.data.id)).toBe(true)

    const quote = await api(request, session.token, 'POST', '/coupons/quote', {
      merchantId: 1,
      totalAmount: 100,
      userCouponId: claimed.body.data.id,
    })
    expect(quote.response.status()).toBe(200)
    expect(quote.body.data.reachMinOrder).toBe(true)
    expect(quote.body.data.discountAmount).toBeGreaterThan(0)
    expect(quote.body.data.discountAmount).toBeGreaterThan(0)
    expect(quote.body.data.payAmount).toBeLessThan(100)
  })
})
