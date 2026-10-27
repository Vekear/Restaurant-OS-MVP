import { API_BASE_URL, API_ORIGIN, REQUEST_TIMEOUT, IS_DEV } from '@/utils/config'
import { getToken, clearSession } from '@/utils/session'
import { getTableSessionToken } from '@/utils/tableSession'

const LOGIN_PAGE = '/pages/login/login'

let loadingCount = 0
let navigatingToLogin = false

function showLoading(title) {
  loadingCount += 1
  if (loadingCount === 1) {
    uni.showLoading({ title: title || '加载中', mask: true })
  }
}

function hideLoading() {
  loadingCount = Math.max(0, loadingCount - 1)
  if (loadingCount === 0) {
    uni.hideLoading()
  }
}

function toast(message) {
  if (!message) return
  uni.showToast({ title: String(message).slice(0, 40), icon: 'none', duration: 2000 })
}

/** 构造带上下文的错误对象，页面可按 err.code / err.statusCode 自行处理 */
export function createApiError(message, options = {}) {
  const error = new Error(message || '请求失败')
  error.name = 'ApiError'
  error.code = options.code !== undefined ? options.code : null
  error.statusCode = options.statusCode || 0
  error.traceId = options.traceId || ''
  error.type = options.type || 'business'
  return error
}

/** 统一登录失效处理：清理登录态、通知全局、跳转登录页（防重入） */
function handleUnauthorized() {
  clearSession()
  uni.$emit('auth:unauthorized')
  if (navigatingToLogin) return
  const pages = typeof getCurrentPages === 'function' ? getCurrentPages() : []
  const currentRoute = pages.length ? pages[pages.length - 1].route || '' : ''
  if (currentRoute.indexOf('pages/login/login') !== -1) return
  navigatingToLogin = true
  uni.navigateTo({
    url: LOGIN_PAGE,
    fail: () => {
      uni.reLaunch({ url: LOGIN_PAGE })
    },
    complete: () => {
      setTimeout(() => {
        navigatingToLogin = false
      }, 1200)
    }
  })
}

function buildUrl(url) {
  if (/^https?:\/\//i.test(url)) return url
  const path = url.startsWith('/') ? url : `/${url}`
  return `${API_BASE_URL}${path}`
}

/** 将接口路径或后端下发的绝对路径转换为可直接访问的绝对地址（uni.uploadFile 等原生能力需要） */
export function absoluteUrl(url) {
  const value = String(url || '')
  if (/^https?:\/\//i.test(value)) return value
  // 后端下发的 uploadUrl 已是含 /api/v1 前缀的绝对路径（如 /api/v1/files/upload）
  if (value.startsWith('/api/')) return `${API_ORIGIN}${value}`
  return buildUrl(value)
}

function extractMessage(body, fallback) {
  if (!body) return fallback
  if (typeof body === 'string') return body
  if (typeof body === 'object') {
    return body.message || body.error || body.msg || fallback
  }
  return fallback
}

function shouldToast(options) {
  return options.silent !== true && options.showError !== false
}

/**
 * 统一请求封装。
 *
 * @param {object} options
 * @param {string} options.url        相对路径（自动拼接 API_BASE_URL）或绝对地址
 * @param {string} [options.method]   HTTP 方法，默认 GET
 * @param {object} [options.data]     请求数据（GET 时作为 query）
 * @param {object} [options.header]   额外请求头
 * @param {boolean} [options.auth]    是否携带 token，默认 true
 * @param {boolean} [options.withTableSession] 是否自动携带 X-Table-Session，默认 true
 * @param {string} [options.idempotencyKey] 幂等键（下单/支付类写操作），非空时写入 Idempotency-Key
 * @param {boolean} [options.silent]  是否静默（不弹错误提示）
 * @param {boolean} [options.loading] 是否展示全局 loading
 * @param {string} [options.loadingText]
 * @param {number} [options.timeout]
 * @returns {Promise<any>} 解析后的业务 data
 */
export function request(options = {}) {
  const {
    url,
    method = 'GET',
    data,
    header = {},
    auth = true,
    withTableSession = true,
    idempotencyKey = '',
    silent = false,
    loading = false,
    loadingText = '加载中',
    timeout = REQUEST_TIMEOUT
  } = options

  if (!url) {
    return Promise.reject(createApiError('请求地址不能为空', { type: 'client' }))
  }

  return new Promise((resolve, reject) => {
    const finalHeader = Object.assign({ 'Content-Type': 'application/json' }, header)
    const token = getToken()
    if (auth && token) {
      finalHeader.Authorization = `Bearer ${token}`
    }
    if (withTableSession && !finalHeader['X-Table-Session']) {
      const sessionToken = getTableSessionToken()
      if (sessionToken) finalHeader['X-Table-Session'] = sessionToken
    }
    if (idempotencyKey) {
      finalHeader['Idempotency-Key'] = idempotencyKey
    }

    if (loading) showLoading(loadingText)

    uni.request({
      url: buildUrl(url),
      method: String(method).toUpperCase(),
      data,
      header: finalHeader,
      timeout,
      success: (response) => {
        const statusCode = Number(response.statusCode) || 0
        const body = response.data

        if (statusCode === 401) {
          const error = createApiError(extractMessage(body, '登录已过期，请重新登录'), {
            code: 401,
            statusCode,
            type: 'unauthorized'
          })
          if (shouldToast(options) && !silent) toast('登录已过期，请重新登录')
          handleUnauthorized()
          reject(error)
          return
        }

        if (statusCode >= 200 && statusCode < 300) {
          // 统一响应体：{ code, message, data, traceId }
          if (body && typeof body === 'object' && Object.prototype.hasOwnProperty.call(body, 'code')) {
            if (Number(body.code) === 0) {
              resolve(body.data)
              return
            }
            const error = createApiError(extractMessage(body, '请求失败'), {
              code: body.code,
              statusCode,
              traceId: body.traceId,
              type: 'business'
            })
            if (shouldToast(options)) toast(error.message)
            reject(error)
            return
          }
          // 非统一响应体（如 Actuator / 第三方）时原样返回
          resolve(body)
          return
        }

        const fallback =
          statusCode === 403
            ? '没有访问权限'
            : statusCode === 404
              ? '接口不存在'
              : statusCode >= 500
                ? '服务器开小差了，请稍后重试'
                : `请求失败（${statusCode}）`
        const error = createApiError(extractMessage(body, fallback), {
          statusCode,
          type: 'http'
        })
        if (shouldToast(options)) toast(error.message)
        reject(error)
      },
      fail: (response) => {
        const error = createApiError('网络异常，请检查网络或后端服务是否已启动', {
          type: 'network'
        })
        error.raw = response
        if (IS_DEV) console.warn('[request] 网络异常', buildUrl(url), response)
        if (shouldToast(options)) toast(error.message)
        reject(error)
      },
      complete: () => {
        if (loading) hideLoading()
      }
    })
  })
}

function withMethod(method) {
  return (url, data, options = {}) => request(Object.assign({ url, method, data }, options))
}

export const http = {
  request,
  get: withMethod('GET'),
  post: withMethod('POST'),
  put: withMethod('PUT'),
  patch: withMethod('PATCH'),
  delete: withMethod('DELETE')
}

export default http
