<template>
  <view class="empty-state">
    <view class="empty-state__art">
      <view class="empty-state__plate"></view>
      <view class="empty-state__dot empty-state__dot--a"></view>
      <view class="empty-state__dot empty-state__dot--b"></view>
    </view>
    <text class="empty-state__title">{{ title }}</text>
    <text v-if="description" class="empty-state__desc">{{ description }}</text>
    <view v-if="actionText" class="empty-state__action" @tap="onAction">{{ actionText }}</view>
  </view>
</template>

<script setup>
defineProps({
  title: { type: String, default: '暂无数据' },
  description: { type: String, default: '' },
  actionText: { type: String, default: '' }
})

const emit = defineEmits(['action'])

function onAction() {
  emit('action')
}
</script>

<style lang="scss" scoped>
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 96rpx 48rpx 64rpx;

  &__art {
    position: relative;
    width: 200rpx;
    height: 200rpx;
    margin-bottom: 28rpx;
  }

  &__plate {
    position: absolute;
    top: 20rpx;
    left: 20rpx;
    width: 160rpx;
    height: 160rpx;
    border-radius: 50%;
    background: linear-gradient(160deg, rgba(255, 107, 53, 0.14), rgba(255, 183, 3, 0.16));
    border: 2rpx solid rgba(255, 107, 53, 0.18);
  }

  &__dot {
    position: absolute;
    border-radius: 50%;
    background: rgba(255, 107, 53, 0.35);

    &--a {
      width: 18rpx;
      height: 18rpx;
      top: 56rpx;
      right: 18rpx;
    }

    &--b {
      width: 12rpx;
      height: 12rpx;
      bottom: 40rpx;
      left: 10rpx;
      background: rgba(255, 183, 3, 0.5);
    }
  }

  &__title {
    font-size: 30rpx;
    font-weight: 600;
    color: $color-text;
  }

  &__desc {
    margin-top: 12rpx;
    font-size: 24rpx;
    line-height: 1.6;
    color: $color-text-muted;
    text-align: center;
  }

  &__action {
    margin-top: 32rpx;
    padding: 16rpx 48rpx;
    border-radius: 999rpx;
    background: $color-brand;
    color: #ffffff;
    font-size: 26rpx;
    font-weight: 600;
    box-shadow: 0 12rpx 24rpx rgba(255, 107, 53, 0.28);
  }
}
</style>
