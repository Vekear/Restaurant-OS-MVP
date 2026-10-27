/**
 * 全局界面状态（目前只有控制台侧栏折叠，后续放主题/语言等）
 */
import { ref } from 'vue'
import { defineStore } from 'pinia'
import { STORAGE_KEYS, readBoolean, writeBoolean } from '@/utils/storage'

export const useAppStore = defineStore('app', () => {
  const sidebarCollapsed = ref(readBoolean(STORAGE_KEYS.sidebarCollapsed, false))

  function setSidebarCollapsed(value) {
    sidebarCollapsed.value = Boolean(value)
    writeBoolean(STORAGE_KEYS.sidebarCollapsed, sidebarCollapsed.value)
  }

  function toggleSidebar() {
    setSidebarCollapsed(!sidebarCollapsed.value)
  }

  return { sidebarCollapsed, setSidebarCollapsed, toggleSidebar }
})
