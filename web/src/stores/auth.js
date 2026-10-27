/**
 * 鉴权会话 Store
 *
 * - token/user 持久化在 localStorage（utils/storage 统一 key）
 * - 登录响应做了字段归一化：兼容 { token, user }、{ token, ...用户字段 }、{ accessToken, userInfo }
 * - RBAC：ADMIN / MERCHANT / CUSTOMER，路由守卫读取 role 做角色隔离
 * - 退出登录时同时断开 WebSocket，避免旧连接继续收消息
 */
import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { authApi } from '@/api/auth'
import { STORAGE_KEYS, readJSON, readRaw, removeRaw, writeJSON, writeRaw } from '@/utils/storage'
import { realtime } from '@/api/ws'
import { ROLE_HOME, ROLE_LABELS, ROLE_TAG_TYPES } from '@/utils/constants'

// 兼容既有引用：角色文案/落地路由统一维护在 utils/constants.js
export { ROLE_HOME, ROLE_LABELS }

/** 兼容 role / roleCode / roles[0] / authorities[0]，去掉 ROLE_ 前缀并转大写 */
export function normalizeRole(raw) {
  if (!raw || typeof raw !== 'object') return null
  const candidates = [
    raw.role,
    raw.roleCode,
    raw.roleName,
    Array.isArray(raw.roles) ? raw.roles[0]?.code || raw.roles[0]?.name || raw.roles[0] : raw.roles,
    Array.isArray(raw.authorities)
      ? raw.authorities[0]?.authority || raw.authorities[0]?.role || raw.authorities[0]
      : raw.authorities
  ]
  for (const candidate of candidates) {
    const value = String(candidate || '')
      .replace(/^ROLE_/i, '')
      .trim()
      .toUpperCase()
    if (value) return value
  }
  return null
}

export function normalizeRoleTag(role) {
  return ROLE_TAG_TYPES[role] || 'info'
}

/** 解析 JWT payload（仅用于读取 username/role 等展示信息，不做签名校验） */
export function decodeJwtPayload(token) {
  try {
    const segment = String(token || '').split('.')[1]
    if (!segment) return null
    const normalized = segment.replace(/-/g, '+').replace(/_/g, '/')
    const binary = window.atob(normalized)
    const bytes = Uint8Array.from(binary, (char) => char.charCodeAt(0))
    return JSON.parse(new TextDecoder().decode(bytes))
  } catch {
    return null
  }
}

/** 归一化用户对象：接口 / JWT claims 字段差异在此收敛 */
export function normalizeUser(raw, token) {
  const claims = decodeJwtPayload(token) || {}
  const source = raw && typeof raw === 'object' ? raw : {}
  const merged = { ...claims, ...source }
  const username = merged.username || merged.account || merged.loginName || ''
  return {
    ...merged,
    id: merged.id ?? merged.userId ?? merged.uid ?? null,
    username,
    nickname: merged.nickname || merged.name || merged.displayName || username,
    phone: merged.phone || merged.mobile || '',
    email: merged.email || '',
    avatar: merged.avatar || merged.avatarUrl || '',
    role: normalizeRole(merged) || normalizeRole(claims) || null,
    merchantId: merged.merchantId ?? merged.storeId ?? null
  }
}

export const useAuthStore = defineStore('auth', () => {
  const token = ref(readRaw(STORAGE_KEYS.token) || '')
  const user = ref(readJSON(STORAGE_KEYS.user, null))

  const isLoggedIn = computed(() => Boolean(token.value))
  const role = computed(
    () => normalizeRole(user.value) || normalizeRole(decodeJwtPayload(token.value) || {})
  )
  const roleLabel = computed(() => (role.value && ROLE_LABELS[role.value]) || '访客')
  const roleTagType = computed(() => normalizeRoleTag(role.value))
  const displayName = computed(
    () => user.value?.nickname || user.value?.username || '未登录'
  )
  const homePath = computed(() => ROLE_HOME[role.value] || '/')
  const isAdmin = computed(() => role.value === 'ADMIN')
  const isMerchant = computed(() => role.value === 'MERCHANT')
  const isCustomer = computed(() => role.value === 'CUSTOMER')

  function persistSession() {
    if (token.value) writeRaw(STORAGE_KEYS.token, token.value)
    else removeRaw(STORAGE_KEYS.token)
    if (user.value) writeJSON(STORAGE_KEYS.user, user.value)
    else removeRaw(STORAGE_KEYS.user)
  }

  /**
   * 写入登录/注册/资料接口返回的会话数据
   * @param {any} payload
   * @returns {object} 归一化后的用户
   */
  function applySession(payload) {
    const data = payload && typeof payload === 'object' ? payload : {}
    const nextToken =
      data.token || data.accessToken || data.access_token || data.tokenValue || ''
    if (nextToken) token.value = String(nextToken).replace(/^Bearer\s+/i, '')

    const rawUser =
      data.user || data.userInfo || data.profile || (data.username ? data : null)
    if (rawUser) user.value = normalizeUser(rawUser, token.value)
    else if (token.value) user.value = normalizeUser(user.value, token.value)

    persistSession()
    return user.value
  }

  function clearSession() {
    token.value = ''
    user.value = null
    removeRaw(STORAGE_KEYS.token)
    removeRaw(STORAGE_KEYS.user)
    realtime.disconnect()
  }

  async function login(form) {
    const data = await authApi.login(form)
    applySession(data)
    if (!token.value) throw new Error('登录响应缺少令牌字段，请联系管理员')
    return user.value
  }

  async function register(form) {
    const data = await authApi.register(form)
    if (data?.token || data?.accessToken) applySession(data)
    return data
  }

  /** 刷新页面后用 token 拉取最新用户资料（路由守卫在缺少 user 时调用） */
  async function fetchProfile() {
    const data = await authApi.fetchProfile()
    const rawUser = data?.user || data
    if (!rawUser) return user.value
    user.value = normalizeUser(rawUser, token.value)
    persistSession()
    return user.value
  }

  async function logout(options = {}) {
    const { remote = true } = options
    if (remote && token.value) {
      try {
        await authApi.logout()
      } catch (error) {
        // 后端不可用也要保证本地退出
        console.warn('[auth] 退出接口调用失败，已清理本地会话', error)
      }
    }
    clearSession()
  }

  return {
    token,
    user,
    isLoggedIn,
    role,
    roleLabel,
    roleTagType,
    displayName,
    homePath,
    isAdmin,
    isMerchant,
    isCustomer,
    applySession,
    clearSession,
    login,
    register,
    fetchProfile,
    logout
  }
})
