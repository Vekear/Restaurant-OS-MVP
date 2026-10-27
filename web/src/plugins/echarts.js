/**
 * ECharts 按需注册 + 主题
 * 商家报表（T13）与管理后台（T14）统一从本文件取 echarts 实例，保证只注册一次。
 */
import * as echarts from 'echarts/core'
import { BarChart, LineChart, PieChart } from 'echarts/charts'
import {
  DatasetComponent,
  GridComponent,
  LegendComponent,
  TitleComponent,
  TooltipComponent
} from 'echarts/components'
import { LabelLayout, UniversalTransition } from 'echarts/features'
import { CanvasRenderer } from 'echarts/renderers'

/** 图表色板（与 tokens.css 品牌色一致） */
export const chartPalette = ['#e4572e', '#e39a2b', '#2e9e6b', '#5b7a99', '#9a6ad1', '#d64545', '#4aa3a3']

const THEME_NAME = 'restaurant'

const theme = {
  color: chartPalette,
  backgroundColor: 'transparent',
  textStyle: {
    fontFamily:
      "'Inter', 'PingFang SC', 'Hiragino Sans GB', 'Microsoft YaHei', 'Noto Sans SC', sans-serif",
    color: '#6b6159'
  },
  title: {
    textStyle: { color: '#221c17', fontWeight: 600, fontSize: 15 },
    subtextStyle: { color: '#9a8f85', fontSize: 12 }
  },
  legend: {
    textStyle: { color: '#6b6159' },
    icon: 'roundRect',
    itemWidth: 10,
    itemHeight: 10
  },
  tooltip: {
    backgroundColor: 'rgba(34, 28, 23, 0.92)',
    borderWidth: 0,
    padding: [8, 12],
    textStyle: { color: '#fffdfb', fontSize: 12 },
    axisPointer: {
      lineStyle: { color: '#d5cbc0' },
      crossStyle: { color: '#d5cbc0' }
    }
  },
  grid: { left: 12, right: 16, top: 40, bottom: 8, containLabel: true },
  categoryAxis: {
    axisLine: { lineStyle: { color: '#e7e0d7' } },
    axisTick: { show: false },
    axisLabel: { color: '#9a8f85' },
    splitLine: { show: false }
  },
  valueAxis: {
    axisLine: { show: false },
    axisTick: { show: false },
    axisLabel: { color: '#9a8f85' },
    splitLine: { lineStyle: { color: '#f0eae2', type: 'dashed' } }
  },
  line: { symbol: 'circle', symbolSize: 6, smooth: true, lineStyle: { width: 2 } },
  bar: { barMaxWidth: 28, itemStyle: { borderRadius: [4, 4, 0, 0] } },
  pie: {
    itemStyle: { borderColor: '#ffffff', borderWidth: 2 },
    label: { color: '#6b6159' }
  }
}

let installed = false

/** 注册按需组件与主题，幂等 */
export function registerEcharts() {
  if (installed) return echarts
  installed = true
  echarts.use([
    BarChart,
    LineChart,
    PieChart,
    DatasetComponent,
    GridComponent,
    LegendComponent,
    TitleComponent,
    TooltipComponent,
    LabelLayout,
    UniversalTransition,
    CanvasRenderer
  ])
  echarts.registerTheme(THEME_NAME, theme)
  return echarts
}

export const ECHARTS_THEME = THEME_NAME

export { echarts }
export default echarts
