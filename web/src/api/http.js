/**
 * axios 统一封装
 *
 * 约定（对齐 docs/reference/brief.md §9）：
 *   - 基地址 /api/v1，Dev 由 vite.config.js 代理到 127.0.0.1:8080，Prod 由 Nginx 反代
 *   - 统一响应体 {code, message, data, traceId}，code=0|200 视为成功并解包出 data
 *   - 请求自动携带 Authorization: Bearer <token>（来源 utils/storage）
 *   - 401：清理会话 + 跳转登录（通过 setUnauthorizedHandler 注册，避免与 store 循环依赖）
 *   - 业务错误 / HTTP 错误统一 ElMessage 提示；config.silent 可静默，config.skipAuthRedirect 不触发登录跳转
 *   - config.idempotent 自动生成 Idempotency-Key（下单幂等，brief §4.4）
 */
import axios from 'axios'
import { ElMessage } from 'element-plus'
import { STORAGE_KEYS, readRaw } from '@/utils/storage'

const BASE_URL = import.meta.env.VITE_API_BASE_URL || '/api/v1'
const REQUEST_TIMEOUT = 15000

/** @type {null | ((message?: string) => void)} */
let unauthorizedHandler = null

/** 由 main.js 注册：401 时清理 Pinia 会话并跳转登录页 */
export function setUnauthorizedHandler(handler) {
  unauthorizedHandler = handler
}

export const service = axios.create({
  baseURL: BASE_URL,
  timeout: REQUEST_TIMEOUT,
  headers: { 'Content-Type': 'application/json' }
})

function getToken() {
  return readRaw(STORAGE_KEYS.token) || ''
}

function createRequestId() {
  if (globalThis.crypto?.randomUUID) return globalThis.crypto.randomUUID()
  return `web-${Date.now()}-${Math.random().toString(16).slice(2, 10)}`
}

let lastAuthExpiredAt = 0

function notifyAuthExpired(message) {
  const now = Date.now()
  // 并发请求同时 401 时只提示/跳转一次
  const shouldNotify = now - lastAuthExpiredAt > 2000
  lastAuthExpiredAt = now
  if (shouldNotify) ElMessage.warning(message || '登录已过期，请重新登录')
  if (unauthorizedHandler) unauthorizedHandler(message)
}

function extractMessage(body) {
  if (!body) return ''
  if (typeof body === 'string') return body
  return body.message || body.msg || body.error || ''
}

service.interceptors.request.use(
  (config) => {
    const token = getToken()
    if (token) {
      config.headers.Authorization = token.startsWith('Bearer ') ? token : `Bearer ${token}`
    }
    if (config.idempotent) {
      config.headers['Idempotency-Key'] = config.headers['Idempotency-Key'] || createRequestId()
    }
    return config
  },
  (error) => Promise.reject(error)
)

service.interceptors.response.use(
  (response) => {
    // 文件下载等场景需要拿到 headers（Content-Disposition），原样返回
    if (response.config?.raw || response.config?.responseType === 'blob') return response

    const body = response.data
    if (body && typeof body === 'object' && Object.prototype.hasOwnProperty.call(body, 'code')) {
      if (body.code === 0 || body.code === 200) return body.data

      const message = extractMessage(body) || '请求失败'
      const error = new Error(message)
      error.code = body.code
      error.traceId = body.traceId
      error.data = body.data
      if (!response.config?.silent) ElMessage.error(message)
      return Promise.reject(error)
    }
    return body
  },
  (error) => {
    const config = error.config || {}
    const response = error.response
    const body = response?.data
    const status = response?.status
    let message = ''

    if (response) {
      if (status === 401 && !config.skipAuthRedirect) {
        notifyAuthExpired(extractMessage(body) || '登录已过期，请重新登录')
        const expired = new Error(extractMessage(body) || '登录已过期')
        expired.status = status
        return Promise.reject(expired)
      }
      switch (status) {
        case 400:
          message = extractMessage(body) || '请求参数有误'
          break
        case 401:
          message = extractMessage(body) || '用户名或密码错误'
          break
        case 403:
          message = extractMessage(body) || '没有访问该资源的权限'
          break
        case 404:
          message = extractMessage(body) || '接口不存在，请确认后端已启动'
          break
        case 409:
          message = extractMessage(body) || '操作冲突，请刷新后重试'
          break
        case 429:
          message = extractMessage(body) || '操作过于频繁，请稍后再试'
          break
        default:
          message =
            extractMessage(body) ||
            (status >= 500 ? '服务器开小差了，请稍后重试' : `请求失败（${status}）`)
      }
    } else if (error.code === 'ECONNABORTED' || error.code === 'ETIMEDOUT') {
      message = '请求超时，请检查网络后重试'
    } else if (error.code === 'ERR_NETWORK') {
      message = '无法连接服务器，请确认后端服务（8080）已启动'
    } else {
      message = error.message || '网络异常，请稍后重试'
    }

    if (!config.silent) ElMessage.error(message)

    const wrapped = new Error(message)
    wrapped.status = status
    wrapped.code = body?.code
    wrapped.traceId = body?.traceId
    return Promise.reject(wrapped)
  }
)

function parseFilename(headers, fallback = 'download.xlsx') {
  const disposition = headers?.['content-disposition'] || headers?.['Content-Disposition'] || ''
  const utf8Match = disposition.match(/filename\*=UTF-8''([^;]+)/i)
  if (utf8Match) return decodeURIComponent(utf8Match[1])
  const match = disposition.match(/filename="?([^";]+)"?/i)
  return match ? decodeURIComponent(match[1]) : fallback
}

function saveBlob(blob, filename) {
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  URL.revokeObjectURL(url)
}

export const http = {
  get(url, config) {
    return service.get(url, config)
  },
  post(url, data, config) {
    return service.post(url, data, config)
  },
  put(url, data, config) {
    return service.put(url, data, config)
  },
  patch(url, data, config) {
    return service.patch(url, data, config)
  },
  delete(url, config) {
    return service.delete(url, config)
  },
  /**
   * multipart 上传（本地兜底存储 /api/v1/files/upload）
   * @param {string} url
   * @param {File} file
   * @param {{ field?: string, onProgress?: (percent:number)=>void, data?: Record<string, any> }} options
   */
  upload(url, file, options = {}) {
    const { field = 'file', onProgress, data = {}, ...config } = options
    const formData = new FormData()
    formData.append(field, file)
    Object.entries(data).forEach(([key, value]) => formData.append(key, value))
    return service.post(url, formData, {
      ...config,
      headers: { 'Content-Type': 'multipart/form-data' },
      onUploadProgress: (event) => {
        if (onProgress && event.total) {
          onProgress(Math.round((event.loaded / event.total) * 100))
        }
      }
    })
  },
  /**
   * 下载二进制文件（Excel 导出）。若后端返回 JSON 错误，转为异常提示。
   */
  download(url, params, filename) {
    return service.get(url, { params, responseType: 'blob', raw: true }).then(async (response) => {
      const blob = response.data
      const contentType = response.headers?.['content-type'] || blob?.type || ''
      if (contentType.includes('application/json')) {
        const text = await blob.text()
        let message = '导出失败'
        try {
          message = JSON.parse(text)?.message || message
        } catch {
          /* 保底使用默认提示 */
        }
        ElMessage.error(message)
        throw new Error(message)
      }
      saveBlob(blob, filename || parseFilename(response.headers))
      return true
    })
  }
}

export default http
