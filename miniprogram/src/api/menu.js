import { http } from './request'
import { IS_MOCK, mockDelay } from '@/mock'
import { MOCK_CATEGORIES, MOCK_DISHES } from '@/mock/menu'

/**
 * 菜单接口封装（契约来源：docs/02-api.md §6，集中在此文件，后端字段调整只需改这里）。
 * - GET /api/v1/menu/categories                    → data: CategoryView[]
 * - GET /api/v1/menu/dishes?merchantId=&categoryId=&keyword=&sort=&page=&size=
 *                                                 → data: PageResult<DishSummaryView>
 *
 * 契约要点：/menu/dishes 默认 size=12（1–100），sort 仅 default/price_asc/price_desc/sales_desc；
 * 分页结构固定 { list, total, page, size, pages }；soldOut = stock <= 0 仍返回，前端置灰。
 */

/** 菜品分页默认值（与后端契约一致） */
const DEFAULT_PAGE = 1
const DEFAULT_SIZE = 12
const MAX_SIZE = 100

function toList(payload) {
  if (Array.isArray(payload)) return payload
  if (!payload || typeof payload !== 'object') return []
  const keys = ['list', 'records', 'items', 'content', 'rows', 'data']
  for (let i = 0; i < keys.length; i += 1) {
    const value = payload[keys[i]]
    if (Array.isArray(value)) return value
  }
  return []
}

/** 归一化分页响应：PageResult<T> → { list, total, page, size, pages } */
export function normalizePage(payload, page, size) {
  const currentPage = Number(page) || DEFAULT_PAGE
  const currentSize = Number(size) || DEFAULT_SIZE
  const list = toList(payload)
  const total = Number(payload && payload.total) || list.length
  const pages =
    Number(payload && payload.pages) ||
    (currentSize > 0 ? Math.ceil(total / currentSize) : 0)
  return {
    list: list.map(normalizeDish),
    total,
    page: Number(payload && payload.page) || currentPage,
    size: Number(payload && payload.size) || currentSize,
    pages
  }
}

export function normalizeCategory(raw) {
  const item = raw || {}
  return {
    id: item.id !== undefined ? item.id : item.categoryId,
    name: item.name || item.categoryName || '',
    sort: Number(item.sort !== undefined ? item.sort : item.sortOrder || 0),
    dishCount: Number(item.dishCount !== undefined ? item.dishCount : 0),
    status: item.status
  }
}

export function normalizeDish(raw) {
  const item = raw || {}
  const stockValue = item.stock !== undefined ? item.stock : item.stockQuantity
  const stock = stockValue === undefined || stockValue === null ? null : Number(stockValue)
  return {
    id: item.id !== undefined ? item.id : item.dishId,
    categoryId: item.categoryId !== undefined ? item.categoryId : item.category_id,
    categoryName: item.categoryName || '',
    name: item.name || item.dishName || '',
    description: item.description || item.desc || item.remark || '',
    price: Number(item.price !== undefined ? item.price : item.unitPrice || 0),
    originalPrice: item.originalPrice === undefined || item.originalPrice === null ? null : Number(item.originalPrice),
    image: item.imageUrl || item.image || item.coverUrl || item.picture || '',
    sales: Number(item.sales !== undefined ? item.sales : item.salesCount || 0),
    stock,
    soldOut: item.soldOut === true || (stock !== null && stock <= 0),
    status: item.status,
    tags: Array.isArray(item.tags) ? item.tags : []
  }
}

/** GET /menu/categories（可选 merchantId） */
export function fetchCategories(merchantId) {
  if (IS_MOCK) {
    return mockDelay(MOCK_CATEGORIES.map(normalizeCategory), 180)
  }
  const query = {}
  if (merchantId !== undefined && merchantId !== null) query.merchantId = merchantId
  return http.get('/menu/categories', query).then((payload) => toList(payload).map(normalizeCategory))
}

/**
 * GET /menu/dishes（分页）
 * @param {{categoryId?:number|string, keyword?:string, sort?:string, page?:number, size?:number, merchantId?:number|string}} params
 * @returns {Promise<{list:Array,total:number,page:number,size:number,pages:number}>}
 */
export function fetchDishes(params = {}) {
  const page = Math.max(DEFAULT_PAGE, Number(params.page) || DEFAULT_PAGE)
  const size = Math.min(MAX_SIZE, Math.max(1, Number(params.size) || DEFAULT_SIZE))
  const keyword = String(params.keyword || '').trim()
  const sort = params.sort || ''

  if (IS_MOCK) {
    const list = MOCK_DISHES.filter((dish) => {
      const matchCategory =
        params.categoryId === undefined ||
        params.categoryId === null ||
        params.categoryId === '' ||
        String(dish.categoryId) === String(params.categoryId)
      const matchKeyword =
        !keyword ||
        dish.name.indexOf(keyword) !== -1 ||
        String(dish.description || '').indexOf(keyword) !== -1
      return matchCategory && matchKeyword
    })
    const start = (page - 1) * size
    const pageList = list.slice(start, start + size)
    return mockDelay(
      {
        list: pageList.map(normalizeDish),
        total: list.length,
        page,
        size,
        pages: size > 0 ? Math.ceil(list.length / size) : 0
      },
      220
    )
  }

  const query = { page, size }
  if (params.categoryId !== undefined && params.categoryId !== null && params.categoryId !== '') {
    query.categoryId = params.categoryId
  }
  if (keyword) query.keyword = keyword
  if (sort && sort !== 'default') query.sort = sort
  if (params.merchantId !== undefined && params.merchantId !== null) query.merchantId = params.merchantId

  return http.get('/menu/dishes', query).then((payload) => normalizePage(payload, page, size))
}
