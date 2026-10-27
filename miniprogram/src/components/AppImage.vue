<template>
  <image
    class="app-image"
    :src="currentSrc"
    :mode="mode"
    :lazy-load="lazyLoad"
    :style="styleObj"
    @error="onError"
  />
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { FALLBACK_IMAGE, resolveImageUrl } from '@/utils/image'

const props = defineProps({
  src: { type: String, default: '' },
  mode: { type: String, default: 'aspectFill' },
  width: { type: String, default: '160rpx' },
  height: { type: String, default: '160rpx' },
  radius: { type: String, default: '12rpx' },
  lazyLoad: { type: Boolean, default: true }
})

const failed = ref(false)
const currentSrc = ref(resolveImageUrl(props.src))

watch(
  () => props.src,
  (value) => {
    failed.value = false
    currentSrc.value = resolveImageUrl(value)
  }
)

const styleObj = computed(() => ({
  width: props.width,
  height: props.height,
  borderRadius: props.radius
}))

function onError() {
  if (failed.value) return
  failed.value = true
  currentSrc.value = FALLBACK_IMAGE
}
</script>

<style lang="scss" scoped>
.app-image {
  display: block;
  background-color: $color-placeholder-bg;
  flex-shrink: 0;
  overflow: hidden;
}
</style>
