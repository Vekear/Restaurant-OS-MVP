/**
 * 商家店铺 / 看板 / 报表导出接口（docs/02-api.md §15、§19）
 * 统一守卫：role=MERCHANT；统计口径 Asia/Shanghai 自然日，仅统计 COMPLETED，金额取 orders.pay_amount。
 */
import { http } from './http'

/* ==================== 店铺与营业状态 ==================== */

/** GET /merchant/profile —— 商家资料（含 auditStatus/status） */
export function getMerchantProfile(options = {}) {
  return http.get('/merchant/profile', { silent: true, ...options })
}

/**
 * PUT /merchant/profile
 * @param {{ name:string, description?:string, phone:string, address:string,
 *           logoUrl?:string, deliveryFee:number, minOrderAmount:number }} payload
 */
export function updateMerchantProfile(payload) {
  return http.put('/merchant/profile', payload, { silent: true })
}

/** GET /merchant/business-status → { businessStatus } */
export function getBusinessStatus() {
  return http.get('/merchant/business-status', { silent: true })
}

/**
 * PUT /merchant/business-status —— 仅拦截新订单（40908），存量订单照常流转
 * @param {'OPEN'|'CLOSED'} businessStatus
 */
export function updateBusinessStatus(businessStatus) {
  return http.put('/merchant/business-status', { businessStatus }, { silent: true })
}

/* ==================== 看板 ==================== */

/**
 * GET /merchant/dashboard/summary
 * @param {{ date?: string }} params yyyy-MM-dd，默认今天
 */
export function getDashboardSummary(params = {}) {
  return http.get('/merchant/dashboard/summary', { params, silent: true })
}

/**
 * GET /merchant/dashboard/trend —— 按自然日补零，跨度 ≤92 天
 * @param {{ startDate?: string, endDate?: string }} params
 */
export function getDashboardTrend(params = {}) {
  return http.get('/merchant/dashboard/trend', { params, silent: true })
}

/**
 * GET /merchant/dashboard/hot-dishes
 * @param {{ startDate?: string, endDate?: string, limit?: number }} params
 */
export function getHotDishes(params = {}) {
  return http.get('/merchant/dashboard/hot-dishes', { params, silent: true })
}

/**
 * GET /merchant/dashboard/order-type-ratio
 * @param {{ startDate?: string, endDate?: string }} params
 */
export function getOrderTypeRatio(params = {}) {
  return http.get('/merchant/dashboard/order-type-ratio', { params, silent: true })
}

/* ==================== 评价 ==================== */

/**
 * GET /merchant/reviews
 * @param {{ rating?: number, page?: number, size?: number }} params
 */
export function listReviews(params = {}) {
  return http.get('/merchant/reviews', { params, silent: true })
}

/* ==================== 报表导出（xlsx 流） ==================== */

/**
 * GET /merchant/export/orders —— 订单明细 xlsx（含「订单实付金额」「模拟扣款金额」数值列）
 * @param {{ startDate:string, endDate:string, status?:string }} params
 * @returns {Promise<boolean>}
 */
export function exportOrders(params) {
  return http.download('/merchant/export/orders', params)
}

/**
 * GET /merchant/export/sales —— 营业报表 xlsx（汇总/趋势/热销/订单类型 4 个 sheet）
 * @param {{ startDate:string, endDate:string }} params
 * @returns {Promise<boolean>}
 */
export function exportSales(params) {
  return http.download('/merchant/export/sales', params)
}

export const merchantApi = {
  getMerchantProfile,
  updateMerchantProfile,
  getBusinessStatus,
  updateBusinessStatus,
  getDashboardSummary,
  getDashboardTrend,
  getHotDishes,
  getOrderTypeRatio,
  listReviews,
  exportOrders,
  exportSales
}

export default merchantApi
