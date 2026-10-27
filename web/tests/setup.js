/**
 * Vitest 全局环境
 * 1. 注册 Element Plus + 全量图标，组件测试可直接使用 <el-*> 与 <KnifeFork /> 等图标
 * 2. jsdom 缺少 ResizeObserver / matchMedia，这里提供最小实现，供 EChartPanel 等组件安全挂载
 */
import { config } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'

config.global.plugins = [[ElementPlus, { locale: zhCn }]]
config.global.components = { ...ElementPlusIconsVue }

if (!globalThis.ResizeObserver) {
  globalThis.ResizeObserver = class ResizeObserver {
    observe() {}
    unobserve() {}
    disconnect() {}
  }
}

if (!globalThis.matchMedia) {
  globalThis.matchMedia = (query) => ({
    matches: false,
    media: query,
    onchange: null,
    addListener() {},
    removeListener() {},
    addEventListener() {},
    removeEventListener() {},
    dispatchEvent() {
      return false
    }
  })
}
