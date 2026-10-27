<script setup>
/**
 * 管理后台 · 审计日志（docs/02-api.md §20.20）
 * - 筛选：action、userId、日期区间、分页
 * - 明细：detail 为 JSON 字符串，展开为格式化 JSON（解析失败则原样展示）
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { listAuditLogs } from '@/api/admin'
import {
  AUDIT_ACTION_OPTIONS,
  AUDIT_ROLE_LABELS,
  auditActionLabel,
  auditRoleLabel
} from '@/utils/auditActions'
import { DEFAULT_PAGE_SIZE, PAGE_SIZES } from '@/utils/constants'
import { formatDateTime } from '@/utils/format'
import { isRangeWithin } from '@/utils/dateRange'
import { ElMessage } from 'element-plus'
import DataState from '@/components/DataState.vue'

const filters = reactive({ action: '', userId: '', range: [] })
const page = ref(1)
const size = ref(DEFAULT_PAGE_SIZE)
const rows = ref([])
const total = ref(0)
const loading = ref(false)
const error = ref('')

const isEmpty = computed(() => !loading.value && !error.value && rows.value.length === 0)
const actionOptions = AUDIT_ACTION_OPTIONS

function buildQuery() {
  const query = {
    action: filters.action || undefined,
    userId: filters.userId ? Number(filters.userId) : undefined
  }
  if (Array.isArray(filters.range) && filters.range.length === 2) {
    query.startDate = filters.range[0]
    query.endDate = filters.range[1]
  }
  return query
}

async function load() {
  if (Array.isArray(filters.range) && filters.range.length === 2 && !isRangeWithin(filters.range, 92)) {
    ElMessage.warning('时间跨度最长 92 天，请缩小范围')
    return
  }
  loading.value = true
  error.value = ''
  try {
    const data = await listAuditLogs({ ...buildQuery(), page: page.value, size: size.value })
    rows.value = data?.list || []
    total.value = data?.total || 0
  } catch (err) {
    error.value = err?.message || '审计日志加载失败'
  } finally {
    loading.value = false
  }
}

function search() {
  page.value = 1
  load()
}

function resetFilters() {
  filters.action = ''
  filters.userId = ''
  filters.range = []
  page.value = 1
  load()
}

function changeSize(value) {
  size.value = value
  page.value = 1
  load()
}

function prettyDetail(detail) {
  if (!detail) return '（无附加信息）'
  try {
    return JSON.stringify(JSON.parse(detail), null, 2)
  } catch {
    return String(detail)
  }
}

onMounted(load)
</script>

<template>
  <div class="page admin-audit">
    <header class="page-header">
      <div>
        <h1 class="page-title">审计日志</h1>
        <p class="page-subtitle">记录登录、状态变更、审核、优惠券等关键操作；按动作、操作人、时间检索。</p>
      </div>
      <el-button @click="load">刷新</el-button>
    </header>

    <section class="filter-bar surface-card">
      <el-select v-model="filters.action" placeholder="全部动作" clearable filterable class="filter-bar__action">
        <el-option v-for="item in actionOptions" :key="item.value" :label="item.label" :value="item.value" />
      </el-select>
      <el-input
        v-model="filters.userId"
        placeholder="操作人用户 ID"
        clearable
        class="filter-bar__short"
        @keyup.enter="search"
      />
      <el-date-picker
        v-model="filters.range"
        type="daterange"
        value-format="YYYY-MM-DD"
        range-separator="至"
        start-placeholder="开始日期"
        end-placeholder="结束日期"
        class="filter-bar__range"
      />
      <el-button type="primary" @click="search">查询</el-button>
      <el-button @click="resetFilters">重置</el-button>
    </section>

    <DataState :loading="loading" :error="error" :empty="isEmpty" @retry="load">
      <template #empty>
        <el-empty description="没有符合条件的审计记录" />
      </template>
      <div class="surface-card table-card">
        <el-table :data="rows" border>
          <el-table-column type="expand">
            <template #default="{ row }">
              <div class="log-detail">
                <div class="log-detail__meta">
                  <span>traceId：{{ row.traceId || '—' }}</span>
                  <span>IP：{{ row.ip || '—' }}</span>
                  <span>UA：{{ row.userAgent || '—' }}</span>
                </div>
                <pre class="log-detail__json">{{ prettyDetail(row.detail) }}</pre>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="时间" min-width="170">
            <template #default="{ row }">{{ formatDateTime(row.createdAt, true) }}</template>
          </el-table-column>
          <el-table-column label="操作人" min-width="150">
            <template #default="{ row }">
              <div class="operator">{{ row.username || '—' }}</div>
              <div class="operator__role">{{ auditRoleLabel(row.role) }}</div>
            </template>
          </el-table-column>
          <el-table-column label="动作" min-width="200">
            <template #default="{ row }">{{ auditActionLabel(row.action) }}</template>
          </el-table-column>
          <el-table-column label="目标" min-width="160">
            <template #default="{ row }">
              <span class="target-type">{{ row.targetType || '—' }}</span>
              <span v-if="row.targetId" class="target-id">#{{ row.targetId }}</span>
            </template>
          </el-table-column>
          <el-table-column label="结果" width="90" align="center">
            <template #default="{ row }">
              <el-tag size="small" :type="row.success ? 'success' : 'danger'" effect="light">
                {{ row.success ? '成功' : '失败' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="userId" label="用户 ID" width="100" align="center" />
        </el-table>
      </div>

      <div class="pagination">
        <span class="pagination__total">共 {{ total }} 条</span>
        <el-pagination
          :current-page="page"
          :page-size="size"
          :page-sizes="PAGE_SIZES"
          :total="total"
          layout="sizes, prev, pager, next, jumper"
          background
          @current-change="(value) => { page = value; load() }"
          @size-change="changeSize"
        />
      </div>
    </DataState>
  </div>
</template>

<style scoped>
.filter-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-4);
  margin-bottom: var(--space-5);
}

.filter-bar__action {
  width: 240px;
}

.filter-bar__short {
  width: 150px;
}

.filter-bar__range {
  max-width: 280px;
}

.table-card {
  padding: var(--space-2) var(--space-3) var(--space-3);
}

.log-detail {
  padding: var(--space-3) var(--space-5) var(--space-4);
  background: var(--color-bg-subtle);
}

.log-detail__meta {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-4);
  color: var(--color-text-tertiary);
  font-size: var(--font-size-xs);
  margin-bottom: var(--space-3);
}

.log-detail__json {
  margin: 0;
  max-height: 320px;
  overflow: auto;
  padding: var(--space-3);
  border-radius: var(--radius-sm);
  background: var(--color-surface);
  border: 1px solid var(--color-border-subtle);
  font-family: var(--font-family-mono);
  font-size: var(--font-size-xs);
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-all;
}

.operator {
  font-weight: var(--font-weight-medium);
}

.operator__role {
  color: var(--color-text-tertiary);
  font-size: var(--font-size-xs);
}

.target-type {
  color: var(--color-text-secondary);
}

.target-id {
  margin-left: var(--space-2);
  color: var(--color-text-tertiary);
  font-size: var(--font-size-xs);
}

.pagination {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  margin-top: var(--space-4);
}

.pagination__total {
  color: var(--color-text-tertiary);
  font-size: var(--font-size-sm);
}
</style>
