/**
 * 商家桌台与二维码接口（docs/02-api.md §17）
 * 变更桌号会自动重签二维码（qrVersion+1，旧码立即失效）；重签接口返回 TableQrView。
 */
import { http } from './http'

/** GET /merchant/tables → TableView[]（最多 200，按 tableNo ASC） */
export function listTables() {
  return http.get('/merchant/tables', { silent: true })
}

/**
 * POST /merchant/tables
 * @param {{ tableNo:string, name?:string, capacity?:number, zone?:string }} payload
 */
export function createTable(payload) {
  return http.post('/merchant/tables', payload, { silent: true })
}

/** GET /merchant/tables/{id} */
export function getTable(id) {
  return http.get(`/merchant/tables/${id}`, { silent: true })
}

/**
 * PUT /merchant/tables/{id}
 * @param {number} id
 * @param {{ tableNo?:string, name?:string, capacity?:number, zone?:string }} payload
 */
export function updateTable(id, payload) {
  return http.put(`/merchant/tables/${id}`, payload, { silent: true })
}

/** DELETE /merchant/tables/{id} —— 存在 ACTIVE 会话或进行中订单 → 40909 */
export function deleteTable(id) {
  return http.delete(`/merchant/tables/${id}`, { silent: true })
}

/** GET /merchant/tables/{id}/qr → TableQrView */
export function getTableQr(id) {
  return http.get(`/merchant/tables/${id}/qr`, { silent: true })
}

/** POST /merchant/tables/{id}/qr —— 重新签名（旧码立即失效） */
export function regenerateTableQr(id) {
  return http.post(`/merchant/tables/${id}/qr`, null, { silent: true })
}

export const merchantTableApi = {
  listTables,
  createTable,
  getTable,
  updateTable,
  deleteTable,
  getTableQr,
  regenerateTableQr
}

export default merchantTableApi
