<script setup>
/**
 * 统一加载骨架（docs/01-architecture.md §6）
 * 页面/表格/卡片加载中统一使用，避免布局跳动。
 */
defineProps({
  rows: { type: Number, default: 4 },
  animated: { type: Boolean, default: true },
  card: { type: Boolean, default: true }
})
</script>

<template>
  <div class="loading-block" :class="{ 'loading-block--card': card }" aria-busy="true" aria-live="polite">
    <el-skeleton :rows="rows" :animated="animated">
      <template #template>
        <div class="loading-block__item">
          <el-skeleton-item variant="image" class="loading-block__image" />
          <div class="loading-block__body">
            <el-skeleton-item variant="h3" class="loading-block__title" />
            <el-skeleton-item variant="text" />
            <el-skeleton-item variant="text" class="loading-block__short" />
          </div>
        </div>
        <el-skeleton-item v-for="index in Math.max(rows - 1, 0)" :key="index" variant="text" />
      </template>
    </el-skeleton>
  </div>
</template>

<style scoped>
.loading-block {
  width: 100%;
}

.loading-block--card {
  padding: var(--space-6);
  border: 1px solid var(--color-border-subtle);
  border-radius: var(--radius-lg);
  background: var(--color-surface);
}

.loading-block__item {
  display: flex;
  gap: var(--space-4);
  margin-bottom: var(--space-4);
}

.loading-block__image {
  width: 96px;
  height: 96px;
  border-radius: var(--radius-md);
}

.loading-block__body {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: var(--space-3);
}

.loading-block__title {
  width: 40%;
}

.loading-block__short {
  width: 60%;
}
</style>
