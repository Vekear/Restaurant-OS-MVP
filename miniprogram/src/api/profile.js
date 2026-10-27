import { http } from './request'

/**
 * 个人资料（契约 docs/02-api.md §4.6）。
 * PUT /auth/profile { nickname?, phone?, avatarUrl? } → UserView
 */
export function updateProfile(payload) {
  const body = {}
  if (payload.nickname !== undefined) body.nickname = payload.nickname
  if (payload.phone !== undefined) body.phone = payload.phone
  if (payload.avatarUrl !== undefined) body.avatarUrl = payload.avatarUrl
  return http.put('/auth/profile', body, { silent: true, loading: true, loadingText: '保存中' })
}
