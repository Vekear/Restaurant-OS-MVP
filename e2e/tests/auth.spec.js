/**
 * 登录 / 注册 E2E（mock 驱动，真后端可复用）
 *
 * 覆盖（T25）：
 *  - 登录页 / 注册页渲染
 *  - 表单校验：必填、账号长度、密码长度、确认密码、手机号格式
 *  - 未登录访问受保护路由 → /login?redirect=...
 *  - 错误口令：统一错误提示且不写入会话
 *  - 登录成功：会话持久化、进入顾客首页
 *  - 登录后回跳 redirect 指定页面
 *
 * 默认 mock 模式（E2E_MOCK 不等于 '0'）由 support/api-mocks.js 在浏览器网络层
 * 返回严格对齐 docs/02-api.md 的响应；真后端模式（E2E_MOCK=0）不注入桩，
 * 后端 /api/v1/public/config 不可达时整组显式 skip（不伪造通过）。
 */
import { test, expect } from '@playwright/test'
import {
  installApiMocks,
  collectApiRequests,
  isRealMode,
  isRealBackendReachable,
  loginViaUi,
  expectToast,
  STORAGE_TOKEN_KEY,
  AUTH_TOKEN
} from '../support/api-mocks.js'

test.describe('登录 / 注册', () => {
  /** @type {ReturnType<typeof collectApiRequests>} */
  let requests
  /** @type {Awaited<ReturnType<typeof installApiMocks>> | null} */
  let mockState = null

  test.beforeEach(async ({ page }) => {
    if (isRealMode() && !(await isRealBackendReachable(page))) {
      test.skip(
        true,
        '真实后端未就绪：GET /api/v1/public/config 不可达。mock 模式请勿设置 E2E_MOCK=0。'
      )
    }
    if (!isRealMode()) {
      mockState = await installApiMocks(page)
    }
    requests = collectApiRequests(page)
  })

  test('登录页渲染账号/密码表单与演示账号入口', async ({ page }) => {
    await page.goto('/login')

    await expect(page.getByRole('heading', { name: '欢迎回来' })).toBeVisible()
    await expect(page.locator('input[autocomplete="username"]')).toBeVisible()
    await expect(page.locator('input[autocomplete="current-password"]')).toBeVisible()
    await expect(page.getByRole('button', { name: '登录', exact: true })).toBeVisible()
    await expect(page.locator('.demo-account').first()).toContainText('顾客')
    await expect(page.getByRole('link', { name: /注册/ }).first()).toBeVisible()
  })

  test('空表单提交显示必填校验', async ({ page }) => {
    await page.goto('/login')
    await page.getByRole('button', { name: '登录', exact: true }).click()

    await expect(page.locator('.el-form-item__error', { hasText: '请输入登录账号' })).toBeVisible()
    await expect(page.locator('.el-form-item__error', { hasText: '请输入密码' })).toBeVisible()
    expect(requests.filter('POST', '/auth/login')).toHaveLength(0)
  })

  test('账号格式与密码长度校验不通过时不发登录请求', async ({ page }) => {
    await page.goto('/login')

    await page.locator('input[autocomplete="username"]').fill('ab')
    await page.locator('input[autocomplete="current-password"]').fill('123456')
    await page.getByRole('button', { name: '登录', exact: true }).click()
    await expect(
      page.locator('.el-form-item__error', { hasText: '账号为 4 - 20 位字母、数字或下划线' })
    ).toBeVisible()

    await page.locator('input[autocomplete="username"]').fill('customer')
    await page.locator('input[autocomplete="current-password"]').fill('123')
    await page.getByRole('button', { name: '登录', exact: true }).click()
    await expect(
      page.locator('.el-form-item__error', { hasText: '密码长度为 6 - 32 个字符' })
    ).toBeVisible()
    expect(requests.filter('POST', '/auth/login')).toHaveLength(0)
  })

  test('注册页确认密码与手机号格式校验', async ({ page }) => {
    await page.goto('/register')

    await expect(page.getByRole('heading', { name: '注册顾客账号' })).toBeVisible()
    await page.locator('input[placeholder="4 - 20 位字母、数字或下划线"]').fill('e2e_user')
    await page.locator('input[placeholder="11 位手机号"]').fill('123')
    await page.locator('input[placeholder="6 - 32 位"]').fill('123456')
    await page.locator('input[placeholder="再次输入密码"]').fill('654321')
    await page.getByRole('button', { name: '创建账号' }).click()

    await expect(
      page.locator('.el-form-item__error', { hasText: '两次输入的密码不一致' })
    ).toBeVisible()
    await expect(
      page.locator('.el-form-item__error', { hasText: '请输入有效的 11 位手机号' })
    ).toBeVisible()
    expect(requests.filter('POST', '/auth/register')).toHaveLength(0)
  })

  test('未登录访问购物车/订单/结算页重定向登录并携带 redirect', async ({ page }) => {
    for (const target of ['/cart', '/orders', '/checkout']) {
      await page.goto(target)
      await expect(page).toHaveURL(/\/login/)
      const url = new URL(page.url())
      expect(url.searchParams.get('redirect'), `${target} 的 redirect 参数`).toBe(target)
      await expect(page.locator('input[autocomplete="username"]')).toBeVisible()
    }
  })

  test('错误口令显示统一错误提示且不写入会话', async ({ page }) => {
    if (mockState) mockState.login = 'invalid'
    await page.goto('/login')
    await page.locator('input[autocomplete="username"]').fill('customer')
    await page.locator('input[autocomplete="current-password"]').fill('wrong-password')

    const responsePromise = page.waitForResponse(
      (response) =>
        response.request().method() === 'POST' && response.url().includes('/api/v1/auth/login')
    )
    await page.getByRole('button', { name: '登录', exact: true }).click()
    const response = await responsePromise

    if (isRealMode() && [404, 500, 501, 502, 503].includes(response.status())) {
      test.skip(true, `真实后端 /auth/login 返回 ${response.status()}（接口未就绪），跳过错误口令断言`)
    }

    expect(response.status()).toBe(401)
    await expectToast(page, '用户名或密码错误')
    await expect(page).toHaveURL(/\/login/)
    const token = await page.evaluate((key) => window.localStorage.getItem(key), STORAGE_TOKEN_KEY)
    expect(token).toBeNull()
  })

  test('正确口令登录成功并持久化会话', async ({ page }) => {
    await page.goto('/login')
    const response = await loginViaUi(page, { username: 'customer', password: 'customer123' })

    expect(response.status()).toBe(200)
    await expect
      .poll(() => new URL(page.url()).pathname, { message: '登录后应进入顾客首页 /' })
      .toBe('/')
    await expect(page.locator('.user-menu__name')).toHaveText('演示顾客')
    await expect(page.locator('.user-menu__role')).toHaveText('顾客')

    const token = await page.evaluate((key) => window.localStorage.getItem(key), STORAGE_TOKEN_KEY)
    expect(token).toBe(AUTH_TOKEN)
    const loginRequest = requests.last('POST', '/auth/login')
    expect(loginRequest?.body).toEqual({ username: 'customer', password: 'customer123' })
  })

  test('从受保护页面跳登录后登录，回跳到原页面', async ({ page }) => {
    await page.goto('/cart')
    await expect(page).toHaveURL(/\/login/)
    expect(new URL(page.url()).searchParams.get('redirect')).toBe('/cart')

    await page.locator('input[autocomplete="username"]').fill('customer')
    await page.locator('input[autocomplete="current-password"]').fill('customer123')
    await page.getByRole('button', { name: '登录', exact: true }).click()

    await expect
      .poll(() => new URL(page.url()).pathname, { message: '登录后应回跳 /cart' })
      .toBe('/cart')
    await expect(page.getByRole('heading', { name: '购物车' })).toBeVisible()
    await expect(page.getByText('购物车还空着')).toBeVisible()
  })
})
