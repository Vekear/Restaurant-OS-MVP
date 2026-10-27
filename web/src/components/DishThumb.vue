<script setup>
/**
 * 菜品图片（向后兼容包装）
 * 统一实现已迁移到 ImageWithFallback（懒加载 + 骨架 + 破图兜底），
 * 保留原有 props 形状，避免顾客端/商家端页面批量改动。
 */
import ImageWithFallback from '@/components/ImageWithFallback.vue'

defineProps({
  src: { type: String, default: '' },
  alt: { type: String, default: '菜品图片' },
  radius: { type: String, default: 'md' }
})
</script>

<template>
  <ImageWithFallback :src="src" :alt="alt" :radius="radius" thumb />
</template>
