/**
 * 真实后端 E2E：登录与 RBAC（T19）
 *
 * 运行：`E2E_MOCK=0 npx playwright test tests/real-auth-rbac.spec.js`
 * 前置：本地栈已启动（scripts/dev-local.ps1 或等价的 8080 + 5173）。
 *
 * 覆盖：
 *  - 顾客 UI 登录真实后端：token/用户持久化、进入顾客首页
 *  - 未登录访问受保护路由 → /login?redirect=...
 *  - 顾客 token 访问 /admin/dashboard → 403 页；商家 token 同理
 *  - 商家 token 可进入商家订单页
 *  - API：无 token / 篡改 JWT → 401；顾客 token 调商家接口 → 403
 */
import { test, expect } from '@playwright/test'
import {
  API,
  DEMO,
  apiLogin,
  authHeaders,
  backendBaseURL,
  isRealMode,
  loginViaRealUi,
  seedRealSession
} from '../support/real-stack.js'

test.describe('真实后端 · 登录与 RBAC', () => {
  test.skip(!isRealMode(), '真实后端用例：设置 E2E_MOCK=0 且本地栈在线后运行')

  test.beforeEach(async ({ request }) => {
    const health = await request.get(`${backendBaseURL()}/actuator/health`)
    test.skip(health.status() !== 200, '本地后端未就绪：/actuator/health 非 200')
  })

  test('顾客 UI 登录真实后端：会话持久化并进入首页', async ({ page }) => {
    const body = await loginViaRealUi(page, DEMO.customer)
    expect(body.code).toBe(0)
    expect(body.data.user.username).toBe('customer')

    await expect(page).toHaveURL(/\/$/)
    const token = await page.evaluate(() => window.localStorage.getItem('restaurant.token'))
    expect(token, '登录后应写入 restaurant.token').toBeTruthy()
    const user = await page.evaluate(() =>
      JSON.parse(window.localStorage.getItem('restaurant.user') || 'null')
    )
    expect(user?.username).toBe('customer')
    expect(user?.role).toBe('CUSTOMER')

    await page.goto('/')
    await expect(page.locator('.user-menu__name')).toContainText('演示顾客')
    await expect(page.locator('.user-menu__role')).toContainText('顾客')
  })

  test('未登录访问 /cart 重定向登录并带 redirect', async ({ page }) => {
    await page.goto('/cart')
    await expect(page).toHaveURL(/\/login\?/)
    expect(new URL(page.url()).searchParams.get('redirect')).toBe('/cart')
    await expect(page.getByRole('heading', { name: '欢迎回来' })).toBeVisible()
  })

  test('顾客/商家 token 访问 /admin/dashboard 均被 403 拦截', async ({ page, request }) => {
    const customer = await apiLogin(request, DEMO.customer.username, DEMO.customer.password)
    await seedRealSession(page, customer)
    await page.goto('/admin/dashboard')
    await expect(page).toHaveURL(/\/403(\?|$)/)

    const merchant = await apiLogin(request, DEMO.merchant.username, DEMO.merchant.password)
    await seedRealSession(page, merchant)
    await page.goto('/admin/dashboard')
    await expect(page).toHaveURL(/\/403(\?|$)/)
  })

  test('商家 token 可进入商家订单页并加载真实订单列表', async ({ page, request }) => {
    const merchant = await apiLogin(request, DEMO.merchant.username, DEMO.merchant.password)
    await seedRealSession(page, merchant)
    await page.goto('/merchant/orders')
    await expect(page.getByRole('heading', { name: '外卖订单' })).toBeVisible()
    // 真实 GET /merchant/orders 必须返回 200（空列表也算通过）
    await expect(page.locator('.orders__count')).toContainText('共')
  })

  test('API 认证：缺失 token / 篡改 JWT → 401，顾客 token 调商家接口 → 403', async ({
    request
  }) => {
    const noToken = await request.get(`${backendBaseURL()}${API}/orders`)
    expect(noToken.status()).toBe(401)

    const customer = await apiLogin(request, DEMO.customer.username, DEMO.customer.password)
    const tampered = `${customer.token.slice(0, -2)}xx`
    const badToken = await request.get(`${backendBaseURL()}${API}/orders`, {
      headers: authHeaders(tampered)
    })
    expect(badToken.status()).toBe(401)

    const forbidden = await request.get(`${backendBaseURL()}${API}/merchant/orders`, {
      headers: authHeaders(customer.token)
    })
    expect(forbidden.status()).toBe(403)
  })
})
