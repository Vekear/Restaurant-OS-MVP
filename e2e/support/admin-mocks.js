/**
 * 管理后台 E2E 桩（T19）
 *
 * 用途：本期后端未实现 /admin/** 控制器（真实请求返回 40401），
 * 管理后台的图表/导出无法做真后端联调。这里在浏览器网络层提供严格对齐
 * docs/02-api.md 的管理端数据，验证前端渲染、图表容器与导出请求参数；
 * 这些用例在真后端模式（E2E_MOCK=0）下跳过，并在 docs/04-testing.md 标注
 * 「管理后台真实联调：未验证」。
 */
import { expect } from '@playwright/test'

export const ADMIN_TOKEN = 'e2e-admin-token'
export const ADMIN_USER = Object.freeze({
  id: 1,
  username: 'admin',
  nickname: '演示管理员',
  role: 'ADMIN',
  merchantId: null,
  status: 'ENABLED'
})

const RANGE = { startDate: '2026-09-29', endDate: '2026-10-28' }

export const statsOverview = {
  ...RANGE,
  orderCount: 128,
  revenue: 12680.5,
  avgOrderAmount: 99.07,
  refundAmount: 32,
  userCount: 42,
  newUserCount: 3,
  merchantCount: 2
}

export const statsTrend = {
  ...RANGE,
  totalOrderCount: 128,
  totalRevenue: 12680.5,
  points: [
    { date: '2026-10-26', orderCount: 20, revenue: 1800 },
    { date: '2026-10-27', orderCount: 28, revenue: 2680.5 },
    { date: '2026-10-28', orderCount: 18, revenue: 1600 }
  ]
}

export const statsRatio = {
  items: [
    { orderType: 'TAKEOUT', orderCount: 100, orderAmount: 9800, percent: 78.13 },
    { orderType: 'DINE_IN', orderCount: 28, orderAmount: 2880.5, percent: 21.87 }
  ],
  totalOrderCount: 128
}

export const statsHot = {
  ...RANGE,
  items: [
    { dishId: 1, dishName: '宫保鸡丁', quantity: 120, amount: 3360 },
    { dishId: 2, dishName: '水煮牛肉', quantity: 80, amount: 4640 }
  ]
}

export const adminOrder = {
  id: 1001,
  orderNo: 'T20261028120000001',
  orderType: 'TAKEOUT',
  status: 'COMPLETED',
  statusText: '已完成',
  payAmount: 88,
  totalAmount: 88,
  discountAmount: 0,
  deliveryFee: 3,
  itemCount: 2,
  tableNo: null,
  createdAt: '2026-10-28T12:00:00+08:00',
  paidAt: '2026-10-28T12:01:00+08:00',
  finishedAt: '2026-10-28T12:45:00+08:00',
  merchantId: 1,
  merchantName: '示范餐厅（人民广场店）',
  username: 'customer'
}

export const auditLog = {
  id: 1,
  userId: 2,
  username: 'merchant',
  role: 'MERCHANT',
  action: 'LOGIN_SUCCESS',
  targetType: 'AUTH',
  targetId: null,
  detail: '{"username":"merchant"}',
  ip: '127.0.0.1',
  userAgent: 'Playwright E2E',
  success: true,
  createdAt: '2026-10-28T01:00:00+08:00'
}

const json = (route, data, status = 200) =>
  route.fulfill({
    status,
    contentType: 'application/json; charset=utf-8',
    body: JSON.stringify({ code: status === 200 ? 0 : 40401, message: 'ok', data, traceId: 'e2e-admin-mock' })
  })

const xlsx = (route) =>
  route.fulfill({
    status: 200,
    contentType: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    headers: { 'content-disposition': "attachment; filename*=UTF-8''t19-admin.xlsx" },
    body: Buffer.from('PK\u0003\u0004T19-E2E-XLSX-MOCK', 'binary')
  })

/**
 * 安装管理端 mock，并返回请求记录器。
 * @param {import('@playwright/test').Page} page
 */
export async function installAdminMocks(page) {
  const requests = []
  // 管理台布局会尝试建立实时推送连接；断掉后端也能保持页面稳定
  if (typeof page.routeWebSocket === 'function') {
    try {
      await page.routeWebSocket(/\/ws$/, (ws) => {
        ws.send('CONNECTED\nversion:1.2\nheart-beat:0,0\n\n\u0000')
      })
    } catch {
      /* 浏览器不支持 WebSocket 路由时忽略，页面会停在“重连中”状态 */
    }
  }
  page.on('request', (request) => {
    const url = new URL(request.url())
    if (url.pathname.startsWith('/api/v1/admin/')) {
      requests.push({
        method: request.method(),
        path: url.pathname,
        query: Object.fromEntries(url.searchParams.entries()),
        headers: request.headers(),
        body: request.postData() || ''
      })
    }
  })

  await page.route('**/api/v1/admin/**', async (route) => {
    const request = route.request()
    const url = new URL(request.url())
    const path = url.pathname

    if (path === '/api/v1/admin/stats/overview') return json(route, statsOverview)
    if (path === '/api/v1/admin/stats/trend') return json(route, statsTrend)
    if (path === '/api/v1/admin/stats/order-type-ratio') return json(route, statsRatio)
    if (path === '/api/v1/admin/stats/hot-dishes') return json(route, statsHot)
    if (path === '/api/v1/admin/stats/export') return xlsx(route)

    if (path === '/api/v1/admin/orders') {
      return json(route, { list: [adminOrder], total: 1, page: 1, size: 10, pages: 1 })
    }
    if (path === '/api/v1/admin/orders/export') return xlsx(route)
    if (path === '/api/v1/admin/audit-logs') {
      return json(route, { list: [auditLog], total: 1, page: 1, size: 20, pages: 1 })
    }

    return json(route, null, 404)
  })

  return {
    requests,
    of(path) {
      return requests.filter((item) => item.path === path)
    },
    last(path) {
      const list = this.of(path)
      return list[list.length - 1]
    }
  }
}

/** 写入管理员会话（路由守卫直接放行，不请求 /auth/me） */
export async function seedAdminSession(page) {
  await page.addInitScript(
    ([token, userJson]) => {
      window.localStorage.setItem('restaurant.token', token)
      window.localStorage.setItem('restaurant.user', userJson)
    },
    [ADMIN_TOKEN, JSON.stringify(ADMIN_USER)]
  )
}
