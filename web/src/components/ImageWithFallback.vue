<script setup>
/**
 * 统一图片组件（docs/01-architecture.md §6：ImageWithFallback.vue）
 *
 * - 懒加载：默认 loading="lazy"，列表滚动时才请求
 * - 骨架：加载中显示 shimmer 占位，避免布局跳动
 * - 兜底：无地址或加载失败时展示品牌色渐变 + 图标，绝不出现破图
 * - 缩略：fit 默认 cover；thumb 模式使用更小的兜底图标与圆角
 * - 兼容站内相对路径（/files/...），由 Vite 代理或 Nginx 提供
 */
import { computed } from 'vue'
import { resolveImageUrl } from '@/utils/media'

const props = defineProps({
  src: { type: String, default: '' },
  alt: { type: String, default: '图片' },
  fit: { type: String, default: 'cover' },
  /** sm | md | lg | xl 或任意 CSS 长度 */
  radius: { type: String, default: 'md' },
  /** 缩略图模式：更小的兜底图标 */
  thumb: { type: Boolean, default: false },
  lazy: { type: Boolean, default: true },
  loadingText: { type: String, default: '' }
})

const emit = defineEmits(['load', 'error'])

const imageSrc = computed(() => resolveImageUrl(props.src))
const radiusClass = computed(() =>
  ['sm', 'md', 'lg', 'xl'].includes(props.radius) ? `image-fallback--${props.radius}` : ''
)
const radiusStyle = computed(() =>
  ['sm', 'md', 'lg', 'xl'].includes(props.radius) ? {} : { borderRadius: props.radius }
)

function onLoad(event) {
  emit('load', event)
}

function onError(event) {
  emit('error', event)
}
</script>

<template>
  <el-image
    class="image-fallback"
    :class="[radiusClass, { 'image-fallback--thumb': thumb }]"
    :style="radiusStyle"
    :src="imageSrc"
    :alt="alt"
    :fit="fit"
    :loading="lazy ? 'lazy' : 'eager'"
    @load="onLoad"
    @error="onError"
  >
    <template #placeholder>
      <div class="image-fallback__skeleton" :aria-label="loadingText || '图片加载中'">
        <span v-if="loadingText" class="image-fallback__skeleton-text">{{ loadingText }}</span>
      </div>
    </template>
    <template #error>
      <div class="image-fallback__error">
        <el-icon><KnifeFork /></el-icon>
        <span v-if="!thumb" class="image-fallback__error-text">暂无图片</span>
      </div>
    </template>
  </el-image>
</template>

<style scoped>
.image-fallback {
  display: block;
  width: 100%;
  height: 100%;
  min-height: 48px;
  background: var(--color-bg-subtle);
}

.image-fallback--sm {
  border-radius: var(--radius-sm);
}

.image-fallback--md {
  border-radius: var(--radius-md);
}

.image-fallback--lg {
  border-radius: var(--radius-lg);
}

.image-fallback--xl {
  border-radius: var(--radius-xl);
}

.image-fallback__skeleton {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 100%;
  min-height: 72px;
  background: linear-gradient(
    100deg,
    var(--color-bg-subtle) 25%,
    var(--color-border-subtle) 37%,
    var(--color-bg-subtle) 63%
  );
  background-size: 400% 100%;
  animation: image-fallback-shimmer 1.4s ease infinite;
}

.image-fallback__skeleton-text {
  color: var(--color-text-tertiary);
  font-size: var(--font-size-xs);
}

.image-fallback__error {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--space-1);
  width: 100%;
  height: 100%;
  min-height: 72px;
  color: var(--color-brand-400);
  background: linear-gradient(135deg, var(--color-brand-50), var(--color-accent-100));
  font-size: 26px;
}

.image-fallback--thumb .image-fallback__error {
  min-height: 40px;
  font-size: 16px;
}

.image-fallback__error-text {
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}

@keyframes image-fallback-shimmer {
  0% {
    background-position: 100% 50%;
  }

  100% {
    background-position: 0 50%;
  }
}

@media (prefers-reduced-motion: reduce) {
  .image-fallback__skeleton {
    animation: none;
  }
}
</style>
