/**
 * 菜单页 E2E（mock 驱动，真后端可复用）
 *
 * 覆盖（T25）：
 *  - 分类 + 第一页菜品渲染、请求参数符合 docs/02-api.md
 *  - 售罄菜品展示
 *  - 关键词搜索（320ms 防抖）与请求参数
 *  - 分类切换（categoryId）与分页出现/消失
 *  - 价格排序（price_desc）
 *  - 分页翻页（page=2）
 *  - 空态（暂无数据）
 *  - 错误态 + 重新加载
 *  - 游客加购 → 提示并跳转登录（不产生购物车写请求）
 */
import { test, expect } from '@playwright/test'
import {
  installApiMocks,
  collectApiRequests,
  isRealMode,
  isRealBackendReachable,
  expectToast
} from '../support/api-mocks.js'

test.describe('菜单页', () => {
  test.skip(isRealMode(), 'mock 驱动用例：真实栈模式只运行 real-*.spec.js')
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

  test('默认加载分类与第一页菜品，请求参数符合契约', async ({ page }) => {
    await page.goto('/menu')

    await expect(page.locator('.dish-card')).toHaveCount(12)
    await expect(page.locator('.page-subtitle')).toContainText('共 26 道菜')
    await expect(page.locator('.menu-category').filter({ hasText: '热菜' })).toBeVisible()
    await expect(page.locator('.menu-category').filter({ hasText: '主食' })).toBeVisible()

    // 售罄菜品：按钮禁用文案为「已售罄」
    const soldOutCard = page.locator('.dish-card').filter({ hasText: '酸辣土豆丝' })
    await expect(soldOutCard.getByRole('button', { name: '已售罄' })).toBeVisible()

    const categoriesRequest = requests.last('GET', '/menu/categories')
    expect(categoriesRequest?.query).toMatchObject({ merchantId: '1' })

    const dishesRequest = requests.last('GET', '/menu/dishes')
    expect(dishesRequest?.query).toMatchObject({
      merchantId: '1',
      sort: 'default',
      page: '1',
      size: '12'
    })
    expect(dishesRequest?.query.categoryId).toBeUndefined()
    expect(dishesRequest?.query.keyword).toBeUndefined()
  })

  test('关键词搜索按防抖参数请求并过滤结果', async ({ page }) => {
    await page.goto('/menu')
    await expect(page.locator('.dish-card')).toHaveCount(12)

    const dishesRequestsBefore = requests.filter('GET', '/menu/dishes').length
    await page.locator('.menu-page__search input').fill('宫保')

    await expect
      .poll(() => requests.filter('GET', '/menu/dishes').length, { message: '搜索应触发菜品请求' })
      .toBeGreaterThan(dishesRequestsBefore)
    await expect(page.locator('.dish-card')).toHaveCount(1)
    await expect(page.locator('.dish-card__title')).toHaveText('宫保鸡丁')
    await expect(page.locator('.page-subtitle')).toContainText('共 1 道菜')

    const searchRequest = requests.last('GET', '/menu/dishes')
    expect(searchRequest?.query.keyword).toBe('宫保')
    expect(searchRequest?.query.page).toBe('1')
  })

  test('分类切换按 categoryId 请求，并在超过一页时显示分页', async ({ page }) => {
    await page.goto('/menu')
    await expect(page.locator('.dish-card')).toHaveCount(12)

    await page.locator('.menu-category').filter({ hasText: '热菜' }).click()
    await expect(page.locator('.dish-card')).toHaveCount(12)
    await expect(page.locator('.menu-results__head h2')).toHaveText('热菜')
    await expect(page.locator('.menu-results__pagination')).toBeVisible()
    expect(requests.last('GET', '/menu/dishes')?.query.categoryId).toBe('1')

    await page.locator('.menu-category').filter({ hasText: '主食' }).click()
    await expect(page.locator('.dish-card')).toHaveCount(8)
    await expect(page.locator('.menu-results__head h2')).toHaveText('主食')
    await expect(page.locator('.menu-results__pagination')).toHaveCount(0)
    expect(requests.last('GET', '/menu/dishes')?.query.categoryId).toBe('2')
  })

  test('价格从高到低排序按 sort=price_desc 请求并重排首项', async ({ page }) => {
    await page.goto('/menu')
    await expect(page.locator('.dish-card')).toHaveCount(12)

    await page.locator('.menu-page__sort').click()
    await page.getByRole('option', { name: '价格从高到低' }).click()

    await expect(page.locator('.dish-card__title').first()).toHaveText('南瓜粥')
    const sortRequest = requests.last('GET', '/menu/dishes')
    expect(sortRequest?.query.sort).toBe('price_desc')
    expect(sortRequest?.query.page).toBe('1')
  })

  test('翻页按 page=2 请求并展示第二页菜品', async ({ page }) => {
    await page.goto('/menu')
    await expect(page.locator('.dish-card')).toHaveCount(12)
    await expect(page.locator('.menu-results__pagination')).toBeVisible()

    await page.locator('.el-pager li').filter({ hasText: /^2$/ }).click()

    await expect(page.locator('.dish-card__title').first()).toHaveText('京酱肉丝')
    await expect(page.locator('.el-pager li.is-active')).toHaveText('2')
    const pageRequest = requests.last('GET', '/menu/dishes')
    expect(pageRequest?.query.page).toBe('2')
    expect(pageRequest?.query.sort).toBe('default')
  })

  test('空菜单展示空态文案', async ({ page }) => {
    if (mockState) mockState.menu = 'empty'
    await page.goto('/menu')

    await expect(page.locator('.el-empty__description')).toHaveText('暂无数据')
    await expect(page.locator('.dish-card')).toHaveCount(0)
    await expect(page.locator('.page-subtitle')).toContainText('共 0 道菜')
  })

  test('接口失败展示错误态，点击重新加载后恢复', async ({ page }) => {
    if (mockState) mockState.dishesFailuresLeft = 1
    await page.goto('/menu')

    await expect(page.locator('.el-result__title')).toContainText('加载失败')
    await expect(page.getByRole('button', { name: '重新加载' })).toBeVisible()

    await page.getByRole('button', { name: '重新加载' }).click()
    await expect(page.locator('.dish-card')).toHaveCount(12)
    await expect(page.locator('.el-result__title')).toHaveCount(0)
  })

  test('游客加入购物车提示登录并携带 redirect，不产生写请求', async ({ page }) => {
    await page.goto('/menu')
    await expect(page.locator('.dish-card')).toHaveCount(12)

    await page
      .locator('.dish-card')
      .first()
      .getByRole('button', { name: '加入购物车' })
      .click()

    await expectToast(page, '登录后即可加入购物车')
    await expect
      .poll(() => new URL(page.url()).searchParams.get('redirect'), { message: '应跳转登录并回跳菜单' })
      .toBe('/menu')
    expect(requests.filter('POST', '/cart/items')).toHaveLength(0)
  })
})
