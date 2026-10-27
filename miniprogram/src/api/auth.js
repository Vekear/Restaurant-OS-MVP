import { http } from './request'
import { IS_MOCK, mockDelay } from '@/mock'
import { buildMockLoginResponse } from '@/mock/user'

/**
 * 微信小程序登录：`uni.login` 得到的 code 交给后端换取 JWT。
 * 后端接口（契约）：POST /api/v1/auth/wx/login  body: { code }
 * 响应 data 兼容多种命名（token / accessToken，user / userInfo）。
 *
 * 注意：后端开启 `wechat.mock=true` 时接受任意 code；小程序端在无法获取真实 code
 * （H5 预览、开发者工具限制）时回退到 mock code，便于本地联调。
 */
export function wxLogin(code) {
  if (IS_MOCK) {
    return mockDelay(buildMockLoginResponse(code), 420)
  }
  return http.post(
    '/auth/wx/login',
    { code },
    { auth: false, silent: true }
  )
}

/** 将后端登录响应归一化为 { token, user } */
export function normalizeLoginResult(payload) {
  const data = payload || {}
  const token = data.token || data.accessToken || data.access_token || ''
  const rawUser = data.user || data.userInfo || data.customer || null
  const user =
    rawUser ||
    (data.userId || data.nickname || data.nickName
      ? {
          id: data.userId || data.id || null,
          nickname: data.nickname || data.nickName || '',
          avatarUrl: data.avatarUrl || data.avatar || '',
          role: data.role || 'CUSTOMER'
        }
      : null)
  return {
    token: String(token || ''),
    user: user
      ? {
          id: user.id !== undefined ? user.id : null,
          nickname: user.nickname || user.nickName || '微信用户',
          avatarUrl: user.avatarUrl || user.avatar || '',
          role: user.role || 'CUSTOMER'
        }
      : null
  }
}
