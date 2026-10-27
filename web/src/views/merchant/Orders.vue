<script setup>
/**
 * 商家订单看板（外卖 / 堂食共用，按 route props.orderType 区分）
 * - 状态页签：进行中 / 待接单 / 已接单 / 制作中 / 配送中(外卖) / 已出餐(堂食) / 已完成 / 全部
 * - 行内动作：接单 / 拒单 / 开始制作 / 出餐配送 / 已出餐 / 完成
 * - WebSocket 新订单高亮 + 30s 兜底轮询（事件可能丢失，以 GET 快照为准）
 */
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { listMerchantOrders } from '@/api/merchantOrders'
import { useMerchantStore } from '@/stores/merchant'
import { formatCurrency, formatDateTime } from '@/utils/format'
import { boardTabsFor } from '@/utils/merchantOrders'
import {
  ORDER_STATUS_LABELS,
  ORDER_STATUS_TAG_TYPES,
  ORDER_TYPE,
  WS_EVENT_TYPE,
  labelOf,
  tagTypeOf
} from '@/utils/constants'
import DataState from '@/components/DataState.vue'
import OrderActionButtons from '@/components/merchant/OrderActionButtons.vue'
import OrderDetailDrawer from '@/components/merchant/OrderDetailDrawer.vue'

const props = defineProps({
  orderType: { type: String, default: ORDER_TYPE.TAKEOUT }
})

const route = useRoute()
const router = useRouter()
const merchant = useMerchantStore()

const isDineIn = computed(() => props.orderType === ORDER_TYPE.DINE_IN)
const pageTitle = computed(() => (isDineIn.value ? '堂食订单' : '外卖订单'))
const pageSubtitle = computed(() =>
  isDineIn.value
    ? '扫码点餐订单：接单 → 制作中 → 已出餐 → 完成（自动关闭桌台会话）'
    : '外卖订单：接单 → 制作中 → 出餐配送 → 完成'
)

const tabs = computed(() => boardTabsFor(props.orderType))
const activeTab = ref('ACTIVE')
const activeTabItem = computed(() => tabs.value.find((tab) => tab.key === activeTab.value) || tabs.value[0])

const rows = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const loading = ref(false)
const error = ref('')

const newOrderIds = ref(new Set())
const detailVisible = ref(false)
const detailId = ref(null)

const isEmpty = computed(() => !loading.value && !error.value && rows.value.length === 0)

function params() {
  const tab = activeTabItem.value || {}
  const query = { page: page.value, size: size.value }
  if (tab.statusGroup) query.statusGroup = tab.statusGroup
  if (tab.status) query.status = tab.status
  return query
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    const result = await listMerchantOrders(props.orderType, params())
    rows.value = result?.list || []
    total.value = result?.total || 0
  } catch (err) {
    error.value = err?.message || '订单加载失败'
    rows.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function changeTab(key) {
  activeTab.value = key
  page.value = 1
  const tab = tabs.value.find((item) => item.key === key)
  if (tab?.status) {
    router.replace({ path: route.path, query: { status: tab.status } })
  } else {
    router.replace({ path: route.path, query: {} })
  }
  load()
}

function onPageChange(next) {
  page.value = next
  load()
}

function openDetail(row) {
  detailId.value = row.id
  detailVisible.value = true
}

function handleActionDone() {
  load()
}

function handleRowRefresh() {
  load()
}

function rowClassName({ row }) {
  return newOrderIds.value.has(row.id) ? 'is-new-order' : ''
}

/** 状态页签对应的待处理数量提示 */
const pendingHint = computed(() => {
  const pending = activeTab.value === 'PAID'
  return pending ? '新订单请尽快接单，超时会触发自动取消' : ''
})

let pollTimer = null
let highlightTimer = null

function startPolling() {
  pollTimer = window.setInterval(() => {
    if (document.visibilityState === 'visible' && !detailVisible.value) load()
  }, 30000)
}

watch(
  () => merchant.lastEvent,
  (event) => {
    const payload = event?.payload
    if (!payload) return
    if (payload.orderType && payload.orderType !== props.orderType) return
    if (payload.eventType === WS_EVENT_TYPE.ORDER_CREATED) {
      newOrderIds.value = new Set([...newOrderIds.value, payload.orderId])
      if (highlightTimer) window.clearTimeout(highlightTimer)
      highlightTimer = window.setTimeout(() => {
        newOrderIds.value = new Set()
      }, 30000)
    }
    load()
  }
)

onMounted(() => {
  const queryStatus = route.query.status
  const matched = tabs.value.find((tab) => tab.status === queryStatus || tab.key === queryStatus)
  if (matched) activeTab.value = matched.key
  load()
  startPolling()
})

onBeforeUnmount(() => {
  if (pollTimer) window.clearInterval(pollTimer)
  if (highlightTimer) window.clearTimeout(highlightTimer)
  pollTimer = null
  highlightTimer = null
})
</script>

<template>
  <div class="orders">
    <div class="page-header">
      <div>
        <h1 class="page-title">{{ pageTitle }}</h1>
        <p class="page-subtitle">{{ pageSubtitle }}</p>
      </div>
      <div class="orders__actions">
        <span v-if="pendingHint" class="orders__hint">{{ pendingHint }}</span>
        <el-button :loading="loading" @click="load">
          <el-icon><Refresh /></el-icon>
          刷新
        </el-button>
      </div>
    </div>

    <div class="surface-card orders__toolbar">
      <el-radio-group :model-value="activeTab" size="default" @change="changeTab">
        <el-radio-button v-for="tab in tabs" :key="tab.key" :value="tab.key">
          {{ tab.label }}
        </el-radio-button>
      </el-radio-group>
      <span class="orders__count">共 {{ total }} 单</span>
    </div>

    <DataState :loading="loading" :error="error" :empty="isEmpty" @retry="load">
      <template #empty>
        <el-empty description="该状态下暂无订单" />
      </template>

      <div class="surface-card orders__table">
        <el-table :data="rows" :row-class-name="rowClassName" size="default">
          <el-table-column label="订单号" min-width="200">
            <template #default="{ row }">
              <div class="order-no">{{ row.orderNo }}</div>
              <div class="order-time">{{ formatDateTime(row.createdAt, true) }}</div>
            </template>
          </el-table-column>
          <el-table-column v-if="isDineIn" label="桌号" width="100">
            <template #default="{ row }">
              <el-tag v-if="row.tableNo" type="warning" effect="plain">{{ row.tableNo }}</el-tag>
              <span v-else>—</span>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="120">
            <template #default="{ row }">
              <el-tag :type="tagTypeOf(ORDER_STATUS_TAG_TYPES, row.status)" effect="light">
                {{ labelOf(ORDER_STATUS_LABELS, row.status, row.statusText) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="实付金额" width="130" align="right">
            <template #default="{ row }">
              <span class="amount">{{ formatCurrency(row.payAmount) }}</span>
            </template>
          </el-table-column>
          <el-table-column v-if="!isDineIn" label="件数" width="80" align="center">
            <template #default="{ row }">{{ row.itemCount ?? '—' }}</template>
          </el-table-column>
          <el-table-column label="支付时间" width="170">
            <template #default="{ row }">
              {{ row.paidAt ? formatDateTime(row.paidAt, true) : '—' }}
            </template>
          </el-table-column>
          <el-table-column label="操作" min-width="260" fixed="right">
            <template #default="{ row }">
              <div class="order-ops">
                <OrderActionButtons
                  :order="row"
                  size="small"
                  @done="handleActionDone"
                  @refresh="handleRowRefresh"
                />
                <el-button text type="primary" size="small" @click="openDetail(row)">详情</el-button>
              </div>
            </template>
          </el-table-column>
        </el-table>

        <div class="orders__pager">
          <el-pagination
            layout="prev, pager, next, total"
            :total="total"
            :page-size="size"
            :current-page="page"
            background
            @current-change="onPageChange"
          />
        </div>
      </div>
    </DataState>

    <OrderDetailDrawer
      v-model="detailVisible"
      :order-id="detailId"
      :order-type="orderType"
      @done="load"
    />
  </div>
</template>

<style scoped>
.orders {
  display: flex;
  flex-direction: column;
  gap: var(--layout-gap);
}

.orders__actions {
  display: flex;
  align-items: center;
  gap: var(--space-3);
}

.orders__hint {
  color: var(--color-warning);
  font-size: var(--font-size-xs);
}

.orders__toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  padding: var(--space-4) var(--space-5);
}

.orders__count {
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
}

.orders__table {
  padding: var(--space-2) var(--space-4) var(--space-4);
}

.orders__pager {
  display: flex;
  justify-content: flex-end;
  padding-top: var(--space-4);
}

.order-no {
  font-family: var(--font-family-number);
  font-weight: var(--font-weight-medium);
}

.order-time {
  margin-top: 2px;
  color: var(--color-text-tertiary);
  font-size: var(--font-size-xs);
}

.order-ops {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-2);
}

:deep(.is-new-order) {
  background: var(--color-brand-50);
}

:deep(.is-new-order:hover > td) {
  background: var(--color-brand-50) !important;
}
</style>
