import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'

/**
 * Vitest 配置（T15）
 * - jsdom 环境：组件可挂载、localStorage/document 可用
 * - 复用 `@` -> src 别名，与 vite.config.js 保持一致
 * - 单测只覆盖纯逻辑与轻量组件，不启动 dev server、不访问网络
 */
export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  test: {
    environment: 'jsdom',
    globals: true,
    include: ['tests/**/*.spec.js'],
    setupFiles: ['./tests/setup.js'],
    restoreMocks: true,
    clearMocks: true
  }
})
