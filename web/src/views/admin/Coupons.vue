<script setup>
/**
 * 管理后台 · 优惠券管理（docs/02-api.md §20.9–20.14）
 * - 列表：status、keyword、分页
 * - 新建 / 编辑：CouponFormDialog（面额、门槛、总量、有效期）
 * - 停用 / 启用、删除：已领取的券不可删（40918），停用后不可领、已领可用
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  deleteCoupon,
  listCoupons,
  updateCouponStatus
} from '@/api/admin'
import { COUPON_TYPE_LABELS, DEFAULT_PAGE_SIZE, PAGE_SIZES } from '@/utils/constants'
import { formatAmount, formatDateTime } from '@/utils/format'
import DataState from '@/components/DataState.vue'
import CouponFormDialog from '@/components/admin/CouponFormDialog.vue'

const COUPON_STATUS_OPTIONS = [
  { value: 'ENABLED', label: '启用中' },
  { value: 'DISABLED', label: '已停用' }
]
const COUPON_STATUS_LABELS = { ENABLED: '启用中', DISABLED: '已停用' }
const COUPON_STATUS_TAG_TYPES = { ENABLED: 'success', DISABLED: 'info' }

const filters = reactive({ keyword: '', status: '' })
const page = ref(1)
const size = ref(DEFAULT_PAGE_SIZE)
const rows = ref([])
const total = ref(0)
const loading = ref(false)
const error = ref('')
const mutatingId = ref(null)
const dialogVisible = ref(false)
const editingCoupon = ref(null)

const isEmpty = computed(() => !loading.value && !error.value && rows.value.length === 0)

async function load() {
  loading.value = true
  error.value = ''
  try {
    const data = await listCoupons({
      keyword: filters.keyword.trim() || undefined,
      status: filters.status || undefined,
      page: page.value,
      size: size.value
    })
    rows.value = data?.list || []
    total.value = data?.total || 0
  } catch (err) {
    error.value = err?.message || '优惠券列表加载失败'
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
  filters.status = ''
  page.value = 1
  load()
}

function changeSize(value) {
  size.value = value
  page.value = 1
  load()
}

function openCreate() {
  editingCoupon.value = null
  dialogVisible.value = true
}

function openEdit(row) {
  editingCoupon.value = row
  dialogVisible.value = true
}

function handleSaved() {
  load()
}

function claimedCount(row) {
  return Math.max(0, Number(row.total || 0) - Number(row.remaining || 0))
}

async function toggleStatus(row) {
  const next = row.status === 'ENABLED' ? 'DISABLED' : 'ENABLED'
  const isDisable = next === 'DISABLED'
  try {
    await ElMessageBox.confirm(
      isDisable
        ? `停用「${row.name}」后用户将无法再领取，已领取的券仍可使用。确认停用？`
        : `确认重新启用「${row.name}」？启用后用户可继续领取。`,
      isDisable ? '停用优惠券' : '启用优惠券',
      { confirmButtonText: '确认', cancelButtonText: '取消', type: 'warning' }
    )
  } catch {
    return
  }
  mutatingId.value = row.id
  try {
    const updated = await updateCouponStatus(row.id, next)
    row.status = updated?.status || next
    ElMessage.success(isDisable ? '已停用' : '已启用')
  } catch (err) {
    ElMessage.error(err?.message || '操作失败，请稍后重试')
  } finally {
    mutatingId.value = null
  }
}

async function remove(row) {
  try {
    await ElMessageBox.confirm(
      `确认删除优惠券「${row.name}」？删除后不可恢复。`,
      '删除优惠券',
      { confirmButtonText: '确认删除', cancelButtonText: '取消', type: 'warning' }
    )
  } catch {
    return
  }
  mutatingId.value = row.id
  try {
    await deleteCoupon(row.id)
    ElMessage.success('优惠券已删除')
    load()
  } catch (err) {
    if (err?.code === 40918) {
      ElMessage.warning('该券已被用户领取，不能删除，请改为停用')
    } else {
      ElMessage.error(err?.message || '删除失败，请稍后重试')
    }
  } finally {
    mutatingId.value = null
  }
}

onMounted(load)
</script>

<template>
  <div class="page admin-coupons">
    <header class="page-header">
      <div>
        <h1 class="page-title">优惠券管理</h1>
        <p class="page-subtitle">平台统一发放满减券；停用后不可领取，已领取的券仍可在有效期内使用。</p>
      </div>
      <el-button type="primary" @click="openCreate">新建优惠券</el-button>
    </header>

    <section class="filter-bar surface-card">
      <el-input
        v-model="filters.keyword"
        placeholder="优惠券名称"
        clearable
        maxlength="30"
        class="filter-bar__keyword"
        @keyup.enter="search"
        @clear="search"
      />
      <el-select v-model="filters.status" placeholder="全部状态" clearable class="filter-bar__select">
        <el-option v-for="item in COUPON_STATUS_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
      </el-select>
      <el-button type="primary" @click="search">查询</el-button>
      <el-button @click="resetFilters">重置</el-button>
    </section>

    <DataState :loading="loading" :error="error" :empty="isEmpty" @retry="load">
      <template #empty>
        <el-empty description="还没有优惠券，点击右上角新建" />
      </template>
      <div class="surface-card table-card">
        <el-table :data="rows" stripe border>
          <el-table-column prop="id" label="ID" width="70" />
          <el-table-column prop="name" label="名称" min-width="150" />
          <el-table-column label="类型" width="100">
            <template #default="{ row }">
              <el-tag size="small" effect="plain" type="warning">
                {{ COUPON_TYPE_LABELS[row.type] || row.type }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="优惠" min-width="170">
            <template #default="{ row }">
              满 {{ formatAmount(row.thresholdAmount) }} 减 {{ formatAmount(row.discountAmount) }}
            </template>
          </el-table-column>
          <el-table-column label="领取 / 总量" width="130" align="center">
            <template #default="{ row }">
              {{ claimedCount(row) }} / {{ row.total }}
              <div class="remaining-text">剩余 {{ row.remaining }}</div>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag size="small" :type="COUPON_STATUS_TAG_TYPES[row.status] || 'info'" effect="light">
                {{ COUPON_STATUS_LABELS[row.status] || row.status }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="有效期" min-width="300">
            <template #default="{ row }">
              <span class="validity">
                {{ formatDateTime(row.startAt) }} ~ {{ formatDateTime(row.endAt) }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="220" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
              <el-button
                link
                :type="row.status === 'ENABLED' ? 'warning' : 'success'"
                :loading="mutatingId === row.id"
                @click="toggleStatus(row)"
              >
                {{ row.status === 'ENABLED' ? '停用' : '启用' }}
              </el-button>
              <el-button
                link
                type="danger"
                :loading="mutatingId === row.id"
                @click="remove(row)"
              >
                删除
              </el-button>
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

    <CouponFormDialog v-model="dialogVisible" :coupon="editingCoupon" @saved="handleSaved" />
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
  width: 240px;
}

.filter-bar__select {
  width: 150px;
}

.table-card {
  padding: var(--space-2) var(--space-3) var(--space-3);
}

.remaining-text {
  color: var(--color-text-tertiary);
  font-size: var(--font-size-xs);
}

.validity {
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
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
