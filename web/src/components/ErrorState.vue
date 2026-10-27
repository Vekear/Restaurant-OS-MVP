<script setup>
/**
 * 统一错误态（docs/01-architecture.md §6）
 * 加载失败时展示中文原因与「重新加载」，emit retry 由页面重新拉取。
 */
defineProps({
  title: { type: String, default: '加载失败' },
  message: { type: String, default: '' },
  retryText: { type: String, default: '重新加载' }
})

defineEmits(['retry'])
</script>

<template>
  <div class="error-state">
    <el-result icon="warning" :title="title" :sub-title="message">
      <template #extra>
        <el-button type="primary" plain @click="$emit('retry')">{{ retryText }}</el-button>
        <slot name="extra" />
      </template>
    </el-result>
  </div>
</template>

<style scoped>
.error-state {
  width: 100%;
}
</style>
