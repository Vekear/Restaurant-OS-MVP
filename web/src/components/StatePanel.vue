<script setup>
/**
 * 状态页通用外壳（403 / 404）
 */
import BrandMark from '@/components/BrandMark.vue'

defineProps({
  code: { type: String, default: '404' },
  title: { type: String, default: '页面不存在' },
  description: { type: String, default: '' }
})
</script>

<template>
  <div class="state-page">
    <div class="state-card">
      <BrandMark size="sm" />
      <p class="state-card__code">{{ code }}</p>
      <h1 class="state-card__title">{{ title }}</h1>
      <p class="state-card__desc">{{ description }}</p>
      <div class="state-card__actions">
        <slot />
      </div>
    </div>
  </div>
</template>

<style scoped>
.state-page {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  padding: var(--space-8) var(--space-5);
  background:
    radial-gradient(700px 360px at 82% -8%, rgba(228, 87, 46, 0.1), transparent 62%),
    var(--color-bg-page);
}

.state-card {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: var(--space-4);
  width: 100%;
  max-width: 480px;
  padding: var(--space-10) var(--space-9);
  background: var(--color-surface);
  border: 1px solid var(--color-border-subtle);
  border-radius: var(--radius-xl);
  box-shadow: var(--shadow-lg);
}

.state-card__code {
  margin-top: var(--space-6);
  font-family: var(--font-family-number);
  font-size: var(--font-size-5xl);
  font-weight: var(--font-weight-bold);
  line-height: 1;
  letter-spacing: -0.04em;
  background: linear-gradient(100deg, var(--color-brand-500), var(--color-accent-500));
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}

.state-card__title {
  font-size: var(--font-size-2xl);
}

.state-card__desc {
  font-size: var(--font-size-sm);
  line-height: var(--line-height-loose);
  color: var(--color-text-secondary);
}

.state-card__actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-4);
  margin-top: var(--space-4);
}
</style>
