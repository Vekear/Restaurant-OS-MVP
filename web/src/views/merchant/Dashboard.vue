<script setup>
/**
 * 商家工作台 · 经营概览
 * - 今日指标卡（订单数 / 营业额 / 待接单 / 制作中 / 低库存…）
 * - 近 7 天营业额与订单量趋势、热销菜品 Top5、订单类型占比
 * - WebSocket 新订单通知 + 60s 兜底轮询（事件可能丢失，以快照为准）
 */
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getDashboardTrend, getHotDishes, getOrderTypeRatio } from '@/api/merchant'
import { useMerchantStore } from '@/stores/merchant'
import { formatAmount, formatCurrency } from '@/utils/format'
import { recentRange, shortDate, todayString } from '@/utils/dateRange'
import { MERCHANT_AUDIT_STATUS_LABELS, ORDER_TYPE, WS_EVENT_TYPE } from '@/utils/constants'
import EChartPanel from '@/components/EChartPanel.vue'

const router = useRouter()
const merchant = useMerchantStore()

const loading = ref(false)
const error = ref('')
const trend = ref(null)
const hot = ref(null)
const ratio = ref(null)
const chartsLoading = ref(false)

const range = recentRange(7)
const summary = computed(() => merchant.summary || {})
const auditLabel = computed(
  () => MERCHANT_AUDIT_STATUS_LABELS[merchant.auditStatus] || merchant.auditStatus
)

const metrics = computed(() => [
  {
    key: 'orderCount',
    label: '今日订单',
    value: String(summary.value.orderCount ?? 0),
    hint: '已支付订单数'
  },
  {
    key: 'revenue',
    label: '今日营业额',
    value: formatCurrency(summary.value.revenue ?? 0),
    hint: '仅统计已完成订单实付',
    tone: 'is-brand'
  },
  {
    key: 'avg',
    label: '客单价',
    value: formatCurrency(summary.value.avgOrderAmount ?? 0),
    hint: '营业额 / 完成单量'
  },
  {
    key: 'pending',
    label: '待接单',
    value: String(summary.value.pendingAcceptCount ?? 0),
    hint: '点击进入接单看板',
    to: { path: '/merchant/orders', query: { status: 'PAID' } },
    tone: summary.value.pendingAcceptCount > 0 ? 'is-warning' : ''
  },
  {
    key: 'making',
    label: '制作中',
    value: String(summary.value.makingCount ?? 0),
    hint: '含外卖与堂食',
    to: { path: '/merchant/orders', query: { status: 'MAKING' } }
  },
  {
    key: 'completed',
    label: '今日完成',
    value: String(summary.value.completedCount ?? 0),
    hint: 'DELIVERING/SERVED → COMPLETED'
  },
  {
    key: 'cancel',
    label: '取消 / 退款',
    value: `${summary.value.cancelCount ?? 0} / ${formatAmount(summary.value.refundAmount ?? 0)}`,
    hint: '取消单量与模拟退款金额'
  },
  {
    key: 'lowStock',
    label: '库存预警',
    value: String(summary.value.lowStockCount ?? 0),
    hint: '库存 ≤ 5 的菜品',
    to: { path: '/merchant/dishes', query: { lowStock: '1' } },
    tone: summary.value.lowStockCount > 0 ? 'is-danger' : ''
  }
])

const trendOption = computed(() => {
  const points = trend.value?.points || []
  return {
    tooltip: { trigger: 'axis' },
    legend: { show: true, data: ['营业额', '订单量'] },
    grid: { left: 56, right: 48, top: 44, bottom: 32 },
    xAxis: { type: 'category', data: points.map((point) => shortDate(point.date)) },
    yAxis: [
      { type: 'value', name: '营业额(元)', axisLabel: { formatter: (value) => `${value}` } },
      { type: 'value', name: '订单量', minInterval: 1 }
    ],
    series: [
      {
        name: '营业额',
        type: 'line',
        smooth: true,
        symbolSize: 6,
        data: points.map((point) => Number(point.revenue || 0)),
        areaStyle: { opacity: 0.12 }
      },
      {
        name: '订单量',
        type: 'bar',
        yAxisIndex: 1,
        barWidth: '38%',
        data: points.map((point) => Number(point.orderCount || 0))
      }
    ]
  }
})

const hotOption = computed(() => {
  const items = [...(hot.value?.items || [])].slice(0, 5).reverse()
  return {
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { left: 96, right: 32, top: 16, bottom: 24 },
    xAxis: { type: 'value', name: '销量', minInterval: 1 },
    yAxis: { type: 'category', data: items.map((item) => item.dishName) },
    series: [
      {
        name: '销量',
        type: 'bar',
        barWidth: '52%',
        data: items.map((item) => Number(item.quantity || 0)),
        label: { show: true, position: 'right' }
      }
    ]
  }
})

const ratioOption = computed(() => {
  const items = ratio.value?.items || []
  const labelOfType = (type) => (type === ORDER_TYPE.DINE_IN ? '堂食' : '外卖')
  return {
    tooltip: {
      trigger: 'item',
      formatter: (params) =>
        `${params.name}<br/>订单 ${params.value} 单 · ${params.percent}%<br/>金额 ¥${formatAmount(
          params.data.amount
        )}`
    },
    legend: { bottom: 0 },
    series: [
      {
        name: '订单类型',
        type: 'pie',
        radius: ['46%', '68%'],
        center: ['50%', '44%'],
        itemStyle: { borderRadius: 8, borderColor: '#fff', borderWidth: 2 },
        label: { formatter: '{b}\n{d}%' },
        data: items.map((item) => ({
          name: labelOfType(item.orderType),
          value: Number(item.orderCount || 0),
          amount: item.orderAmount || 0
        }))
      }
    ]
  }
})

async function loadCharts() {
  chartsLoading.value = true
  try {
    const params = { startDate: range[0], endDate: range[1] }
    const [trendData, hotData, ratioData] = await Promise.all([
      getDashboardTrend(params),
      getHotDishes({ ...params, limit: 5 }),
      getOrderTypeRatio(params)
    ])
    trend.value = trendData
    hot.value = hotData
    ratio.value = ratioData
  } catch (err) {
    ElMessage.warning(err?.message || '图表数据加载失败')
  } finally {
    chartsLoading.value = false
  }
}

async function loadAll() {
  loading.value = true
  error.value = ''
  try {
    await Promise.all([merchant.loadSummary(), loadCharts()])
  } catch (err) {
    error.value = err?.message || '看板数据加载失败'
  } finally {
    loading.value = false
  }
}

async function toggleBusiness(next) {
  try {
    await merchant.setBusinessStatus(next)
    ElMessage.success(next === 'OPEN' ? '已开始营业，可接收新订单' : '已暂停营业，新订单将被拦截')
  } catch (err) {
    ElMessage.error(err?.message || '营业状态切换失败')
    await merchant.loadBusinessStatus()
  }
}

async function handleStatusChange(value) {
  const next = value ? 'OPEN' : 'CLOSED'
  if (next === 'CLOSED') {
    try {
      await ElMessageBox.confirm('暂停营业后顾客将无法下单（40908），已有订单仍可正常流转。', '暂停营业', {
        confirmButtonText: '暂停营业',
        cancelButtonText: '取消',
        type: 'warning'
      })
    } catch {
      return
    }
  }
  await toggleBusiness(next)
}

function goTo(metric) {
  if (metric?.to) router.push(metric.to)
}

let pollTimer = null

function startPolling() {
  pollTimer = window.setInterval(() => {
    if (document.visibilityState === 'visible') {
      merchant.loadSummary().catch(() => {})
    }
  }, 60000)
}

watch(
  () => merchant.lastEvent,
  (event) => {
    const payload = event?.payload
    if (!payload) return
    if (payload.eventType === WS_EVENT_TYPE.ORDER_CREATED) {
      merchant.loadSummary().catch(() => {})
      loadCharts()
    }
  }
)

onMounted(async () => {
  await merchant.loadProfile()
  await merchant.loadBusinessStatus()
  await loadAll()
  startPolling()
})

onBeforeUnmount(() => {
  if (pollTimer) window.clearInterval(pollTimer)
  pollTimer = null
})
</script>

<template>
  <div class="dashboard">
    <div class="page-header">
      <div>
        <h1 class="page-title">工作台</h1>
        <p class="page-subtitle">
          {{ merchant.storeName }} · 统计日期 {{ todayString() }}（Asia/Shanghai，仅统计已完成订单）
        </p>
      </div>
      <div class="dashboard__actions">
        <el-switch
          :model-value="merchant.isOpen"
          :loading="merchant.statusSaving"
          active-text="营业中"
          inactive-text="休息中"
          inline-prompt
          size="large"
          @change="handleStatusChange"
        />
        <el-button :loading="loading" @click="loadAll">
          <el-icon><Refresh /></el-icon>
          刷新
        </el-button>
      </div>
    </div>

    <el-alert
      v-if="error"
      type="error"
      :title="error"
      :closable="false"
      show-icon
    >
      <el-button size="small" text type="primary" @click="loadAll">重试</el-button>
    </el-alert>

    <el-alert
      v-if="!merchant.isApproved"
      type="warning"
      :title="`店铺审核状态：${auditLabel}，审核通过前无法接收订单`"
      :closable="false"
      show-icon
    />

    <el-skeleton v-if="loading && !merchant.summary" :rows="3" animated />

    <template v-else>
      <div class="metric-grid">
        <button
          v-for="metric in metrics"
          :key="metric.key"
          type="button"
          class="metric-card"
          :class="[metric.tone, { 'is-clickable': metric.to }]"
          @click="goTo(metric)"
        >
          <span class="metric-card__label">{{ metric.label }}</span>
          <span class="metric-card__value">{{ metric.value }}</span>
          <span class="metric-card__hint">{{ metric.hint }}</span>
        </button>
      </div>

      <div class="chart-grid">
        <section class="surface-card chart-card chart-card--wide">
          <header class="chart-card__head">
            <div>
              <h2 class="chart-card__title">营业额与订单量趋势</h2>
              <p class="chart-card__desc">近 7 天 · 营业额取 orders.pay_amount</p>
            </div>
            <router-link to="/merchant/stats" class="chart-card__link">报表中心</router-link>
          </header>
          <EChartPanel :option="trendOption" :height="320" :loading="chartsLoading" />
        </section>

        <section class="surface-card chart-card">
          <header class="chart-card__head">
            <div>
              <h2 class="chart-card__title">订单类型占比</h2>
              <p class="chart-card__desc">近 7 天外卖 / 堂食</p>
            </div>
          </header>
          <EChartPanel :option="ratioOption" :height="320" :loading="chartsLoading" />
        </section>

        <section class="surface-card chart-card chart-card--wide">
          <header class="chart-card__head">
            <div>
              <h2 class="chart-card__title">热销菜品 Top 5</h2>
              <p class="chart-card__desc">近 7 天完成订单口径</p>
            </div>
            <router-link to="/merchant/dishes" class="chart-card__link">菜品管理</router-link>
          </header>
          <EChartPanel :option="hotOption" :height="300" :loading="chartsLoading" />
        </section>

        <section class="surface-card chart-card">
          <header class="chart-card__head">
            <div>
              <h2 class="chart-card__title">待处理订单</h2>
              <p class="chart-card__desc">接单 / 制作 / 出餐快捷入口</p>
            </div>
          </header>
          <div class="quick-links">
            <router-link class="quick-link" to="/merchant/orders?status=PAID">
              <span class="quick-link__label">外卖待接单</span>
              <span class="quick-link__value">{{ summary.pendingAcceptCount ?? 0 }}</span>
              <el-icon><ArrowRight /></el-icon>
            </router-link>
            <router-link class="quick-link" to="/merchant/orders?status=MAKING">
              <span class="quick-link__label">外卖制作中</span>
              <span class="quick-link__value">{{ summary.makingCount ?? 0 }}</span>
              <el-icon><ArrowRight /></el-icon>
            </router-link>
            <router-link class="quick-link" to="/merchant/dine-in">
              <span class="quick-link__label">堂食订单看板</span>
              <span class="quick-link__value"><el-icon><Tickets /></el-icon></span>
              <el-icon><ArrowRight /></el-icon>
            </router-link>
            <router-link class="quick-link" to="/merchant/tables">
              <span class="quick-link__label">桌台与二维码</span>
              <span class="quick-link__value"><el-icon><Grid /></el-icon></span>
              <el-icon><ArrowRight /></el-icon>
            </router-link>
          </div>
        </section>
      </div>
    </template>
  </div>
</template>

<style scoped>
.dashboard {
  display: flex;
  flex-direction: column;
  gap: var(--layout-gap);
}

.dashboard__actions {
  display: flex;
  align-items: center;
  gap: var(--space-3);
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: var(--space-4);
}

.metric-card {
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
  padding: var(--space-5);
  border: 1px solid var(--color-border-subtle);
  border-radius: var(--radius-lg);
  background: var(--color-surface);
  box-shadow: var(--shadow-xs);
  text-align: left;
  color: inherit;
  font: inherit;
  transition: transform var(--duration-fast) var(--ease-standard),
    box-shadow var(--duration-fast) var(--ease-standard), border-color var(--duration-fast);
}

.metric-card.is-clickable {
  cursor: pointer;
}

.metric-card.is-clickable:hover {
  transform: translateY(-2px);
  border-color: var(--color-border);
  box-shadow: var(--shadow-sm);
}

.metric-card__label {
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
}

.metric-card__value {
  font-family: var(--font-family-number);
  font-size: var(--font-size-2xl);
  font-weight: var(--font-weight-semibold);
  font-variant-numeric: tabular-nums;
  letter-spacing: -0.01em;
}

.metric-card.is-brand .metric-card__value {
  color: var(--color-primary);
}

.metric-card.is-warning .metric-card__value {
  color: var(--color-warning);
}

.metric-card.is-danger .metric-card__value {
  color: var(--color-danger);
}

.metric-card__hint {
  color: var(--color-text-tertiary);
  font-size: var(--font-size-xs);
}

.chart-grid {
  display: grid;
  grid-template-columns: minmax(0, 2fr) minmax(0, 1fr);
  gap: var(--space-4);
}

.chart-card {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}

.chart-card--wide {
  grid-column: span 1;
}

.chart-card__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-3);
}

.chart-card__title {
  font-size: var(--font-size-lg);
  font-weight: var(--font-weight-semibold);
}

.chart-card__desc {
  margin-top: var(--space-1);
  color: var(--color-text-tertiary);
  font-size: var(--font-size-xs);
}

.chart-card__link {
  color: var(--color-primary);
  font-size: var(--font-size-sm);
  white-space: nowrap;
}

.quick-links {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.quick-link {
  display: grid;
  grid-template-columns: 1fr auto auto;
  gap: var(--space-3);
  align-items: center;
  padding: var(--space-3) var(--space-4);
  border: 1px solid var(--color-border-subtle);
  border-radius: var(--radius-md);
  background: var(--color-bg-subtle);
  color: var(--color-text-primary);
  transition: border-color var(--duration-fast), background var(--duration-fast);
}

.quick-link:hover {
  border-color: var(--color-primary);
  background: var(--color-brand-50);
}

.quick-link__label {
  font-size: var(--font-size-sm);
}

.quick-link__value {
  color: var(--color-primary);
  font-family: var(--font-family-number);
  font-weight: var(--font-weight-semibold);
}

@media (max-width: 1200px) {
  .metric-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .chart-grid {
    grid-template-columns: 1fr;
  }
}
</style>
