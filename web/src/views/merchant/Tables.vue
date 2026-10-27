<script setup>
/**
 * 商家桌台管理（docs/02-api.md §17）
 * - 桌台 CRUD、占用状态、区域筛选
 * - 二维码：查看 / 复制链接 / 下载 PNG / 重签（旧码立即失效）
 * - 有进行中会话或订单时，删除/改桌号会被后端拒绝（40909）
 */
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { deleteTable, listTables } from '@/api/merchantTables'
import { useMerchantStore } from '@/stores/merchant'
import { TABLE_STATUS, TABLE_STATUS_LABELS, TABLE_STATUS_TAG_TYPES, tagTypeOf } from '@/utils/constants'
import DataState from '@/components/DataState.vue'
import TableFormDialog from '@/components/merchant/TableFormDialog.vue'
import TableQrDialog from '@/components/merchant/TableQrDialog.vue'

const merchant = useMerchantStore()

const tables = ref([])
const loading = ref(false)
const error = ref('')
const zoneFilter = ref('')
const statusFilter = ref('')

const formVisible = ref(false)
const editingTable = ref(null)
const qrVisible = ref(false)
const qrTable = ref(null)

const zones = computed(() => {
  const set = new Set()
  tables.value.forEach((table) => {
    if (table.zone) set.add(table.zone)
  })
  return [...set]
})

const filtered = computed(() =>
  tables.value.filter((table) => {
    if (zoneFilter.value && table.zone !== zoneFilter.value) return false
    if (statusFilter.value && table.status !== statusFilter.value) return false
    return true
  })
)

const stats = computed(() => {
  const total = tables.value.length
  const occupied = tables.value.filter((table) => table.status === TABLE_STATUS.OCCUPIED).length
  return { total, occupied, free: total - occupied }
})

const isEmpty = computed(() => !loading.value && !error.value && tables.value.length === 0)

async function load() {
  loading.value = true
  error.value = ''
  try {
    tables.value = (await listTables()) || []
  } catch (err) {
    error.value = err?.message || '桌台加载失败'
    tables.value = []
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editingTable.value = null
  formVisible.value = true
}

function openEdit(table) {
  editingTable.value = table
  formVisible.value = true
}

function openQr(table) {
  qrTable.value = table
  qrVisible.value = true
}

async function remove(table) {
  try {
    await ElMessageBox.confirm(
      `删除桌台「${table.tableNo}」？桌台存在进行中会话或订单时将无法删除。`,
      '删除桌台',
      { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning' }
    )
  } catch {
    return
  }
  try {
    await deleteTable(table.id)
    ElMessage.success('桌台已删除')
    await load()
  } catch (err) {
    if (err?.code === 40909) {
      ElMessage.error('桌台存在进行中会话或订单，暂时无法删除')
    } else {
      ElMessage.error(err?.message || '删除失败')
    }
  }
}

function handleSaved() {
  load()
}

function handleRenewed() {
  load()
}

onMounted(async () => {
  await merchant.loadProfile()
  await load()
})
</script>

<template>
  <div class="tables">
    <div class="page-header">
      <div>
        <h1 class="page-title">桌台管理</h1>
        <p class="page-subtitle">每张桌台对应一张签名二维码，扫码进入堂食点餐；重签后旧码立即失效</p>
      </div>
      <div class="tables__actions">
        <el-button :loading="loading" @click="load">
          <el-icon><Refresh /></el-icon>
          刷新
        </el-button>
        <el-button type="primary" @click="openCreate">
          <el-icon><Plus /></el-icon>
          新建桌台
        </el-button>
      </div>
    </div>

    <div class="stat-row">
      <div class="stat-pill">
        <span class="stat-pill__label">桌台总数</span>
        <span class="stat-pill__value">{{ stats.total }}</span>
      </div>
      <div class="stat-pill">
        <span class="stat-pill__label">使用中</span>
        <span class="stat-pill__value is-occupied">{{ stats.occupied }}</span>
      </div>
      <div class="stat-pill">
        <span class="stat-pill__label">空闲</span>
        <span class="stat-pill__value is-free">{{ stats.free }}</span>
      </div>
      <div class="stat-pill stat-pill--filters">
        <el-select v-model="zoneFilter" placeholder="全部区域" clearable size="default" class="filter-select">
          <el-option v-for="zone in zones" :key="zone" :label="zone" :value="zone" />
        </el-select>
        <el-select v-model="statusFilter" placeholder="全部状态" clearable size="default" class="filter-select">
          <el-option
            v-for="(label, value) in TABLE_STATUS_LABELS"
            :key="value"
            :label="label"
            :value="value"
          />
        </el-select>
      </div>
    </div>

    <DataState :loading="loading" :error="error" :empty="isEmpty" @retry="load">
      <template #empty>
        <div class="surface-card">
          <el-empty description="还没有桌台，点击「新建桌台」生成二维码">
            <el-button type="primary" @click="openCreate">新建桌台</el-button>
          </el-empty>
        </div>
      </template>

      <div class="table-grid">
        <article
          v-for="table in filtered"
          :key="table.id"
          class="table-card"
          :class="`is-${String(table.status).toLowerCase()}`"
        >
          <header class="table-card__head">
            <div class="table-card__no">
              <span class="table-card__no-text">{{ table.tableNo }}</span>
              <span v-if="table.zone" class="table-card__zone">{{ table.zone }}</span>
            </div>
            <el-tag :type="tagTypeOf(TABLE_STATUS_TAG_TYPES, table.status)" effect="light" size="small">
              {{ TABLE_STATUS_LABELS[table.status] || table.status }}
            </el-tag>
          </header>

          <div class="table-card__body">
            <p class="table-card__name">{{ table.name || '未命名桌台' }}</p>
            <p class="table-card__meta">
              <el-icon><User /></el-icon>
              {{ table.capacity ?? 4 }} 人位
              <span class="table-card__dot">·</span>
              二维码 v{{ table.qrVersion ?? 1 }}
            </p>
          </div>

          <footer class="table-card__foot">
            <el-button type="primary" plain size="small" @click="openQr(table)">
              <el-icon><Grid /></el-icon>
              二维码
            </el-button>
            <el-button text size="small" @click="openEdit(table)">编辑</el-button>
            <el-button text type="danger" size="small" @click="remove(table)">删除</el-button>
          </footer>
        </article>
      </div>

      <el-empty
        v-if="filtered.length === 0 && tables.length > 0"
        description="当前筛选条件下没有桌台"
        :image-size="90"
      />
    </DataState>

    <TableFormDialog v-model="formVisible" :table="editingTable" @saved="handleSaved" />
    <TableQrDialog v-model="qrVisible" :table="qrTable" @renewed="handleRenewed" />
  </div>
</template>

<style scoped>
.tables {
  display: flex;
  flex-direction: column;
  gap: var(--layout-gap);
}

.tables__actions {
  display: flex;
  gap: var(--space-2);
}

.stat-row {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-3);
}

.stat-pill {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-3) var(--space-5);
  border: 1px solid var(--color-border-subtle);
  border-radius: var(--radius-pill);
  background: var(--color-surface);
  box-shadow: var(--shadow-xs);
}

.stat-pill__label {
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
}

.stat-pill__value {
  font-family: var(--font-family-number);
  font-size: var(--font-size-lg);
  font-weight: var(--font-weight-semibold);
}

.stat-pill__value.is-occupied {
  color: var(--color-warning);
}

.stat-pill__value.is-free {
  color: var(--color-success);
}

.stat-pill--filters {
  margin-left: auto;
  gap: var(--space-2);
  border-radius: var(--radius-lg);
  padding: var(--space-2);
}

.filter-select {
  width: 140px;
}

.table-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: var(--space-4);
}

.table-card {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
  padding: var(--space-5);
  border: 1px solid var(--color-border-subtle);
  border-left: 4px solid var(--color-success);
  border-radius: var(--radius-lg);
  background: var(--color-surface);
  box-shadow: var(--shadow-xs);
  transition: transform var(--duration-fast) var(--ease-standard), box-shadow var(--duration-fast);
}

.table-card:hover {
  transform: translateY(-2px);
  box-shadow: var(--shadow-sm);
}

.table-card.is-occupied {
  border-left-color: var(--color-warning);
  background: linear-gradient(180deg, #fffaf1 0%, var(--color-surface) 46%);
}

.table-card__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-2);
}

.table-card__no {
  display: flex;
  align-items: baseline;
  gap: var(--space-2);
}

.table-card__no-text {
  font-family: var(--font-family-number);
  font-size: var(--font-size-2xl);
  font-weight: var(--font-weight-bold);
  letter-spacing: -0.02em;
}

.table-card__zone {
  color: var(--color-text-tertiary);
  font-size: var(--font-size-xs);
}

.table-card__body {
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
}

.table-card__name {
  font-size: var(--font-size-sm);
  color: var(--color-text-primary);
}

.table-card__meta {
  display: inline-flex;
  align-items: center;
  gap: var(--space-1);
  color: var(--color-text-tertiary);
  font-size: var(--font-size-xs);
}

.table-card__dot {
  margin: 0 var(--space-1);
}

.table-card__foot {
  display: flex;
  align-items: center;
  gap: var(--space-1);
  padding-top: var(--space-2);
  border-top: 1px dashed var(--color-border-subtle);
}
</style>
