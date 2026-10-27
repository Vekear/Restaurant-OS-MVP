import { test, expect } from '@playwright/test'
import { isRealMode } from '../support/real-stack.js'

/**
 * 商家订单看板（mock 驱动）
 * - 覆盖 T28 回归：空闲态动作按钮必须可点（running 为 null，而非 ''）
 * - 覆盖外卖状态机 UI：PAID →(接单)→ ACCEPTED →(开始制作)→ MAKING →(出餐配送)→ DELIVERING →(完成订单)→ COMPLETED
 * - 覆盖拒单理由校验（2–100 字）与 REJECTED 后从「进行中」列表消失
 * 真实后端链路见 real-order-flow.spec.js（E2E_MOCK=0）。
 */

const API = '**/api/v1/**'
const MERCHANT_TOKEN = 'e2e-merchant-token'

function makeOrder(overrides = {}) {
  return {
    id: 501,
    orderNo: 'T20261028000000501',
    orderType: 'TAKEOUT',
    status: 'PAID',
    statusText: '待接单',
    payAmount: 69.0,
    itemCount: 2,
    paidAt: '2026-10-28T02:10:00.000+08:00',
    createdAt: '2026-10-28T02:05:00.000+08:00',
    ...overrides
  }
}

const ACTIVE_STATUSES = new Set(['PAID', 'ACCEPTED', 'MAKING', 'DELIVERING', 'SERVED'])

function envelope(data) {
  return { traceId: 'e2e-mock', code: 0, message: 'ok', data }
}

function pageResult(list, page = 1, size = 10) {
  return { list, total: list.length, page, size, pages: Math.ceil(list.length / size) }
}

/**
 * 有状态的商家订单桩：动作会真实改变桩内订单状态，刷新后 UI 应看到新状态。
 * 返回 { state, actions }，actions 记录收到的动作请求。
 */
async function installMerchantOrderMocks(page, initialOrder = makeOrder()) {
  const state = { order: { ...initialOrder }, actions: [] }

  await page.route('**/ws', (route) => route.abort())
  await page.route(API, async (route) => {
    const request = route.request()
    const url = new URL(request.url())
    const path = url.pathname
    const method = request.method()

    if (path === '/api/v1/merchant/orders' && method === 'GET') {
      const status = url.searchParams.get('status')
      const group = url.searchParams.get('statusGroup') || 'ACTIVE'
      const order = state.order
      const statusMatches = !status || order.status === status
      const groupMatches = group === 'ALL' || (group === 'ACTIVE' ? ACTIVE_STATUSES.has(order.status) : !ACTIVE_STATUSES.has(order.status))
      const list = statusMatches && groupMatches ? [order] : []
      return route.fulfill({ json: envelope(pageResult(list)) })
    }

    const actionMatch = path.match(/^\/api\/v1\/merchant\/orders\/(\d+)\/(accept|reject|prepare|deliver|complete)$/)
    if (actionMatch && method === 'POST') {
      const body = request.postDataJSON?.() || {}
      state.actions.push({ action: actionMatch[2], id: Number(actionMatch[1]), body })
      const transitions = { accept: 'ACCEPTED', reject: 'REJECTED', prepare: 'MAKING', deliver: 'DELIVERING', complete: 'COMPLETED' }
      const labels = { ACCEPTED: '已接单', REJECTED: '已拒单', MAKING: '制作中', DELIVERING: '配送中', COMPLETED: '已完成' }
      state.order = { ...state.order, status: transitions[actionMatch[2]], statusText: labels[transitions[actionMatch[2]]] }
      return route.fulfill({ json: envelope({ id: state.order.id, status: state.order.status, statusText: state.order.statusText }) })
    }

    if (path === '/api/v1/merchant/orders/501' && method === 'GET') {
      return route.fulfill({ json: envelope({ ...state.order, items: [{ dishId: 1, dishName: '宫保鸡丁', quantity: 2, price: 32 }] }) })
    }

    return route.fulfill({
      status: 404,
      json: { traceId: 'e2e-mock', code: 40401, message: `E2E mock 未实现接口：${method} ${path}` }
    })
  })

  return state
}

async function seedMerchantSession(page) {
  await page.addInitScript(
    ([token]) => {
      localStorage.setItem('restaurant.token', token)
      localStorage.setItem(
        'restaurant.user',
        JSON.stringify({ id: 2, username: 'merchant', nickname: '演示商家', role: 'MERCHANT', merchantId: 1, status: 'ENABLED' })
      )
    },
    [MERCHANT_TOKEN]
  )
}

async function gotoOrders(page) {
  await page.goto('/merchant/orders')
  await expect(page.getByRole('heading', { name: '外卖订单' })).toBeVisible()
}

test.describe('商家订单看板（mock 驱动）', () => {
  test.skip(isRealMode(), 'mock 驱动用例：真实栈模式只运行 real-*.spec.js')

  test('待接单动作按钮空闲态可点，接单→制作→配送→完成全链路刷新状态', async ({ page }) => {
    await seedMerchantSession(page)
    const state = await installMerchantOrderMocks(page)
    await gotoOrders(page)

    const row = page.locator('.el-table__row').filter({ hasText: 'T20261028000000501' })
    await expect(row).toBeVisible()
    await expect(row.getByText('待接单')).toBeVisible()
    await expect(page.locator('.orders__count')).toHaveText('共 1 单')
    await expect(row.locator('.amount')).toHaveText('¥69.00')

    // T28 回归：空闲态「接单/拒单」必须可点（修复前 Boolean cast 导致 disabled=true）
    const acceptButton = row.getByRole('button', { name: '接单' })
    await expect(acceptButton).toBeEnabled()
    await expect(acceptButton).not.toHaveAttribute('disabled', '')
    await expect(row.getByRole('button', { name: '拒单', exact: true })).toBeEnabled()

    await acceptButton.click()
    await expect(page.locator('.el-message').filter({ hasText: '接单成功' })).toBeVisible()
    await expect(row.getByText('已接单')).toBeVisible()
    await expect(row.getByRole('button', { name: '开始制作' })).toBeEnabled()
    expect(state.actions.at(-1)).toMatchObject({ action: 'accept', id: 501 })

    await row.getByRole('button', { name: '开始制作' }).click()
    await expect(page.locator('.el-message').filter({ hasText: '开始制作成功' })).toBeVisible()
    await expect(row.getByText('制作中')).toBeVisible()
    expect(state.actions.at(-1)).toMatchObject({ action: 'prepare', id: 501 })

    await row.getByRole('button', { name: '出餐配送' }).click()
    const deliverDialog = page.locator('.el-message-box')
    await expect(deliverDialog).toContainText('确认餐品已出餐并进入配送？')
    await deliverDialog.getByRole('button', { name: '确认', exact: true }).click()
    await expect(row.getByText('配送中')).toBeVisible()
    expect(state.actions.at(-1)).toMatchObject({ action: 'deliver', id: 501 })

    await row.getByRole('button', { name: '完成订单' }).click()
    const completeDialog = page.locator('.el-message-box')
    await expect(completeDialog).toContainText('完成后订单进入终态')
    await completeDialog.getByRole('button', { name: '确认', exact: true }).click()
    await expect(page.locator('.el-message').filter({ hasText: '完成订单成功' })).toBeVisible()
    expect(state.actions.at(-1)).toMatchObject({ action: 'complete', id: 501 })

    // COMPLETED 不属于「进行中」，刷新后从当前页签消失并回到空态
    await expect(page.getByText('该状态下暂无订单')).toBeVisible()
    await expect(page.locator('.orders__count')).toHaveText('共 0 单')
    await expect(page.locator('.order-actions__empty')).toHaveCount(0)
    await expect(page.locator('.el-table__row')).toHaveCount(0)
  })

  test('拒单要求 2–100 字理由，成功后订单离开进行中列表', async ({ page }) => {
    await seedMerchantSession(page)
    const state = await installMerchantOrderMocks(page)
    await gotoOrders(page)

    const row = page.locator('.el-table__row').filter({ hasText: 'T20261028000000501' })
    await row.getByRole('button', { name: '拒单', exact: true }).click()

    const prompt = page.locator('.el-message-box')
    await expect(prompt).toContainText('请填写拒单理由（2–100 字）')
    const input = prompt.locator('input')
    await input.fill('缺')
    await prompt.getByRole('button', { name: '确认拒单' }).click()
    await expect(prompt).toContainText('拒单理由需为 2–100 个字符')

    await input.fill('食材售罄，无法出餐')
    await prompt.getByRole('button', { name: '确认拒单' }).click()
    await expect(page.locator('.el-message').filter({ hasText: '拒单成功' })).toBeVisible()
    expect(state.actions.at(-1)).toMatchObject({ action: 'reject', id: 501, body: { reason: '食材售罄，无法出餐' } })

    await expect(page.getByText('该状态下暂无订单')).toBeVisible()
    await expect(page.locator('.orders__count')).toHaveText('共 0 单')
  })
})
