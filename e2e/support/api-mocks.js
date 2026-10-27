/**
 * E2E 接口桩（mock 驱动）与请求记录工具
 *
 * 目标：在真实后端（T07/T08/T10）就绪前，用严格对齐 docs/02-api.md 的桩数据
 * 驱动 Web 顾客端（登录 / 菜单 / 购物车）的 Playwright 用例；后端就绪后可切到
 * 真后端模式（E2E_MOCK=0），用例逻辑不变。
 *
 * 约束：
 *   - 桩响应字段必须与 docs/02-api.md 一致，禁止自造字段（见各 fixture 注释）
 *   - 只通过 page.route 在浏览器网络层拦截，不向 web/ 源码注入任何 mock 数据
 *   - 真后端模式不安装任何桩；后端未就绪时用例显式 test.skip，绝不伪造通过
 */
import { expect } from '@playwright/test'

export const API_PREFIX = '/api/v1'

/** localStorage 会话 key（与 web/src/utils/storage.js 保持一致） */
export const STORAGE_TOKEN_KEY = 'restaurant.token'
export const STORAGE_USER_KEY = 'restaurant.user'

/** 桩 token：不是合法 JWT，auth store 的 decodeJwtPayload 会安全降级为 {} */
export const AUTH_TOKEN = 'e2e-mock-token'

/** AuthTokenView.user（docs/02-api.md §4.1） */
export const CUSTOMER_USER = Object.freeze({
  id: 3,
  username: 'customer',
  nickname: '演示顾客',
  role: 'CUSTOMER',
  merchantId: null,
  status: 'ENABLED'
})

/** PublicConfigView（docs/02-api.md §3.1） */
export const PUBLIC_CONFIG = Object.freeze({
  appName: '餐厅点餐系统',
  currency: 'CNY',
  timezone: 'Asia/Shanghai',
  defaultMerchantId: 1,
  mockPayment: {
    enabled: true,
    chargeAmount: 0.01,
    currency: 'CNY',
    hint: '演示环境：订单金额按实展示，实际模拟扣款 ¥0.01'
  },
  order: { paymentTimeoutMinutes: 15 },
  table: { sessionExpireMinutes: 120, allowManualInput: true },
  storage: {
    type: 'local',
    uploadMode: 'backend',
    maxSizeMb: 5,
    allowedTypes: ['image/jpeg', 'image/png', 'image/webp']
  },
  websocket: { stompPath: '/ws', nativePath: '/ws/native', pollIntervalSeconds: 10 }
})

/** CategoryView[]（docs/02-api.md §6.1）：只含可售菜品数量 */
export function createCategoryFixtures() {
  return [
    {
      id: 1,
      merchantId: 1,
      name: '热菜',
      sort: 1,
      status: 'ENABLED',
      dishCount: 18,
      createdAt: '2026-10-01T09:00:00+08:00'
    },
    {
      id: 2,
      merchantId: 1,
      name: '主食',
      sort: 2,
      status: 'ENABLED',
      dishCount: 8,
      createdAt: '2026-10-01T09:00:00+08:00'
    }
  ]
}

const HOT_DISH_NAMES = [
  '宫保鸡丁',
  '鱼香肉丝',
  '麻婆豆腐',
  '水煮牛肉',
  '辣子鸡',
  '回锅肉',
  '酸辣土豆丝',
  '干煸四季豆',
  '蒜蓉西兰花',
  '番茄炒蛋',
  '红烧排骨',
  '糖醋里脊',
  '京酱肉丝',
  '地三鲜',
  '蚂蚁上树',
  '剁椒鱼头',
  '小炒黄牛肉',
  '农家小炒肉'
]

const STAPLE_DISH_NAMES = ['米饭', '蛋炒饭', '扬州炒饭', '葱油拌面', '牛肉面', '酸辣粉', '小笼包', '南瓜粥']

/**
 * DishSummaryView[]（docs/02-api.md §6.2）
 * 18 道热菜 + 8 道主食 = 26 道，默认分页 size=12 → 共 3 页
 * 第 9 道（酸辣土豆丝）置为售罄，用于菜单页售罄态断言
 */
export function createDishFixtures() {
  const names = [...HOT_DISH_NAMES, ...STAPLE_DISH_NAMES]
  return names.map((name, index) => {
    const id = 101 + index
    const isHot = index < HOT_DISH_NAMES.length
    const soldOut = name === '酸辣土豆丝'
    return {
      id,
      name,
      categoryId: isHot ? 1 : 2,
      categoryName: isHot ? '热菜' : '主食',
      price: roundMoney(28 + index * 3),
      originalPrice: index % 4 === 0 ? roundMoney(28 + index * 3 + 10) : null,
      imageUrl: `/files/e2e/dish-${id}.png`,
      stock: soldOut ? 0 : 20,
      sales: 120 - index * 3,
      status: 'ON_SALE',
      soldOut,
      sort: index + 1
    }
  })
}

/** 金额统一保留 2 位，避免浮点误差影响断言 */
export function roundMoney(value) {
  return Math.round((Number(value) + Number.EPSILON) * 100) / 100
}

/** CartItemView（docs/02-api.md §7） */
export function makeCartItem(dish, quantity = 1, overrides = {}) {
  const count = Number(quantity) || 0
  return {
    dishId: dish.id,
    dishName: dish.name,
    dishImage: dish.imageUrl,
    price: dish.price,
    quantity: count,
    stock: dish.stock,
    available: dish.status === 'ON_SALE' && !dish.soldOut && count <= dish.stock,
    subtotal: roundMoney(dish.price * count),
    ...overrides
  }
}

/** CartView（docs/02-api.md §7）：invalidCount 只统计不可售行，金额不含不可售行 */
export function cartViewFromItems(items) {
  const list = Array.isArray(items) ? items : []
  const invalidCount = list.filter((item) => !item.available).length
  return {
    items: list.map((item) => ({ ...item })),
    totalQuantity: list.reduce((sum, item) => sum + Number(item.quantity || 0), 0),
    totalAmount: roundMoney(
      list.filter((item) => item.available).reduce((sum, item) => sum + Number(item.subtotal || 0), 0)
    ),
    invalidCount,
    maxItems: 50
  }
}

export function emptyCartView() {
  return cartViewFromItems([])
}

/** 是否真后端模式（默认 mock 驱动） */
export function isRealMode() {
  return process.env.E2E_MOCK === '0'
}

/** 真后端模式前置探测：/public/config 不可达则整组用例 skip */
export async function isRealBackendReachable(page) {
  try {
    const response = await page.request.get(`${API_PREFIX}/public/config`, { timeout: 5000 })
    return response.ok()
  } catch {
    return false
  }
}

function parseRequestBody(request) {
  const raw = request.postData()
  if (!raw) return null
  try {
    return JSON.parse(raw)
  } catch {
    return raw
  }
}

function createState(options = {}) {
  return {
    login: options.login || 'success',
    loginRequests: 0,
    /** menu: 'normal' | 'empty'；dishesFailuresLeft 用于错误态 + 重试 */
    menu: options.menu || 'normal',
    dishesFailuresLeft: Number(options.failNextDishRequests || 0),
    dishRequests: 0,
    dishes: options.dishes || createDishFixtures(),
    categories: options.categories || createCategoryFixtures(),
    cartItems: (options.cartItems || []).map((item) => ({ ...item }))
  }
}

function findDish(state, dishId) {
  return state.dishes.find((dish) => Number(dish.id) === Number(dishId))
}

function upsertCartItem(state, dish, quantity) {
  const existing = state.cartItems.find((item) => Number(item.dishId) === Number(dish.id))
  if (existing) {
    existing.quantity = quantity
    existing.stock = dish.stock
    existing.available = dish.status === 'ON_SALE' && !dish.soldOut && quantity <= dish.stock
    existing.subtotal = roundMoney(dish.price * quantity)
    return existing
  }
  const item = makeCartItem(dish, quantity)
  state.cartItems.push(item)
  return item
}

async function json(route, status, payload) {
  await route.fulfill({
    status,
    contentType: 'application/json; charset=utf-8',
    body: JSON.stringify({ traceId: 'e2e-mock', ...payload })
  })
}

function okJson(route, data) {
  return json(route, 200, { code: 0, message: 'ok', data })
}

/* eslint-disable complexity -- 桩路由集中在一处分发，便于与 docs/02-api.md 对照 */
async function handleApiRoute(route, state) {
  const request = route.request()
  const url = new URL(request.url())
  const method = request.method()
  const path = url.pathname.startsWith(API_PREFIX)
    ? url.pathname.slice(API_PREFIX.length)
    : url.pathname
  const query = url.searchParams

  // ---- 鉴权（docs/02-api.md §4） ----
  if (method === 'POST' && path === '/auth/login') {
    state.loginRequests += 1
    if (state.login === 'invalid') {
      return json(route, 401, { code: 40101, message: '用户名或密码错误', data: null })
    }
    if (state.login === 'server-error') {
      return json(route, 500, { code: 50001, message: '服务器内部错误', data: null })
    }
    return okJson(route, {
      token: AUTH_TOKEN,
      tokenType: 'Bearer',
      expiresIn: 7200,
      user: { ...CUSTOMER_USER }
    })
  }
  if (method === 'POST' && path === '/auth/logout') return okJson(route, true)
  if (method === 'GET' && path === '/auth/me') return okJson(route, { ...CUSTOMER_USER })

  // ---- 公开配置（§3.1） ----
  if (method === 'GET' && path === '/public/config') return okJson(route, PUBLIC_CONFIG)

  // ---- 菜单（§6） ----
  if (method === 'GET' && path === '/menu/categories') {
    return okJson(route, state.categories.map((item) => ({ ...item })))
  }

  if (method === 'GET' && path === '/menu/dishes') {
    state.dishRequests += 1
    if (state.dishesFailuresLeft > 0) {
      state.dishesFailuresLeft -= 1
      return json(route, 500, { code: 50001, message: '服务器内部错误', data: null })
    }
    if (state.menu === 'empty') {
      return okJson(route, { list: [], total: 0, page: 1, size: 12, pages: 0 })
    }

    const categoryId = Number(query.get('categoryId') || 0)
    const keyword = String(query.get('keyword') || '').trim()
    const sort = query.get('sort') || 'default'
    const page = Math.max(1, Number(query.get('page') || 1) || 1)
    const size = Math.max(1, Number(query.get('size') || 12) || 12)

    let list = state.dishes.filter((dish) => dish.status === 'ON_SALE')
    if (categoryId > 0) list = list.filter((dish) => dish.categoryId === categoryId)
    if (keyword) list = list.filter((dish) => dish.name.includes(keyword))

    const sorters = {
      default: (a, b) => a.sort - b.sort || a.id - b.id,
      price_asc: (a, b) => a.price - b.price || a.id - b.id,
      price_desc: (a, b) => b.price - a.price || a.id - b.id,
      sales_desc: (a, b) => b.sales - a.sales || a.id - b.id
    }
    list = [...list].sort(sorters[sort] || sorters.default)

    const total = list.length
    const pages = total === 0 ? 0 : Math.ceil(total / size)
    const start = (page - 1) * size
    return okJson(route, {
      list: list.slice(start, start + size).map((dish) => ({ ...dish })),
      total,
      page,
      size,
      pages
    })
  }

  // ---- 购物车（§7） ----
  if (method === 'GET' && path === '/cart') {
    return okJson(route, cartViewFromItems(state.cartItems))
  }

  if (method === 'POST' && path === '/cart/items') {
    const body = parseRequestBody(request) || {}
    const dish = findDish(state, body.dishId)
    const quantity = Number(body.quantity ?? 1)
    if (!Number.isInteger(quantity) || quantity < 1) {
      return json(route, 400, { code: 40001, message: 'quantity 必须为正整数', data: null })
    }
    if (!dish) return json(route, 404, { code: 40401, message: '菜品不存在', data: null })
    if (dish.status !== 'ON_SALE' || dish.soldOut) {
      return json(route, 409, { code: 40918, message: '菜品已下架', data: null })
    }
    const existing = state.cartItems.find((item) => Number(item.dishId) === Number(dish.id))
    const merged = (existing?.quantity || 0) + quantity
    if (merged > dish.stock) {
      return json(route, 409, {
        code: 40901,
        message: `${dish.name}库存不足，仅剩 ${dish.stock} 份`,
        data: null
      })
    }
    if (!existing && state.cartItems.length >= 50) {
      return json(route, 409, { code: 40902, message: '购物车已达 50 行上限', data: null })
    }
    upsertCartItem(state, dish, merged)
    return okJson(route, cartViewFromItems(state.cartItems))
  }

  const cartItemMatch = path.match(/^\/cart\/items\/(\d+)$/)
  if (cartItemMatch && method === 'PATCH') {
    const dishId = Number(cartItemMatch[1])
    const body = parseRequestBody(request) || {}
    const quantity = Number(body.quantity)
    const item = state.cartItems.find((entry) => Number(entry.dishId) === dishId)
    if (!item) return json(route, 404, { code: 40401, message: '购物车条目不存在', data: null })
    if (!Number.isInteger(quantity) || quantity < 1) {
      return json(route, 400, { code: 40001, message: 'quantity 必须为正整数', data: null })
    }
    if (quantity > item.stock) {
      return json(route, 409, { code: 40901, message: `${item.dishName}库存不足`, data: null })
    }
    item.quantity = quantity
    item.available = quantity <= item.stock
    item.subtotal = roundMoney(item.price * quantity)
    return okJson(route, cartViewFromItems(state.cartItems))
  }

  if (cartItemMatch && method === 'DELETE') {
    const dishId = Number(cartItemMatch[1])
    state.cartItems = state.cartItems.filter((item) => Number(item.dishId) !== dishId)
    return okJson(route, cartViewFromItems(state.cartItems))
  }

  if (method === 'DELETE' && path === '/cart') {
    state.cartItems = []
    return okJson(route, cartViewFromItems(state.cartItems))
  }

  // 未建模的接口显式 404，避免测试误以为走通
  return json(route, 404, {
    code: 40401,
    message: `E2E mock 未实现接口：${method} ${path}`,
    data: null
  })
}
/* eslint-enable complexity */

const PIXEL_PNG = Buffer.from(
  'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=',
  'base64'
)

/** WS 桩：回 STOMP CONNECTED，避免客户端的重连定时器在用例期间刷屏/抖动 */
async function stubWebSocket(page) {
  if (typeof page.routeWebSocket !== 'function') return
  try {
    await page.routeWebSocket(/\/ws$/, (ws) => {
      ws.onMessage((message) => {
        const frame = message.toString()
        if (frame.startsWith('CONNECT')) {
          ws.send('CONNECTED\nversion:1.2\nheart-beat:0,0\n\n\u0000')
        }
      })
    })
  } catch (error) {
    // WS 桩失败不应阻断用例；realtime 层自带重连与降级
    console.warn('[e2e] WebSocket 桩注册失败：', error?.message || error)
  }
}

/**
 * 安装接口桩并返回可变 state（用例可在运行中切换登录失败、空菜单、库存等场景）
 * @param {import('@playwright/test').Page} page
 * @param {{ login?: 'success'|'invalid'|'server-error', menu?: 'normal'|'empty',
 *           failNextDishRequests?: number, cartItems?: object[], dishes?: object[],
 *           categories?: object[] }} options
 */
export async function installApiMocks(page, options = {}) {
  const state = createState(options)

  await page.route(`${API_PREFIX}/**`, (route) => {
    try {
      return handleApiRoute(route, state)
    } catch (error) {
      console.warn('[e2e] mock 处理失败：', error?.message || error)
      return route.fulfill({
        status: 500,
        contentType: 'application/json; charset=utf-8',
        body: JSON.stringify({ code: 50001, message: 'e2e mock error', data: null, traceId: 'e2e-mock' })
      })
    }
  })

  // 图片占位：避免依赖真实 /files 静态资源
  await page.route('**/files/**', (route) =>
    route.fulfill({ status: 200, contentType: 'image/png', body: PIXEL_PNG })
  )

  await stubWebSocket(page)
  return state
}

/**
 * 记录页面发出的 /api/v1 请求（mock / 真后端模式都可用）
 * 返回 { entries, filter(method, path), last(method, path), queryOf(...) }
 */
export function collectApiRequests(page) {
  const entries = []
  page.on('request', (request) => {
    let url
    try {
      url = new URL(request.url())
    } catch {
      return
    }
    if (!url.pathname.startsWith(`${API_PREFIX}/`)) return
    entries.push({
      method: request.method(),
      path: url.pathname.slice(API_PREFIX.length),
      query: Object.fromEntries(url.searchParams.entries()),
      body: parseRequestBody(request)
    })
  })

  return {
    entries,
    filter(method, path) {
      return entries.filter((entry) => entry.method === method && entry.path === path)
    },
    last(method, path) {
      const matched = entries.filter((entry) => entry.method === method && entry.path === path)
      return matched[matched.length - 1] || null
    }
  }
}

/** 等待某个接口响应（用于 click 前后的确定性等待） */
export function waitForApiResponse(page, method, pathPrefix) {
  return page.waitForResponse((response) => {
    const request = response.request()
    if (request.method() !== method) return false
    let url
    try {
      url = new URL(response.url())
    } catch {
      return false
    }
    return url.pathname.startsWith(`${API_PREFIX}${pathPrefix}`)
  })
}

/** localStorage 注入登录态（等价于真实登录成功后的持久化结果） */
export async function seedSession(page) {
  await page.addInitScript(
    ({ tokenKey, userKey, token, user }) => {
      window.localStorage.setItem(tokenKey, token)
      window.localStorage.setItem(userKey, JSON.stringify(user))
    },
    { tokenKey: STORAGE_TOKEN_KEY, userKey: STORAGE_USER_KEY, token: AUTH_TOKEN, user: CUSTOMER_USER }
  )
}

/** 清理 localStorage 会话（401 重定向等场景） */
export async function clearSessionStorage(page) {
  await page.addInitScript(
    ({ tokenKey, userKey }) => {
      window.localStorage.removeItem(tokenKey)
      window.localStorage.removeItem(userKey)
    },
    { tokenKey: STORAGE_TOKEN_KEY, userKey: STORAGE_USER_KEY }
  )
}

/** 通过登录页完成真实 UI 登录（mock 模式下由桩响应 200） */
export async function loginViaUi(page, { username = 'customer', password = 'customer123' } = {}) {
  await page.goto('/login')
  await page.locator('input[autocomplete="username"]').fill(username)
  await page.locator('input[autocomplete="current-password"]').fill(password)
  const responsePromise = page.waitForResponse(
    (response) =>
      response.request().method() === 'POST' && response.url().includes('/api/v1/auth/login'),
    { timeout: 15000 }
  )
  await page.getByRole('button', { name: '登录', exact: true }).click()
  return responsePromise
}

/** 断言 Element Plus 全局 toast 文案 */
export async function expectToast(page, text) {
  const toast = page.locator('.el-message').filter({ hasText: text })
  await expect(toast.first()).toBeVisible()
}
