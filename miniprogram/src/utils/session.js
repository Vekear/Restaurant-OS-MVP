import { readStorage, writeStorage, removeStorage } from './storage'

/** 小程序登录态存储键（与 Web 端 token 隔离） */
export const TOKEN_KEY = 'restaurant_mp_token'
export const USER_KEY = 'restaurant_mp_user'

export function getToken() {
  return readStorage(TOKEN_KEY, '')
}

export function setToken(token) {
  writeStorage(TOKEN_KEY, token || '')
}

export function getUser() {
  return readStorage(USER_KEY, null)
}

export function setUser(user) {
  writeStorage(USER_KEY, user || null)
}

export function clearSession() {
  removeStorage(TOKEN_KEY)
  removeStorage(USER_KEY)
}

export function hasSession() {
  return Boolean(getToken())
}
