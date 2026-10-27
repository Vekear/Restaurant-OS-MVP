<template>
  <view class="mine-page">
    <!-- 用户卡片 -->
    <view class="profile">
      <view class="profile__glow profile__glow--a"></view>
      <view class="profile__glow profile__glow--b"></view>
      <view class="profile__body">
        <view class="profile__avatar" @tap="chooseAvatar">
          <AppImage
            :src="avatarUrl"
            width="120rpx"
            height="120rpx"
            radius="50%"
          />
          <view class="profile__avatar-mask">
            <text class="profile__avatar-mask-text">{{ uploading ? '上传中' : '更换' }}</text>
          </view>
        </view>
        <view class="profile__meta">
          <text class="profile__name">{{ displayName }}</text>
          <text class="profile__desc">{{ isLoggedIn ? '已登录 · 堂食会员' : '登录后可绑定桌台并查看订单进度' }}</text>
          <text v-if="hasSession" class="profile__table">当前桌台 {{ tableLabel }}</text>
        </view>
        <view class="profile__action" :class="{ 'profile__action--ghost': isLoggedIn }" @tap="onAuthTap">
          {{ isLoggedIn ? '退出' : '登录' }}
        </view>
      </view>
    </view>

    <!-- 功能入口 -->
    <view class="panel">
      <view class="panel__title">我的服务</view>
      <view class="cell" @tap="openOrders">
        <view class="cell__icon cell__icon--order"></view>
        <view class="cell__main">
          <text class="cell__title">我的订单</text>
          <text class="cell__desc">查看堂食订单与制作进度</text>
        </view>
        <view class="cell__arrow"></view>
      </view>
      <view class="cell" @tap="openTableSession">
        <view class="cell__icon cell__icon--table"></view>
        <view class="cell__main">
          <text class="cell__title">桌台会话</text>
          <text class="cell__desc">扫码入座后在此查看当前桌号</text>
        </view>
        <view class="cell__arrow"></view>
      </view>
      <view class="cell cell--last" @tap="openAbout">
        <view class="cell__icon cell__icon--about"></view>
        <view class="cell__main">
          <text class="cell__title">关于小馆点餐</text>
          <text class="cell__desc">版本与运行环境</text>
        </view>
        <view class="cell__arrow"></view>
      </view>
    </view>

    <!-- 运行环境 -->
    <view class="panel">
      <view class="panel__title">运行环境</view>
      <view class="env-row">
        <text class="env-row__label">数据模式</text>
        <text class="env-row__value">{{ isMock ? '演示数据（VITE_MOCK）' : '后端接口' }}</text>
      </view>
      <view class="env-row env-row--last">
        <text class="env-row__label">接口地址</text>
        <text class="env-row__value env-row__value--path">{{ apiBaseUrl }}</text>
      </view>
    </view>

    <view v-if="isMock" class="mock-tip">
      当前为演示模式：菜单与登录使用内置假数据，不依赖后端服务
    </view>

    <view class="footer">
      <text class="footer__text">小馆点餐 v{{ version }}</text>
      <text class="footer__sub">堂食扫码点餐 · UniApp</text>
    </view>
  </view>
</template>

<script setup>
import { computed, ref } from 'vue'
import AppImage from '@/components/AppImage.vue'
import { useUserStore } from '@/store/user'
import { useTableStore } from '@/store/table'
import { uploadImage, uploadErrorMessage } from '@/api/upload'
import { updateProfile } from '@/api/profile'
import { API_BASE_URL, APP_NAME, APP_VERSION, IS_MOCK } from '@/utils/config'

const userStore = useUserStore()
const { isLoggedIn, displayName, state: userState, signOut, updateUser } = userStore
const tableStore = useTableStore()
const { hasSession, tableLabel } = tableStore

const isMock = IS_MOCK
const version = APP_VERSION
const appName = APP_NAME
const apiBaseUrl = API_BASE_URL
const uploading = ref(false)

const avatarUrl = computed(() => {
  const user = userState.user
  return user && user.avatarUrl ? user.avatarUrl : '/static/logo.png'
})

function onAuthTap() {
  if (!isLoggedIn.value) {
    uni.navigateTo({ url: '/pages/login/login' })
    return
  }
  uni.showModal({
    title: '退出登录',
    content: '退出后购物车内容仍会保留，确定退出吗？',
    confirmColor: '#FF6B35',
    success: (res) => {
      if (!res.confirm) return
      signOut()
      uni.showToast({ title: '已退出登录', icon: 'none' })
    }
  })
}

function openOrders() {
  if (!isLoggedIn.value) {
    uni.navigateTo({ url: '/pages/login/login' })
    return
  }
  uni.navigateTo({ url: '/pages/order/list' })
}

function openTableSession() {
  uni.navigateTo({ url: '/pages/scan/scan' })
}

function chooseAvatar() {
  if (!isLoggedIn.value) {
    uni.navigateTo({ url: '/pages/login/login' })
    return
  }
  if (uploading.value) return
  uni.chooseImage({
    count: 1,
    sizeType: ['compressed'],
    sourceType: ['album', 'camera'],
    success: async (res) => {
      const filePath = (res.tempFilePaths && res.tempFilePaths[0]) || ''
      if (!filePath) return
      uploading.value = true
      try {
        // local 模式走 /files/upload（uni.uploadFile）；oss 模式走 PostPolicy 直传 + confirm
        const file = await uploadImage(filePath, 'avatar')
        if (!isMock) {
          await updateProfile({ avatarUrl: file.url })
        }
        updateUser({ avatarUrl: file.url })
        uni.showToast({ title: '头像已更新', icon: 'success' })
      } catch (error) {
        uni.showToast({ title: uploadErrorMessage(error), icon: 'none', duration: 2400 })
      } finally {
        uploading.value = false
      }
    }
  })
}

function openAbout() {
  uni.showModal({
    title: appName,
    content: `版本 v${version}\n数据模式：${isMock ? '演示数据' : '后端接口'}\n接口地址：${apiBaseUrl}`,
    showCancel: false,
    confirmText: '知道了',
    confirmColor: '#FF6B35'
  })
}
</script>

<style lang="scss" scoped>
.mine-page {
  min-height: 100vh;
  padding: $space-lg $space-lg calc(#{$space-lg} + env(safe-area-inset-bottom));
}

.profile {
  position: relative;
  padding: $space-lg;
  border-radius: $radius-xl;
  background: linear-gradient(135deg, #ff8f5e 0%, $color-brand 58%, $color-brand-dark 100%);
  box-shadow: $shadow-brand;
  overflow: hidden;

  &__glow {
    position: absolute;
    border-radius: 50%;

    &--a {
      width: 220rpx;
      height: 220rpx;
      right: -70rpx;
      top: -90rpx;
      background: rgba(255, 255, 255, 0.16);
    }

    &--b {
      width: 160rpx;
      height: 160rpx;
      left: -50rpx;
      bottom: -80rpx;
      background: rgba(255, 183, 3, 0.3);
    }
  }

  &__body {
    position: relative;
    display: flex;
    align-items: center;
    z-index: 1;
  }

  &__meta {
    flex: 1;
    min-width: 0;
    margin-left: $space-md;
  }

  &__avatar {
    position: relative;
    width: 120rpx;
    height: 120rpx;
    border-radius: 50%;
    overflow: hidden;
  }

  &__avatar-mask {
    position: absolute;
    left: 0;
    right: 0;
    bottom: 0;
    height: 40rpx;
    background: rgba(0, 0, 0, 0.35);
    display: flex;
    align-items: center;
    justify-content: center;
  }

  &__avatar-mask-text {
    font-size: 20rpx;
    color: #ffffff;
    letter-spacing: 1rpx;
  }

  &__table {
    display: inline-block;
    margin-top: 10rpx;
    padding: 4rpx 14rpx;
    border-radius: $radius-pill;
    background: rgba(255, 255, 255, 0.18);
    font-size: 20rpx;
    color: #ffffff;
  }

  &__name {
    display: block;
    font-size: 36rpx;
    font-weight: 700;
    color: #ffffff;
  }

  &__desc {
    display: block;
    margin-top: 8rpx;
    font-size: $font-xs;
    color: rgba(255, 255, 255, 0.82);
    line-height: 1.5;
  }

  &__action {
    flex-shrink: 0;
    padding: 12rpx $space-md;
    border-radius: $radius-pill;
    background: #ffffff;
    color: $color-brand-dark;
    font-size: $font-sm;
    font-weight: 600;

    &--ghost {
      background: rgba(255, 255, 255, 0.18);
      color: #ffffff;
      border: 2rpx solid rgba(255, 255, 255, 0.5);
    }
  }
}

.panel {
  margin-top: $space-lg;
  padding: $space-md $space-md 0;
  border-radius: $radius-lg;
  background: $color-card;
  box-shadow: $shadow-card;
  overflow: hidden;

  &__title {
    font-size: $font-sm;
    font-weight: 600;
    color: $color-text-muted;
    padding: $space-sm $space-sm $space-xs;
  }
}

.cell {
  display: flex;
  align-items: center;
  padding: $space-md $space-sm;
  border-bottom: 2rpx solid #f7f0eb;

  &--last {
    border-bottom: none;
  }

  &__icon {
    position: relative;
    width: 64rpx;
    height: 64rpx;
    border-radius: 20rpx;
    background: $color-brand-soft;
    flex-shrink: 0;

    &--order::after {
      content: '';
      position: absolute;
      left: 20rpx;
      top: 16rpx;
      width: 24rpx;
      height: 30rpx;
      border: 3rpx solid $color-brand;
      border-radius: 4rpx;
    }

    &--table::after {
      content: '';
      position: absolute;
      left: 16rpx;
      top: 24rpx;
      width: 32rpx;
      height: 6rpx;
      border-radius: 3rpx;
      background: $color-brand;
      box-shadow: 0 12rpx 0 0 $color-brand;
    }

    &--about::after {
      content: '';
      position: absolute;
      left: 30rpx;
      top: 14rpx;
      width: 4rpx;
      height: 36rpx;
      border-radius: 2rpx;
      background: $color-brand;
    }
  }

  &__main {
    flex: 1;
    min-width: 0;
    margin-left: $space-md;
  }

  &__title {
    display: block;
    font-size: $font-md;
    font-weight: 600;
    color: $color-text;
  }

  &__desc {
    display: block;
    margin-top: 4rpx;
    font-size: $font-xs;
    color: $color-text-muted;
  }

  &__arrow {
    width: 16rpx;
    height: 16rpx;
    border-top: 3rpx solid #c9bcb4;
    border-right: 3rpx solid #c9bcb4;
    transform: rotate(45deg);
    margin-left: $space-sm;
  }
}

.env-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: $space-md $space-sm;
  border-bottom: 2rpx solid #f7f0eb;

  &--last {
    border-bottom: none;
  }

  &__label {
    font-size: $font-sm;
    color: $color-text-muted;
  }

  &__value {
    font-size: $font-sm;
    color: $color-text;
    max-width: 70%;
    text-align: right;
    word-break: break-all;

    &--path {
      color: $color-text-muted;
      font-size: $font-xs;
    }
  }
}

.mock-tip {
  margin-top: $space-md;
  padding: $space-sm $space-md;
  border-radius: $radius-md;
  background: $color-accent-soft;
  color: #8a6a20;
  font-size: $font-xs;
  line-height: 1.6;
}

.footer {
  margin-top: $space-xl;
  display: flex;
  flex-direction: column;
  align-items: center;

  &__text {
    font-size: $font-xs;
    color: #b6aaa3;
  }

  &__sub {
    margin-top: 6rpx;
    font-size: 20rpx;
    color: #cdbfb6;
  }
}
</style>
