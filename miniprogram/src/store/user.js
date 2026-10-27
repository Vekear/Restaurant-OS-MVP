import { computed, reactive } from 'vue'
import { clearSession, getToken, getUser, setToken, setUser } from '@/utils/session'

const state = reactive({
  token: getToken(),
  user: getUser()
})

const isLoggedIn = computed(() => Boolean(state.token))
const displayName = computed(() => {
  const user = state.user
  if (user && user.nickname) return user.nickname
  return isLoggedIn.value ? '微信用户' : '未登录'
})

function signIn(payload) {
  const result = payload || {}
  if (!result.token) {
    throw new Error('登录响应缺少 token')
  }
  state.token = result.token
  setToken(result.token)
  state.user = result.user || null
  setUser(state.user)
}

function signOut() {
  state.token = ''
  state.user = null
  clearSession()
}

/** 从本地存储重新同步（登录页/其他入口改动登录态后调用） */
function syncFromStorage() {
  state.token = getToken()
  state.user = getUser()
}

/** 局部更新用户资料（头像、昵称等），并持久化 */
function updateUser(patch) {
  if (!patch || typeof patch !== 'object') return
  state.user = Object.assign({}, state.user || {}, patch)
  setUser(state.user)
}

const userStore = {
  state,
  isLoggedIn,
  displayName,
  signIn,
  signOut,
  syncFromStorage,
  updateUser
}

export function useUserStore() {
  return userStore
}
