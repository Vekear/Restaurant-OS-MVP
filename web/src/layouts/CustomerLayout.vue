<script setup>
/**
 * 顾客端布局：吸顶导航 + 内容区 + 页脚
 * 未登录时导航仍可浏览首页/菜单，订单类页面由路由守卫引导登录。
 */
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import BrandMark from '@/components/BrandMark.vue'
import UserMenu from '@/components/UserMenu.vue'
import { customerNav } from '@/config/navigation'
import { useAuthStore } from '@/stores/auth'
import { useCartStore } from '@/stores/cart'
import { realtime } from '@/api/ws'

const auth = useAuthStore()
const cart = useCartStore()
const route = useRoute()
const router = useRouter()

const mobileNavOpen = ref(false)
const navItems = computed(() => customerNav)
const cartCount = computed(() => cart.totalQuantity || 0)

function isActive(item) {
  if (item.exact) return route.path === item.path
  return route.path === item.path || route.path.startsWith(`${item.path}/`)
}

function go(path) {
  mobileNavOpen.value = false
  router.push(path)
}

onMounted(() => {
  if (auth.token) {
    realtime.connect({ token: auth.token })
    if (auth.role === 'CUSTOMER') cart.ensureLoaded()
  }
})

watch(
  () => auth.isLoggedIn,
  (loggedIn) => {
    if (loggedIn && auth.role === 'CUSTOMER') {
      cart.ensureLoaded()
    } else if (!loggedIn) {
      cart.reset()
    }
  }
)
</script>

<template>
  <div class="customer-layout">
    <header class="site-header">
      <div class="site-header__inner app-container">
        <router-link to="/" class="site-header__brand" aria-label="拾味点餐首页">
          <BrandMark size="sm" />
        </router-link>

        <nav class="site-nav" aria-label="主导航">
          <router-link
            v-for="item in navItems"
            :key="item.path"
            :to="item.path"
            class="site-nav__link"
            :class="{ 'is-active': isActive(item) }"
          >
            {{ item.title }}
          </router-link>
        </nav>

        <div class="site-header__actions">
          <el-badge :value="cartCount" :max="99" :hidden="cartCount === 0" class="site-header__cart-badge">
            <el-button class="site-header__cart" text @click="go('/cart')">
              <el-icon><ShoppingCart /></el-icon>
              <span class="site-header__cart-text">购物车</span>
            </el-button>
          </el-badge>

          <template v-if="auth.isLoggedIn">
            <UserMenu />
          </template>
          <template v-else>
            <el-button text class="hide-sm" @click="go('/login')">登录</el-button>
            <el-button type="primary" round @click="go('/register')">注册</el-button>
          </template>

          <button
            type="button"
            class="site-header__toggle"
            aria-label="打开导航菜单"
            @click="mobileNavOpen = true"
          >
            <el-icon><Menu /></el-icon>
          </button>
        </div>
      </div>
    </header>

    <main class="site-main">
      <router-view v-slot="{ Component, route: viewRoute }">
        <transition name="fade-slide" mode="out-in">
          <component :is="Component" :key="viewRoute.path" />
        </transition>
      </router-view>
    </main>

    <footer class="site-footer">
      <div class="site-footer__inner app-container">
        <div class="site-footer__brand">
          <BrandMark size="sm" />
          <p>外卖 + 堂食一体化点餐 MVP，Vue 3 · Spring Boot 3 · MySQL / Redis / Nginx。</p>
        </div>
        <div class="site-footer__col">
          <h4>顾客服务</h4>
          <router-link to="/menu">浏览菜单</router-link>
          <router-link to="/orders">我的订单</router-link>
          <router-link to="/addresses">收货地址</router-link>
        </div>
        <div class="site-footer__col">
          <h4>工作台</h4>
          <router-link to="/login">商家登录</router-link>
          <router-link to="/login">管理员登录</router-link>
          <router-link to="/register">注册顾客账号</router-link>
        </div>
        <div class="site-footer__col site-footer__col--note">
          <h4>演示说明</h4>
          <p>模拟支付：界面展示真实订单金额，实际扣款 0.01 元。</p>
          <p>订单与桌台状态通过 WebSocket（/ws）实时推送。</p>
        </div>
      </div>
      <div class="site-footer__bottom app-container">
        <span>© 2026 拾味点餐 · 教学演示项目</span>
        <span class="site-footer__stage"><i class="tag-dot" /> MVP · 阶段 4A（Web 骨架）</span>
      </div>
    </footer>

    <el-drawer v-model="mobileNavOpen" direction="rtl" size="78%" :with-header="false">
      <div class="mobile-nav">
        <BrandMark size="sm" />
        <nav class="mobile-nav__links">
          <button
            v-for="item in navItems"
            :key="item.path"
            type="button"
            class="mobile-nav__link"
            :class="{ 'is-active': isActive(item) }"
            @click="go(item.path)"
          >
            <el-icon><component :is="item.icon" /></el-icon>
            {{ item.title }}
          </button>
          <button type="button" class="mobile-nav__link" @click="go('/cart')">
            <el-icon><ShoppingCart /></el-icon>
            购物车
          </button>
        </nav>
        <div class="mobile-nav__footer">
          <template v-if="auth.isLoggedIn">
            <el-button round class="mobile-nav__btn" @click="go(auth.homePath)">进入我的空间</el-button>
          </template>
          <template v-else>
            <el-button type="primary" round class="mobile-nav__btn" @click="go('/login')">登录</el-button>
            <el-button round class="mobile-nav__btn" @click="go('/register')">注册新账号</el-button>
          </template>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<style scoped>
.customer-layout {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
}

/* ============ 吸顶导航 ============ */
.site-header {
  position: sticky;
  top: 0;
  z-index: var(--z-header);
  background: rgba(255, 253, 251, 0.82);
  backdrop-filter: blur(14px);
  border-bottom: 1px solid var(--color-border-subtle);
}

.site-header__inner {
  display: flex;
  align-items: center;
  gap: var(--space-8);
  height: var(--layout-header-height);
}

.site-header__brand {
  flex: none;
  color: inherit;
}

.site-header__brand:hover {
  color: inherit;
}

.site-nav {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

.site-nav__link {
  position: relative;
  padding: var(--space-2) var(--space-4);
  border-radius: var(--radius-pill);
  font-size: var(--font-size-md);
  color: var(--color-text-secondary);
  transition: all var(--duration-fast) var(--ease-standard);
}

.site-nav__link:hover {
  color: var(--color-text-primary);
  background: var(--color-bg-subtle);
}

.site-nav__link.is-active {
  color: var(--color-primary);
  font-weight: var(--font-weight-medium);
  background: var(--color-brand-50);
}

.site-header__actions {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  margin-left: auto;
}

.site-header__cart-badge {
  display: inline-flex;
}

.site-header__cart-badge :deep(.el-badge__content) {
  top: 6px;
  right: 12px;
  border: none;
  background-color: var(--color-primary);
}

.site-header__cart {
  color: var(--color-text-secondary);
}

.site-header__cart-text {
  margin-left: var(--space-2);
}

.site-header__toggle {
  display: none;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  background: var(--color-surface);
  color: var(--color-text-secondary);
  cursor: pointer;
}

.site-main {
  flex: 1;
}

/* ============ 页脚 ============ */
.site-footer {
  margin-top: var(--space-12);
  background: #2a211c;
  color: rgba(207, 197, 187, 0.72);
}

.site-footer__inner {
  display: grid;
  grid-template-columns: 1.6fr 1fr 1fr 1.2fr;
  gap: var(--space-9);
  padding-top: var(--space-10);
  padding-bottom: var(--space-9);
}

.site-footer__brand p {
  margin-top: var(--space-4);
  max-width: 280px;
  font-size: var(--font-size-sm);
  line-height: var(--line-height-loose);
}

.site-footer__col {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
  font-size: var(--font-size-sm);
}

.site-footer__col h4 {
  margin-bottom: var(--space-2);
  font-size: var(--font-size-sm);
  color: var(--color-text-inverse);
  letter-spacing: 0.04em;
}

.site-footer__col a {
  color: rgba(207, 197, 187, 0.72);
}

.site-footer__col a:hover {
  color: #f6c56e;
}

.site-footer__col--note p {
  font-size: var(--font-size-xs);
  line-height: var(--line-height-loose);
}

.site-footer__bottom {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-4);
  padding-top: var(--space-5);
  padding-bottom: var(--space-6);
  border-top: 1px solid rgba(255, 255, 255, 0.08);
  font-size: var(--font-size-xs);
  color: rgba(207, 197, 187, 0.48);
}

.site-footer__stage {
  display: inline-flex;
  align-items: center;
}

/* ============ 移动端抽屉 ============ */
.mobile-nav {
  display: flex;
  flex-direction: column;
  gap: var(--space-8);
  height: 100%;
  padding: var(--space-2);
}

.mobile-nav__links {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.mobile-nav__link {
  display: flex;
  align-items: center;
  gap: var(--space-4);
  width: 100%;
  padding: var(--space-5) var(--space-4);
  border: none;
  border-radius: var(--radius-md);
  background: transparent;
  color: var(--color-text-secondary);
  font-size: var(--font-size-lg);
  text-align: left;
  cursor: pointer;
}

.mobile-nav__link.is-active {
  background: var(--color-brand-50);
  color: var(--color-primary);
  font-weight: var(--font-weight-medium);
}

.mobile-nav__footer {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
  margin-top: auto;
}

.mobile-nav__btn {
  width: 100%;
}

@media (max-width: 960px) {
  .site-nav {
    display: none;
  }

  .site-header__toggle {
    display: inline-flex;
  }

  .site-footer__inner {
    grid-template-columns: 1fr 1fr;
  }
}

@media (max-width: 640px) {
  .site-header__inner {
    gap: var(--space-4);
  }

  .site-header__cart-text {
    display: none;
  }

  .site-footer__inner {
    grid-template-columns: 1fr;
    gap: var(--space-6);
  }

  .site-footer__bottom {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
