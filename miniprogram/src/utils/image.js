import { API_ORIGIN } from './config'

/** 图片兜底占位图（本地静态资源，永不 404） */
export const FALLBACK_IMAGE = '/static/placeholder.png'

/**
 * 将后端返回的图片地址解析为可展示地址：
 * - 绝对地址、协议相对地址、base64 原样返回；
 * - 以 /static/ 开头的本地静态资源原样返回（不可拼接后端源站）；
 * - 以 / 开头的后端资源（如 /files/xxx.png）拼接 API_ORIGIN（H5 同源时为空，交给 vite 代理 / nginx）。
 */
export function resolveImageUrl(url) {
  const raw = String(url || '').trim()
  if (!raw) return FALLBACK_IMAGE
  if (/^(https?:)?\/\//i.test(raw) || raw.startsWith('data:')) return raw
  if (raw.startsWith('/static/')) return raw
  if (raw.startsWith('/')) return API_ORIGIN ? `${API_ORIGIN}${raw}` : raw
  return raw
}
