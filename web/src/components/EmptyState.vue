<script setup>
/**
 * 统一空态（docs/01-architecture.md §6：EmptyState.vue）
 * 列表/图表/卡片区无数据时使用，保证三端空态文案与留白一致。
 */
defineProps({
  title: { type: String, default: '暂无数据' },
  description: { type: String, default: '' },
  imageSize: { type: Number, default: 96 }
})
</script>

<template>
  <div class="empty-state">
    <el-empty :description="description || title" :image-size="imageSize">
      <template #image>
        <div class="empty-state__art">
          <el-icon><KnifeFork /></el-icon>
        </div>
      </template>
      <slot />
    </el-empty>
  </div>
</template>

<style scoped>
.empty-state {
  display: flex;
  justify-content: center;
  width: 100%;
  padding: var(--space-6) 0;
}

.empty-state__art {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 72px;
  height: 72px;
  border-radius: var(--radius-pill);
  background: linear-gradient(135deg, var(--color-brand-50), var(--color-accent-100));
  color: var(--color-brand-400);
  font-size: 30px;
}
</style>
