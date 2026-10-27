/**
 * 优惠券接口（docs/02-api.md §8）
 * 本期仅 FULL_REDUCTION（满减），不可叠加，单用户每券限领 1 张。
 */
import { http } from './http'

/**
 * GET /coupons —— 可领取券模板分页（公开可领，登录后计算 claimed）
 * @param {{ amount?: number, page?: number, size?: number }} params
 */
export function listCoupons(params = {}) {
  return http.get('/coupons', { params, silent: true })
}

/** POST /coupons/{couponId}/claim —— 领取 */
export function claimCoupon(couponId) {
  return http.post(`/coupons/${couponId}/claim`, null, { silent: true })
}

/**
 * GET /coupons/mine —— 我的券
 * @param {{ status?: 'UNUSED'|'USED'|'EXPIRED'|'ALL', amount?: number, page?: number, size?: number }} params
 */
export function listMyCoupons(params = {}) {
  return http.get('/coupons/mine', { params, silent: true })
}

/**
 * POST /coupons/quote —— 试算（服务端算优惠，前端不信任本地金额）
 * @param {{ merchantId:number, totalAmount:number, userCouponId?:number }} payload
 */
export function quoteCoupon(payload) {
  return http.post('/coupons/quote', payload, { silent: true })
}

export const couponApi = {
  listCoupons,
  claimCoupon,
  listMyCoupons,
  quoteCoupon
}

export default couponApi
