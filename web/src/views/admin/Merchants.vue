<script setup>
/**
 * 管理后台 · 商家管理（docs/02-api.md §20.3–20.5）
 * - 检索：keyword（店名/店主）、auditStatus、status
 * - 审核 / 驳回 / 禁用 / 恢复：弹窗填写原因，写审计日志
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { listMerchants } from '@/api/admin'
import {
  MERCHANT_AUDIT_STATUS,
  MERCHANT_AUDIT_STATUS_LABELS,
  MERCHANT_AUDIT_STATUS_TAG_TYPES,
  MERCHANT_BUSINESS_STATUS_LABELS,
  MERCHANT_STATUS_LABELS,
  MERCHANT_STATUS_TAG_TYPES,
  DEFAULT_PAGE_SIZE,
  PAGE_SIZES
} from '@/utils/constants'
import { formatDateTime } from '@/utils/format'
import DataState from '@/components/DataState.vue'
import MerchantAuditDialog from '@/components/admin/MerchantAuditDialog.vue'

const filters = reactive({ keyword: '', auditStatus: '', status: '' })
const page = ref(1)
const size = ref(DEFAULT_PAGE_SIZE)
const rows = ref([])
const total = ref(0)
const loading = ref(false)
const error = ref('')
const dialogVisible = ref(false)
const activeMerchant = ref(null)

const auditOptions = Object.entries(MERCHANT_AUDIT_STATUS_LABELS).map(([value, label]) => ({
  value,
  label
}))
const statusOptions = Object.entries(MERCHANT_STATUS_LABELS).map(([value, label]) => ({ value, label }))
const isEmpty = computed(() => !loading.value && !error.value && rows.value.length === 0)
const pendingCount = computed(
  () => rows.value.filter((row) => row.auditStatus === MERCHANT_AUDIT_STATUS.PENDING).length
)

async function load() {
  loading.value = true
  error.value = ''
  try {
    const data = await listMerchants({
      keyword: filters.keyword.trim() || undefined,
      auditStatus: filters.auditStatus || undefined,
      status: filters.status || undefined,
      page: page.value,
      size: size.value
    })
    rows.value = data?.list || []
    total.value = data?.total || 0
  } catch (err) {
    error.value = err?.message || '商家列表加载失败'
  } finally {
    loading.value = false
  }
}

function search() {
  page.value = 1
  load()
}

function resetFilters() {
  filters.keyword = ''
  filters.auditStatus = ''
  filters.status = ''
  page.value = 1
  load()
}

function changeSize(value) {
  size.value = value
  page.value = 1
  load()
}

function openAudit(row) {
  activeMerchant.value = row
  dialogVisible.value = true
}

function handleSaved() {
  ElMessage.success('操作成功')
  load()
}

onMounted(load)
</script>

<template>
  <div class="page admin-merchants">
    <header class="page-header">
      <div>
        <h1 class="page-title">商家管理</h1>
        <p class="page-subtitle">入驻审核、营业信息与平台停启用；当前列表待审核 {{ pendingCount }} 家。</p>
      </div>
      <el-button @click="load">刷新</el-button>
    </header>

    <section class="filter-bar surface-card">
      <el-input
        v-model="filters.keyword"
        placeholder="店名 / 店主用户名"
        clearable
        maxlength="30"
        class="filter-bar__keyword"
        @keyup.enter="search"
        @clear="search"
      />
      <el-select v-model="filters.auditStatus" placeholder="全部审核状态" clearable class="filter-bar__select">
        <el-option v-for="item in auditOptions" :key="item.value" :label="item.label" :value="item.value" />
      </el-select>
      <el-select v-model="filters.status" placeholder="全部账号状态" clearable class="filter-bar__select">
        <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
      </el-select>
      <el-button type="primary" @click="search">查询</el-button>
      <el-button @click="resetFilters">重置</el-button>
    </section>

    <DataState :loading="loading" :error="error" :empty="isEmpty" @retry="load">
      <template #empty>
        <el-empty description="没有符合条件的商家" />
      </template>
      <div class="surface-card table-card">
        <el-table :data="rows" stripe border>
          <el-table-column prop="id" label="ID" width="70" />
          <el-table-column prop="name" label="店铺名称" min-width="180" />
          <el-table-column prop="ownerUsername" label="店主" min-width="110" />
          <el-table-column prop="phone" label="联系电话" min-width="130" />
          <el-table-column label="起送 / 配送费" min-width="140">
            <template #default="{ row }">
              ¥{{ Number(row.minOrderAmount ?? 0).toFixed(2) }} 起送 · 配送 ¥{{
                Number(row.deliveryFee ?? 0).toFixed(2)
              }}
            </template>
          </el-table-column>
          <el-table-column label="营业状态" width="110">
            <template #default="{ row }">
              <el-tag size="small" type="warning" effect="plain">
                {{ MERCHANT_BUSINESS_STATUS_LABELS[row.businessStatus] || row.businessStatus || '—' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="审核" width="100">
            <template #default="{ row }">
              <el-tag
                size="small"
                :type="MERCHANT_AUDIT_STATUS_TAG_TYPES[row.auditStatus] || 'info'"
                effect="light"
              >
                {{ MERCHANT_AUDIT_STATUS_LABELS[row.auditStatus] || row.auditStatus }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-tag size="small" :type="MERCHANT_STATUS_TAG_TYPES[row.status] || 'info'" effect="light">
                {{ MERCHANT_STATUS_LABELS[row.status] || row.status }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="orderCount" label="订单数" width="90" align="right" />
          <el-table-column label="入驻时间" min-width="170">
            <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="120" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openAudit(row)">审核 / 状态</el-button>
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

    <MerchantAuditDialog v-model="dialogVisible" :merchant="activeMerchant" @saved="handleSaved" />
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

.filter-bar__keyword {
  width: 260px;
}

.filter-bar__select {
  width: 170px;
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
