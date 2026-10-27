<script setup>
/**
 * 品牌标识：SVG 图标 + 中英文名称
 * size: sm | md | lg；theme: light（浅底）| dark（深底）；compact 只显示图标
 */
import { computed, useId } from 'vue'

const props = defineProps({
  size: { type: String, default: 'md' },
  theme: { type: String, default: 'light' },
  compact: { type: Boolean, default: false },
  subtitle: { type: String, default: 'SHIWEI ORDER' }
})

const gradientId = `brand-gradient-${useId().replace(/[^a-zA-Z0-9_-]/g, '')}`
const sizeClass = computed(() => `brand-mark--${props.size}`)
</script>

<template>
  <span class="brand-mark" :class="[sizeClass, `brand-mark--${theme}`, { 'is-compact': compact }]">
    <svg class="brand-mark__logo" viewBox="0 0 44 44" role="img" aria-label="拾味点餐">
      <defs>
        <linearGradient :id="gradientId" x1="0" y1="0" x2="1" y2="1">
          <stop offset="0%" stop-color="#fb7748" />
          <stop offset="55%" stop-color="#e4572e" />
          <stop offset="100%" stop-color="#c8441f" />
        </linearGradient>
      </defs>
      <rect x="0" y="0" width="44" height="44" rx="13" :fill="`url(#${gradientId})`" />
      <path
        d="M10.5 22.5h23a11.5 11.5 0 0 1-23 0Z"
        fill="#fffdfb"
        opacity="0.96"
      />
      <path d="M9 25.2h26" stroke="#fffdfb" stroke-width="2.4" stroke-linecap="round" opacity="0.9" />
      <path
        d="M17.6 9.5c0 1.7 1.3 2.2 1.3 3.6M22 8.6c0 1.9 1.4 2.4 1.4 3.9M26.4 9.5c0 1.7 1.3 2.2 1.3 3.6"
        stroke="#fffdfb"
        stroke-width="1.9"
        stroke-linecap="round"
        fill="none"
        opacity="0.72"
      />
    </svg>
    <span v-if="!compact" class="brand-mark__text">
      <strong class="brand-mark__name">拾味点餐</strong>
      <span v-if="subtitle" class="brand-mark__subtitle">{{ subtitle }}</span>
    </span>
  </span>
</template>

<style scoped>
.brand-mark {
  display: inline-flex;
  align-items: center;
  gap: var(--space-4);
  user-select: none;
}

.brand-mark__logo {
  flex: none;
  filter: drop-shadow(0 4px 10px rgba(228, 87, 46, 0.24));
}

.brand-mark__text {
  display: flex;
  flex-direction: column;
  line-height: 1.15;
}

.brand-mark__name {
  font-size: var(--font-size-lg);
  font-weight: var(--font-weight-bold);
  letter-spacing: 0.02em;
  color: var(--color-text-primary);
}

.brand-mark__subtitle {
  margin-top: 2px;
  font-size: 10px;
  letter-spacing: 0.22em;
  color: var(--color-text-tertiary);
}

.brand-mark--sm .brand-mark__logo {
  width: 28px;
  height: 28px;
}

.brand-mark--sm .brand-mark__name {
  font-size: var(--font-size-md);
}

.brand-mark--md .brand-mark__logo {
  width: 36px;
  height: 36px;
}

.brand-mark--lg .brand-mark__logo {
  width: 48px;
  height: 48px;
}

.brand-mark--lg .brand-mark__name {
  font-size: var(--font-size-2xl);
}

.brand-mark--lg .brand-mark__subtitle {
  font-size: 11px;
}

.brand-mark--dark .brand-mark__name {
  color: var(--color-text-inverse);
}

.brand-mark--dark .brand-mark__subtitle {
  color: rgba(207, 197, 187, 0.62);
}

.is-compact .brand-mark__text {
  display: none;
}
</style>
