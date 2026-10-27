/**
 * 菜单 / 店铺 / 公开配置接口（docs/02-api.md §3、§6）
 * 公开浏览，未登录也可访问；页面自带错误态，故默认 silent 关闭全局 toast。
 */
import { http } from './http'

/** GET /public/config —— 默认商家、模拟支付参数、文件/WS 配置 */
export function getPublicConfig() {
  return http.get('/public/config', { silent: true })
}

/** GET /merchants/{id} —— 店铺公开信息（配送费、起送价、营业状态） */
export function getMerchant(id) {
  return http.get(`/merchants/${id}`, { silent: true })
}

/** GET /menu/categories —— 分类（含 dishCount） */
export function getCategories(params = {}) {
  return http.get('/menu/categories', { params, silent: true })
}

/**
 * GET /menu/dishes —— 菜品分页
 * @param {{ merchantId?: number, categoryId?: number, keyword?: string,
 *           sort?: 'default'|'price_asc'|'price_desc'|'sales_desc',
 *           page?: number, size?: number }} params
 */
export function getDishes(params = {}) {
  return http.get('/menu/dishes', { params, silent: true })
}

/** GET /menu/dishes/{id} —— 菜品详情（含评分聚合） */
export function getDish(id) {
  return http.get(`/menu/dishes/${id}`, { silent: true })
}

/** GET /menu/dishes/{id}/reviews —— 菜品评价分页 */
export function getDishReviews(id, params = {}) {
  return http.get(`/menu/dishes/${id}/reviews`, { params, silent: true })
}

export const menuApi = {
  getPublicConfig,
  getMerchant,
  getCategories,
  getDishes,
  getDish,
  getDishReviews
}

export default menuApi
