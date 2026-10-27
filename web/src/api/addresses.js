/**
 * 收货地址接口（docs/02-api.md §5）
 * 仅本人地址；单用户最多 20 条。
 */
import { http } from './http'

/** GET /addresses —— 按 isDefault DESC, updatedAt DESC */
export function listAddresses() {
  return http.get('/addresses', { silent: true })
}

/**
 * POST /addresses
 * @param {{ consignee:string, phone:string, province:string, city:string,
 *           district:string, detail:string, isDefault?:boolean }} payload
 */
export function createAddress(payload) {
  return http.post('/addresses', payload, { silent: true })
}

/** GET /addresses/{id} */
export function getAddress(id) {
  return http.get(`/addresses/${id}`, { silent: true })
}

/** PUT /addresses/{id} —— 全量更新 */
export function updateAddress(id, payload) {
  return http.put(`/addresses/${id}`, payload, { silent: true })
}

/** DELETE /addresses/{id} */
export function deleteAddress(id) {
  return http.delete(`/addresses/${id}`, { silent: true })
}

export const addressApi = {
  listAddresses,
  createAddress,
  getAddress,
  updateAddress,
  deleteAddress
}

export default addressApi
