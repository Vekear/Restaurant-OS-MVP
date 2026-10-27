<script setup>
/**
 * 顶栏用户菜单：展示昵称/角色，提供控制台入口与退出登录
 * theme: light（浅色页头）| dark（深色控制台顶栏）
 */
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAuthStore } from '@/stores/auth'

const props = defineProps({
  theme: { type: String, default: 'light' },
  showMeta: { type: Boolean, default: true }
})

const auth = useAuthStore()
const router = useRouter()
const route = useRoute()

const initials = computed(() => {
  const name = auth.displayName || '客'
  return name.slice(0, 1).toUpperCase()
})

const workspaceEntry = computed(() => {
  if (auth.isAdmin) return { label: '平台管理后台', path: '/admin/dashboard' }
  if (auth.isMerchant) return { label: '商家工作台', path: '/merchant/dashboard' }
  return { label: '我的订单', path: '/orders' }
})

async function handleCommand(command) {
  if (command === 'workspace') {
    router.push(workspaceEntry.value.path)
    return
  }
  if (command === 'home') {
    router.push('/')
    return
  }
  if (command === 'logout') {
    await confirmLogout()
  }
}

async function confirmLogout() {
  try {
    await ElMessageBox.confirm('退出后需要重新登录才能查看订单与工作台。', '退出登录', {
      confirmButtonText: '退出登录',
      cancelButtonText: '再想想',
      type: 'warning',
      appendTo: 'body'
    })
  } catch {
    return
  }
  await auth.logout()
  ElMessage.success('已退出登录')
  if (!route.meta.public) router.replace('/')
}
</script>

<template>
  <el-dropdown
    trigger="click"
    placement="bottom-end"
    popper-class="user-menu-popper"
    @command="handleCommand"
  >
    <button type="button" class="user-menu" :class="`user-menu--${theme}`">
      <span class="user-menu__avatar">{{ initials }}</span>
      <span v-if="showMeta" class="user-menu__meta">
        <span class="user-menu__name">{{ auth.displayName }}</span>
        <span class="user-menu__role">{{ auth.roleLabel }}</span>
      </span>
      <el-icon class="user-menu__caret"><ArrowDown /></el-icon>
    </button>
    <template #dropdown>
      <el-dropdown-menu>
        <div class="user-menu__panel">
          <p class="user-menu__panel-name">{{ auth.displayName }}</p>
          <p class="user-menu__panel-role">
            {{ auth.roleLabel }}
            <template v-if="auth.user?.username"> · {{ auth.user.username }}</template>
          </p>
        </div>
        <el-dropdown-item command="workspace">
          <el-icon><Monitor /></el-icon>
          {{ workspaceEntry.label }}
        </el-dropdown-item>
        <el-dropdown-item v-if="!auth.isCustomer" command="home">
          <el-icon><House /></el-icon>
          顾客端首页
        </el-dropdown-item>
        <el-dropdown-item command="logout" divided>
          <el-icon><SwitchButton /></el-icon>
          退出登录
        </el-dropdown-item>
      </el-dropdown-menu>
    </template>
  </el-dropdown>
</template>

<style scoped>
.user-menu {
  display: inline-flex;
  align-items: center;
  gap: var(--space-4);
  padding: var(--space-2) var(--space-3) var(--space-2) var(--space-2);
  background: transparent;
  border: 1px solid transparent;
  border-radius: var(--radius-pill);
  cursor: pointer;
  transition: background var(--duration-fast) var(--ease-standard),
    border-color var(--duration-fast) var(--ease-standard);
}

.user-menu:hover {
  background: rgba(34, 28, 23, 0.04);
  border-color: var(--color-border-subtle);
}

.user-menu__avatar {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: linear-gradient(135deg, var(--color-brand-400), var(--color-brand-600));
  color: var(--color-text-inverse);
  font-size: var(--font-size-md);
  font-weight: var(--font-weight-semibold);
  box-shadow: var(--shadow-xs);
}

.user-menu__meta {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  line-height: 1.2;
}

.user-menu__name {
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-primary);
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.user-menu__role {
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}

.user-menu__caret {
  font-size: 12px;
  color: var(--color-text-tertiary);
}

.user-menu--dark .user-menu:hover {
  background: rgba(255, 255, 255, 0.08);
  border-color: rgba(255, 255, 255, 0.12);
}

.user-menu--dark .user-menu__name {
  color: var(--color-text-inverse);
}

.user-menu--dark .user-menu__role {
  color: rgba(207, 197, 187, 0.72);
}

.user-menu__panel {
  padding: var(--space-4) var(--space-6) var(--space-3);
  min-width: 168px;
}

.user-menu__panel-name {
  font-size: var(--font-size-md);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-primary);
}

.user-menu__panel-role {
  margin-top: 2px;
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}
</style>
