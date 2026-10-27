/**
 * 真后端 E2E 辅助（T19）
 *
 * 仅在 `E2E_MOCK=0` 时由 real-*.spec.js 使用；所有请求都打真实后端
 * （默认 http://127.0.0.1:8080，经 Vite 代理 /api/v1），不注入任何网络桩。
 *
 * 约定：
 * - 测试数据带 `T19 E2E` 前缀/备注，订单通过取消或完成进入终态，不删除历史；
 * - 登录成功后统一使用 `Authorization: Bearer <token>`；
 * - 库存/订单断言一律以真实接口返回为准，禁止用 UI 文案伪造。
 */
import { expect } from '@playwright/test'
import { mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

export const API = '/api/v1'
export const DEMO = Object.freeze({
  admin: { username: 'admin', password: 'admin123' },
  merchant: { username: 'merchant', password: 'merchant123' },
  customer: { username: 'customer', password: 'customer123' }
})

/**
 * 登录限流规避：后端对 /auth/login 实施 IP 级 5 次/分钟限流，
 * 而 E2E 每条用例都直接登录会瞬间打满阈值。这里把 token 缓存到
 * e2e/.cache/e2e-tokens.json（已被 .gitignore 忽略），30 分钟内复用；
 * 命中缓存前先用 /auth/me 验证 token 仍然有效，失效则重新登录。
 * 遇到 429 时按限流窗口退避重试（不伪造成功）。
 */
const TOKEN_CACHE_FILE = join(dirname(fileURLToPath(import.meta.url)), '..', '.cache', 'e2e-tokens.json')
const TOKEN_TTL_MS = 30 * 60 * 1000
const LOGIN_RETRY_DELAYS_MS = [11_000, 11_000, 11_000, 11_000]

function readTokenCache() {
  try {
    const parsed = JSON.parse(readFileSync(TOKEN_CACHE_FILE, 'utf8'))
    return parsed && typeof parsed === 'object' ? parsed : {}
  } catch {
    return {}
  }
}

function writeTokenCache(cache) {
  try {
    mkdirSync(dirname(TOKEN_CACHE_FILE), { recursive: true })
    writeFileSync(TOKEN_CACHE_FILE, JSON.stringify(cache, null, 2), 'utf8')
  } catch {
    // 缓存写失败不影响测试本身（只是下一轮可能多一次登录）
  }
}

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms))

/** mock 模式（默认）还是真后端模式 */
export function isRealMode() {
  return process.env.E2E_MOCK === '0'
}

/** 真后端基础地址（供 request 断言使用；相对路径由 baseURL 解析） */
export function backendBaseURL() {
  return process.env.E2E_BACKEND_URL || 'http://127.0.0.1:8080'
}

export function uniqueKey(prefix = 't19') {
  return `${prefix}-${Date.now()}-${Math.random().toString(16).slice(2, 8)}`
}

export async function apiLogin(request, username, password) {
  const cache = readTokenCache()
  const cached = cache[username]
  if (cached?.token && Date.now() - Number(cached.at || 0) < TOKEN_TTL_MS) {
    const me = await request.get(`${backendBaseURL()}${API}/auth/me`, {
      headers: authHeaders(cached.token)
    })
    if (me.status() === 200) {
      const meBody = await me.json()
      if (meBody.code === 0) {
        return { token: cached.token, tokenType: 'Bearer', user: meBody.data }
      }
    }
  }

  let lastBody = null
  let lastStatus = 0
  for (let attempt = 0; attempt <= LOGIN_RETRY_DELAYS_MS.length; attempt += 1) {
    const response = await request.post(`${backendBaseURL()}${API}/auth/login`, {
      data: { username, password }
    })
    lastStatus = response.status()
    lastBody = await response.json()
    if (lastStatus === 429) {
      const delay = LOGIN_RETRY_DELAYS_MS[attempt]
      if (delay == null) break
      await sleep(delay)
      continue
    }
    expect(lastStatus, `登录接口失败：${JSON.stringify(lastBody)}`).toBe(200)
    expect(lastBody.code, `登录业务码异常：${JSON.stringify(lastBody)}`).toBe(0)
    const nextCache = readTokenCache()
    nextCache[username] = { token: lastBody.data.token, at: Date.now() }
    writeTokenCache(nextCache)
    return { token: lastBody.data.token, tokenType: lastBody.data.tokenType, user: lastBody.data.user }
  }
  throw new Error(
    `登录被限流且重试耗尽（HTTP ${lastStatus}）：${JSON.stringify(lastBody)}；请等待 1 分钟后重跑`
  )
}


export function authHeaders(token) {
  return { Authorization: `Bearer ${token}` }
}

/** 公开菜品列表（默认取第一页 12 条） */
export async function fetchDishes(request, params = {}) {
  const response = await request.get(`${backendBaseURL()}${API}/menu/dishes`, {
    params: { merchantId: 1, page: 1, size: 12, ...params }
  })
  const body = await response.json()
  expect(response.status()).toBe(200)
  expect(body.code).toBe(0)
  return body.data
}

/** 挑选一个可售且库存充足的菜品（优先库存 >= minStock，跳过测试数据名） */
export async function pickSellableDish(request, { minStock = 3 } = {}) {
  const data = await fetchDishes(request, { size: 50 })
  const dish = (data.list || []).find(
    (item) => item.status === 'ON_SALE' && !item.soldOut && Number(item.stock || 0) >= minStock
  )
  expect(dish, '公开菜单中没有库存充足的菜品，无法创建真实订单').toBeTruthy()
  return dish
}

/** 单菜品库存（公开详情接口；注意菜单缓存可能返回旧快照，断言扣减请用 fetchMerchantStock） */
export async function fetchDishStock(request, dishId) {
  const response = await request.get(`${backendBaseURL()}${API}/menu/dishes/${dishId}`)
  const body = await response.json()
  expect(response.status()).toBe(200)
  expect(body.code).toBe(0)
  return Number(body.data.stock || 0)
}

/** 商家端库存（直读 DB，无菜单缓存），用于扣减/回补断言 */
export async function fetchMerchantStock(request, token, dishId) {
  const response = await request.get(`${backendBaseURL()}${API}/merchant/dishes/${dishId}`, {
    headers: authHeaders(token)
  })
  const body = await response.json()
  expect(response.status(), JSON.stringify(body)).toBe(200)
  expect(body.code, JSON.stringify(body)).toBe(0)
  return Number(body.data.stock || 0)
}

/**
 * 创建外卖订单（幂等键由调用方决定，便于重放断言）。
 * 后端无收货地址接口，使用种子数据 addressId=1（customer id=3 的默认地址）。
 */
export async function createOrder(
  request,
  token,
  { dishId = 1, quantity = 1, merchantId = 1, addressId = 1, remark = 'T19 E2E', key = uniqueKey('order') } = {}
) {
  const response = await request.post(`${backendBaseURL()}${API}/orders`, {
    headers: { ...authHeaders(token), 'Idempotency-Key': key },
    data: {
      merchantId,
      addressId,
      items: [{ dishId, quantity }],
      remark
    }
  })
  const body = await response.json()
  return { status: response.status(), body, idempotencyKey: key }
}

export async function getOrder(request, token, orderId) {
  const response = await request.get(`${backendBaseURL()}${API}/orders/${orderId}`, {
    headers: authHeaders(token)
  })
  const body = await response.json()
  expect(response.status()).toBe(200)
  expect(body.code).toBe(0)
  return body.data
}

/** 创建支付单并完成模拟支付（真实 PaymentController） */
export async function payOrder(request, token, { orderId, orderType = 'TAKEOUT' }) {
  const create = await request.post(`${backendBaseURL()}${API}/payments`, {
    headers: { ...authHeaders(token), 'Idempotency-Key': uniqueKey('pay-create') },
    data: { orderId, orderType }
  })
  const createBody = await create.json()
  expect(create.status(), JSON.stringify(createBody)).toBe(200)
  expect(createBody.code, JSON.stringify(createBody)).toBe(0)
  const payNo = createBody.data.payNo
  const mockPay = await request.post(
    `${backendBaseURL()}${API}/payments/${payNo}/mock-pay`,
    {
      headers: { ...authHeaders(token), 'Idempotency-Key': uniqueKey('pay-mock') },
      data: {}
    }
  )
  const mockBody = await mockPay.json()
  expect(mockPay.status(), JSON.stringify(mockBody)).toBe(200)
  expect(mockBody.code, JSON.stringify(mockBody)).toBe(0)
  return mockBody.data
}

/** 商家订单动作：accept / prepare / deliver / complete / reject */
export async function merchantAction(request, token, orderId, action, data = {}) {
  const response = await request.post(
    `${backendBaseURL()}${API}/merchant/orders/${orderId}/${action}`,
    { headers: authHeaders(token), data }
  )
  const body = await response.json()
  return { status: response.status(), body }
}

/** 上传 1x1 PNG（真实 /files/upload，本地存储兜底） */
export async function uploadPng(request, token, bizType = 'dish') {
  const png = Buffer.from(
    'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==',
    'base64'
  )
  const response = await request.post(`${backendBaseURL()}${API}/files/upload`, {
    headers: authHeaders(token),
    multipart: {
      file: { name: 't19-e2e.png', mimeType: 'image/png', buffer: png },
      bizType
    }
  })
  const body = await response.json()
  return { status: response.status(), body }
}

/** 写入真实登录会话（token + 用户），供路由守卫直接放行 */
export async function seedRealSession(page, { token, user }) {
  await page.addInitScript(
    ([tokenValue, userJson]) => {
      window.localStorage.setItem('restaurant.token', tokenValue)
      window.localStorage.setItem('restaurant.user', userJson)
    },
    [token, JSON.stringify(user)]
  )
}

/** UI 登录（真实后端），等待登录接口返回后再断言会话 */
export async function loginViaRealUi(page, { username, password }) {
  await page.goto('/login')
  await page.locator('input[autocomplete="username"]').fill(username)
  await page.locator('input[autocomplete="current-password"]').fill(password)
  const loginResponse = page.waitForResponse(
    (response) =>
      response.url().includes('/api/v1/auth/login') && response.request().method() === 'POST'
  )
  await page.getByRole('button', { name: '登录', exact: true }).click()
  const response = await loginResponse
  expect(response.status(), '真实后端登录 HTTP 状态').toBe(200)
  return response.json()
}
