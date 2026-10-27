<script setup>
/**
 * 业务页面占位组件
 * 三个布局的控制台页面与顾客端功能页在 T12/T13/T14 才接入真实数据，
 * 本组件根据 route.meta（title / description / phase / planned）渲染骨架与计划清单。
 */
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()

const meta = computed(() => route.meta || {})
const phase = computed(() => meta.value.phase || '后续阶段')
const planned = computed(() =>
  Array.isArray(meta.value.planned) && meta.value.planned.length
    ? meta.value.planned
    : [
        { title: '列表与筛选', desc: '表格 / 卡片列表、状态筛选与分页' },
        { title: '表单与校验', desc: '新增编辑表单、字段校验与提交反馈' },
        { title: '接口联调', desc: '对接 /api/v1 接口并处理加载与错误态' }
      ]
)

const highlight = computed(() => meta.value.highlight || '')
</script>

<template>
  <div class="app-container">
    <div class="page placeholder">
      <header class="page-header">
        <div>
          <p class="placeholder__eyebrow">{{ phase }} · 页面骨架已就绪</p>
          <h1 class="page-title">{{ meta.title || '功能页面' }}</h1>
          <p class="page-subtitle">{{ meta.description || '业务能力将在后续阶段接入。' }}</p>
        </div>
        <el-tag round type="warning" effect="plain">{{ phase }} 接入</el-tag>
      </header>

      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="路由、布局与权限守卫已在阶段 4A 完成"
      >
        <template #default>
          菜单入口、角色守卫与设计令牌已生效；业务列表、表单与接口联调将在
          {{ phase }} 交付。当前页面为占位视图。
        </template>
      </el-alert>

      <section class="placeholder__grid">
        <article
          v-for="(item, index) in planned"
          :key="item.title"
          class="surface-card placeholder__card"
        >
          <span class="placeholder__index">{{ String(index + 1).padStart(2, '0') }}</span>
          <h3 class="placeholder__card-title">{{ item.title }}</h3>
          <p class="placeholder__card-desc">{{ item.desc }}</p>
        </article>
      </section>

      <section v-if="highlight" class="surface-card placeholder__note">
        <h3 class="placeholder__card-title">阶段说明</h3>
        <p class="placeholder__card-desc">{{ highlight }}</p>
      </section>

      <section class="surface-card placeholder__preview">
        <div class="flex-between">
          <div>
            <h3 class="placeholder__card-title">列表预览占位</h3>
            <p class="placeholder__card-desc">接入数据后此处渲染真实表格 / 卡片视图</p>
          </div>
          <el-tag size="small" effect="plain" type="info">未接入数据</el-tag>
        </div>
        <el-skeleton :rows="5" animated class="placeholder__skeleton" />
      </section>

      <div class="placeholder__actions">
        <el-button @click="router.back()">
          <el-icon><Back /></el-icon>
          返回上一页
        </el-button>
        <el-button type="primary" plain @click="router.push('/')">
          <el-icon><HomeFilled /></el-icon>
          返回顾客端首页
        </el-button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.placeholder__eyebrow {
  margin-bottom: var(--space-3);
  font-size: var(--font-size-xs);
  font-weight: var(--font-weight-medium);
  letter-spacing: 0.16em;
  text-transform: uppercase;
  color: var(--color-primary);
}

.placeholder__grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: var(--space-5);
}

.placeholder__card {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.placeholder__index {
  font-family: var(--font-family-number);
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
  letter-spacing: 0.12em;
}

.placeholder__card-title {
  font-size: var(--font-size-lg);
  font-weight: var(--font-weight-semibold);
}

.placeholder__card-desc {
  font-size: var(--font-size-sm);
  line-height: var(--line-height-base);
  color: var(--color-text-secondary);
}

.placeholder__note {
  border-left: 3px solid var(--color-warning);
}

.placeholder__preview {
  display: flex;
  flex-direction: column;
  gap: var(--space-6);
}

.placeholder__skeleton {
  padding-top: var(--space-2);
}

.placeholder__actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-4);
}

@media (max-width: 900px) {
  .placeholder__grid {
    grid-template-columns: 1fr;
  }
}
</style>
