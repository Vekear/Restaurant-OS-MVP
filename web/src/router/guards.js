/**
 * 全局路由守卫
 * 1. 已登录但刷新后缺少 user 信息时，用 token 拉取资料（失败即清理会话）
 * 2. public 页面直接放行；guestOnly 页面登录后跳回自身空间
 * 3. 未登录访问受保护页面 → /login?redirect=...
 * 4. meta.roles 与当前角色不匹配 → /403
 */
import { useAuthStore } from '@/stores/auth'
import { safeRedirect } from '@/utils/redirect'

const APP_TITLE = import.meta.env.VITE_APP_TITLE || '拾味点餐'

export function setupRouterGuards(router) {
  router.beforeEach(async (to) => {
    const auth = useAuthStore()

    if (auth.isLoggedIn && !auth.user) {
      try {
        await auth.fetchProfile()
      } catch {
        auth.clearSession()
      }
    }

    if (to.meta.public) {
      if (to.meta.guestOnly && auth.isLoggedIn) {
        return safeRedirect(to.query.redirect) || auth.homePath
      }
      return true
    }

    if (!auth.isLoggedIn) {
      return { name: 'login', query: { redirect: to.fullPath } }
    }

    const roles = to.meta.roles
    if (Array.isArray(roles) && roles.length > 0 && !roles.includes(auth.role)) {
      return { name: 'forbidden', query: { from: to.fullPath } }
    }

    return true
  })

  router.afterEach((to) => {
    document.title = to.meta.title ? `${to.meta.title} · ${APP_TITLE}` : APP_TITLE
  })
}

export default setupRouterGuards
