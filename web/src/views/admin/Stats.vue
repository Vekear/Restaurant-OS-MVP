<script setup>
/**
 * 管理后台 · 数据统计（docs/02-api.md §20.15–20.19）
 * - 区间：默认近 30 天，最长 92 天
 * - 概览卡 + 平台趋势 / 订单类型占比 / 全平台热销 Top10
 * - 导出：/admin/stats/export（xlsx，5 个 sheet）
 * - 金额口径：orders.pay_amount，仅统计已完成订单
 */
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  exportStats,
  getStatsHotDishes,
  getStatsOrderTypeRatio,
  getStatsOverview,
  getStatsTrend
} from '@/api/admin'
import { ORDER_TYPE } from '@/utils/constants'
import { formatAmount, formatCurrency } from '@/utils/format'
import {
  RANGE_PRESETS,
  formatRangeText,
  isRangeWithin,
  recentRange,
  shortDate,
  todayString
} from '@/utils/dateRange'
import EChartPanel from '@/components/EChartPanel.vue'

const range = ref(recentRange(30))
const overview = ref(null)
const trend = ref(null)
const ratio = ref(null)
const hot = ref(null)
const loading = ref(false)
const exporting = ref(false)

const shortcuts = RANGE_PRESETS.map((preset) => ({
  text: preset.label,
  value: () => recentRange(preset.days)
}))

const rangeText = computed(() => formatRangeText(range.value))
const points = computed(() => trend.value?.points || [])
const hotItems = computed(() => hot.value?.items || [])

const overviewCards = computed(() => {
  const data = overview.value || {}
  return [
    { label: '区间成交额', value: formatCurrency(data.revenue), hint: '订单实付金额合计', brand: true },
    { label: '订单量', value: Number(data.orderCount || 0), hint: '仅已完成订单' },
    { label: '客单价', value: formatCurrency(data.avgOrderAmount), hint: '成交额 / 订单量' },
    { label: '退款金额', value: formatCurrency(data.refundAmount), hint: '取消 / 退款订单' },
    { label: '平台用户数', value: Number(data.userCount || 0), hint: '全部注册用户' },
    { label: '区间新增用户', value: Number(data.newUserCount || 0), hint: '注册时间落在区间内' },
    { label: '已审核商家', value: Number(data.merchantCount || 0), hint: 'auditStatus=APPROVED' }
  ]
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

async function load() {
  if (!validateRange()) return
  loading.value = true
  try {
    const query = params()
    const [overviewData, trendData, ratioData, hotData] = await Promise.all([
      getStatsOverview(query),
      getStatsTrend(query),
      getStatsOrderTypeRatio(query),
      getStatsHotDishes({ ...query, limit: 10 })
    ])
    overview.value = overviewData
    trend.value = trendData
    ratio.value = ratioData
    hot.value = hotData
  } catch (error) {
    ElMessage.error(error?.message || '统计数据加载失败')
  } finally {
    loading.value = false
  }
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

const trendOption = computed(() => ({
  tooltip: { trigger: 'axis' },
  legend: { data: ['成交额', '订单量'] },
  grid: { left: 64, right: 52, top: 44, bottom: 40 },
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
      barWidth: '36%',
      data: points.value.map((point) => Number(point.orderCount || 0))
    }
  ]
}))

const ratioOption = computed(() => {
  const items = ratio.value?.items || []
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
        radius: ['46%', '70%'],
        center: ['50%', '44%'],
        itemStyle: { borderRadius: 8, borderColor: '#fff', borderWidth: 2 },
        label: { formatter: '{b}\n{d}%' },
        data: items.map((item) => ({
          name: item.orderType === ORDER_TYPE.DINE_IN ? '堂食' : '外卖',
          value: Number(item.orderCount || 0),
          amount: item.orderAmount || 0
        }))
      }
    ]
  }
})

const hotOption = computed(() => {
  const items = [...hotItems.value].reverse()
  return {
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { left: 130, right: 64, top: 20, bottom: 28 },
    xAxis: { type: 'value', name: '销量', minInterval: 1 },
    yAxis: { type: 'category', data: items.map((item) => item.dishName) },
    series: [
      {
        name: '销量',
        type: 'bar',
        barWidth: '55%',
        data: items.map((item) => Number(item.quantity || 0)),
        label: { show: true, position: 'right' }
      }
    ]
  }
})

onMounted(load)
</script>

<template>
  <div class="admin-stats">
    <header class="page-header">
      <div>
        <h1 class="page-title">数据统计</h1>
        <p class="page-subtitle">
          统计区间 {{ rangeText }} · 金额以订单实付（pay_amount）为准，仅统计已完成订单
        </p>
      </div>
      <el-button type="primary" :loading="exporting" @click="doExport">导出平台报表</el-button>
    </header>

    <div class="surface-card stats__filters">
      <span class="stats__label">统计区间</span>
      <el-date-picker
        v-model="range"
        type="daterange"
        value-format="YYYY-MM-DD"
        range-separator="→"
        start-placeholder="开始日期"
        end-placeholder="结束日期"
        :shortcuts="shortcuts"
        :disabled-date="disabledDate"
        :clearable="false"
        class="stats__picker"
      />
      <el-button :loading="loading" @click="load">查询</el-button>
      <span class="stats__hint">最长 92 天 · 今天 {{ todayString() }}</span>
    </div>

    <div class="overview-grid">
      <div v-for="card in overviewCards" :key="card.label" class="overview-card">
        <span class="overview-card__label">{{ card.label }}</span>
        <span class="overview-card__value" :class="{ 'is-brand': card.brand }">{{ card.value }}</span>
        <span class="overview-card__hint">{{ card.hint }}</span>
      </div>
    </div>

    <section class="surface-card chart-card">
      <header class="chart-card__head">
        <div>
          <h2 class="chart-card__title">平台成交额与订单量趋势</h2>
          <p class="chart-card__desc">按自然日聚合，金额单位元</p>
        </div>
      </header>
      <EChartPanel :option="trendOption" :height="340" :loading="loading" loading-text="统计加载中" />
    </section>

    <div class="chart-row">
      <section class="surface-card chart-card">
        <header class="chart-card__head">
          <div>
            <h2 class="chart-card__title">全平台热销菜品 Top 10</h2>
            <p class="chart-card__desc">跨门店同名菜品合并统计</p>
          </div>
        </header>
        <EChartPanel :option="hotOption" :height="340" :loading="loading" loading-text="统计加载中" />
      </section>

      <section class="surface-card chart-card">
        <header class="chart-card__head">
          <div>
            <h2 class="chart-card__title">订单类型占比</h2>
            <p class="chart-card__desc">外卖 / 堂食订单量与金额</p>
          </div>
        </header>
        <EChartPanel :option="ratioOption" :height="340" :loading="loading" loading-text="统计加载中" />
      </section>
    </div>
  </div>
</template>

<style scoped>
.admin-stats {
  display: flex;
  flex-direction: column;
  gap: var(--layout-gap);
}

.stats__filters {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-4) var(--space-5);
}

.stats__label {
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
}

.stats__picker {
  width: 280px;
}

.stats__hint {
  margin-left: auto;
  color: var(--color-text-tertiary);
  font-size: var(--font-size-xs);
}

.overview-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: var(--space-4);
}

.overview-card {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  padding: var(--space-5);
  border: 1px solid var(--color-border-subtle);
  border-radius: var(--radius-lg);
  background: var(--color-surface);
  box-shadow: var(--shadow-xs);
}

.overview-card__label {
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
}

.overview-card__value {
  font-family: var(--font-family-number);
  font-size: var(--font-size-2xl);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-primary);
}

.overview-card__value.is-brand {
  color: var(--color-brand-600);
}

.overview-card__hint {
  color: var(--color-text-tertiary);
  font-size: var(--font-size-xs);
}

.chart-row {
  display: grid;
  grid-template-columns: 1.1fr 0.9fr;
  gap: var(--layout-gap);
}

.chart-card {
  padding: var(--space-5);
}

.chart-card__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-4);
  margin-bottom: var(--space-4);
}

.chart-card__title {
  font-size: var(--font-size-lg);
}

.chart-card__desc {
  margin-top: var(--space-1);
  color: var(--color-text-tertiary);
  font-size: var(--font-size-xs);
}

@media (max-width: 1080px) {
  .chart-row {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 720px) {
  .stats__hint {
    margin-left: 0;
  }
}
</style>
