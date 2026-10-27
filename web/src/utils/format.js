/**
 * 通用格式化工具（金额 / 时间 / 订单状态），供各端页面复用。
 * 对齐 brief §4.3：页面展示订单真实应付金额 orders.pay_amount。
 */

const currencyFormatter = new Intl.NumberFormat('zh-CN', {
  minimumFractionDigits: 2,
  maximumFractionDigits: 2
})

/** 金额 -> "12.00"（不含货币符号，配合 .amount 样式使用） */
export function formatAmount(value) {
  const amount = Number(value)
  if (!Number.isFinite(amount)) return '0.00'
  return currencyFormatter.format(amount)
}

/** 金额 -> "¥12.00" */
export function formatCurrency(value) {
  return `¥${formatAmount(value)}`
}

/** 后端可能返回 "2026-10-27T12:00:00" 或时间戳，统一按本地时区展示 */
export function formatDateTime(value, withSeconds = false) {
  if (!value) return '-'
  const date = value instanceof Date ? value : new Date(String(value).replace(' ', 'T'))
  if (Number.isNaN(date.getTime())) return String(value)
  const pad = (n) => String(n).padStart(2, '0')
  const base = `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(
    date.getHours()
  )}:${pad(date.getMinutes())}`
  return withSeconds ? `${base}:${pad(date.getSeconds())}` : base
}

export function formatDate(value) {
  const text = formatDateTime(value)
  return text === '-' ? text : text.slice(0, 10)
}

/** 距现在的人性化描述，用于倒计时 / 列表 */
export function fromNow(value) {
  if (!value) return '-'
  const date = new Date(String(value).replace(' ', 'T'))
  if (Number.isNaN(date.getTime())) return '-'
  const diff = Date.now() - date.getTime()
  const abs = Math.abs(diff)
  const minute = 60 * 1000
  const hour = 60 * minute
  const day = 24 * hour
  let text
  if (abs < minute) text = '刚刚'
  else if (abs < hour) text = `${Math.floor(abs / minute)} 分钟`
  else if (abs < day) text = `${Math.floor(abs / hour)} 小时`
  else text = `${Math.floor(abs / day)} 天`
  return diff >= 0 ? `${text}前` : `${text}后`
}

/** 距离超时时间还剩的毫秒数（用于待支付倒计时，负数为已超时） */
export function remainingMs(timeoutAt) {
  if (!timeoutAt) return 0
  const target = new Date(String(timeoutAt).replace(' ', 'T')).getTime()
  if (Number.isNaN(target)) return 0
  return target - Date.now()
}

/** 倒计时毫秒 -> "05:32" */
export function formatCountdown(ms) {
  const total = Math.max(0, Math.floor(ms / 1000))
  const minutes = Math.floor(total / 60)
  const seconds = total % 60
  return `${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`
}
