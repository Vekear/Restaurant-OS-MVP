/**
 * Web 应用入口
 *
 * 引入顺序很重要：
 *   1. element-plus/dist/index.css —— 组件库默认变量
 *   2. styles/tokens.css           —— 设计令牌（覆盖/扩展）
 *   3. styles/element-vars.css     —— 把 Element Plus 变量映射到令牌（必须在其后）
 *   4. styles/base.css             —— reset、工具类、过渡
 *   5. styles/auth.css             —— 登录注册页共用样式
 */
import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'

import App from './App.vue'
import router from './router'
import { setUnauthorizedHandler } from './api/http'
import { useAuthStore } from './stores/auth'

import 'element-plus/dist/index.css'
import './styles/tokens.css'
import './styles/element-vars.css'
import './styles/base.css'
import './styles/auth.css'

const app = createApp(App)
const pinia = createPinia()

app.use(pinia)

// 全局注册 Element Plus 图标（模板中可直接使用 <el-icon><Search /></el-icon>）
for (const [name, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(name, component)
}

app.use(ElementPlus, { locale: zhCn, zIndex: 3000 })

/**
 * 401 统一处理：清理本地会话并回到登录页，登录成功后跳回原页面。
 * 注册到 axios 封装里，避免 api 层直接依赖 router / store 造成循环引用。
 */
setUnauthorizedHandler(() => {
  const auth = useAuthStore(pinia)
  auth.clearSession()
  const current = router.currentRoute.value
  if (current.name === 'login') return
  router.replace({
    name: 'login',
    query: current.meta.public ? {} : { redirect: current.fullPath }
  })
})

app.use(router)

app.mount('#app')
