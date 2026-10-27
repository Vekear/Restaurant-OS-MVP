/**
 * 管理后台 E2E（mock 驱动，T19）
 *
 * 运行：`npx playwright test tests/admin-dashboard.spec.js`（默认 mock 模式）
 *
 * 背景：后端本期未实现 /admin/** 控制器（真请求 40401），管理后台只能用桩数据
 * 验证前端渲染、ECharts 容器与导出请求；真实后端联调在 docs/04-testing.md 标注
 * 「未验证」。用例在 E2E_MOCK=0 时整组跳过。
 *
 * 覆盖：
 *  - 平台概览：7 张指标卡、3 个 ECharts 画布、报表导出请求（startDate/endDate）
 *  - 数据统计：7 张概览卡、3 个图表、导出请求
 *  - 订单管理：列表渲染 + 导出订单明细请求
 *  - 审计日志：真实契约字段渲染（操作人/动作/结果）
 */
import { test, expect } from '@playwright/test'
import { isRealMode } from '../support/real-stack.js'
import {
  installAdminMocks,
  seedAdminSession,
  statsOverview
} from '../support/admin-mocks.js'

const formatMoney = (value) =>
  `¥${Number(value).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`

test.describe('管理后台（mock 驱动）', () => {
  test.skip(isRealMode(), '管理后台本期无真实后端接口：mock 模式运行，真实联调未验证')

  /** @type {Awaited<ReturnType<typeof installAdminMocks>>} */
  let mock

  test.beforeEach(async ({ page }) => {
    mock = await installAdminMocks(page)
    await seedAdminSession(page)
  })

  test('平台概览：指标卡、ECharts 画布与报表导出', async ({ page }) => {
    await page.goto('/admin/dashboard')

    await expect(page.getByRole('heading', { name: '平台概览' })).toBeVisible()
    const metrics = page.locator('.metric-card')
    await expect(metrics).toHaveCount(7)
    // 顺序：成交额 / 订单量 / 客单价 / 退款额 / 用户总数 / 新增用户 / 已审核商家
    await expect(metrics.nth(0)).toContainText(formatMoney(statsOverview.revenue))
    await expect(metrics.nth(1)).toContainText('128')
    await expect(metrics.nth(4)).toContainText('42')

    await expect(page.getByText('成交额与订单趋势')).toBeVisible()
    await expect(page.getByText('订单类型占比')).toBeVisible()
    await expect(page.getByText('热销菜品 Top 10')).toBeVisible()
    await expect(page.locator('.base-chart canvas')).toHaveCount(3, { timeout: 15_000 })

    await page.getByRole('button', { name: '导出平台报表' }).click()
    await expect(page.locator('.el-message').filter({ hasText: '平台报表已开始下载' })).toBeVisible()
    const exportRequest = mock.last('/api/v1/admin/stats/export')
    expect(exportRequest, '应请求 /admin/stats/export').toBeTruthy()
    expect(exportRequest.query.startDate).toMatch(/^\d{4}-\d{2}-\d{2}$/)
    expect(exportRequest.query.endDate).toMatch(/^\d{4}-\d{2}-\d{2}$/)
    expect(mock.of('/api/v1/admin/stats/export')).toHaveLength(1)
  })

  test('数据统计：概览卡、趋势/热销/占比图与导出', async ({ page }) => {
    await page.goto('/admin/stats')

    await expect(page.getByRole('heading', { name: '数据统计' })).toBeVisible()
    await expect(page.locator('.overview-card')).toHaveCount(7)
    await expect(page.locator('.overview-card', { hasText: '区间成交额' })).toContainText(
      formatMoney(statsOverview.revenue)
    )
    await expect(page.getByText('平台成交额与订单量趋势')).toBeVisible()
    await expect(page.getByText('全平台热销菜品 Top 10')).toBeVisible()
    await expect(page.locator('.base-chart canvas')).toHaveCount(3, { timeout: 15_000 })

    await page.getByRole('button', { name: '导出平台报表' }).click()
    await expect(page.locator('.el-message').filter({ hasText: '平台报表已开始下载' })).toBeVisible()
    expect(mock.last('/api/v1/admin/stats/export')).toBeTruthy()
  })

  test('订单管理导出与审计日志渲染', async ({ page }) => {
    await page.goto('/admin/orders')
    await expect(page.getByRole('heading', { name: '订单管理' })).toBeVisible()
    const orderRow = page.locator('.el-table__row', { hasText: 'T20261028120000001' })
    await expect(orderRow).toBeVisible()
    await expect(orderRow).toContainText('示范餐厅（人民广场店）')

    await page.getByRole('button', { name: '导出订单明细' }).click()
    await expect(
      page.locator('.el-message').filter({ hasText: '订单明细已开始下载' })
    ).toBeVisible()
    const exportRequest = mock.last('/api/v1/admin/orders/export')
    expect(exportRequest, '应请求 /admin/orders/export').toBeTruthy()

    await page.goto('/admin/audit')
    await expect(page.getByRole('heading', { name: '审计日志' })).toBeVisible()
    const auditRow = page.locator('.el-table__row', { hasText: 'merchant' })
    await expect(auditRow).toBeVisible()
    await expect(auditRow).toContainText('登录成功')
    await expect(auditRow.getByText('成功', { exact: true })).toBeVisible()
  })
})
