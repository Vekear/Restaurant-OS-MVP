<template>
  <view class="stepper" :class="{ 'stepper--disabled': disabled }">
    <view
      v-if="quantity > 0"
      class="stepper__btn stepper__btn--minus"
      :class="{ 'stepper__btn--disabled': disabled }"
      @tap.stop="onDecrease"
    >
      <view class="stepper__minus-line"></view>
    </view>
    <text v-if="quantity > 0" class="stepper__value">{{ quantity }}</text>
    <view
      class="stepper__btn stepper__btn--plus"
      :class="{ 'stepper__btn--disabled': disabled || soldOut }"
      @tap.stop="onIncrease"
    >
      <view class="stepper__plus-line stepper__plus-line--h"></view>
      <view class="stepper__plus-line stepper__plus-line--v"></view>
    </view>
  </view>
</template>

<script setup>
const props = defineProps({
  quantity: { type: Number, default: 0 },
  disabled: { type: Boolean, default: false },
  soldOut: { type: Boolean, default: false }
})

const emit = defineEmits(['increase', 'decrease'])

function onIncrease() {
  if (props.disabled || props.soldOut) return
  emit('increase')
}

function onDecrease() {
  if (props.disabled) return
  emit('decrease')
}
</script>

<style lang="scss" scoped>
.stepper {
  display: flex;
  align-items: center;

  &__btn {
    position: relative;
    width: 52rpx;
    height: 52rpx;
    border-radius: 50%;
    display: flex;
    align-items: center;
    justify-content: center;

    &--minus {
      border: 2rpx solid $color-brand;
      background: #ffffff;
    }

    &--plus {
      background: linear-gradient(135deg, $color-brand, $color-brand-dark);
      box-shadow: 0 8rpx 16rpx rgba(255, 107, 53, 0.32);
    }

    &--disabled {
      opacity: 0.45;
    }
  }

  &__minus-line {
    width: 22rpx;
    height: 4rpx;
    border-radius: 2rpx;
    background: $color-brand;
  }

  &__plus-line {
    position: absolute;
    background: #ffffff;
    border-radius: 2rpx;

    &--h {
      width: 22rpx;
      height: 4rpx;
    }

    &--v {
      width: 4rpx;
      height: 22rpx;
    }
  }

  &__value {
    min-width: 48rpx;
    text-align: center;
    font-size: 28rpx;
    font-weight: 600;
    color: $color-text;
  }
}
</style>
