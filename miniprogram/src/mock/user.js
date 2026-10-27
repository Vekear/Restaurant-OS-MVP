/** 演示模式：内置用户与登录响应 */
export const MOCK_USER = {
  id: 1001,
  nickname: '微信用户',
  avatarUrl: '',
  role: 'CUSTOMER'
}

export function buildMockLoginResponse(code) {
  return {
    token: `mock-token-${Date.now()}`,
    tokenType: 'Bearer',
    expiresIn: 7200,
    user: Object.assign({}, MOCK_USER, { openid: code ? `mock-openid-${code}` : 'mock-openid' })
  }
}
