<script setup>
/**
 * 控制台通用布局（商家 / 管理后台）
 * - 深色侧栏 + 浅色内容区，侧栏可折叠并持久化
 * - 顶栏面包屑、实时连接状态、用户菜单
 * - 子路由通过嵌套 router-view 渲染（带过渡）
 */
import { computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import BrandMark from '@/components/BrandMark.vue'
import UserMenu from '@/components/UserMenu.vue'
import { findNavItem } from '@/config/navigation'
import { useAppStore } from '@/stores/app'
import { useAuthStore } from '@/stores/auth'
import { REALTIME_STATUS_TEXT, realtime } from '@/api/ws'

const props = defineProps({
  theme: { type: String, default: 'merchant' },
  title: { type: String, required: true },
  subtitle: { type: String, default: '' },
  menuItems: { type: Array, required: true },
  homePath: { type: String, required: true }
})

const route = useRoute()
const app = useAppStore()
const auth = useAuthStore()

const collapsed = computed(() => app.sidebarCollapsed)
const currentItem = computed(() => findNavItem(props.menuItems, route.path))
const crumbs = computed(() => [
  props.title,
  currentItem.value?.title || route.meta.title || '工作台'
])
const statusText = computed(() => REALTIME_STATUS_TEXT[realtime.status] || '未知状态')

onMounted(() => {
  if (auth.token) realtime.connect({ token: auth.token })
})
</script>

<template>
  <div class="console" :class="`theme-${theme}`">
    <aside class="console__sider" :class="{ 'is-collapsed': collapsed }">
      <router-link :to="homePath" class="console__brand" :title="title">
        <BrandMark size="sm" theme="dark" :compact="collapsed" subtitle="" />
      </router-link>

      <el-scrollbar class="console__nav">
        <el-menu
          :default-active="route.path"
          :collapse="collapsed"
          :collapse-transition="false"
          router
          class="console-menu"
        >
          <el-menu-item v-for="item in menuItems" :key="item.path" :index="item.path">
            <el-icon><component :is="item.icon" /></el-icon>
            <template #title>
              <span class="console-menu__title">{{ item.title }}</span>
              <span v-if="item.realtime" class="console-menu__live" title="实时推送" />
            </template>
          </el-menu-item>
        </el-menu>
      </el-scrollbar>

      <div class="console__sider-footer">
        <span class="console__status" :class="`is-${realtime.status}`">
          <span class="console__status-dot" />
          <span v-if="!collapsed">{{ statusText }}</span>
        </span>
      </div>
    </aside>

    <div class="console__body">
      <header class="console__header">
        <button
          type="button"
          class="console__collapse"
          :aria-label="collapsed ? '展开侧栏' : '收起侧栏'"
          @click="app.toggleSidebar()"
        >
          <el-icon><component :is="collapsed ? 'Expand' : 'Fold'" /></el-icon>
        </button>

        <nav class="console__crumbs" aria-label="面包屑">
          <span class="console__crumb-root">{{ crumbs[0] }}</span>
          <el-icon class="console__crumb-sep"><ArrowRight /></el-icon>
          <span class="console__crumb-current">{{ crumbs[1] }}</span>
        </nav>

        <div class="console__actions">
          <span class="console__status console__status--header hide-sm" :class="`is-${realtime.status}`">
            <span class="console__status-dot" />
            {{ statusText }}
          </span>
          <UserMenu theme="light" />
        </div>
      </header>

      <main class="console__content">
        <router-view v-slot="{ Component, route: viewRoute }">
          <transition name="fade-slide" mode="out-in">
            <component :is="Component" :key="viewRoute.path" />
          </transition>
        </router-view>
      </main>
    </div>
  </div>
</template>

<style scoped>
.console {
  display: flex;
  min-height: 100vh;
  background: var(--color-bg-page);
}

/* ============ 侧栏 ============ */
.console__sider {
  position: sticky;
  top: 0;
  display: flex;
  flex-direction: column;
  flex: none;
  width: var(--layout-sider-width);
  height: 100vh;
  background: var(--console-sider-bg);
  color: var(--color-sider-text);
  transition: width var(--duration-base) var(--ease-standard);
  overflow: hidden;
}

.console__sider.is-collapsed {
  width: var(--layout-sider-collapsed-width);
}

.console__brand {
  display: flex;
  align-items: center;
  height: var(--layout-header-height);
  padding: 0 var(--space-5);
  border-bottom: 1px solid rgba(255, 255, 255, 0.06);
  color: inherit;
  text-decoration: none;
}

.console__brand:hover {
  color: inherit;
}

.console__nav {
  flex: 1;
  padding: var(--space-4) var(--space-3);
}

.console__sider-footer {
  padding: var(--space-4) var(--space-5);
  border-top: 1px solid rgba(255, 255, 255, 0.06);
}

.console__status {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  font-size: var(--font-size-xs);
  color: rgba(207, 197, 187, 0.62);
  white-space: nowrap;
}

.console__status-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--color-text-tertiary);
  flex: none;
}

.console__status.is-connected .console__status-dot {
  background: #4ade80;
  box-shadow: 0 0 0 3px rgba(74, 222, 128, 0.16);
}

.console__status.is-connecting .console__status-dot,
.console__status.is-reconnecting .console__status-dot {
  background: var(--color-warning);
  animation: pulse 1.2s var(--ease-standard) infinite;
}

.console__status.is-error .console__status-dot {
  background: var(--color-danger);
}

.console__status.is-connected {
  color: rgba(233, 224, 216, 0.86);
}

@keyframes pulse {
  0%,
  100% {
    opacity: 1;
  }
  50% {
    opacity: 0.32;
  }
}

/* ============ 菜单 ============ */
.console-menu {
  --el-menu-bg-color: transparent;
  --el-menu-text-color: var(--color-sider-text);
  --el-menu-hover-bg-color: var(--console-sider-hover);
  --el-menu-active-color: var(--color-sider-text-active);
  --el-menu-item-height: 44px;
  --el-menu-collapse-width: var(--layout-sider-collapsed-width);
  border-right: none;
}

.console-menu :deep(.el-menu-item) {
  position: relative;
  margin-bottom: var(--space-2);
  border-radius: var(--radius-md);
  font-size: var(--font-size-md);
}

.console-menu :deep(.el-menu-item:hover) {
  background: var(--console-sider-hover);
  color: var(--color-sider-text-active);
}

.console-menu :deep(.el-menu-item.is-active) {
  background: var(--console-accent-soft);
  color: var(--color-sider-text-active);
  font-weight: var(--font-weight-medium);
}

.console-menu :deep(.el-menu-item.is-active::before) {
  content: '';
  position: absolute;
  left: 0;
  top: 50%;
  width: 3px;
  height: 18px;
  border-radius: 0 3px 3px 0;
  background: var(--console-accent);
  transform: translateY(-50%);
}

.console-menu :deep(.el-menu-item .el-icon) {
  color: inherit;
}

.console-menu__title {
  margin-left: var(--space-1);
}

.console-menu__live {
  width: 5px;
  height: 5px;
  margin-left: auto;
  border-radius: 50%;
  background: #4ade80;
}

/* ============ 右侧主体 ============ */
.console__body {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-width: 0;
}

.console__header {
  position: sticky;
  top: 0;
  z-index: var(--z-header);
  display: flex;
  align-items: center;
  gap: var(--space-5);
  height: var(--layout-header-height);
  padding: 0 var(--space-8);
  background: rgba(255, 253, 251, 0.86);
  backdrop-filter: blur(12px);
  border-bottom: 1px solid var(--color-border-subtle);
}

.console__collapse {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  background: var(--color-surface);
  color: var(--color-text-secondary);
  cursor: pointer;
  transition: all var(--duration-fast) var(--ease-standard);
}

.console__collapse:hover {
  color: var(--color-primary);
  border-color: var(--color-primary);
  box-shadow: var(--shadow-focus);
}

.console__crumbs {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  font-size: var(--font-size-sm);
  color: var(--color-text-tertiary);
  min-width: 0;
}

.console__crumb-current {
  color: var(--color-text-primary);
  font-weight: var(--font-weight-medium);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.console__crumb-sep {
  font-size: 11px;
}

.console__actions {
  display: flex;
  align-items: center;
  gap: var(--space-5);
  margin-left: auto;
}

.console__status--header {
  padding: var(--space-1) var(--space-4);
  border: 1px solid var(--color-border-subtle);
  border-radius: var(--radius-pill);
  background: var(--color-surface);
  color: var(--color-text-tertiary);
}

.console__status--header.is-connected {
  color: var(--color-success);
  border-color: rgba(46, 158, 107, 0.24);
}

.console__content {
  flex: 1;
  padding: var(--space-8);
  width: 100%;
  max-width: 1440px;
  margin: 0 auto;
}

@media (max-width: 768px) {
  .console__sider {
    width: var(--layout-sider-collapsed-width);
  }

  .console__header {
    padding: 0 var(--space-5);
  }

  .console__content {
    padding: var(--space-5);
  }

  .console__crumbs {
    display: none;
  }
}
</style>
