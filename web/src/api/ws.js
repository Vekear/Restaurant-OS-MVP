/**
 * WebSocket 客户端封装（STOMP over native WebSocket）
 *
 * 后端（brief §9 / plan §1.10）：STOMP 端点 /ws，原生端点 /ws/native。
 * 本封装负责：JWT 握手头、订阅登记与断线后自动重订阅、指数退避重连、连接状态响应式暴露。
 *
 * 用法：
 *   import { realtime } from '@/utils/websocket'
 *   realtime.connect()                                      // token 默认取 localStorage
 *   const key = realtime.subscribe('/topic/merchant/1/orders', (payload) => { ... })
 *   realtime.publish('/app/orders/1/ping', { at: Date.now() })
 *   realtime.unsubscribe(key)
 *   realtime.disconnect()
 */
import { readonly, ref } from 'vue'
import { Client } from '@stomp/stompjs'
import { STORAGE_KEYS, readRaw } from '@/utils/storage'

export const RealtimeStatus = Object.freeze({
  IDLE: 'idle',
  CONNECTING: 'connecting',
  CONNECTED: 'connected',
  RECONNECTING: 'reconnecting',
  CLOSED: 'closed',
  ERROR: 'error'
})

export const REALTIME_STATUS_TEXT = Object.freeze({
  idle: '未连接',
  connecting: '连接中',
  connected: '实时连接',
  reconnecting: '重连中',
  closed: '已断开',
  error: '连接异常'
})

/** 重连退避序列（架构 §6：1s / 2s / 5s，上限 30s） */
const RETRY_DELAYS = [1000, 2000, 5000, 10000, 30000]
const MAX_RETRY_DELAY = 30000

function resolveBrokerUrl() {
  const configured = import.meta.env.VITE_WS_URL
  if (configured) return configured
  const path = import.meta.env.VITE_WS_PATH || '/ws'
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  return `${protocol}//${window.location.host}${path}`
}

function defaultTokenProvider() {
  return readRaw(STORAGE_KEYS.token) || ''
}

export class RealtimeClient {
  /**
   * @param {{
   *   url?: string,
   *   getToken?: () => string,
   *   debug?: boolean,
   *   onStatusChange?: (status: string) => void
   * }} options
   */
  constructor(options = {}) {
    this.url = options.url || ''
    this.getToken = options.getToken || defaultTokenProvider
    this.debugEnabled = Boolean(options.debug)
    this.onStatusChange = options.onStatusChange || null

    this.token = ''
    this.client = null
    this.generation = 0
    this.retryCount = 0
    this.retryTimer = null
    this.manuallyClosed = false
    /** @type {Map<string, { destination: string, handler: Function, headers: object, active: any }>} */
    this.subscriptions = new Map()

    this._status = ref(RealtimeStatus.IDLE)
    this.statusRef = readonly(this._status)
  }

  get status() {
    return this._status.value
  }

  get connected() {
    return this._status.value === RealtimeStatus.CONNECTED
  }

  setStatus(next) {
    if (this._status.value === next) return
    this._status.value = next
    if (this.onStatusChange) {
      try {
        this.onStatusChange(next)
      } catch (error) {
        console.error('[ws] onStatusChange 回调异常', error)
      }
    }
  }

  /**
   * 建立连接。未提供 token 时回退到 getToken()（默认读取 localStorage）。
   * @param {{ token?: string }} options
   * @returns {boolean} 是否发起连接
   */
  connect(options = {}) {
    const nextToken = options.token !== undefined ? options.token : this.token || this.getToken()
    if (nextToken) this.token = String(nextToken).replace(/^Bearer\s+/i, '')
    if (!this.token) {
      this.setStatus(RealtimeStatus.IDLE)
      return false
    }
    if (this.client?.active) return true

    this.manuallyClosed = false
    this.retryCount = 0
    this.clearRetryTimer()
    this.startClient()
    return true
  }

  startClient() {
    if (!this.url) this.url = resolveBrokerUrl()
    this.generation += 1
    const generation = this.generation

    const client = new Client({
      brokerURL: this.url,
      connectHeaders: {
        Authorization: `Bearer ${this.token}`,
        token: this.token
      },
      // 关闭 stompjs 内置固定延时重连，改由本封装做指数退避
      reconnectDelay: 0,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      debug: (message) => {
        if (this.debugEnabled) console.debug('[ws]', message)
      },
      onConnect: () => {
        if (generation !== this.generation) return
        this.retryCount = 0
        this.setStatus(RealtimeStatus.CONNECTED)
        this.resubscribeAll()
      },
      onDisconnect: () => {
        if (generation !== this.generation) return
        this.setStatus(RealtimeStatus.IDLE)
      },
      onStompError: (frame) => {
        if (generation !== this.generation) return
        console.warn('[ws] STOMP 错误：', frame.headers?.message || frame.body)
        this.setStatus(RealtimeStatus.ERROR)
        this.scheduleReconnect()
      },
      onWebSocketClose: () => {
        if (generation !== this.generation) return
        this.clearActiveSubscriptions()
        this.scheduleReconnect()
      },
      onWebSocketError: (event) => {
        if (generation !== this.generation) return
        if (this.debugEnabled) console.warn('[ws] WebSocket 异常', event?.type)
        this.setStatus(RealtimeStatus.ERROR)
      }
    })

    this.client = client
    this.setStatus(this.retryCount > 0 ? RealtimeStatus.RECONNECTING : RealtimeStatus.CONNECTING)
    try {
      client.activate()
    } catch (error) {
      console.error('[ws] 连接初始化失败', error)
      this.setStatus(RealtimeStatus.ERROR)
      this.scheduleReconnect()
    }
  }

  clearRetryTimer() {
    if (this.retryTimer) {
      clearTimeout(this.retryTimer)
      this.retryTimer = null
    }
  }

  scheduleReconnect() {
    if (this.manuallyClosed || this.retryTimer) return
    const delay = Math.min(
      MAX_RETRY_DELAY,
      RETRY_DELAYS[Math.min(this.retryCount, RETRY_DELAYS.length - 1)]
    )
    this.retryCount += 1
    this.setStatus(RealtimeStatus.RECONNECTING)
    this.retryTimer = setTimeout(async () => {
      this.retryTimer = null
      if (this.manuallyClosed) return
      if (!this.token) {
        this.setStatus(RealtimeStatus.IDLE)
        return
      }
      await this.disposeClient()
      if (this.manuallyClosed) return
      this.startClient()
    }, delay)
  }

  async disposeClient() {
    this.generation += 1
    const previous = this.client
    this.client = null
    this.clearActiveSubscriptions()
    if (!previous) return
    previous.reconnectDelay = 0
    try {
      await previous.deactivate()
    } catch (error) {
      if (this.debugEnabled) console.warn('[ws] 关闭旧连接失败', error)
    }
  }

  clearActiveSubscriptions() {
    for (const entry of this.subscriptions.values()) {
      try {
        entry.active?.unsubscribe()
      } catch (error) {
        if (this.debugEnabled) console.warn('[ws] 取消订阅失败', entry.destination, error)
      }
      entry.active = null
    }
  }

  resubscribeAll() {
    for (const key of this.subscriptions.keys()) this.activateSubscription(key)
  }

  activateSubscription(key) {
    const entry = this.subscriptions.get(key)
    if (!entry || entry.active || !this.client || !this.connected) return
    entry.active = this.client.subscribe(
      entry.destination,
      (frame) => {
        let payload = frame.body
        if (frame.body) {
          try {
            payload = JSON.parse(frame.body)
          } catch {
            /* 非 JSON 消息保留原始字符串 */
          }
        }
        try {
          entry.handler(payload, frame)
        } catch (error) {
          console.error('[ws] 订阅回调异常', entry.destination, error)
        }
      },
      entry.headers
    )
  }

  /**
   * 订阅 STOMP 目的地。断线重连成功后自动重新订阅。
   * @param {string} destination 例如 /topic/merchant/1/orders
   * @param {(payload: any, frame: any) => void} handler
   * @param {{ key?: string, headers?: object }} options
   * @returns {string} 订阅 key（用于 unsubscribe）
   */
  subscribe(destination, handler, options = {}) {
    if (!destination || typeof handler !== 'function') {
      throw new Error('[ws] subscribe 需要 destination 与 handler')
    }
    const key = options.key || destination
    this.subscriptions.set(key, {
      destination,
      handler,
      headers: options.headers || {},
      active: null
    })
    this.activateSubscription(key)
    return key
  }

  unsubscribe(key) {
    const entry = this.subscriptions.get(key)
    if (!entry) return
    try {
      entry.active?.unsubscribe()
    } catch (error) {
      if (this.debugEnabled) console.warn('[ws] 取消订阅失败', key, error)
    }
    this.subscriptions.delete(key)
  }

  publish(destination, body, headers = {}) {
    if (!this.connected || !this.client) return false
    this.client.publish({
      destination,
      body: typeof body === 'string' ? body : JSON.stringify(body ?? {}),
      headers
    })
    return true
  }

  /** 主动断开：清空定时器与订阅，不再自动重连 */
  disconnect() {
    this.manuallyClosed = true
    this.clearRetryTimer()
    this.retryCount = 0
    this.subscriptions.clear()
    this.disposeClient()
    this.setStatus(RealtimeStatus.CLOSED)
  }
}

/** 全局单例：整个 Web 应用共享一条 STOMP 连接 */
export const realtime = new RealtimeClient()

export function useRealtime() {
  return realtime
}

export function useRealtimeStatus() {
  return realtime.statusRef
}

export default realtime
