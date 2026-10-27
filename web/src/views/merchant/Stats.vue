<script setup>
/**
 * 商家营业报表（docs/02-api.md §15、§19）
 * - 区间选择（≤92 天，默认近 7 天），趋势 / 热销 / 订单类型三类图表均取真实接口
 * - Excel 导出：订单明细（14 列，含实付与模拟扣款金额）、营业报表（4 个 sheet）
 * - 金额口径：orders.pay_amount；取消/退款不计入营业额
 */
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { exportOrders, exportSales, getDashboardTrend, getHotDishes, getOrderTypeRatio } from '@/api/merchant'
import { useMerchantStore } from '@/stores/merchant'
import { formatAmount, formatCurrency } from '@/utils/format'
import {
  RANGE_PRESETS,
  formatRangeText,
  isRangeWithin,
  recentRange,
  shortDate,
  todayString
} from '@/utils/dateRange'
import { ORDER_STATUS_LABELS, ORDER_TYPE } from '@/utils/constants'
import EChartPanel from '@/components/EChartPanel.vue'

const merchant = useMerchantStore()

const range = ref(recentRange(7))
const trend = ref(null)
const hot = ref(null)
const ratio = ref(null)
const loading = ref(false)
const exporting = ref('')

const exportStatus = ref('')

const shortcuts = RANGE_PRESETS.map((preset) => ({
  text: preset.label,
  value: () => recentRange(preset.days)
}))

const statusOptions = Object.entries(ORDER_STATUS_LABELS).map(([value, label]) => ({ value, label }))

const rangeText = computed(() => formatRangeText(range.value))
const points = computed(() => trend.value?.points || [])
const totalOrderCount = computed(() => trend.value?.totalOrderCount ?? 0)
const totalRevenue = computed(() => trend.value?.totalRevenue ?? 0)
const avgOrderAmount = computed(() =>
  totalOrderCount.value > 0 ? totalRevenue.value / totalOrderCount.value : 0
)

function disabledDate(date) {
  return date.getTime() > Date.now() + 24 * 3600 * 1000
}

function validateRange() {
  if (!Array.isArray(range.value) || range.value.length !== 2) {
    ElMessage.warning('请选择完整的日期区间')
    return false
  }
  if (!isRangeWithin(range.value, 92)) {
    ElMessage.warning('报表区间最长 92 天，请缩小范围')
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
    const [trendData, hotData, ratioData] = await Promise.all([
      getDashboardTrend(query),
      getHotDishes({ ...query, limit: 8 }),
      getOrderTypeRatio(query)
    ])
    trend.value = trendData
    hot.value = hotData
    ratio.value = ratioData
  } catch (error) {
    ElMessage.error(error?.message || '报表数据加载失败')
  } finally {
    loading.value = false
  }
}

async function doExport(type) {
  if (!validateRange()) return
  exporting.value = type
  try {
    if (type === 'orders') {
      const query = { ...params() }
      if (exportStatus.value) query.status = exportStatus.value
      await exportOrders(query)
      ElMessage.success('订单明细已开始下载（xlsx）')
    } else {
      await exportSales(params())
      ElMessage.success('营业报表已开始下载（xlsx，4 个 sheet）')
    }
  } catch (error) {
    ElMessage.error(error?.message || '导出失败，请稍后重试')
  } finally {
    exporting.value = ''
  }
}

const trendOption = computed(() => ({
  tooltip: { trigger: 'axis' },
  legend: { data: ['营业额', '订单量'] },
  grid: { left: 60, right: 52, top: 44, bottom: 40 },
  xAxis: { type: 'category', data: points.value.map((point) => shortDate(point.date)) },
  yAxis: [
    { type: 'value', name: '营业额(元)' },
    { type: 'value', name: '订单量', minInterval: 1 }
  ],
  series: [
    {
      name: '营业额',
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

const hotOption = computed(() => {
  const items = [...(hot.value?.items || [])].reverse()
  return {
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { left: 110, right: 60, top: 20, bottom: 28 },
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

onMounted(async () => {
  await merchant.loadProfile()
  await load()
})
</script>

<template>
  <div class="stats">
    <div class="page-header">
      <div>
        <h1 class="page-title">营业报表</h1>
        <p class="page-subtitle">
          统计区间 {{ rangeText }} · 金额以订单实付（pay_amount）为准，模拟支付仅扣 0.01 元
        </p>
      </div>
      <div class="stats__actions">
        <el-select v-model="exportStatus" placeholder="订单状态（默认全部）" clearable class="export-select">
          <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
        <el-button :loading="exporting === 'orders'" @click="doExport('orders')">
          <el-icon><Download /></el-icon>
          导出订单明细
        </el-button>
        <el-button type="primary" :loading="exporting === 'sales'" @click="doExport('sales')">
          <el-icon><Files /></el-icon>
          导出营业报表
        </el-button>
      </div>
    </div>

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
      <el-button :loading="loading" @click="load">
        <el-icon><Search /></el-icon>
        查询
      </el-button>
      <span class="stats__hint">最长 92 天 · 今天 {{ todayString() }}</span>
    </div>

    <div class="summary-grid">
      <div class="summary-card">
        <span class="summary-card__label">区间订单数</span>
        <span class="summary-card__value">{{ totalOrderCount }}</span>
        <span class="summary-card__hint">仅统计已完成订单</span>
      </div>
      <div class="summary-card">
        <span class="summary-card__label">区间营业额</span>
        <span class="summary-card__value is-brand">{{ formatCurrency(totalRevenue) }}</span>
        <span class="summary-card__hint">取消 / 退款不计入</span>
      </div>
      <div class="summary-card">
        <span class="summary-card__label">客单价</span>
        <span class="summary-card__value">{{ formatCurrency(avgOrderAmount) }}</span>
        <span class="summary-card__hint">营业额 / 完成单量</span>
      </div>
      <div class="summary-card">
        <span class="summary-card__label">统计天数</span>
        <span class="summary-card__value">{{ points.length }}</span>
        <span class="summary-card__hint">按自然日补零</span>
      </div>
    </div>

    <section class="surface-card chart-card">
      <header class="chart-card__head">
        <div>
          <h2 class="chart-card__title">营业额与订单量趋势</h2>
          <p class="chart-card__desc">按自然日聚合，金额单位元</p>
        </div>
      </header>
      <EChartPanel :option="trendOption" :height="340" :loading="loading" loading-text="报表加载中" />
    </section>

    <div class="chart-row">
      <section class="surface-card chart-card">
        <header class="chart-card__head">
          <div>
            <h2 class="chart-card__title">热销菜品 Top 8</h2>
            <p class="chart-card__desc">按完成订单中的销量排序</p>
          </div>
        </header>
        <EChartPanel :option="hotOption" :height="320" :loading="loading" loading-text="报表加载中" />
      </section>

      <section class="surface-card chart-card">
        <header class="chart-card__head">
          <div>
            <h2 class="chart-card__title">订单类型占比</h2>
            <p class="chart-card__desc">外卖 / 堂食订单量与金额</p>
          </div>
        </header>
        <EChartPanel :option="ratioOption" :height="320" :loading="loading" loading-text="报表加载中" />
      </section>
    </div>
  </div>
</template>

<style scoped>
.stats {
  display: flex;
  flex-direction: column;
  gap: var(--layout-gap);
}

.stats__actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-2);
}

.export-select {
  width: 180px;
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

.summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: var(--space-4);
}

.summary-card {
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
  padding: var(--space-5);
  border: 1px solid var(--color-border-subtle);
  border-radius: var(--radius-lg);
  background: var(--color-surface);
  box-shadow: var(--shadow-xs);
}

.summary-card__label {
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
}

.summary-card__value {
  font-family: var(--font-family-number);
  font-size: var(--font-size-2xl);
  font-weight: var(--font-weight-semibold);
  font-variant-numeric: tabular-nums;
}

.summary-card__value.is-brand {
  color: var(--color-primary);
}

.summary-card__hint {
  color: var(--color-text-tertiary);
  font-size: var(--font-size-xs);
}

.chart-card {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
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

.chart-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  gap: var(--space-4);
}

@media (max-width: 1200px) {
  .summary-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .chart-row {
    grid-template-columns: 1fr;
  }

  .stats__hint {
    margin-left: 0;
  }
}
</style>
