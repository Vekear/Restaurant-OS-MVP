<template>
  <view class="list-page">
    <view class="tabs">
      <view
        v-for="tab in tabs"
        :key="tab.value"
        class="tabs__item"
        :class="{ 'tabs__item--active': statusGroup === tab.value }"
        @tap="switchTab(tab.value)"
      >
        {{ tab.label }}
      </view>
    </view>

    <view v-if="loading" class="skeleton">
      <view v-for="index in 3" :key="index" class="skeleton__row"></view>
    </view>

    <EmptyState
      v-else-if="orders.length === 0"
      :title="statusGroup === 'ACTIVE' ? '暂无进行中的订单' : '还没有堂食订单'"
      description="扫码入座后点几道喜欢的菜吧"
      action-text="去点餐"
      @action="goMenu"
    />

    <view v-else class="list">
      <view v-for="order in orders" :key="order.id" class="order-card" @tap="openOrder(order.id)">
        <view class="order-card__head">
          <view class="order-card__table">
            <text class="order-card__table-no">{{ order.tableNo || '堂食' }}</text>
            <text class="order-card__table-label">桌</text>
          </view>
          <text class="order-card__status" :class="statusClass(order.status)">{{ order.statusText }}</text>
        </view>
        <view class="order-card__meta">
          <text class="order-card__no">{{ order.orderNo }}</text>
          <text class="order-card__time">{{ formatDateTime(order.createdAt) }}</text>
        </view>
        <view class="order-card__items">{{ itemSummary(order) }}</view>
        <view class="order-card__foot">
          <text class="order-card__count">共 {{ order.itemCount }} 件</text>
          <view class="price">
            <text class="price__symbol">¥</text>
            <text class="price__amount">{{ formatAmount(order.payAmount) }}</text>
          </view>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onPullDownRefresh, onShow } from '@dcloudio/uni-app'
import EmptyState from '@/components/EmptyState.vue'
import { fetchDineInOrders } from '@/api/order'
import { friendlyMessage } from '@/api/dinein'
import { formatAmount, formatDateTime } from '@/utils/format'

const tabs = [
  { label: '进行中', value: 'ACTIVE' },
  { label: '已完成', value: 'FINISHED' },
  { label: '全部', value: 'ALL' }
]

const statusGroup = ref('ACTIVE')
const orders = ref([])
const loading = ref(true)

function statusClass(status) {
  if (status === 'COMPLETED') return 'order-card__status--done'
  if (status === 'CANCELLED' || status === 'REJECTED') return 'order-card__status--close'
  return 'order-card__status--active'
}

function itemSummary(order) {
  const names = (order.items || []).map((item) => item.dishName).filter(Boolean)
  if (!names.length) return '堂食订单'
  const text = names.slice(0, 3).join('、')
  return names.length > 3 ? `${text} 等` : text
}

async function load() {
  loading.value = true
  try {
    const pageData = await fetchDineInOrders({ statusGroup: statusGroup.value, page: 1, size: 20 })
    orders.value = pageData.list || []
  } catch (error) {
    uni.showToast({ title: friendlyMessage(error), icon: 'none' })
    orders.value = []
  } finally {
    loading.value = false
  }
}

function switchTab(value) {
  if (statusGroup.value === value) return
  statusGroup.value = value
  load()
}

function openOrder(id) {
  uni.navigateTo({ url: `/pages/order/status?orderId=${id}` })
}

function goMenu() {
  uni.switchTab({ url: '/pages/menu/menu' })
}

onShow(() => {
  load()
})

onPullDownRefresh(async () => {
  await load()
  uni.stopPullDownRefresh()
})
</script>

<style lang="scss" scoped>
.list-page {
  min-height: 100vh;
  padding: $space-lg $space-lg calc(#{$space-xl} + env(safe-area-inset-bottom));
}

.tabs {
  display: flex;
  padding: 8rpx;
  border-radius: $radius-pill;
  background: #ffffff;
  box-shadow: $shadow-card;

  &__item {
    flex: 1;
    height: 68rpx;
    line-height: 68rpx;
    text-align: center;
    border-radius: $radius-pill;
    font-size: $font-sm;
    color: $color-text-muted;

    &--active {
      background: $color-brand;
      color: #ffffff;
      font-weight: 700;
    }
  }
}

.list {
  margin-top: $space-lg;
}

.order-card {
  margin-bottom: $space-md;
  padding: $space-lg;
  border-radius: $radius-xl;
  background: $color-card;
  box-shadow: $shadow-card;

  &__head {
    display: flex;
    align-items: center;
    justify-content: space-between;
  }

  &__table {
    display: flex;
    align-items: baseline;
  }

  &__table-no {
    font-size: 40rpx;
    font-weight: 800;
    color: $color-brand-dark;
  }

  &__table-label {
    margin-left: 6rpx;
    font-size: $font-xs;
    color: $color-text-muted;
  }

  &__status {
    padding: 6rpx 18rpx;
    border-radius: $radius-pill;
    font-size: $font-xs;
    background: #fff1e9;
    color: $color-brand-dark;

    &--done {
      background: #e8f8ee;
      color: #2c9b5d;
    }

    &--close {
      background: #f1ece9;
      color: $color-text-muted;
    }
  }

  &__meta {
    display: flex;
    justify-content: space-between;
    margin-top: $space-sm;
  }

  &__no,
  &__time {
    font-size: $font-xs;
    color: $color-text-light;
  }

  &__items {
    margin-top: 12rpx;
    font-size: $font-sm;
    color: $color-text-muted;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  &__foot {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-top: $space-md;
    padding-top: $space-md;
    border-top: 1rpx solid $color-border;
  }

  &__count {
    font-size: $font-xs;
    color: $color-text-muted;
  }
}

.skeleton {
  margin-top: $space-lg;

  &__row {
    height: 180rpx;
    margin-bottom: $space-md;
    border-radius: $radius-xl;
    background: #f3e7df;
  }
}
</style>
