<template>
  <view class="login-page">
    <view class="hero">
      <view class="hero__badge">
        <AppImage src="/static/logo.png" width="128rpx" height="128rpx" radius="32rpx" />
      </view>
      <text class="hero__title">{{ appName }}</text>
      <text class="hero__subtitle">{{ appSubtitle }}</text>
      <view class="hero__chips">
        <text class="hero__chip">扫码入座</text>
        <text class="hero__chip">桌边点餐</text>
        <text class="hero__chip">实时进度</text>
      </view>
    </view>

    <view class="card">
      <template v-if="!isLoggedIn">
        <text class="card__title">微信一键登录</text>
        <text class="card__desc">登录后可绑定桌台、下单并查看后厨制作进度</text>
        <button class="login-btn" :class="{ 'login-btn--loading': loading }" :disabled="loading" @tap="handleLogin">
          {{ loading ? '登录中…' : '微信一键登录' }}
        </button>
        <view class="card__ghost" @tap="goMenu">先看看菜单</view>
        <text class="card__note">仅用于识别你的账户与订单，不会公开微信昵称以外的信息</text>
      </template>

      <template v-else>
        <text class="card__title">已登录</text>
        <text class="card__desc">当前账号：{{ displayName }}</text>
        <button class="login-btn" @tap="goBack">开始点餐</button>
        <view class="card__ghost" @tap="handleLogout">退出登录</view>
      </template>
    </view>

    <view v-if="isMock" class="env-note">
      演示模式：未配置微信 AppID，使用本地模拟登录
    </view>
    <view v-else-if="isDev" class="env-note">
      开发模式：小程序授权失败时会回退到模拟 code（后端需开启 wechat.mock）
    </view>

    <text class="footer">登录即表示同意《用户协议》与《隐私政策》</text>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import AppImage from '@/components/AppImage.vue'
import { wxLogin, normalizeLoginResult } from '@/api/auth'
import { useUserStore } from '@/store/user'
import { APP_NAME, APP_SUBTITLE, IS_DEV, IS_MINI_PROGRAM, IS_MOCK } from '@/utils/config'

const userStore = useUserStore()
const { isLoggedIn, displayName } = userStore

const appName = APP_NAME
const appSubtitle = APP_SUBTITLE
const isMock = IS_MOCK
const isDev = IS_DEV
const loading = ref(false)

function acquireCode() {
  if (IS_MOCK) {
    return Promise.resolve(`mock_${Date.now()}`)
  }
  if (!IS_MINI_PROGRAM) {
    // H5 调试：无 wx.login，使用一次性回退 code，需后端 wechat.mock=true
    return Promise.resolve(`h5_${Date.now()}`)
  }
  return new Promise((resolve) => {
    uni.login({
      provider: 'weixin',
      success: (res) => resolve((res && res.code) || ''),
      fail: () => resolve('')
    })
  })
}

async function handleLogin() {
  if (loading.value) return
  loading.value = true
  try {
    let code = await acquireCode()
    if (!code) {
      if (!IS_DEV) {
        throw new Error('未获取到微信授权 code，请重试')
      }
      code = `dev_fallback_${Date.now()}`
    }
    const payload = await wxLogin(code)
    const result = normalizeLoginResult(payload)
    if (!result.token) {
      throw new Error('登录失败：服务端未返回 token')
    }
    userStore.signIn(result)
    uni.showToast({ title: '登录成功', icon: 'success' })
    setTimeout(() => {
      goBack()
    }, 600)
  } catch (err) {
    uni.showToast({
      title: (err && err.message) || '登录失败，请稍后重试',
      icon: 'none',
      duration: 2400
    })
  } finally {
    loading.value = false
  }
}

function handleLogout() {
  uni.showModal({
    title: '退出登录',
    content: '确定要退出当前账号吗？',
    confirmColor: '#FF6B35',
    success: (res) => {
      if (!res.confirm) return
      userStore.signOut()
      uni.showToast({ title: '已退出登录', icon: 'none' })
    }
  })
}

function goMenu() {
  uni.switchTab({ url: '/pages/menu/menu' })
}

function goBack() {
  const pages = getCurrentPages()
  if (pages && pages.length > 1) {
    uni.navigateBack()
  } else {
    goMenu()
  }
}
</script>

<style lang="scss" scoped>
.login-page {
  min-height: 100vh;
  padding: 96rpx $space-lg calc(#{$space-lg} + env(safe-area-inset-bottom));
  display: flex;
  flex-direction: column;
  background: linear-gradient(180deg, #fff1e9 0%, #fff8f3 42%, #fff8f3 100%);
}

.hero {
  display: flex;
  flex-direction: column;
  align-items: center;

  &__badge {
    padding: 16rpx;
    border-radius: $radius-xl;
    background: #ffffff;
    box-shadow: $shadow-brand;
  }

  &__title {
    margin-top: $space-lg;
    font-size: 52rpx;
    font-weight: 800;
    letter-spacing: 2rpx;
    color: $color-text;
  }

  &__subtitle {
    margin-top: 12rpx;
    font-size: $font-sm;
    color: $color-text-muted;
  }

  &__chips {
    display: flex;
    margin-top: $space-lg;
  }

  &__chip {
    margin: 0 8rpx;
    padding: 8rpx $space-md;
    border-radius: $radius-pill;
    background: rgba(255, 107, 53, 0.1);
    color: $color-brand-dark;
    font-size: $font-xs;
  }
}

.card {
  margin-top: 80rpx;
  padding: $space-lg;
  border-radius: $radius-xl;
  background: $color-card;
  box-shadow: $shadow-card;

  &__title {
    display: block;
    font-size: $font-xl;
    font-weight: 700;
    color: $color-text;
  }

  &__desc {
    display: block;
    margin-top: $space-xs;
    font-size: $font-sm;
    line-height: 1.6;
    color: $color-text-muted;
  }

  &__ghost {
    margin-top: $space-md;
    height: 88rpx;
    border-radius: $radius-pill;
    border: 2rpx solid $color-border;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: $font-md;
    color: $color-text-muted;
  }

  &__note {
    display: block;
    margin-top: $space-md;
    font-size: 22rpx;
    line-height: 1.6;
    color: #b6aaa3;
    text-align: center;
  }
}

.login-btn {
  margin-top: $space-lg;
  height: 96rpx;
  line-height: 96rpx;
  border-radius: $radius-pill;
  background: linear-gradient(135deg, #ff8f5e, $color-brand-dark);
  color: #ffffff;
  font-size: $font-lg;
  font-weight: 700;
  box-shadow: $shadow-brand;
  border: none;
  padding: 0;

  &::after {
    border: none;
  }

  &--loading {
    opacity: 0.75;
  }
}

.env-note {
  margin-top: $space-md;
  padding: $space-sm $space-md;
  border-radius: $radius-md;
  background: rgba(255, 183, 3, 0.16);
  color: #8a6a20;
  font-size: $font-xs;
  line-height: 1.6;
  text-align: center;
}

.footer {
  margin-top: auto;
  padding-top: $space-xl;
  text-align: center;
  font-size: 22rpx;
  color: #c0b3aa;
}
</style>
