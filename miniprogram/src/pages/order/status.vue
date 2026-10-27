<template>
  <view class="status-page">
    <template v-if="order">
      <view class="banner" :class="bannerClass">
        <text class="banner__status">{{ order.statusText }}</text>
        <text class="banner__desc">{{ statusDesc }}</text>
        <view v-if="order.isActive" class="banner__live">
          <view class="banner__pulse"></view>
          <text class="banner__live-text">自动刷新中 · 每 {{ pollSeconds }} 秒</text>
        </view>
      </view>

      <view class="card steps">
        <view v-for="(step, index) in steps" :key="step.status" class="step">
          <view class="step__rail">
            <view class="step__dot" :class="stepDotClass(index)"></view>
            <view v-if="index < steps.length - 1" class="step__line" :class="{ 'step__line--done': index < currentStepIndex }"></view>
          </view>
          <view class="step__main">
            <text class="step__name" :class="{ 'step__name--active': index === currentStepIndex, 'step__name--done': index < currentStepIndex }">
              {{ step.label }}
            </text>
            <text v-if="index === currentStepIndex" class="step__hint">{{ step.hint }}</text>
          </view>
        </view>
      </view>

      <view class="card">
        <view class="card__title">订单信息</view>
        <view class="info-row">
          <text class="info-row__label">订单号</text>
          <text class="info-row__value">{{ order.orderNo }}</text>
        </view>
        <view class="info-row">
          <text class="info-row__label">桌台</text>
          <text class="info-row__value">{{ order.tableNo || '—' }}</text>
        </view>
        <view class="info-row">
          <text class="info-row__label">下单时间</text>
          <text class="info-row__value">{{ formatDateTime(order.createdAt) }}</text>
        </view>
        <view v-if="order.remark" class="info-row">
          <text class="info-row__label">备注</text>
          <text class="info-row__value">{{ order.remark }}</text>
        </view>
        <view class="info-row info-row--total">
          <text class="info-row__label">订单金额</text>
          <view class="price">
            <text class="price__symbol">¥</text>
            <text class="price__amount">{{ formatAmount(order.payAmount) }}</text>
          </view>
        </view>
      </view>

      <view v-if="order.items.length" class="card">
        <view class="card__title">菜品明细</view>
        <view v-for="item in order.items" :key="item.id" class="item">
          <AppImage :src="item.dishImage" width="96rpx" height="96rpx" radius="12rpx" />
          <view class="item__main">
            <text class="item__name">{{ item.dishName }}</text>
            <text class="item__qty">× {{ item.quantity }}</text>
          </view>
          <text class="item__price">¥{{ formatAmount(item.subtotal) }}</text>
        </view>
      </view>

      <view class="card">
        <view class="card__title">状态记录</view>
        <view v-for="(log, index) in timeline" :key="index" class="log">
          <view class="log__dot"></view>
          <view class="log__main">
            <text class="log__title">{{ statusTextOf(log.toStatus) }}</text>
            <text class="log__time">{{ formatDateTime(log.createdAt) }}{{ log.operatorRole ? ` · ${operatorText(log.operatorRole)}` : '' }}</text>
          </view>
        </view>
        <view v-if="!timeline.length" class="card__empty">暂无状态记录</view>
      </view>

      <view class="actions">
        <view v-if="order.canPay" class="btn-primary" @tap="goPay">去支付 ¥{{ formatAmount(order.payAmount) }}</view>
        <view class="btn-primary btn-primary--ghost" @tap="reload">刷新状态</view>
        <view v-if="order.canCancel" class="cancel" @tap="confirmCancel">取消订单</view>
      </view>
    </template>

    <template v-else-if="!loading">
      <EmptyState title="订单不存在" description="订单可能已被取消，请返回菜单重新下单" action-text="去点餐" @action="goMenu" />
    </template>
  </view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { onHide, onLoad, onPullDownRefresh, onShow, onUnload } from '@dcloudio/uni-app'
import AppImage from '@/components/AppImage.vue'
import EmptyState from '@/components/EmptyState.vue'
import { fetchDineInOrder, cancelDineInOrder, isActiveStatus, statusLabel } from '@/api/order'
import { friendlyMessage } from '@/api/dinein'
import { formatAmount, formatDateTime } from '@/utils/format'

/** 轮询间隔：小程序端 WebSocket 不可用时的兜底（契约 §14，与后端 pollIntervalSeconds 同量级） */
const pollSeconds = 5

const STEPS = [
  { status: 'PENDING_PAYMENT', label: '待支付', hint: '支付后商家即可接单' },
  { status: 'PAID', label: '待接单', hint: '等待商家接单' },
  { status: 'ACCEPTED', label: '已接单', hint: '商家已接单' },
  { status: 'MAKING', label: '制作中', hint: '后厨正在制作' },
  { status: 'SERVED', label: '已出餐', hint: '请到取餐口取餐' },
  { status: 'COMPLETED', label: '已完成', hint: '欢迎再次光临' }
]

const STATUS_DESC = {
  PENDING_PAYMENT: '请尽快完成支付，超时订单会自动取消',
  PAID: '已支付成功，正在等待商家接单',
  ACCEPTED: '商家已接单，即将开始制作',
  MAKING: '后厨正在制作，请稍候',
  SERVED: '已出餐，请到取餐口取餐',
  COMPLETED: '订单已完成，感谢用餐',
  CANCELLED: '订单已取消',
  REJECTED: '商家已拒单，请联系服务员'
}

const order = ref(null)
const loading = ref(true)
const refreshing = ref(false)
let orderId = ''
let pollTimer = null

const steps = STEPS
const currentStepIndex = computed(() => {
  if (!order.value) return 0
  const index = STEPS.findIndex((step) => step.status === order.value.status)
  return index === -1 ? 0 : index
})
const statusDesc = computed(() => (order.value && STATUS_DESC[order.value.status]) || '')
const bannerClass = computed(() => {
  const status = order.value && order.value.status
  if (status === 'COMPLETED') return 'banner--done'
  if (status === 'CANCELLED' || status === 'REJECTED') return 'banner--close'
  return 'banner--active'
})
const timeline = computed(() => (order.value && order.value.statusLogs ? order.value.statusLogs.slice().reverse() : []))

function stepDotClass(index) {
  if (index < currentStepIndex.value) return 'step__dot--done'
  if (index === currentStepIndex.value) return 'step__dot--active'
  return ''
}

function statusTextOf(status) {
  return statusLabel(status)
}

function operatorText(role) {
  const map = { CUSTOMER: '顾客操作', MERCHANT: '商家操作', ADMIN: '管理员操作', SYSTEM: '系统' }
  return map[role] || role
}

function stopPolling() {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

function startPolling() {
  stopPolling()
  pollTimer = setInterval(() => {
    if (!order.value || !isActiveStatus(order.value.status)) {
      stopPolling()
      return
    }
    load(true)
  }, pollSeconds * 1000)
}

async function load(silent) {
  if (!orderId) return
  if (!silent) loading.value = true
  try {
    order.value = await fetchDineInOrder(orderId)
    if (!isActiveStatus(order.value.status)) stopPolling()
  } catch (error) {
    if (!silent) {
      uni.showToast({ title: friendlyMessage(error), icon: 'none' })
    }
  } finally {
    loading.value = false
    if (silent === true) refreshing.value = false
  }
}

function reload() {
  load(false)
}

async function confirmCancel() {
  uni.showModal({
    title: '取消订单',
    content: '取消后菜品将退回库存，确定要取消这单吗？',
    confirmColor: '#FF6B35',
    success: async (res) => {
      if (!res.confirm) return
      try {
        order.value = await cancelDineInOrder(orderId, '顾客主动取消')
        stopPolling()
        uni.showToast({ title: '订单已取消', icon: 'none' })
      } catch (error) {
        uni.showModal({
          title: '取消失败',
          content: friendlyMessage(error),
          showCancel: false,
          confirmText: '我知道了',
          confirmColor: '#FF6B35'
        })
      }
    }
  })
}

function goPay() {
  uni.redirectTo({ url: `/pages/order/pay?orderId=${orderId}` })
}

function goMenu() {
  uni.switchTab({ url: '/pages/menu/menu' })
}

onLoad((options) => {
  orderId = (options && options.orderId) || ''
  if (!orderId) {
    loading.value = false
    uni.showToast({ title: '缺少订单号', icon: 'none' })
    return
  }
  load(false).then(startPolling)
})

onShow(() => {
  if (order.value && isActiveStatus(order.value.status)) startPolling()
})

onHide(() => {
  stopPolling()
})

onUnload(() => {
  stopPolling()
})

onPullDownRefresh(async () => {
  await load(true)
  uni.stopPullDownRefresh()
})
</script>

<style lang="scss" scoped>
.status-page {
  min-height: 100vh;
  padding: $space-lg $space-lg calc(#{$space-xl} + env(safe-area-inset-bottom));
}

.banner {
  display: flex;
  flex-direction: column;
  padding: 40rpx $space-lg;
  border-radius: $radius-xl;
  background: linear-gradient(140deg, #ff7a45 0%, #ff5a1f 100%);
  box-shadow: $shadow-float;

  &--done {
    background: linear-gradient(140deg, #6fd08c 0%, #38a866 100%);
  }

  &--close {
    background: linear-gradient(140deg, #b3a49b 0%, #8c7d74 100%);
  }

  &__status {
    font-size: 52rpx;
    font-weight: 800;
    color: #ffffff;
  }

  &__desc {
    margin-top: 10rpx;
    font-size: $font-sm;
    color: rgba(255, 255, 255, 0.88);
  }

  &__live {
    display: flex;
    align-items: center;
    margin-top: $space-md;
  }

  &__pulse {
    width: 14rpx;
    height: 14rpx;
    margin-right: 10rpx;
    border-radius: 50%;
    background: #ffffff;
    animation: pulse 1.4s infinite ease-in-out;
  }

  &__live-text {
    font-size: $font-xs;
    color: rgba(255, 255, 255, 0.8);
  }
}

@keyframes pulse {
  0% {
    opacity: 0.4;
    transform: scale(0.8);
  }
  50% {
    opacity: 1;
    transform: scale(1);
  }
  100% {
    opacity: 0.4;
    transform: scale(0.8);
  }
}

.card {
  margin-top: $space-lg;
  padding: $space-lg;
  border-radius: $radius-xl;
  background: $color-card;
  box-shadow: $shadow-card;

  &__title {
    margin-bottom: $space-md;
    font-size: $font-md;
    font-weight: 700;
    color: $color-text;
  }

  &__empty {
    font-size: $font-xs;
    color: $color-text-light;
  }
}

.steps {
  padding-bottom: $space-md;
}

.step {
  display: flex;

  &__rail {
    width: 32rpx;
    display: flex;
    flex-direction: column;
    align-items: center;
  }

  &__dot {
    width: 18rpx;
    height: 18rpx;
    margin-top: 8rpx;
    border-radius: 50%;
    background: #e6d9d1;

    &--active {
      background: $color-brand;
      box-shadow: 0 0 0 6rpx rgba(255, 107, 53, 0.15);
    }

    &--done {
      background: $color-brand;
    }
  }

  &__line {
    flex: 1;
    width: 2rpx;
    margin: 6rpx 0;
    background: #eee1d9;

    &--done {
      background: $color-brand;
    }
  }

  &__main {
    flex: 1;
    padding-bottom: $space-md;
  }

  &__name {
    display: block;
    font-size: $font-sm;
    color: $color-text-light;

    &--active {
      font-size: $font-md;
      font-weight: 700;
      color: $color-text;
    }

    &--done {
      color: $color-text-muted;
    }
  }

  &__hint {
    margin-top: 4rpx;
    font-size: $font-xs;
    color: $color-text-muted;
  }
}

.info-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10rpx 0;

  &__label {
    font-size: $font-sm;
    color: $color-text-muted;
  }

  &__value {
    flex: 1;
    margin-left: $space-lg;
    text-align: right;
    font-size: $font-sm;
    color: $color-text;
  }

  &--total {
    margin-top: $space-sm;
    padding-top: $space-md;
    border-top: 1rpx solid $color-border;
  }
}

.item {
  display: flex;
  align-items: center;
  padding: $space-sm 0;

  &__main {
    flex: 1;
    display: flex;
    flex-direction: column;
    margin-left: $space-md;
  }

  &__name {
    font-size: $font-sm;
    color: $color-text;
  }

  &__qty {
    margin-top: 4rpx;
    font-size: $font-xs;
    color: $color-text-muted;
  }

  &__price {
    font-size: $font-sm;
    font-weight: 600;
    color: $color-brand;
  }
}

.log {
  display: flex;
  padding: 10rpx 0;

  &__dot {
    width: 12rpx;
    height: 12rpx;
    margin-top: 14rpx;
    margin-right: $space-md;
    border-radius: 50%;
    background: $color-brand;
  }

  &__main {
    flex: 1;
    display: flex;
    flex-direction: column;
  }

  &__title {
    font-size: $font-sm;
    color: $color-text;
  }

  &__time {
    margin-top: 4rpx;
    font-size: $font-xs;
    color: $color-text-light;
  }
}

.actions {
  margin-top: $space-lg;

  .btn-primary + .btn-primary {
    margin-top: $space-md;
  }
}

.cancel {
  margin-top: $space-lg;
  text-align: center;
  font-size: $font-sm;
  color: $color-text-muted;
}
</style>
