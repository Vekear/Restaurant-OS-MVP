<script setup>
/**
 * 管理后台 · 平台概览（docs/02-api.md §20.15–20.19）
 * - 区间默认近 30 天（≤92 天），统计仅 COMPLETED、金额取 orders.pay_amount（Asia/Shanghai 自然日）
 * - 卡片：成交额 / 订单数 / 客单价 / 退款额 / 用户总数 / 新增用户 / 已审核商家
 * - 图表：营业额与订单量趋势、订单类型占比、热销菜品 Top10
 * - 60s 兜底轮询；导出平台报表（5 个 sheet）
 */
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  exportStats,
  getStatsHotDishes,
  getStatsOrderTypeRatio,
  getStatsOverview,
  getStatsTrend
} from '@/api/admin'
import { formatAmount, formatCurrency } from '@/utils/format'
import { RANGE_PRESETS, formatRangeText, isRangeWithin, recentRange, shortDate } from '@/utils/dateRange'
import { ORDER_TYPE } from '@/utils/constants'
import EChartPanel from '@/components/EChartPanel.vue'

const router = useRouter()

const range = ref(recentRange(30))
const overview = ref(null)
const trend = ref(null)
const ratio = ref(null)
const hot = ref(null)
const loading = ref(false)
const chartsLoading = ref(false)
const exporting = ref(false)

let timer = null

const shortcuts = RANGE_PRESETS.map((preset) => ({
  text: preset.label,
  value: () => recentRange(preset.days)
}))

const rangeText = computed(() => formatRangeText(range.value))
const points = computed(() => trend.value?.points || [])

const metrics = computed(() => {
  const data = overview.value || {}
  return [
    {
      key: 'revenue',
      label: '成交额',
      value: formatCurrency(data.revenue ?? 0),
      hint: '已完成订单实付合计',
      tone: 'is-brand'
    },
    { key: 'orders', label: '订单量', value: String(data.orderCount ?? 0), hint: '已完成订单数' },
    {
      key: 'avg',
      label: '客单价',
      value: formatCurrency(data.avgOrderAmount ?? 0),
      hint: '成交额 / 订单量'
    },
    {
      key: 'refund',
      label: '退款额',
      value: formatCurrency(data.refundAmount ?? 0),
      hint: '被退款订单实付金额',
      tone: Number(data.refundAmount || 0) > 0 ? 'is-danger' : ''
    },
    { key: 'users', label: '用户总数', value: String(data.userCount ?? 0), hint: '全部注册账号' },
    {
      key: 'newUsers',
      label: '新增用户',
      value: String(data.newUserCount ?? 0),
      hint: '区间内注册',
      tone: Number(data.newUserCount || 0) > 0 ? 'is-success' : ''
    },
    { key: 'merchants', label: '已审核商家', value: String(data.merchantCount ?? 0), hint: 'auditStatus=APPROVED' }
  ]
})

const quickLinks = [
  { label: '用户管理', desc: '检索与启用/禁用账号', to: '/admin/users' },
  { label: '商家审核', desc: '入驻审核与停启用', to: '/admin/merchants' },
  { label: '订单查询', desc: '全平台订单追溯与导出', to: '/admin/orders' },
  { label: '优惠券', desc: '券模板 CRUD 与状态', to: '/admin/coupons' },
  { label: '审计日志', desc: '关键操作留痕检索', to: '/admin/audit' },
  { label: '数据报表', desc: '趋势、结构与导出', to: '/admin/stats' }
]

const trendOption = computed(() => ({
  tooltip: { trigger: 'axis' },
  legend: { show: true, data: ['成交额', '订单量'] },
  grid: { left: 60, right: 48, top: 44, bottom: 32 },
  xAxis: { type: 'category', data: points.value.map((point) => shortDate(point.date)) },
  yAxis: [
    { type: 'value', name: '成交额(元)' },
    { type: 'value', name: '订单量', minInterval: 1 }
  ],
  series: [
    {
      name: '成交额',
      type: 'line',
      smooth: true,
      symbolSize: 6,
      data: points.value.map((point) => Number(point.revenue || 0)),
      areaStyle: { opacity: 0.12 }
    },
    {
      name: '订单量',
      type: 'bar',
      yAxisIndex: 1,
      barWidth: '38%',
      data: points.value.map((point) => Number(point.orderCount || 0))
    }
  ]
}))

const ratioOption = computed(() => {
  const labelOfType = (type) => (type === ORDER_TYPE.DINE_IN ? '堂食' : '外卖')
  return {
    tooltip: {
      trigger: 'item',
      formatter: (params) =>
        `${params.name}<br/>订单 ${params.value} 单 · ${params.percent}%<br/>金额 ¥${formatAmount(
          params.data.amount
        )}`
    },
    legend: { bottom: 0, icon: 'circle' },
    series: [
      {
        type: 'pie',
        radius: ['46%', '70%'],
        center: ['50%', '46%'],
        itemStyle: { borderRadius: 8, borderColor: '#fff', borderWidth: 2 },
        label: { formatter: '{b}\n{d}%' },
        data: (ratio.value?.items || []).map((item) => ({
          name: labelOfType(item.orderType),
          value: Number(item.orderCount || 0),
          amount: Number(item.orderAmount || 0)
        }))
      }
    ]
  }
})

const hotOption = computed(() => {
  const items = [...(hot.value?.items || [])].slice(0, 10).reverse()
  return {
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { left: 118, right: 36, top: 16, bottom: 24 },
    xAxis: { type: 'value', name: '销量', minInterval: 1 },
    yAxis: { type: 'category', data: items.map((item) => item.dishName) },
    series: [
      {
        name: '销量',
        type: 'bar',
        barWidth: '48%',
        data: items.map((item) => Number(item.quantity || 0)),
        label: { show: true, position: 'right' }
      }
    ]
  }
})

function disabledDate(date) {
  return date.getTime() > Date.now() + 24 * 3600 * 1000
}

function validateRange() {
  if (!Array.isArray(range.value) || range.value.length !== 2) {
    ElMessage.warning('请选择完整的日期区间')
    return false
  }
  if (!isRangeWithin(range.value, 92)) {
    ElMessage.warning('统计区间最长 92 天，请缩小范围')
    return false
  }
  return true
}

function params() {
  return { startDate: range.value[0], endDate: range.value[1] }
}

async function loadOverview() {
  if (!validateRange()) return
  loading.value = true
  try {
    overview.value = await getStatsOverview(params())
  } catch (error) {
    ElMessage.error(error?.message || '平台指标加载失败')
  } finally {
    loading.value = false
  }
}

async function loadCharts() {
  if (!validateRange()) return
  chartsLoading.value = true
  try {
    const query = params()
    const [trendData, ratioData, hotData] = await Promise.all([
      getStatsTrend(query),
      getStatsOrderTypeRatio(query),
      getStatsHotDishes({ ...query, limit: 10 })
    ])
    trend.value = trendData
    ratio.value = ratioData
    hot.value = hotData
  } catch (error) {
    ElMessage.error(error?.message || '图表数据加载失败')
  } finally {
    chartsLoading.value = false
  }
}

async function refresh() {
  await Promise.all([loadOverview(), loadCharts()])
}

async function doExport() {
  if (!validateRange()) return
  exporting.value = true
  try {
    await exportStats(params())
    ElMessage.success('平台报表已开始下载（xlsx，5 个 sheet）')
  } catch (error) {
    ElMessage.error(error?.message || '导出失败，请稍后重试')
  } finally {
    exporting.value = false
  }
}

onMounted(() => {
  refresh()
  timer = window.setInterval(() => {
    if (document.visibilityState === 'visible') refresh()
  }, 60 * 1000)
})

onBeforeUnmount(() => {
  if (timer) window.clearInterval(timer)
  timer = null
})
</script>

<template>
  <div class="page admin-dashboard">
    <header class="page-header">
      <div>
        <h1 class="page-title">平台概览</h1>
        <p class="page-subtitle">全平台成交、用户与商家结构 · {{ rangeText }}</p>
      </div>
      <div class="page-header__actions">
        <el-date-picker
          v-model="range"
          type="daterange"
          :shortcuts="shortcuts"
          :disabled-date="disabledDate"
          value-format="YYYY-MM-DD"
          range-separator="至"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          class="range-picker"
          @change="refresh"
        />
        <el-button :loading="loading || chartsLoading" @click="refresh">刷新</el-button>
        <el-button type="primary" :loading="exporting" @click="doExport">导出平台报表</el-button>
      </div>
    </header>

    <section class="metric-grid">
      <div v-for="metric in metrics" :key="metric.key" class="metric-card" :class="metric.tone">
        <p class="metric-card__label">{{ metric.label }}</p>
        <p class="metric-card__value">{{ metric.value }}</p>
        <p class="metric-card__hint">{{ metric.hint }}</p>
      </div>
    </section>

    <section class="chart-grid">
      <div class="panel panel--wide">
        <div class="panel__head">
          <h3 class="panel__title">成交额与订单趋势</h3>
          <span class="panel__hint">自然日口径 · 仅已完成订单</span>
        </div>
        <EChartPanel :option="trendOption" :loading="chartsLoading" height="320px" />
      </div>
      <div class="panel">
        <div class="panel__head">
          <h3 class="panel__title">订单类型占比</h3>
        </div>
        <EChartPanel :option="ratioOption" :loading="chartsLoading" height="320px" />
      </div>
      <div class="panel panel--wide">
        <div class="panel__head">
          <h3 class="panel__title">热销菜品 Top 10</h3>
          <span class="panel__hint">跨店聚合 · 同名合并</span>
        </div>
        <EChartPanel :option="hotOption" :loading="chartsLoading" height="340px" />
      </div>
      <div class="panel">
        <div class="panel__head">
          <h3 class="panel__title">快捷入口</h3>
        </div>
        <div class="quick-list">
          <button
            v-for="link in quickLinks"
            :key="link.to"
            type="button"
            class="quick-item"
            @click="router.push(link.to)"
          >
            <span class="quick-item__label">{{ link.label }}</span>
            <span class="quick-item__desc">{{ link.desc }}</span>
          </button>
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
.page-header__actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-3);
}

.range-picker {
  max-width: 300px;
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: var(--space-4);
  margin-top: var(--space-5);
}

.metric-card {
  padding: var(--space-4) var(--space-5);
  border: 1px solid var(--color-border-subtle);
  border-radius: var(--radius-lg);
  background: var(--color-surface);
  box-shadow: var(--shadow-xs);
}

.metric-card.is-brand {
  border-color: var(--color-primary);
  background: linear-gradient(135deg, var(--color-brand-50), var(--color-surface));
}

.metric-card.is-danger {
  border-color: var(--color-danger);
}

.metric-card.is-success {
  border-color: var(--color-success);
}

.metric-card__label {
  color: var(--color-text-secondary);
  font-size: var(--font-size-xs);
}

.metric-card__value {
  margin-top: var(--space-2);
  font-family: var(--font-family-number);
  font-size: var(--font-size-2xl);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-primary);
}

.metric-card__hint {
  margin-top: var(--space-1);
  color: var(--color-text-tertiary);
  font-size: var(--font-size-xs);
}

.chart-grid {
  display: grid;
  grid-template-columns: 2fr 1fr;
  gap: var(--space-5);
  margin-top: var(--space-6);
}

.panel {
  padding: var(--space-5);
  border: 1px solid var(--color-border-subtle);
  border-radius: var(--radius-lg);
  background: var(--color-surface);
}

.panel--wide {
  min-width: 0;
}

.panel__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--space-3);
  margin-bottom: var(--space-3);
}

.panel__title {
  font-size: var(--font-size-md);
  font-weight: var(--font-weight-semibold);
}

.panel__hint {
  color: var(--color-text-tertiary);
  font-size: var(--font-size-xs);
}

.quick-list {
  display: grid;
  grid-template-columns: 1fr;
  gap: var(--space-2);
}

.quick-item {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: var(--space-3) var(--space-4);
  border: 1px solid var(--color-border-subtle);
  border-radius: var(--radius-md);
  background: var(--color-bg-subtle);
  text-align: left;
  cursor: pointer;
  transition: border-color var(--duration-fast) var(--ease-standard);
}

.quick-item:hover {
  border-color: var(--color-primary);
}

.quick-item__label {
  color: var(--color-text-primary);
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-medium);
}

.quick-item__desc {
  color: var(--color-text-tertiary);
  font-size: var(--font-size-xs);
}

@media (max-width: 1080px) {
  .chart-grid {
    grid-template-columns: 1fr;
  }
}
</style>
