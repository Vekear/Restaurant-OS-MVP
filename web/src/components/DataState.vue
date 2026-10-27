<script setup>
/**
 * 统一的异步数据状态容器：加载中 / 失败重试 / 空态 / 正常内容。
 * 内部组合 LoadingBlock / ErrorState / EmptyState，三端状态表现一致；
 * 页面仍可传入同名插槽做局部定制。
 */
import LoadingBlock from '@/components/LoadingBlock.vue'
import ErrorState from '@/components/ErrorState.vue'
import EmptyState from '@/components/EmptyState.vue'

defineProps({
  loading: { type: Boolean, default: false },
  error: { type: String, default: '' },
  empty: { type: Boolean, default: false },
  skeletonRows: { type: Number, default: 4 },
  emptyText: { type: String, default: '暂无数据' }
})

defineEmits(['retry'])
</script>

<template>
  <div class="data-state">
    <template v-if="loading">
      <slot name="loading">
        <LoadingBlock :rows="skeletonRows" />
      </slot>
    </template>
    <template v-else-if="error">
      <slot name="error">
        <ErrorState :message="error" @retry="$emit('retry')" />
      </slot>
    </template>
    <template v-else-if="empty">
      <slot name="empty">
        <EmptyState :title="emptyText" />
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
</style>
