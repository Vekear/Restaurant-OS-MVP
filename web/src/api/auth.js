/**
 * 鉴权相关接口
 *
 * 说明：architect 的 docs/02-api.md 尚未产出（T02 未完成），这里先按 brief §4.7 / §9 的约定实现：
 *   POST /api/v1/auth/login    { username, password } -> data: { token, user } | { token, ...用户字段 }
 *   POST /api/v1/auth/register { username, password, nickname?, phone? }
 *   POST /api/v1/auth/logout
 *   GET  /api/v1/auth/me       -> data: user
 * 登录响应对 token/accessToken、user/userInfo 做了兼容解包（见 stores/auth.js）。
 * T02 产出接口契约后如字段有差异，只需调整本文件与 stores/auth.js 的归一化逻辑。
 */
import { http } from './http'

export function login(payload) {
  return http.post('/auth/login', payload, { skipAuthRedirect: true })
}

export function register(payload) {
  return http.post('/auth/register', payload, { skipAuthRedirect: true })
}

export function logout() {
  return http.post('/auth/logout', null, { silent: true, skipAuthRedirect: true })
}

export function fetchProfile() {
  return http.get('/auth/me', { silent: true })
}

export const authApi = {
  login,
  register,
  logout,
  fetchProfile
}

export default authApi
