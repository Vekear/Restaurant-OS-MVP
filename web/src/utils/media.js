/**
 * 图片/静态资源地址处理
 *
 * 后端返回的图片为站内相对路径（如 /files/dish/2026/10/uuid.png），
 * 由 Vite 代理（dev）或 Nginx（prod）直接提供，无需拼接域名。
 */

/** 判断是否为可直接使用的绝对地址 */
export function isAbsoluteUrl(value) {
  return /^(https?:)?\/\//i.test(value) || /^(data|blob):/i.test(value)
}

/**
 * 解析图片地址
 * @param {string|null|undefined} value
 * @param {string} [fallback] 无图时的兜底（默认空字符串，交由 el-image 错误插槽渲染占位）
 */
export function resolveImageUrl(value, fallback = '') {
  const url = String(value || '').trim()
  if (!url) return fallback
  if (isAbsoluteUrl(url)) return url
  if (url.startsWith('/')) return url
  return `/${url.replace(/^\.?\//, '')}`
}
