<script setup>
/**
 * 统一金额展示（docs/01-architecture.md §6 / :287）
 * 所有金额统一走 Intl 千分位 + 两位小数，货币符号与数字分离以便等宽对齐。
 */
import { computed } from 'vue'
import { formatAmount } from '@/utils/format'

const props = defineProps({
  value: { type: [Number, String], default: 0 },
  symbol: { type: String, default: '¥' },
  /** sm | md | lg | xl */
  size: { type: String, default: 'md' },
  strong: { type: Boolean, default: false },
  muted: { type: Boolean, default: false },
  /** 是否显示货币符号 */
  showSymbol: { type: Boolean, default: true }
})

const text = computed(() => formatAmount(props.value))
const ariaLabel = computed(() => `${props.showSymbol ? props.symbol : ''}${text.value}`)
</script>

<template>
  <span
    class="money-text"
    :class="[`money-text--${size}`, { 'is-strong': strong, 'is-muted': muted }]"
    :aria-label="ariaLabel"
  >
    <span v-if="showSymbol" class="money-text__symbol">{{ symbol }}</span>
    <span class="money-text__value">{{ text }}</span>
  </span>
</template>

<style scoped>
.money-text {
  display: inline-flex;
  align-items: baseline;
  gap: 1px;
  font-family: var(--font-family-number);
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}

.money-text--sm {
  font-size: var(--font-size-xs);
}

.money-text--md {
  font-size: var(--font-size-base);
}

.money-text--lg {
  font-size: var(--font-size-xl);
}

.money-text--xl {
  font-size: var(--font-size-3xl);
}

.money-text.is-strong {
  font-weight: var(--font-weight-semibold);
}

.money-text.is-muted {
  color: var(--color-text-secondary);
}

.money-text__symbol {
  font-size: 0.78em;
  color: var(--color-text-secondary);
}
</style>
