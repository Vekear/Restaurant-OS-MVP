<template>
  <view class="confirm-page">
    <view class="table-bar">
      <view class="table-bar__left">
        <text class="table-bar__label">堂食桌台</text>
        <text class="table-bar__value">{{ tableLabel || '未绑定' }}</text>
      </view>
      <text class="table-bar__meta">{{ merchantName }}</text>
    </view>

    <view class="card">
      <view class="card__title">已选菜品</view>
      <view v-for="item in items" :key="item.id" class="order-item">
        <AppImage :src="item.image" width="108rpx" height="108rpx" radius="14rpx" />
        <view class="order-item__main">
          <text class="order-item__name">{{ item.name }}</text>
          <text class="order-item__qty">× {{ item.quantity }}</text>
        </view>
        <view class="order-item__price">
          <text class="price__symbol">¥</text>
          <text class="price__amount">{{ formatAmount(item.price * item.quantity) }}</text>
        </view>
      </view>

      <view class="remark">
        <text class="remark__label">备注</text>
        <textarea
          v-model="remark"
          class="remark__input"
          placeholder="口味偏好、忌口等（选填，100 字以内）"
          placeholder-class="remark__placeholder"
          maxlength="100"
          auto-height
        />
      </view>
    </view>

    <view class="card">
      <view class="amount-row">
        <text class="amount-row__label">商品金额</text>
        <text class="amount-row__value">¥{{ formatAmount(totalAmount) }}</text>
      </view>
      <view class="amount-row">
        <text class="amount-row__label">配送费</text>
        <text class="amount-row__value">堂食无配送费</text>
      </view>
      <view class="amount-row amount-row--total">
        <text class="amount-row__label">应付金额</text>
        <view class="price">
          <text class="price__symbol">¥</text>
          <text class="price__amount price__amount--lg">{{ formatAmount(totalAmount) }}</text>
        </view>
      </view>
      <view class="mock-hint">
        <view class="mock-hint__dot"></view>
        <text class="mock-hint__text">演示环境：页面按真实金额展示，模拟支付实际扣款 ¥0.01</text>
      </view>
    </view>

    <view class="submit-bar">
      <view class="submit-bar__info">
        <text class="submit-bar__label">合计</text>
        <view class="submit-bar__amount">
          <text class="submit-bar__symbol">¥</text>
          <text class="submit-bar__number">{{ formatAmount(totalAmount) }}</text>
        </view>
      </view>
      <view class="submit-bar__btn" :class="{ 'submit-bar__btn--disabled': submitting }" @tap="submit">
        {{ submitting ? '提交中…' : '提交订单' }}
      </view>
    </view>
  </view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import AppImage from '@/components/AppImage.vue'
import { useCartStore } from '@/store/cart'
import { useTableStore } from '@/store/table'
import { useUserStore } from '@/store/user'
import { createDineInOrder } from '@/api/order'
import { friendlyMessage } from '@/api/dinein'
import { createIdempotencyKey } from '@/utils/id'
import { formatAmount } from '@/utils/format'

const cartStore = useCartStore()
const { items, totalAmount, clearCart } = cartStore
const tableStore = useTableStore()
const { hasSession, tableLabel, merchantName } = tableStore
const { isLoggedIn } = useUserStore()

const remark = ref('')
const submitting = ref(false)
// 同一次提交复用同一个幂等键，避免用户连点造成重复订单
const idempotencyKey = ref(createIdempotencyKey('dinein-order'))

const canSubmit = computed(() => items.value.length > 0 && !submitting.value)

function backToMenu() {
  uni.switchTab({ url: '/pages/menu/menu' })
}

function goScan() {
  uni.navigateTo({ url: '/pages/scan/scan' })
}

function guard() {
  if (!isLoggedIn.value) {
    uni.showModal({
      title: '需要登录',
      content: '下单前请先完成微信登录',
      confirmText: '去登录',
      confirmColor: '#FF6B35',
      success: (res) => {
        if (res.confirm) uni.navigateTo({ url: '/pages/login/login' })
        else backToMenu()
      }
    })
    return false
  }
  if (!hasSession.value) {
    uni.showModal({
      title: '请先扫码入座',
      content: '堂食下单需要先绑定桌台，扫描桌角二维码即可',
      confirmText: '去扫码',
      confirmColor: '#FF6B35',
      success: (res) => {
        if (res.confirm) goScan()
        else backToMenu()
      }
    })
    return false
  }
  if (!items.value.length) {
    uni.showToast({ title: '购物车为空，请先选菜', icon: 'none' })
    backToMenu()
    return false
  }
  return true
}

async function submit() {
  if (!canSubmit.value) return
  if (!guard()) return
  submitting.value = true
  try {
    const order = await createDineInOrder(
      {
        items: items.value.map((item) => ({ dishId: item.id, quantity: item.quantity })),
        remark: remark.value.trim(),
        expectedPayAmount: Number(totalAmount.value.toFixed(2))
      },
      idempotencyKey.value
    )
    clearCart()
    uni.redirectTo({ url: `/pages/order/pay?orderId=${order.id}` })
  } catch (error) {
    uni.showModal({
      title: '下单失败',
      content: friendlyMessage(error),
      showCancel: false,
      confirmText: '我知道了',
      confirmColor: '#FF6B35'
    })
    if (error && (error.code === 40911 || error.code === 40401)) {
      setTimeout(goScan, 400)
    }
  } finally {
    submitting.value = false
  }
}

onLoad(() => {
  guard()
})
</script>

<style lang="scss" scoped>
.confirm-page {
  min-height: 100vh;
  padding: $space-lg $space-lg calc(220rpx + env(safe-area-inset-bottom));
}

.table-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: $space-md $space-lg;
  border-radius: $radius-lg;
  background: linear-gradient(135deg, #fff1e9 0%, #ffe6da 100%);

  &__left {
    display: flex;
    align-items: baseline;
  }

  &__label {
    font-size: $font-xs;
    color: $color-text-muted;
  }

  &__value {
    margin-left: 12rpx;
    font-size: $font-lg;
    font-weight: 800;
    color: $color-brand-dark;
  }

  &__meta {
    font-size: $font-xs;
    color: $color-text-muted;
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
}

.order-item {
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
    font-weight: 600;
    color: $color-text;
  }

  &__qty {
    margin-top: 4rpx;
    font-size: $font-xs;
    color: $color-text-muted;
  }

  &__price {
    display: flex;
    align-items: baseline;
    color: $color-brand;
    font-weight: 700;
  }
}

.remark {
  margin-top: $space-md;
  padding-top: $space-md;
  border-top: 1rpx solid $color-border;

  &__label {
    font-size: $font-sm;
    font-weight: 600;
    color: $color-text;
  }

  &__input {
    width: 100%;
    min-height: 96rpx;
    margin-top: 12rpx;
    padding: $space-sm $space-md;
    border-radius: $radius-md;
    background: $color-bg;
    font-size: $font-sm;
    line-height: 1.6;
    color: $color-text;
  }

  &__placeholder {
    color: $color-text-light;
  }
}

.amount-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10rpx 0;

  &__label {
    font-size: $font-sm;
    color: $color-text-muted;
  }

  &__value {
    font-size: $font-sm;
    color: $color-text;
  }

  &--total {
    margin-top: $space-sm;
    padding-top: $space-md;
    border-top: 1rpx solid $color-border;

    .amount-row__label {
      font-size: $font-md;
      font-weight: 700;
      color: $color-text;
    }
  }
}

.price__amount--lg {
  font-size: 40rpx;
}

.mock-hint {
  display: flex;
  align-items: flex-start;
  margin-top: $space-md;
  padding: $space-sm $space-md;
  border-radius: $radius-md;
  background: #fff7e6;

  &__dot {
    width: 12rpx;
    height: 12rpx;
    margin-top: 10rpx;
    margin-right: 10rpx;
    border-radius: 50%;
    background: $color-accent;
  }

  &__text {
    flex: 1;
    font-size: $font-xs;
    line-height: 1.6;
    color: #a8730a;
  }
}

.submit-bar {
  position: fixed;
  left: $space-lg;
  right: $space-lg;
  bottom: calc(#{$space-md} + env(safe-area-inset-bottom));
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: $space-md $space-md $space-md $space-lg;
  border-radius: $radius-xl;
  background: #2b2320;
  box-shadow: $shadow-float;

  &__info {
    display: flex;
    flex-direction: column;
  }

  &__label {
    font-size: $font-xs;
    color: rgba(255, 255, 255, 0.6);
  }

  &__amount {
    display: flex;
    align-items: baseline;
    color: #ffffff;
  }

  &__symbol {
    font-size: $font-xs;
  }

  &__number {
    font-size: 40rpx;
    font-weight: 800;
    margin-left: 4rpx;
  }

  &__btn {
    padding: 0 44rpx;
    height: 84rpx;
    line-height: 84rpx;
    border-radius: $radius-pill;
    background: $color-brand;
    color: #ffffff;
    font-size: $font-md;
    font-weight: 700;

    &--disabled {
      opacity: 0.6;
    }
  }
}
</style>
