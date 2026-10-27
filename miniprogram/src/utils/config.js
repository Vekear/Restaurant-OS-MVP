/**
 * 全局运行时配置。
 * 说明：不使用 any 类型标注（项目禁止 TypeScript），所有配置均为原生 JavaScript。
 */

/** 后端本地开发地址（小程序端必须使用绝对地址；H5 开发期走 vite 代理） */
const DEFAULT_BACKEND_ORIGIN = 'http://127.0.0.1:8080'

/** 是否运行在微信小程序/小程序容器中（H5 中 wx 为 undefined） */
export const IS_MINI_PROGRAM = typeof wx !== 'undefined' && typeof wx.getSystemInfoSync === 'function'

const env = import.meta.env || {}
const rawBaseUrl = String(env.VITE_API_BASE_URL || '').trim()

/**
 * API 根地址（含 /api/v1）。
 * - 配置了 VITE_API_BASE_URL：可写 `https://host` 或 `https://host/api/v1`，自动补齐 /api/v1。
 * - 未配置：H5 使用同源 `/api/v1`；小程序使用 http://127.0.0.1:8080/api/v1。
 */
export const API_BASE_URL = (() => {
  if (rawBaseUrl) {
    const trimmed = rawBaseUrl.replace(/\/+$/, '')
    return /\/api\/v1$/.test(trimmed) ? trimmed : `${trimmed}/api/v1`
  }
  return IS_MINI_PROGRAM ? `${DEFAULT_BACKEND_ORIGIN}/api/v1` : '/api/v1'
})()

/** 后端源站（用于拼接 /files/** 静态资源），H5 同源时为空字符串 */
export const API_ORIGIN = /^https?:\/\//i.test(API_BASE_URL) ? API_BASE_URL.replace(/\/api\/v1$/, '') : ''

/** 演示/走查模式：菜单、登录使用内置假数据，不依赖后端 */
export const IS_MOCK = String(env.VITE_MOCK || '').toLowerCase() === 'true'

/** 开发环境标记（vite 内置） */
export const IS_DEV = Boolean(env.DEV)

/** 请求超时（毫秒） */
export const REQUEST_TIMEOUT = 15000

/** 应用信息 */
export const APP_NAME = '小馆点餐'
export const APP_SUBTITLE = '现点现做 · 堂食点餐'
export const APP_VERSION = '0.1.0'
