<script setup>
/**
 * 注册页：创建顾客账号
 * 后端若在注册响应中返回 token，则直接登录并进入顾客端；否则回到登录页并预填用户名。
 */
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import AuthLayout from '@/layouts/AuthLayout.vue'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const router = useRouter()

const formRef = ref(null)
const loading = ref(false)
const form = reactive({
  username: '',
  nickname: '',
  phone: '',
  password: '',
  confirmPassword: ''
})

function validateConfirmPassword(rule, value, callback) {
  if (!value) callback(new Error('请再次输入密码'))
  else if (value !== form.password) callback(new Error('两次输入的密码不一致'))
  else callback()
}

/** 选填字段：留空跳过校验，填写时按契约校验（docs/02-api.md §4.2） */
function validateOptionalNickname(rule, value, callback) {
  const nickname = String(value || '').trim()
  if (!nickname) return callback()
  if (nickname.length > 20) return callback(new Error('昵称最多 20 个字符'))
  return callback()
}

function validateOptionalPhone(rule, value, callback) {
  const phone = String(value || '').trim()
  if (!phone) return callback()
  return /^1[3-9]\d{9}$/.test(phone) ? callback() : callback(new Error('请输入有效的 11 位手机号'))
}

const rules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    // 与 docs/02-api.md §4.2 对齐：4–20 位字母/数字/下划线，唯一
    {
      pattern: /^[A-Za-z0-9_]{4,20}$/,
      message: '4 - 20 位字母、数字或下划线',
      trigger: 'blur'
    }
  ],
  nickname: [{ validator: validateOptionalNickname, trigger: 'blur' }],
  phone: [{ validator: validateOptionalPhone, trigger: 'blur' }],
  password: [
    { required: true, message: '请设置密码', trigger: 'blur' },
    { min: 6, max: 32, message: '密码长度为 6 - 32 个字符', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, validator: validateConfirmPassword, trigger: 'blur' }
  ]
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
    const payload = {
      username: form.username.trim(),
      password: form.password
    }
    const nickname = form.nickname.trim()
    const phone = form.phone.trim()
    if (nickname) payload.nickname = nickname
    if (phone) payload.phone = phone
    await auth.register(payload)
    if (auth.isLoggedIn) {
      ElMessage.success('注册成功，已为你自动登录')
      await router.replace(auth.homePath)
    } else {
      ElMessage.success('注册成功，请使用新账号登录')
      await router.replace({
        name: 'login',
        query: { registered: '1', username: payload.username }
      })
    }
  } catch (error) {
    // 错误提示由统一拦截器处理
    if (!error?.status && !error?.code && error?.message?.includes('令牌')) {
      ElMessage.error(error.message)
    }
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <AuthLayout>
    <header class="auth-card__header">
      <span class="auth-card__eyebrow">Create account</span>
      <h1 class="auth-card__title">注册顾客账号</h1>
      <p class="auth-card__subtitle">
        注册后即可在线点餐、查看订单进度与维护收货地址；商家与管理员账号由平台开通。
      </p>
    </header>

    <el-form
      ref="formRef"
      class="auth-form"
      :model="form"
      :rules="rules"
      label-position="top"
      size="large"
      @submit.prevent="handleSubmit"
    >
      <el-form-item label="用户名" prop="username">
        <el-input v-model="form.username" placeholder="4 - 20 位字母、数字或下划线" clearable>
          <template #prefix>
            <el-icon><User /></el-icon>
          </template>
        </el-input>
      </el-form-item>

      <div class="auth-form__grid">
        <el-form-item label="昵称（选填）" prop="nickname">
          <el-input v-model="form.nickname" placeholder="点餐时展示的名字" clearable />
        </el-form-item>

        <el-form-item label="手机号（选填）" prop="phone">
          <el-input v-model="form.phone" placeholder="11 位手机号" maxlength="11">
            <template #prefix>
              <el-icon><Iphone /></el-icon>
            </template>
          </el-input>
        </el-form-item>
      </div>

      <div class="auth-form__grid">
        <el-form-item label="密码" prop="password">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="6 - 32 位"
            autocomplete="new-password"
            show-password
          >
            <template #prefix>
              <el-icon><Lock /></el-icon>
            </template>
          </el-input>
        </el-form-item>

        <el-form-item label="确认密码" prop="confirmPassword">
          <el-input
            v-model="form.confirmPassword"
            type="password"
            placeholder="再次输入密码"
            autocomplete="new-password"
            show-password
          >
            <template #prefix>
              <el-icon><Lock /></el-icon>
            </template>
          </el-input>
        </el-form-item>
      </div>

      <p class="auth-form__hint">
        演示环境：密码将以 BCrypt 加密存储；请勿使用真实生产密码。
      </p>

      <el-button
        type="primary"
        native-type="submit"
        class="auth-form__submit mt-4"
        :loading="loading"
      >
        创建账号
      </el-button>
    </el-form>

    <footer class="auth-card__footer">
      已经有账号了？
      <router-link :to="{ name: 'login' }">直接登录</router-link>
    </footer>
  </AuthLayout>
</template>

<style scoped>
.auth-form__hint {
  margin-bottom: var(--space-2);
  font-size: var(--font-size-xs);
  line-height: var(--line-height-base);
  color: var(--color-text-tertiary);
}
</style>
