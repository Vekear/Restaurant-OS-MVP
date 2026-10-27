import { http } from './request'
import { IS_MOCK, mockDelay } from '@/mock'
import { MOCK_CATEGORIES, MOCK_DISHES } from '@/mock/menu'

/**
 * 菜单接口封装（契约集中在此文件，后端字段如有调整只需改这里）。
 * - GET /api/v1/menu/categories                分类列表
 * - GET /api/v1/menu/items?categoryId=&keyword= 菜品列表（后端支持关键字搜索）
 */

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

export function normalizeCategory(raw) {
  const item = raw || {}
  return {
    id: item.id !== undefined ? item.id : item.categoryId,
    name: item.name || item.categoryName || '',
    sort: Number(item.sort !== undefined ? item.sort : item.sortOrder || 0)
  }
}

export function normalizeDish(raw) {
  const item = raw || {}
  const stockValue = item.stock !== undefined ? item.stock : item.stockQuantity
  const stock = stockValue === undefined || stockValue === null ? null : Number(stockValue)
  return {
    id: item.id !== undefined ? item.id : item.dishId,
    categoryId: item.categoryId !== undefined ? item.categoryId : item.category_id,
    name: item.name || item.dishName || '',
    description: item.description || item.desc || item.remark || '',
    price: Number(item.price !== undefined ? item.price : item.unitPrice || 0),
    image: item.imageUrl || item.image || item.coverUrl || item.picture || '',
    sales: Number(item.sales !== undefined ? item.sales : item.salesCount || 0),
    stock,
    soldOut: stock !== null && stock <= 0,
    tags: Array.isArray(item.tags) ? item.tags : [],
    status: item.status
  }
}

export function fetchCategories() {
  if (IS_MOCK) {
    return mockDelay(MOCK_CATEGORIES, 180)
  }
  return http.get('/menu/categories').then((payload) => toList(payload).map(normalizeCategory))
}

export function fetchDishes(params = {}) {
  const { categoryId, keyword } = params
  if (IS_MOCK) {
    const key = String(keyword || '').trim()
    const list = MOCK_DISHES.filter((dish) => {
      const matchCategory =
        categoryId === undefined || categoryId === null || String(dish.categoryId) === String(categoryId)
      const matchKeyword =
        !key ||
        dish.name.indexOf(key) !== -1 ||
        String(dish.description || '').indexOf(key) !== -1
      return matchCategory && matchKeyword
    })
    return mockDelay(list.map(normalizeDish), 220)
  }

  const query = {}
  if (categoryId !== undefined && categoryId !== null && categoryId !== '') {
    query.categoryId = categoryId
  }
  if (keyword) query.keyword = keyword
  return http.get('/menu/items', query).then((payload) => toList(payload).map(normalizeDish))
}
