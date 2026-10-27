/**
 * 日期区间工具（商家看板 / 报表共用）
 * 后端按 Asia/Shanghai 自然日统计，日期参数统一使用 yyyy-MM-dd。
 */

const pad = (value) => String(value).padStart(2, '0')

/** Date -> yyyy-MM-dd（本地时区） */
export function toDateString(value = new Date()) {
  const date = value instanceof Date ? value : new Date(String(value).replace(' ', 'T'))
  if (Number.isNaN(date.getTime())) return ''
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

/** 今天（本地时区） */
export function todayString() {
  return toDateString(new Date())
}

/**
 * 最近 N 天（含今天）的日期区间
 * @param {number} days 1–92
 * @returns {[string, string]}
 */
export function recentRange(days = 7) {
  const end = new Date()
  const start = new Date()
  start.setDate(end.getDate() - (Math.max(1, days) - 1))
  return [toDateString(start), toDateString(end)]
}

/** 常用区间预设（与后端 ≤92 天限制对齐） */
export const RANGE_PRESETS = Object.freeze([
  { label: '近 7 天', days: 7 },
  { label: '近 30 天', days: 30 },
  { label: '近 90 天', days: 90 }
])

/** 校验区间跨度是否超过 maxDays 天 */
export function isRangeWithin(range, maxDays = 92) {
  if (!Array.isArray(range) || range.length !== 2) return false
  const start = new Date(`${range[0]}T00:00:00`).getTime()
  const end = new Date(`${range[1]}T00:00:00`).getTime()
  if (!Number.isFinite(start) || !Number.isFinite(end) || end < start) return false
  return Math.round((end - start) / 86400000) + 1 <= maxDays
}

/** 区间文案：10-20 ~ 10-27 */
export function formatRangeText(range) {
  if (!Array.isArray(range) || range.length !== 2) return ''
  const short = (value) => String(value || '').slice(5)
  return `${short(range[0])} ~ ${short(range[1])}`
}

/** 图表 x 轴短标签：10-20 */
export function shortDate(value) {
  return String(value || '').slice(5)
}
