import { defineConfig } from 'vite'
import uni from '@dcloudio/vite-plugin-uni'

const BACKEND = process.env.VITE_PROXY_TARGET || 'http://127.0.0.1:8080'

export default defineConfig({
  plugins: [uni()],
  server: {
    port: 5174,
    strictPort: false,
    proxy: {
      '/api': { target: BACKEND, changeOrigin: true },
      '/ws': { target: BACKEND, changeOrigin: true, ws: true },
      '/files': { target: BACKEND, changeOrigin: true }
    }
  },
  build: {
    sourcemap: false
  }
})
