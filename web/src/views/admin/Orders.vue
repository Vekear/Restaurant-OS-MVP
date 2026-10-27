<script setup>
/**
 * 管理后台 · 订单管理（docs/02-api.md §20.6–20.8）
 * - 筛选：merchantId、orderType、status、startDate/endDate（≤92 天）、keyword（订单号/收货人）
 * - 详情：AdminOrderDrawer（OrderDetailView 只读快照 + 状态流转）
 * - 导出：/admin/orders/export（xlsx，列同商家导出并追加「商家」列）
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { exportOrders, listOrders } from '@/api/admin'
import {
  DEFAULT_PAGE_SIZE,
  ORDER_STATUS_LABELS,
  ORDER_STATUS_TAG_TYPES,
  ORDER_TYPE,
  PAGE_SIZES,
  tagTypeOf,
  labelOf
} from '@/utils/constants'
import { formatCurrency, formatDateTime } from '@/utils/format'
import { isRangeWithin, recentRange } from '@/utils/dateRange'
import DataState from '@/components/DataState.vue'
import AdminOrderDrawer from '@/components/admin/AdminOrderDrawer.vue'

const filters = reactive({
  merchantId: '',
  orderType: '',
  status: '',
  keyword: '',
  range: []
})
const page = ref(1)
const size = ref(DEFAULT_PAGE_SIZE)
const rows = ref([])
const total = ref(0)
const loading = ref(false)
const error = ref('')
const exporting = ref(false)
const drawerVisible = ref(false)
const activeOrderId = ref(null)

const statusOptions = Object.entries(ORDER_STATUS_LABELS).map(([value, label]) => ({ value, label }))
const typeOptions = [
  { value: ORDER_TYPE.TAKEOUT, label: '外卖' },
  { value: ORDER_TYPE.DINE_IN, label: '堂食' }
]
const isEmpty = computed(() => !loading.value && !error.value && rows.value.length === 0)

function buildQuery() {
  const query = {
    merchantId: filters.merchantId ? Number(filters.merchantId) : undefined,
    orderType: filters.orderType || undefined,
    status: filters.status || undefined,
    keyword: filters.keyword.trim() || undefined
  }
  if (Array.isArray(filters.range) && filters.range.length === 2) {
    query.startDate = filters.range[0]
    query.endDate = filters.range[1]
  }
  return query
}

function validateRange() {
  if (!Array.isArray(filters.range) || filters.range.length !== 2) return true
  if (!isRangeWithin(filters.range, 92)) {
    ElMessage.warning('时间跨度最长 92 天，请缩小范围')
    return false
  }
  return true
}

async function load() {
  if (!validateRange()) return
  loading.value = true
  error.value = ''
  try {
    const data = await listOrders({ ...buildQuery(), page: page.value, size: size.value })
    rows.value = data?.list || []
    total.value = data?.total || 0
  } catch (err) {
    error.value = err?.message || '订单列表加载失败'
  } finally {
    loading.value = false
  }
}

function search() {
  page.value = 1
  load()
}

function resetFilters() {
  filters.merchantId = ''
  filters.orderType = ''
  filters.status = ''
  filters.keyword = ''
  filters.range = []
  page.value = 1
  load()
}

function changeSize(value) {
  size.value = value
  page.value = 1
  load()
}

function quickRange(days) {
  filters.range = recentRange(days)
  search()
}

function openDetail(row) {
  activeOrderId.value = row.id
  drawerVisible.value = true
}

async function doExport() {
  if (!validateRange()) return
  exporting.value = true
  try {
    await exportOrders(buildQuery())
    ElMessage.success('订单明细已开始下载（xlsx，含「商家」列）')
  } catch (err) {
    ElMessage.error(err?.message || '导出失败，请稍后重试')
  } finally {
    exporting.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="page admin-orders">
    <header class="page-header">
      <div>
        <h1 class="page-title">订单管理</h1>
        <p class="page-subtitle">全平台订单追溯：按商家、类型、状态、时间与订单号检索，可导出 xlsx。</p>
      </div>
      <div class="page-header__actions">
        <el-button :loading="exporting" @click="doExport">导出订单明细</el-button>
        <el-button @click="load">刷新</el-button>
      </div>
    </header>

    <section class="filter-bar surface-card">
      <el-input
        v-model="filters.keyword"
        placeholder="订单号 / 收货人"
        clearable
        maxlength="30"
        class="filter-bar__keyword"
        @keyup.enter="search"
        @clear="search"
      />
      <el-input
        v-model="filters.merchantId"
        placeholder="商家 ID"
        clearable
        class="filter-bar__short"
        @keyup.enter="search"
      />
      <el-select v-model="filters.orderType" placeholder="全部类型" clearable class="filter-bar__short">
        <el-option v-for="item in typeOptions" :key="item.value" :label="item.label" :value="item.value" />
      </el-select>
      <el-select v-model="filters.status" placeholder="全部状态" clearable class="filter-bar__select">
        <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
      </el-select>
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
      <el-button link type="primary" @click="quickRange(7)">近 7 天</el-button>
      <el-button link type="primary" @click="quickRange(30)">近 30 天</el-button>
    </section>

    <DataState :loading="loading" :error="error" :empty="isEmpty" @retry="load">
      <template #empty>
        <el-empty description="没有符合条件的订单" />
      </template>
      <div class="surface-card table-card">
        <el-table :data="rows" stripe border>
          <el-table-column prop="orderNo" label="订单号" min-width="190" />
          <el-table-column label="类型" width="80">
            <template #default="{ row }">
              <el-tag size="small" effect="plain" :type="row.orderType === 'DINE_IN' ? 'warning' : 'primary'">
                {{ row.orderType === 'DINE_IN' ? '堂食' : '外卖' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="merchantName" label="商家" min-width="160" />
          <el-table-column prop="username" label="顾客" min-width="110">
            <template #default="{ row }">{{ row.username || '—' }}</template>
          </el-table-column>
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag size="small" :type="tagTypeOf(ORDER_STATUS_TAG_TYPES, row.status, 'info')" effect="light">
                {{ labelOf(ORDER_STATUS_LABELS, row.status, row.statusText) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="实付" width="110" align="right">
            <template #default="{ row }">{{ formatCurrency(row.payAmount) }}</template>
          </el-table-column>
          <el-table-column prop="itemCount" label="件数" width="70" align="center" />
          <el-table-column label="桌号" width="80">
            <template #default="{ row }">{{ row.tableNo || '—' }}</template>
          </el-table-column>
          <el-table-column label="下单时间" min-width="170">
            <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="90" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openDetail(row)">详情</el-button>
            </template>
          </el-table-column>
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

    <AdminOrderDrawer v-model="drawerVisible" :order-id="activeOrderId" />
  </div>
</template>

<style scoped>
.page-header__actions {
  display: flex;
  gap: var(--space-3);
}

.filter-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-4);
  margin-bottom: var(--space-5);
}

.filter-bar__keyword {
  width: 210px;
}

.filter-bar__short {
  width: 120px;
}

.filter-bar__select {
  width: 150px;
}

.filter-bar__range {
  max-width: 280px;
}

.table-card {
  padding: var(--space-2) var(--space-3) var(--space-3);
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
