<script setup>
/**
 * 菜品图片：统一走 el-image 懒加载，加载失败/无图时展示图标占位。
 * 后端返回站内相对路径（/files/...），dev 由 Vite 代理，prod 由 Nginx 提供。
 */
import { resolveImageUrl } from '@/utils/media'

const props = defineProps({
  src: { type: String, default: '' },
  alt: { type: String, default: '菜品图片' },
  radius: { type: String, default: 'md' }
})

const imageSrc = () => resolveImageUrl(props.src)
</script>

<template>
  <el-image
    class="dish-thumb"
    :class="`dish-thumb--${radius}`"
    :src="imageSrc()"
    :alt="alt"
    fit="cover"
    loading="lazy"
  >
    <template #placeholder>
      <div class="dish-thumb__fallback dish-thumb__fallback--loading">
        <el-icon class="is-loading"><Loading /></el-icon>
      </div>
    </template>
    <template #error>
      <div class="dish-thumb__fallback">
        <el-icon><KnifeFork /></el-icon>
      </div>
    </template>
  </el-image>
</template>

<style scoped>
.dish-thumb {
  display: block;
  width: 100%;
  height: 100%;
  background: var(--color-bg-subtle);
}

.dish-thumb--sm {
  border-radius: var(--radius-sm);
}

.dish-thumb--md {
  border-radius: var(--radius-md);
}

.dish-thumb--lg {
  border-radius: var(--radius-lg);
}

.dish-thumb__fallback {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 100%;
  min-height: 72px;
  color: var(--color-brand-300);
  background: linear-gradient(135deg, var(--color-brand-50), var(--color-accent-100));
  font-size: 28px;
}

.dish-thumb__fallback--loading {
  color: var(--color-text-tertiary);
  font-size: 18px;
}
</style>
