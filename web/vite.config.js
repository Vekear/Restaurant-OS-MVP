import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

/**
 * 拾味点餐 Web 构建配置
 *
 * 开发态通过 Vite 反代与后端 8080 对接（见 docs/reference/brief.md §9）：
 *   /api    -> http://127.0.0.1:8080   REST
 *   /files  -> http://127.0.0.1:8080   本地兜底存储（OSS 降级时）
 *   /ws     -> ws://127.0.0.1:8080     STOMP WebSocket
 */
const BACKEND_ORIGIN = process.env.VITE_DEV_PROXY_TARGET || 'http://127.0.0.1:8080'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  server: {
    host: '127.0.0.1',
    port: 5173,
    strictPort: true,
    proxy: {
      '/api': {
        target: BACKEND_ORIGIN,
        changeOrigin: true
      },
      '/files': {
        target: BACKEND_ORIGIN,
        changeOrigin: true
      },
      '/ws': {
        target: BACKEND_ORIGIN,
        changeOrigin: true,
        ws: true
      }
    }
  },
  preview: {
    host: '127.0.0.1',
    port: 4173
  },
  build: {
    outDir: 'dist',
    assetsDir: 'assets',
    sourcemap: false,
    chunkSizeWarningLimit: 1600,
    rollupOptions: {
      output: {
        manualChunks: {
          'vendor-vue': ['vue', 'vue-router', 'pinia'],
          'vendor-element': ['element-plus', '@element-plus/icons-vue'],
          'vendor-chart': ['echarts/core', 'echarts/charts', 'echarts/components', 'echarts/renderers'],
          'vendor-net': ['axios', '@stomp/stompjs']
        }
      }
    }
  }
})
