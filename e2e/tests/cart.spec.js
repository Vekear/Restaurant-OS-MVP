/**
 * 购物车 E2E（mock 驱动，真后端可复用）
 *
 * 覆盖（T25）：
 *  - 空购物车态（登录态）
 *  - 菜单加入购物车 → 头部角标 1 → 2，POST /cart/items 请求体符合契约
 *  - 购物车页两行渲染与合计金额（59.00）
 *  - 数量增加 / 减少 → PATCH /cart/items/{dishId}，金额与角标同步
 *  - 删除单行 → DELETE /cart/items/{dishId}，金额与角标同步
 *  - 失效行（下架/库存不足）不计入合计，展示警告与 is-invalid 样式
 *  - 清空购物车 → DELETE /cart + 空态
 *
 * mock 数据严格对齐 docs/02-api.md 的 CartView / CartItemView 字段。
 */
import { test, expect } from '@playwright/test'
import {
  installApiMocks,
  collectApiRequests,
  isRealMode,
  isRealBackendReachable,
  seedSession,
  expectToast,
  createDishFixtures,
  makeCartItem
} from '../support/api-mocks.js'

const dishes = createDishFixtures()
const dishOf = (name) => {
  const dish = dishes.find((item) => item.name === name)
  if (!dish) throw new Error(`测试夹具缺少菜品：${name}`)
  return dish
}

const KUNG_PAO = dishOf('宫保鸡丁') // 28.00
const YU_XIANG = dishOf('鱼香肉丝') // 31.00

test.describe('购物车', () => {
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

  /** 已登录顾客访问购物车 */
  async function openCartAsCustomer(page) {
    await seedSession(page)
    await page.goto('/cart')
    await expect(page.getByRole('heading', { name: '购物车' })).toBeVisible()
  }

  function cartRow(page, name) {
    return page.locator('.cart-item').filter({ hasText: name })
  }

  test('登录后空购物车展示空态与去菜单入口', async ({ page }) => {
    await openCartAsCustomer(page)

    await expect(page.locator('.el-empty__description')).toHaveText('购物车还空着')
    await expect(page.getByRole('button', { name: '去逛逛菜单' })).toBeVisible()
    await expect(page.locator('.cart-item')).toHaveCount(0)
    await expect(page.locator('.page-subtitle')).toContainText('共 0 件')
  })

  test('菜单加入购物车同步头部角标与请求体', async ({ page }) => {
    await seedSession(page)
    await page.goto('/menu')
    await expect(page.locator('.dish-card')).toHaveCount(12)

    await page
      .locator('.dish-card')
      .filter({ hasText: KUNG_PAO.name })
      .getByRole('button', { name: '加入购物车' })
      .click()
    await expectToast(page, `已加入：${KUNG_PAO.name}`)
    await expect(page.locator('.site-header__cart-badge .el-badge__content')).toHaveText('1')
    expect(requests.last('POST', '/cart/items')?.body).toEqual({ dishId: KUNG_PAO.id, quantity: 1 })

    await page
      .locator('.dish-card')
      .filter({ hasText: YU_XIANG.name })
      .getByRole('button', { name: '加入购物车' })
      .click()
    await expectToast(page, `已加入：${YU_XIANG.name}`)
    await expect(page.locator('.site-header__cart-badge .el-badge__content')).toHaveText('2')

    await page.locator('.site-header__cart').click()
    await expect(page).toHaveURL(/\/cart$/)
    await expect(page.locator('.cart-item')).toHaveCount(2)
    await expect(page.locator('.page-subtitle')).toContainText('共 2 件')
    await expect(page.locator('.cart-summary__total strong')).toHaveText('59.00')
    await expect(cartRow(page, KUNG_PAO.name).locator('.cart-item__subtotal')).toHaveText('28.00')
    await expect(cartRow(page, YU_XIANG.name).locator('.cart-item__subtotal')).toHaveText('31.00')
  })

  test('数量增减按 PATCH 请求并同步金额与角标', async ({ page }) => {
    await seedSession(page)
    await installSeedCart(page, [
      makeCartItem(KUNG_PAO, 1),
      makeCartItem(YU_XIANG, 1)
    ])
    await page.goto('/cart')
    await expect(page.locator('.cart-item')).toHaveCount(2)
    await expect(page.locator('.site-header__cart-badge .el-badge__content')).toHaveText('2')

    await cartRow(page, KUNG_PAO.name).locator('.el-input-number__increase').click()
    await expect
      .poll(() => requests.last('PATCH', `/cart/items/${KUNG_PAO.id}`)?.body?.quantity)
      .toBe(2)
    await expect(cartRow(page, KUNG_PAO.name).locator('.cart-item__subtotal')).toHaveText('56.00')
    await expect(page.locator('.cart-summary__total strong')).toHaveText('87.00')
    await expect(page.locator('.site-header__cart-badge .el-badge__content')).toHaveText('3')

    await cartRow(page, KUNG_PAO.name).locator('.el-input-number__decrease').click()
    await expect
      .poll(() => requests.last('PATCH', `/cart/items/${KUNG_PAO.id}`)?.body?.quantity)
      .toBe(1)
    await expect(page.locator('.cart-summary__total strong')).toHaveText('59.00')
    await expect(page.locator('.site-header__cart-badge .el-badge__content')).toHaveText('2')
  })

  test('删除单行按 DELETE 请求并同步金额与角标', async ({ page }) => {
    await seedSession(page)
    await installSeedCart(page, [
      makeCartItem(KUNG_PAO, 1),
      makeCartItem(YU_XIANG, 1)
    ])
    await page.goto('/cart')
    await expect(page.locator('.cart-item')).toHaveCount(2)

    await cartRow(page, YU_XIANG.name).getByRole('button', { name: '删除' }).click()
    await expectToast(page, `已移除：${YU_XIANG.name}`)
    await expect(page.locator('.cart-item')).toHaveCount(1)
    await expect(cartRow(page, YU_XIANG.name)).toHaveCount(0)
    await expect(page.locator('.cart-summary__total strong')).toHaveText('28.00')
    await expect(page.locator('.site-header__cart-badge .el-badge__content')).toHaveText('1')
    expect(requests.filter('DELETE', `/cart/items/${YU_XIANG.id}`)).toHaveLength(1)
  })

  test('失效行不计入合计并展示警告', async ({ page }) => {
    await seedSession(page)
    await installSeedCart(page, [
      makeCartItem(KUNG_PAO, 1),
      makeCartItem(YU_XIANG, 1, { available: false })
    ])
    await page.goto('/cart')

    await expect(page.locator('.cart-item')).toHaveCount(2)
    await expect(
      page.locator('.cart-page__alert .el-alert__title')
    ).toHaveText('有 1 件菜品已下架或库存不足，未计入合计金额')
    await expect(page.locator('.cart-item.is-invalid')).toHaveCount(1)
    await expect(page.locator('.cart-summary__total strong')).toHaveText('28.00')
    await expect(page.locator('.page-subtitle')).toContainText('共 2 件')
  })

  test('清空购物车确认后回到空态', async ({ page }) => {
    await seedSession(page)
    await installSeedCart(page, [
      makeCartItem(KUNG_PAO, 1),
      makeCartItem(YU_XIANG, 1)
    ])
    await page.goto('/cart')
    await expect(page.locator('.cart-item')).toHaveCount(2)

    await page.getByRole('button', { name: '清空购物车' }).click()
    const dialog = page.locator('.el-message-box')
    await expect(dialog).toBeVisible()
    await expect(dialog).toContainText('确定清空购物车吗？')
    await dialog.getByRole('button', { name: '清空' }).click()

    await expect(page.locator('.el-empty__description')).toHaveText('购物车还空着')
    await expect(page.locator('.cart-item')).toHaveCount(0)
    await expect(page.locator('.page-subtitle')).toContainText('共 0 件')
    expect(requests.filter('DELETE', '/cart')).toHaveLength(1)
  })

  /**
   * 先预置登录态与购物车初始数据，再安装 mock。
   * mockState 由 beforeEach 创建（空购物车），这里把种子数据写入 mock state。
   */
  async function installSeedCart(page, items) {
    if (mockState) {
      mockState.cartItems = items.map((item) => ({ ...item }))
    } else {
      // 真后端模式：没有可控预置，直接跳过本用例（禁止伪造通过）
      test.skip(true, '真后端模式无法预置购物车数据，跳过（mock 模式覆盖该场景）')
    }
  }
})
