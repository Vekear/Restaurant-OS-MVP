/**
 * 管理后台接口（docs/02-api.md §20）
 * 统一守卫：role=ADMIN；所有写操作后端会写 audit_log。
 * 列表默认 createdAt DESC，同值 id ASC；page/size ≤100。
 */
import { http } from './http'

/* ==================== 用户管理 ==================== */

/**
 * GET /admin/users
 * @param {{ keyword?:string, role?:string, status?:string, page?:number, size?:number }} params
 */
export function listUsers(params = {}) {
  return http.get('/admin/users', { params, silent: true })
}

/**
 * PATCH /admin/users/{id}/status
 * @param {number|string} id
 * @param {{ status:'ENABLED'|'DISABLED', reason?:string }} payload
 */
export function updateUserStatus(id, payload) {
  return http.patch(`/admin/users/${id}/status`, payload, { silent: true })
}

/* ==================== 商家管理 ==================== */

/**
 * GET /admin/merchants
 * @param {{ keyword?:string, auditStatus?:string, status?:string, page?:number, size?:number }} params
 */
export function listMerchants(params = {}) {
  return http.get('/admin/merchants', { params, silent: true })
}

/** GET /admin/merchants/{id} */
export function getMerchant(id) {
  return http.get(`/admin/merchants/${id}`, { silent: true })
}

/**
 * PATCH /admin/merchants/{id}/status
 * @param {number|string} id
 * @param {{ auditStatus?:'APPROVED'|'REJECTED', status?:'ENABLED'|'DISABLED', reason?:string }} payload
 */
export function updateMerchantStatus(id, payload) {
  return http.patch(`/admin/merchants/${id}/status`, payload, { silent: true })
}

/* ==================== 订单管理 ==================== */

/**
 * GET /admin/orders
 * @param {{ merchantId?:number|string, orderType?:string, status?:string,
 *           startDate?:string, endDate?:string, keyword?:string,
 *           page?:number, size?:number }} params
 */
export function listOrders(params = {}) {
  return http.get('/admin/orders', { params, silent: true })
}

/** GET /admin/orders/{id} → OrderDetailView */
export function getOrder(id) {
  return http.get(`/admin/orders/${id}`, { silent: true })
}

/** GET /admin/orders/export —— xlsx 流（列同商家导出并追加「商家」列） */
export function exportOrders(params = {}) {
  return http.download('/admin/orders/export', params)
}

/* ==================== 优惠券 ==================== */

/**
 * GET /admin/coupons
 * @param {{ status?:string, keyword?:string, page?:number, size?:number }} params
 */
export function listCoupons(params = {}) {
  return http.get('/admin/coupons', { params, silent: true })
}

/**
 * POST /admin/coupons
 * @param {{ name:string, thresholdAmount:number, discountAmount:number, total:number,
 *           startAt:string, endAt:string }} payload
 */
export function createCoupon(payload) {
  return http.post('/admin/coupons', payload, { silent: true })
}

/** GET /admin/coupons/{id} */
export function getCoupon(id) {
  return http.get(`/admin/coupons/${id}`, { silent: true })
}

/** PUT /admin/coupons/{id} —— 全量更新；total 不得小于已领取数（40918） */
export function updateCoupon(id, payload) {
  return http.put(`/admin/coupons/${id}`, payload, { silent: true })
}

/** DELETE /admin/coupons/{id} —— 已有领取记录时后端返回 40918 */
export function deleteCoupon(id) {
  return http.delete(`/admin/coupons/${id}`, { silent: true })
}

/** PATCH /admin/coupons/{id}/status */
export function updateCouponStatus(id, status) {
  return http.patch(`/admin/coupons/${id}/status`, { status }, { silent: true })
}

/* ==================== 平台统计 ==================== */

/**
 * GET /admin/stats/overview —— 默认近 30 天，跨度 ≤92 天
 * @param {{ startDate?:string, endDate?:string }} params
 */
export function getStatsOverview(params = {}) {
  return http.get('/admin/stats/overview', { params, silent: true })
}

/** GET /admin/stats/trend → TrendView */
export function getStatsTrend(params = {}) {
  return http.get('/admin/stats/trend', { params, silent: true })
}

/** GET /admin/stats/order-type-ratio → { items: RatioItem[] } */
export function getStatsOrderTypeRatio(params = {}) {
  return http.get('/admin/stats/order-type-ratio', { params, silent: true })
}

/**
 * GET /admin/stats/hot-dishes
 * @param {{ startDate?:string, endDate?:string, limit?:number }} params
 */
export function getStatsHotDishes(params = {}) {
  return http.get('/admin/stats/hot-dishes', { params, silent: true })
}

/** GET /admin/stats/export —— xlsx（平台汇总/趋势/订单类型/热销菜品/商家排行 5 sheet） */
export function exportStats(params = {}) {
  return http.download('/admin/stats/export', params)
}

/* ==================== 审计日志 ==================== */

/**
 * GET /admin/audit-logs
 * @param {{ action?:string, userId?:number|string, startDate?:string, endDate?:string,
 *           page?:number, size?:number }} params
 */
export function listAuditLogs(params = {}) {
  return http.get('/admin/audit-logs', { params, silent: true })
}

export const adminApi = {
  listUsers,
  updateUserStatus,
  listMerchants,
  getMerchant,
  updateMerchantStatus,
  listOrders,
  getOrder,
  exportOrders,
  listCoupons,
  createCoupon,
  getCoupon,
  updateCoupon,
  deleteCoupon,
  updateCouponStatus,
  getStatsOverview,
  getStatsTrend,
  getStatsOrderTypeRatio,
  getStatsHotDishes,
  exportStats,
  listAuditLogs
}

export default adminApi
