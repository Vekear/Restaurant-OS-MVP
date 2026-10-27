<template>
  <view class="cart-page">
    <EmptyState
      v-if="items.length === 0"
      title="购物车还是空的"
      description="去菜单挑几道喜欢的菜吧，堂食下单更方便"
      action-text="去点餐"
      @action="goMenu"
    />

    <template v-else>
      <view class="summary">
        <view class="summary__left">
          <text class="summary__title">小馆食堂 · 堂食</text>
          <text class="summary__desc">共 {{ totalQuantity }} 件商品</text>
        </view>
        <view class="summary__clear" @tap="confirmClear">清空</view>
      </view>

      <view class="list">
        <view v-for="item in items" :key="item.id" class="cart-item">
          <AppImage :src="item.image" width="136rpx" height="136rpx" radius="16rpx" />
          <view class="cart-item__main">
            <view class="cart-item__row">
              <text class="cart-item__name">{{ item.name }}</text>
              <text class="cart-item__remove" @tap="removeItem(item.id)">删除</text>
            </view>
            <view class="cart-item__row cart-item__row--bottom">
              <view class="price">
                <text class="price__symbol">¥</text>
                <text class="price__amount">{{ formatAmount(item.price) }}</text>
              </view>
              <QuantityStepper
                :quantity="item.quantity"
                @increase="increase(item.id)"
                @decrease="decrease(item.id)"
              />
            </view>
          </view>
        </view>
      </view>

      <view class="tip">
        <view class="tip__dot"></view>
        <text class="tip__text">堂食订单由后厨现做，下单后可在「我的订单」查看制作进度</text>
      </view>

      <view class="checkout-bar">
        <view class="checkout-bar__info">
          <text class="checkout-bar__label">合计</text>
          <view class="checkout-bar__amount">
            <text class="checkout-bar__symbol">¥</text>
            <text class="checkout-bar__number">{{ formatAmount(totalAmount) }}</text>
          </view>
        </view>
        <view class="checkout-bar__btn" @tap="checkout">去下单</view>
      </view>
    </template>
  </view>
</template>

<script setup>
import EmptyState from '@/components/EmptyState.vue'
import AppImage from '@/components/AppImage.vue'
import QuantityStepper from '@/components/QuantityStepper.vue'
import { useCartStore } from '@/store/cart'
import { useUserStore } from '@/store/user'
import { formatAmount } from '@/utils/format'

const { items, totalQuantity, totalAmount, increase, decrease, removeItem, clearCart } = useCartStore()
const { isLoggedIn } = useUserStore()

function goMenu() {
  uni.switchTab({ url: '/pages/menu/menu' })
}

function confirmClear() {
  uni.showModal({
    title: '清空购物车',
    content: '确定要清空已选的全部菜品吗？',
    confirmColor: '#FF6B35',
    success: (res) => {
      if (res.confirm) {
        clearCart()
        uni.showToast({ title: '已清空', icon: 'none' })
      }
    }
  })
}

function checkout() {
  if (!isLoggedIn.value) {
    uni.showModal({
      title: '需要登录',
      content: '堂食下单前请先完成微信登录',
      confirmText: '去登录',
      confirmColor: '#FF6B35',
      success: (res) => {
        if (res.confirm) {
          uni.navigateTo({ url: '/pages/login/login' })
        }
      }
    })
    return
  }
  uni.showModal({
    title: '堂食下单',
    content: `共 ${totalQuantity.value} 件，合计 ¥${formatAmount(totalAmount.value)}。\n下单与支付将在下一阶段（T17）接入，当前为菜单/购物车骨架。`,
    showCancel: false,
    confirmText: '我知道了',
    confirmColor: '#FF6B35'
  })
}
</script>

<style lang="scss" scoped>
.cart-page {
  min-height: 100vh;
  padding-bottom: 340rpx;
}

.summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: $space-lg $space-lg $space-md;

  &__title {
    display: block;
    font-size: $font-lg;
    font-weight: 700;
    color: $color-text;
  }

  &__desc {
    display: block;
    margin-top: 6rpx;
    font-size: $font-xs;
    color: $color-text-muted;
  }

  &__clear {
    padding: 10rpx $space-md;
    border-radius: $radius-pill;
    background: #ffffff;
    border: 2rpx solid $color-border;
    font-size: $font-xs;
    color: $color-text-muted;
  }
}

.list {
  padding: 0 $space-lg;
}

.cart-item {
  display: flex;
  padding: $space-md;
  margin-bottom: $space-md;
  background: $color-card;
  border-radius: $radius-lg;
  box-shadow: $shadow-card;

  &__main {
    flex: 1;
    min-width: 0;
    margin-left: $space-md;
    display: flex;
    flex-direction: column;
    justify-content: space-between;
  }

  &__row {
    display: flex;
    align-items: center;
    justify-content: space-between;

    &--bottom {
      margin-top: $space-sm;
    }
  }

  &__name {
    font-size: $font-md;
    font-weight: 600;
    color: $color-text;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    margin-right: $space-sm;
  }

  &__remove {
    flex-shrink: 0;
    font-size: $font-xs;
    color: #b6aaa3;
  }
}

.tip {
  display: flex;
  align-items: flex-start;
  margin: $space-xs $space-lg 0;
  padding: $space-sm $space-md;
  border-radius: $radius-md;
  background: $color-accent-soft;

  &__dot {
    width: 12rpx;
    height: 12rpx;
    border-radius: 50%;
    background: $color-accent;
    margin: 10rpx 12rpx 0 0;
    flex-shrink: 0;
  }

  &__text {
    font-size: $font-xs;
    line-height: 1.6;
    color: #8a6a20;
  }
}

.checkout-bar {
  position: fixed;
  left: $space-lg;
  right: $space-lg;
  bottom: calc(100rpx + #{$space-md} + env(safe-area-inset-bottom));
  z-index: 20;
  display: flex;
  align-items: center;
  height: 108rpx;
  padding: 0 12rpx 0 $space-lg;
  border-radius: $radius-pill;
  background: #241d19;
  box-shadow: $shadow-float;

  &__info {
    flex: 1;
  }

  &__label {
    font-size: $font-xs;
    color: rgba(255, 255, 255, 0.55);
    margin-right: $space-xs;
  }

  &__amount {
    display: inline-flex;
    align-items: baseline;
    color: #ffffff;
  }

  &__symbol {
    font-size: $font-xs;
    margin-right: 2rpx;
  }

  &__number {
    font-size: 40rpx;
    font-weight: 700;
    letter-spacing: -0.5rpx;
  }

  &__btn {
    height: 84rpx;
    padding: 0 48rpx;
    border-radius: $radius-pill;
    background: linear-gradient(135deg, $color-brand, $color-brand-dark);
    color: #ffffff;
    font-size: $font-lg;
    font-weight: 700;
    display: flex;
    align-items: center;
    justify-content: center;
  }
}
</style>
