<script setup>
/**
 * ECharts 通用容器（T13/T14 报表复用）
 * - 自动注册图表主题，容器尺寸变化时自适应
 * - option 深度监听，支持 loading 状态
 */
import { nextTick, onBeforeUnmount, onMounted, ref, shallowRef, watch } from 'vue'
import { ECHARTS_THEME, echarts, registerEcharts } from '@/plugins/echarts'

const props = defineProps({
  option: { type: Object, default: () => ({}) },
  height: { type: [Number, String], default: 320 },
  loading: { type: Boolean, default: false },
  loadingText: { type: String, default: '加载中…' },
  autoresize: { type: Boolean, default: true }
})

const container = ref(null)
const chart = shallowRef(null)
let observer = null

const resolvedHeight = () => (typeof props.height === 'number' ? `${props.height}px` : props.height)

function resize() {
  chart.value?.resize()
}

function render() {
  if (!chart.value) return
  chart.value.setOption(props.option || {}, true)
}

function syncLoading() {
  if (!chart.value) return
  if (props.loading) {
    chart.value.showLoading('default', {
      text: props.loadingText,
      color: '#e4572e',
      textColor: '#6b6159',
      maskColor: 'rgba(255, 253, 251, 0.72)',
      fontSize: 12
    })
  } else {
    chart.value.hideLoading()
  }
}

onMounted(async () => {
  registerEcharts()
  await nextTick()
  if (!container.value) return
  chart.value = echarts.init(container.value, ECHARTS_THEME, { renderer: 'canvas' })
  render()
  syncLoading()
  if (props.autoresize && typeof ResizeObserver !== 'undefined') {
    observer = new ResizeObserver(() => resize())
    observer.observe(container.value)
  }
})

watch(() => props.option, render, { deep: true })
watch(() => props.loading, syncLoading)

onBeforeUnmount(() => {
  observer?.disconnect()
  observer = null
  chart.value?.dispose()
  chart.value = null
})

defineExpose({ chart, resize })
</script>

<template>
  <div ref="container" class="base-chart" :style="{ height: resolvedHeight() }" />
</template>

<style scoped>
.base-chart {
  width: 100%;
  min-height: 160px;
}
</style>
