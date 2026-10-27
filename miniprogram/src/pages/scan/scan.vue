<template>
  <view class="scan-page">
    <!-- 已入座：展示当前会话 -->
    <template v-if="hasSession">
      <view class="session-card">
        <view class="session-card__glow"></view>
        <view class="session-card__head">
          <text class="session-card__eyebrow">当前桌台</text>
          <view class="session-card__status">
            <view class="session-card__dot"></view>
            <text>用餐中</text>
          </view>
        </view>
        <text class="session-card__table">{{ tableLabel }}</text>
        <text class="session-card__merchant">{{ merchantName }}</text>
        <view class="session-card__meta">
          <view class="session-card__meta-item">
            <text class="session-card__meta-label">入座时间</text>
            <text class="session-card__meta-value">{{ openedText }}</text>
          </view>
          <view class="session-card__meta-item">
            <text class="session-card__meta-label">会话到期</text>
            <text class="session-card__meta-value">{{ expireText }}</text>
          </view>
        </view>
        <view class="session-card__actions">
          <view class="btn-primary" @tap="goMenu">去点餐</view>
          <view class="btn-primary btn-primary--ghost" @tap="confirmClose">结束用餐</view>
        </view>
      </view>

      <view class="panel">
        <view class="panel__title">换桌 / 重新扫码</view>
        <view class="panel__desc">换了桌台时，重新扫一次桌上的二维码即可切换会话（有其他桌进行中的订单时需先处理）。</view>
        <view class="btn-primary btn-primary--ghost" @tap="startScan">扫一扫换桌</view>
      </view>
    </template>

    <!-- 未入座：扫码 + 手输兜底 -->
    <template v-else>
      <view class="hero">
        <view class="hero__qr">
          <view class="hero__corner hero__corner--tl"></view>
          <view class="hero__corner hero__corner--tr"></view>
          <view class="hero__corner hero__corner--bl"></view>
          <view class="hero__corner hero__corner--br"></view>
          <view class="hero__logo">小馆</view>
        </view>
        <text class="hero__title">扫码入座</text>
        <text class="hero__desc">扫描桌角二维码，绑定桌台后即可点餐</text>
      </view>

      <view class="panel">
        <view class="btn-primary" @tap="startScan">扫一扫桌上的二维码</view>
        <view v-if="scanUnsupported" class="panel__hint">
          当前环境不支持扫码（H5 预览），请使用下方手输桌号，或在小程序真机中使用扫码。
        </view>

        <view class="divider">
          <view class="divider__line"></view>
          <text class="divider__text">或手动输入桌号</text>
          <view class="divider__line"></view>
        </view>

        <view class="manual">
          <input
            v-model="tableNo"
            class="manual__input"
            type="text"
            placeholder="例如 A01"
            placeholder-class="manual__placeholder"
            maxlength="20"
            confirm-type="done"
            @confirm="submitManual"
          />
          <view class="manual__btn" :class="{ 'manual__btn--disabled': submitting }" @tap="submitManual">
            {{ submitting ? '绑定中' : '绑定桌台' }}
          </view>
        </view>
        <view class="panel__hint">手输绑定为演示降级能力；正式环境请以扫码为准。</view>
      </view>

      <view class="tips">
        <text class="tips__title">入座说明</text>
        <text class="tips__item">1. 每张桌台都有专属二维码，扫码即可绑定当前桌号</text>
        <text class="tips__item">2. 一个桌台同时只允许一个顾客会话，被占用时会提示联系服务员</text>
        <text class="tips__item">3. 用餐结束后可在本页「结束用餐」释放桌台</text>
      </view>
    </template>
  </view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { scanTable, bindTable, closeTableSession, friendlyMessage } from '@/api/dinein'
import { useTableStore } from '@/store/table'
import { useUserStore } from '@/store/user'
import { parseTableQr, isTableId, isQrSignature, normalizeTableNo } from '@/utils/qr'
import { formatDateTime } from '@/utils/format'

const tableStore = useTableStore()
const { hasSession, tableLabel, merchantName, state: tableState, setSession, clearSession } = tableStore
const { isLoggedIn } = useUserStore()

const tableNo = ref('')
const submitting = ref(false)
const scanUnsupported = ref(false)

const openedText = computed(() => formatDateTime(tableState.session && tableState.session.openedAt))
const expireText = computed(() => formatDateTime(tableState.session && tableState.session.expireAt))

function requireLogin() {
  if (isLoggedIn.value) return true
  uni.showModal({
    title: '需要登录',
    content: '绑定桌台前请先完成微信登录',
    confirmText: '去登录',
    confirmColor: '#FF6B35',
    success: (res) => {
      if (res.confirm) uni.navigateTo({ url: '/pages/login/login' })
    }
  })
  return false
}

function showError(error, title) {
  uni.showModal({
    title: title || '绑定失败',
    content: friendlyMessage(error),
    showCancel: false,
    confirmText: '我知道了',
    confirmColor: '#FF6B35'
  })
}

function applySession(session) {
  setSession(session)
  uni.showToast({ title: `已绑定 ${session.tableNo || ''}`.trim(), icon: 'success' })
  setTimeout(() => {
    uni.switchTab({ url: '/pages/menu/menu' })
  }, 600)
}

async function bindByScan(p, s) {
  if (!requireLogin()) return
  if (!isTableId(p) || !isQrSignature(s)) {
    showError({ message: '二维码内容不合法，请让商家重新生成', code: 40004 }, '二维码无法识别')
    return
  }
  try {
    const session = await scanTable({ p, s })
    applySession(session)
  } catch (error) {
    showError(error, '扫码绑定失败')
  }
}

function startScan() {
  if (!requireLogin()) return
  if (typeof uni.scanCode !== 'function') {
    scanUnsupported.value = true
    return
  }
  uni.scanCode({
    scanType: ['qrCode'],
    success: (res) => {
      const parsed = parseTableQr(res && res.result)
      if (!parsed) {
        showError({ message: '未识别到桌台信息，请扫描桌上专属二维码' }, '二维码无法识别')
        return
      }
      bindByScan(parsed.p, parsed.s)
    },
    fail: (error) => {
      const message = String((error && error.errMsg) || '')
      if (message.indexOf('cancel') !== -1) return
      scanUnsupported.value = true
      uni.showToast({ title: '当前环境暂不支持扫码，请手输桌号', icon: 'none' })
    }
  })
}

async function submitManual() {
  if (!requireLogin()) return
  const no = normalizeTableNo(tableNo.value)
  if (!no) {
    uni.showToast({ title: '请输入桌号', icon: 'none' })
    return
  }
  if (submitting.value) return
  submitting.value = true
  try {
    const session = await bindTable({ tableNo: no })
    applySession(session)
  } catch (error) {
    showError(error, '绑定桌台失败')
  } finally {
    submitting.value = false
  }
}

function confirmClose() {
  uni.showModal({
    title: '结束用餐',
    content: '结束后将释放桌台，确定要结束本次用餐吗？',
    confirmColor: '#FF6B35',
    success: async (res) => {
      if (!res.confirm) return
      try {
        await closeTableSession()
        clearSession()
        uni.showToast({ title: '已结束用餐', icon: 'none' })
      } catch (error) {
        showError(error, '结束用餐失败')
      }
    }
  })
}

function goMenu() {
  uni.switchTab({ url: '/pages/menu/menu' })
}

onLoad((options) => {
  const query = options || {}
  if (query.p && query.s) {
    bindByScan(query.p, query.s)
  } else if (query.tableNo) {
    tableNo.value = String(query.tableNo)
  }
})
</script>

<style lang="scss" scoped>
.scan-page {
  min-height: 100vh;
  padding: $space-lg $space-lg calc(#{$space-xl} + env(safe-area-inset-bottom));
}

.hero {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 48rpx 0 24rpx;

  &__qr {
    position: relative;
    width: 240rpx;
    height: 240rpx;
    border-radius: 40rpx;
    background: linear-gradient(160deg, #fff4ee 0%, #ffe3d5 100%);
    display: flex;
    align-items: center;
    justify-content: center;
    box-shadow: $shadow-brand;
  }

  &__logo {
    font-size: 44rpx;
    font-weight: 800;
    color: $color-brand-dark;
    letter-spacing: 4rpx;
  }

  &__corner {
    position: absolute;
    width: 44rpx;
    height: 44rpx;
    border: 6rpx solid $color-brand;

    &--tl {
      top: 24rpx;
      left: 24rpx;
      border-right: none;
      border-bottom: none;
      border-top-left-radius: 18rpx;
    }

    &--tr {
      top: 24rpx;
      right: 24rpx;
      border-left: none;
      border-bottom: none;
      border-top-right-radius: 18rpx;
    }

    &--bl {
      bottom: 24rpx;
      left: 24rpx;
      border-right: none;
      border-top: none;
      border-bottom-left-radius: 18rpx;
    }

    &--br {
      bottom: 24rpx;
      right: 24rpx;
      border-left: none;
      border-top: none;
      border-bottom-right-radius: 18rpx;
    }
  }

  &__title {
    margin-top: $space-lg;
    font-size: 46rpx;
    font-weight: 800;
    color: $color-text;
  }

  &__desc {
    margin-top: 10rpx;
    font-size: $font-sm;
    color: $color-text-muted;
  }
}

.panel {
  margin-top: $space-lg;
  padding: $space-lg;
  border-radius: $radius-xl;
  background: $color-card;
  box-shadow: $shadow-card;

  &__title {
    font-size: $font-lg;
    font-weight: 700;
    color: $color-text;
  }

  &__desc {
    margin-top: 10rpx;
    font-size: $font-sm;
    line-height: 1.6;
    color: $color-text-muted;
  }

  &__hint {
    margin-top: $space-md;
    font-size: $font-xs;
    line-height: 1.6;
    color: $color-text-light;
  }
}

.divider {
  display: flex;
  align-items: center;
  margin: $space-lg 0 $space-md;

  &__line {
    flex: 1;
    height: 1rpx;
    background: $color-border;
  }

  &__text {
    margin: 0 $space-md;
    font-size: $font-xs;
    color: $color-text-light;
  }
}

.manual {
  display: flex;
  align-items: center;

  &__input {
    flex: 1;
    height: 88rpx;
    padding: 0 $space-md;
    border-radius: $radius-md;
    background: $color-bg;
    font-size: $font-md;
    color: $color-text;
  }

  &__placeholder {
    color: $color-text-light;
  }

  &__btn {
    margin-left: $space-md;
    padding: 0 $space-lg;
    height: 88rpx;
    line-height: 88rpx;
    border-radius: $radius-md;
    background: $color-brand;
    color: #ffffff;
    font-size: $font-sm;
    font-weight: 700;
    box-shadow: $shadow-brand;

    &--disabled {
      opacity: 0.6;
    }
  }
}

.tips {
  display: flex;
  flex-direction: column;
  margin-top: $space-lg;
  padding: 0 $space-xs;

  &__title {
    font-size: $font-sm;
    font-weight: 700;
    color: $color-text-muted;
  }

  &__item {
    margin-top: 12rpx;
    font-size: $font-xs;
    line-height: 1.7;
    color: $color-text-light;
  }
}

.session-card {
  position: relative;
  overflow: hidden;
  padding: $space-lg;
  border-radius: $radius-xl;
  background: linear-gradient(135deg, #ff7a45 0%, #ff5a1f 100%);
  box-shadow: $shadow-float;

  &__glow {
    position: absolute;
    top: -80rpx;
    right: -60rpx;
    width: 260rpx;
    height: 260rpx;
    border-radius: 50%;
    background: rgba(255, 255, 255, 0.18);
  }

  &__head {
    display: flex;
    align-items: center;
    justify-content: space-between;
  }

  &__eyebrow {
    font-size: $font-xs;
    color: rgba(255, 255, 255, 0.85);
    letter-spacing: 2rpx;
  }

  &__status {
    display: flex;
    align-items: center;
    padding: 6rpx 16rpx;
    border-radius: $radius-pill;
    background: rgba(255, 255, 255, 0.22);
    color: #ffffff;
    font-size: $font-xs;
  }

  &__dot {
    width: 12rpx;
    height: 12rpx;
    margin-right: 8rpx;
    border-radius: 50%;
    background: #7cf29c;
  }

  &__table {
    display: block;
    margin-top: 18rpx;
    font-size: 64rpx;
    font-weight: 800;
    color: #ffffff;
    letter-spacing: 2rpx;
  }

  &__merchant {
    display: block;
    margin-top: 6rpx;
    font-size: $font-sm;
    color: rgba(255, 255, 255, 0.9);
  }

  &__meta {
    display: flex;
    margin-top: $space-lg;
  }

  &__meta-item {
    flex: 1;
    display: flex;
    flex-direction: column;
  }

  &__meta-label {
    font-size: $font-xs;
    color: rgba(255, 255, 255, 0.7);
  }

  &__meta-value {
    margin-top: 6rpx;
    font-size: $font-sm;
    font-weight: 600;
    color: #ffffff;
  }

  &__actions {
    margin-top: $space-lg;
    display: flex;

    .btn-primary {
      background: #ffffff;
      color: $color-brand-dark;
      box-shadow: none;

      &--ghost {
        margin-left: $space-md;
        background: rgba(255, 255, 255, 0.16);
        color: #ffffff;
        border: 1rpx solid rgba(255, 255, 255, 0.5);
      }
    }
  }
}
</style>
