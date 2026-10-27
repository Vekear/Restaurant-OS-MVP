<script setup>
/**
 * 统一的异步数据状态容器：加载中 / 失败重试 / 空态 / 正常内容。
 * 顾客端各页面复用，保证状态表现一致。
 */
defineProps({
  loading: { type: Boolean, default: false },
  error: { type: String, default: '' },
  empty: { type: Boolean, default: false },
  skeletonRows: { type: Number, default: 4 }
})

defineEmits(['retry'])
</script>

<template>
  <div class="data-state">
    <template v-if="loading">
      <slot name="loading">
        <div class="data-state__skeleton">
          <el-skeleton :rows="skeletonRows" animated />
        </div>
      </slot>
    </template>
    <template v-else-if="error">
      <slot name="error">
        <el-result icon="warning" title="加载失败" :sub-title="error">
          <template #extra>
            <el-button type="primary" plain @click="$emit('retry')">重新加载</el-button>
          </template>
        </el-result>
      </slot>
    </template>
    <template v-else-if="empty">
      <slot name="empty">
        <el-empty description="暂无数据" />
      </slot>
    </template>
    <template v-else>
      <slot />
    </template>
  </div>
</template>

<style scoped>
.data-state {
  width: 100%;
}

.data-state__skeleton {
  padding: var(--space-6);
  border: 1px solid var(--color-border-subtle);
  border-radius: var(--radius-lg);
  background: var(--color-surface);
}
</style>
