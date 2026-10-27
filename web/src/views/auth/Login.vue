<script setup>
/**
 * 登录页：账号密码 + 演示账号一键登录
 * 登录成功后按角色进入对应空间（顾客 / 商家 / 管理后台），并回跳到 redirect 指定页面。
 */
import { computed, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import AuthLayout from '@/layouts/AuthLayout.vue'
import { useAuthStore } from '@/stores/auth'
import { safeRedirect } from '@/utils/redirect'
import { STORAGE_KEYS, readRaw, removeRaw, writeRaw } from '@/utils/storage'

const REMEMBER_KEY = `${STORAGE_KEYS.token}.remembered-username`

const auth = useAuthStore()
const router = useRouter()
const route = useRoute()

const formRef = ref(null)
const loading = ref(false)
const remembered = ref(Boolean(readRaw(REMEMBER_KEY)))
const form = reactive({ username: readRaw(REMEMBER_KEY) || '', password: '' })

const rules = {
  username: [
    { required: true, message: '请输入登录账号', trigger: 'blur' },
    // 与 docs/02-api.md §4.1 对齐：4–20 位字母/数字/下划线
    { pattern: /^[A-Za-z0-9_]{4,20}$/, message: '账号为 4 - 20 位字母、数字或下划线', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 32, message: '密码长度为 6 - 32 个字符', trigger: 'blur' }
  ]
}

const demoAccounts = [
  {
    role: '顾客',
    username: 'customer',
    password: 'customer123',
    desc: '点餐下单、订单追踪、收货地址'
  },
  {
    role: '商家',
    username: 'merchant',
    password: 'merchant123',
    desc: '接单出餐、菜品与桌台、营业报表'
  },
  {
    role: '管理员',
    username: 'admin',
    password: 'admin123',
    desc: '用户商家治理、优惠券、平台报表'
  }
]

const registered = computed(() => route.query.registered === '1')
const prefillUsername = computed(() => {
  const value = route.query.username
  return typeof value === 'string' ? value : ''
})
if (prefillUsername.value) form.username = prefillUsername.value

watch(remembered, (value) => {
  if (!value) removeRaw(REMEMBER_KEY)
})

watch(
  () => form.username,
  (value) => {
    if (remembered.value) writeRaw(REMEMBER_KEY, value)
  }
)

/** 把 redirect 限制在当前角色可访问的范围内 */
function resolveTarget() {
  const redirect = safeRedirect(route.query.redirect)
  if (!redirect) return auth.homePath
  if (auth.isCustomer && /^\/(merchant|admin)(\/|$)/.test(redirect)) return '/'
  if (auth.isMerchant && /^\/admin(\/|$)/.test(redirect)) return '/merchant/dashboard'
  if (auth.isAdmin && /^\/merchant(\/|$)/.test(redirect)) return '/admin/dashboard'
  return redirect
}

async function handleSubmit() {
  if (!formRef.value || loading.value) return
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  loading.value = true
  try {
    const username = form.username.trim()
    const user = await auth.login({ username, password: form.password })
    if (remembered.value) writeRaw(REMEMBER_KEY, username)
    else removeRaw(REMEMBER_KEY)
    ElMessage.success(`欢迎回来，${user?.nickname || username}`)
    await router.replace(resolveTarget())
  } catch (error) {
    // 业务/网络错误已由 api/http.js 统一提示，这里只兜底未提示的场景
    if (!error?.status && !error?.code && error?.message?.includes('令牌')) {
      ElMessage.error(error.message)
    }
  } finally {
    loading.value = false
  }
}

function loginWithDemo(account) {
  form.username = account.username
  form.password = account.password
  remembered.value = true
  handleSubmit()
}
</script>

<template>
  <AuthLayout>
    <header class="auth-card__header">
      <span class="auth-card__eyebrow">Sign in</span>
      <h1 class="auth-card__title">欢迎回来</h1>
      <p class="auth-card__subtitle">
        顾客、商家与管理员共用同一入口，登录后自动进入对应空间。
      </p>
    </header>

    <el-alert
      v-if="registered"
      class="mt-6"
      type="success"
      :closable="false"
      show-icon
      title="注册成功，请使用新账号登录"
    />

    <el-form
      ref="formRef"
      class="auth-form"
      :model="form"
      :rules="rules"
      label-position="top"
      size="large"
      @submit.prevent="handleSubmit"
    >
      <el-form-item label="账号" prop="username">
        <el-input
          v-model="form.username"
          placeholder="请输入用户名"
          autocomplete="username"
          clearable
        >
          <template #prefix>
            <el-icon><User /></el-icon>
          </template>
        </el-input>
      </el-form-item>

      <el-form-item label="密码" prop="password">
        <el-input
          v-model="form.password"
          type="password"
          placeholder="请输入密码"
          autocomplete="current-password"
          show-password
        >
          <template #prefix>
            <el-icon><Lock /></el-icon>
          </template>
        </el-input>
      </el-form-item>

      <el-checkbox v-model="remembered" class="auth-form__remember">
        记住账号（仅保存用户名）
      </el-checkbox>

      <el-button
        type="primary"
        native-type="submit"
        class="auth-form__submit mt-6"
        :loading="loading"
      >
        登录
      </el-button>
    </el-form>

    <div class="auth-demo">
      <p class="auth-demo__title">演示账号 · 点击直接登录</p>
      <div class="auth-demo__list">
        <button
          v-for="account in demoAccounts"
          :key="account.username"
          type="button"
          class="demo-account"
          :disabled="loading"
          @click="loginWithDemo(account)"
        >
          <span class="demo-account__role">{{ account.role }}</span>
          <span class="demo-account__body">
            <span class="demo-account__desc">{{ account.desc }}</span>
            <span class="demo-account__cred">{{ account.username }} / {{ account.password }}</span>
          </span>
          <el-icon class="demo-account__arrow"><ArrowRight /></el-icon>
        </button>
      </div>
    </div>

    <footer class="auth-card__footer">
      还没有账号？
      <router-link :to="{ name: 'register' }">注册顾客账号</router-link>
    </footer>
  </AuthLayout>
</template>

<style scoped>
.auth-form__remember {
  --el-checkbox-text-color: var(--color-text-secondary);
  --el-checkbox-font-size: var(--font-size-sm);
}
</style>
