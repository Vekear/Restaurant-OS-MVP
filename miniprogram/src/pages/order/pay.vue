<template>
  <view class="pay-page">
    <template v-if="loading">
      <view class="skeleton">
        <view class="skeleton__row"></view>
        <view class="skeleton__row"></view>
      </view>
    </template>

    <template v-else-if="order">
      <view class="amount-card">
        <text class="amount-card__label">订单金额</text>
        <view class="amount-card__amount">
          <text class="amount-card__symbol">¥</text>
          <text class="amount-card__value">{{ formatAmount(order.payAmount) }}</text>
        </view>
        <view class="amount-card__meta">
          <text class="amount-card__meta-text">订单号 {{ order.orderNo }}</text>
          <text v-if="order.tableNo" class="amount-card__meta-text">桌台 {{ order.tableNo }}</text>
        </view>
        <view class="amount-card__mock">
          <view class="amount-card__dot"></view>
          <text class="amount-card__mock-text">
            模拟支付：实际扣款 ¥{{ formatAmount(paidAmount) }}（演示环境），报表按订单金额统计
          </text>
        </view>
      </view>

      <view class="card">
        <view class="card__title">支付方式</view>
        <view class="pay-method">
          <view class="pay-method__icon">微</view>
          <view class="pay-method__main">
            <text class="pay-method__name">微信支付（模拟）</text>
            <text class="pay-method__desc">演示环境不产生真实扣款</text>
          </view>
          <view class="pay-method__check"></view>
        </view>
        <view v-if="countdownText" class="countdown">
          <text class="countdown__text">请在 {{ countdownText }} 内完成支付，超时订单将自动取消</text>
        </view>
      </view>

      <view class="actions">
        <view class="btn-primary" :class="{ 'btn-primary--disabled': paying }" @tap="payNow">
          {{ paying ? '支付处理中…' : '确认支付' }}
        </view>
        <view class="btn-primary btn-primary--ghost" @tap="goStatus">稍后支付，查看订单</view>
      </view>
    </template>

    <template v-else>
      <EmptyState title="订单不存在" description="订单可能已超时取消，请返回重新下单" action-text="去点餐" @action="goMenu" />
    </template>
  </view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { onLoad, onUnload } from '@dcloudio/uni-app'
import EmptyState from '@/components/EmptyState.vue'
import { fetchDineInOrder } from '@/api/order'
import { createPayment, mockPay } from '@/api/payment'
import { friendlyMessage } from '@/api/dinein'
import { formatAmount } from '@/utils/format'

const order = ref(null)
const payment = ref(null)
const loading = ref(true)
const paying = ref(false)
const countdownText = ref('')
let countdownTimer = null

const paidAmount = computed(() => (payment.value ? payment.value.paidAmount : 0.01))

function startCountdown(expireAt) {
  stopCountdown()
  if (!expireAt) return
  const tick = () => {
    const remain = new Date(String(expireAt)).getTime() - Date.now()
    if (Number.isNaN(remain) || remain <= 0) {
      countdownText.value = ''
      stopCountdown()
      return
    }
    const total = Math.floor(remain / 1000)
    const minutes = String(Math.floor(total / 60)).padStart(2, '0')
    const seconds = String(total % 60).padStart(2, '0')
    countdownText.value = `${minutes}:${seconds}`
  }
  tick()
  countdownTimer = setInterval(tick, 1000)
}

function stopCountdown() {
  if (countdownTimer) {
    clearInterval(countdownTimer)
    countdownTimer = null
  }
}

async function loadOrder(orderId) {
  loading.value = true
  try {
    const detail = await fetchDineInOrder(orderId)
    order.value = detail
    if (detail.status !== 'PENDING_PAYMENT') {
      uni.redirectTo({ url: `/pages/order/status?orderId=${detail.id}` })
      return
    }
    startCountdown(detail.timeoutAt)
    await ensurePayment(detail.id)
  } catch (error) {
    order.value = null
    uni.showModal({
      title: '加载订单失败',
      content: friendlyMessage(error),
      showCancel: false,
      confirmText: '我知道了',
      confirmColor: '#FF6B35'
    })
  } finally {
    loading.value = false
  }
}

async function ensurePayment(orderId) {
  try {
    payment.value = await createPayment({ orderId, orderType: 'DINE_IN' })
  } catch (error) {
    // 已支付：直接进入订单状态页
    if (error && error.code === 40913) {
      uni.redirectTo({ url: `/pages/order/status?orderId=${orderId}` })
      return
    }
    uni.showModal({
      title: '创建支付单失败',
      content: friendlyMessage(error),
      showCancel: false,
      confirmText: '我知道了',
      confirmColor: '#FF6B35'
    })
  }
}

async function payNow() {
  if (paying.value) return
  if (!payment.value || !payment.value.payNo) {
    const current = order.value
    if (current) await ensurePayment(current.id)
    if (!payment.value || !payment.value.payNo) return
  }
  paying.value = true
  try {
    await mockPay(payment.value.payNo)
    uni.showToast({ title: '支付成功', icon: 'success' })
    setTimeout(() => {
      uni.redirectTo({ url: `/pages/order/status?orderId=${order.value.id}` })
    }, 600)
  } catch (error) {
    uni.showModal({
      title: '支付失败',
      content: friendlyMessage(error),
      showCancel: false,
      confirmText: '我知道了',
      confirmColor: '#FF6B35'
    })
  } finally {
    paying.value = false
  }
}

function goStatus() {
  if (!order.value) {
    goMenu()
    return
  }
  uni.redirectTo({ url: `/pages/order/status?orderId=${order.value.id}` })
}

function goMenu() {
  uni.switchTab({ url: '/pages/menu/menu' })
}

onLoad((options) => {
  const orderId = (options && options.orderId) || ''
  if (!orderId) {
    uni.showToast({ title: '缺少订单号', icon: 'none' })
    goMenu()
    return
  }
  loadOrder(orderId)
})

onUnload(() => {
  stopCountdown()
})
</script>

<style lang="scss" scoped>
.pay-page {
  min-height: 100vh;
  padding: $space-lg $space-lg calc(#{$space-xl} + env(safe-area-inset-bottom));
}

.amount-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 48rpx $space-lg;
  border-radius: $radius-xl;
  background: linear-gradient(150deg, #ff7a45 0%, #ff5a1f 100%);
  box-shadow: $shadow-float;

  &__label {
    font-size: $font-xs;
    color: rgba(255, 255, 255, 0.8);
    letter-spacing: 2rpx;
  }

  &__amount {
    display: flex;
    align-items: baseline;
    margin-top: 12rpx;
    color: #ffffff;
  }

  &__symbol {
    font-size: $font-lg;
  }

  &__value {
    margin-left: 8rpx;
    font-size: 84rpx;
    font-weight: 800;
    letter-spacing: 1rpx;
  }

  &__meta {
    display: flex;
    margin-top: 12rpx;
  }

  &__meta-text {
    margin: 0 12rpx;
    font-size: $font-xs;
    color: rgba(255, 255, 255, 0.85);
  }

  &__mock {
    display: flex;
    align-items: flex-start;
    margin-top: $space-lg;
    padding: $space-sm $space-md;
    border-radius: $radius-md;
    background: rgba(255, 255, 255, 0.16);
  }

  &__dot {
    width: 12rpx;
    height: 12rpx;
    margin-top: 10rpx;
    margin-right: 10rpx;
    border-radius: 50%;
    background: #ffd166;
  }

  &__mock-text {
    flex: 1;
    font-size: $font-xs;
    line-height: 1.6;
    color: rgba(255, 255, 255, 0.92);
  }
}

.card {
  margin-top: $space-lg;
  padding: $space-lg;
  border-radius: $radius-xl;
  background: $color-card;
  box-shadow: $shadow-card;

  &__title {
    font-size: $font-md;
    font-weight: 700;
    color: $color-text;
  }
}

.pay-method {
  display: flex;
  align-items: center;
  margin-top: $space-md;
  padding: $space-md;
  border-radius: $radius-lg;
  background: $color-bg;

  &__icon {
    width: 72rpx;
    height: 72rpx;
    border-radius: 50%;
    background: #07c160;
    color: #ffffff;
    font-size: $font-lg;
    font-weight: 700;
    display: flex;
    align-items: center;
    justify-content: center;
  }

  &__main {
    flex: 1;
    display: flex;
    flex-direction: column;
    margin-left: $space-md;
  }

  &__name {
    font-size: $font-sm;
    font-weight: 600;
    color: $color-text;
  }

  &__desc {
    margin-top: 4rpx;
    font-size: $font-xs;
    color: $color-text-muted;
  }

  &__check {
    width: 36rpx;
    height: 36rpx;
    border-radius: 50%;
    background: $color-brand;
    position: relative;

    &::after {
      content: '';
      position: absolute;
      left: 12rpx;
      top: 7rpx;
      width: 10rpx;
      height: 18rpx;
      border-right: 4rpx solid #ffffff;
      border-bottom: 4rpx solid #ffffff;
      transform: rotate(45deg);
    }
  }
}

.countdown {
  margin-top: $space-md;

  &__text {
    font-size: $font-xs;
    color: $color-text-light;
  }
}

.actions {
  margin-top: $space-xl;

  .btn-primary + .btn-primary {
    margin-top: $space-md;
  }

  .btn-primary--disabled {
    opacity: 0.6;
  }
}

.skeleton {
  padding: $space-lg;

  &__row {
    height: 60rpx;
    margin-bottom: $space-md;
    border-radius: $radius-md;
    background: #f3e7df;
  }
}
</style>
