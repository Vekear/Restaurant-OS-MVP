/* 路由查询参数安全校验：只允许站内路径，避免开放重定向 */
export function safeRedirect(value) {
  if (typeof value !== 'string') return ''
  const target = value.trim()
  if (!target.startsWith('/') || target.startsWith('//')) return ''
  if (target.startsWith('/login') || target.startsWith('/register')) return ''
  return target
}

export default safeRedirect
