/** 金额格式化：统一两位小数，非数字兜底 0.00 */
export function formatAmount(value) {
  const num = Number(value)
  if (!Number.isFinite(num)) return '0.00'
  return num.toFixed(2)
}

/** 带 ¥ 符号的金额文本 */
export function formatPrice(value) {
  return `¥${formatAmount(value)}`
}

/** 数量角标：超过 99 显示 99+ */
export function formatBadge(count) {
  const num = Number(count)
  if (!Number.isFinite(num) || num <= 0) return ''
  return num > 99 ? '99+' : String(num)
}

/** 文本截断（用于长菜品描述） */
export function truncate(text, max = 40) {
  const value = String(text || '')
  return value.length > max ? `${value.slice(0, max)}…` : value
}

function pad(value) {
  return value < 10 ? `0${value}` : String(value)
}

/**
 * 时间格式化：ISO-8601（如 2026-10-28T12:00:00+08:00）→ 本地时间文本。
 * 默认 `MM-DD HH:mm`，可传 `HH:mm` 等自定义格式（仅支持 MM/DD/HH/mm 占位）。
 */
export function formatDateTime(value, pattern = 'MM-DD HH:mm') {
  if (!value) return '—'
  const date = new Date(String(value))
  if (Number.isNaN(date.getTime())) return String(value)
  const tokens = {
    MM: pad(date.getMonth() + 1),
    DD: pad(date.getDate()),
    HH: pad(date.getHours()),
    mm: pad(date.getMinutes()),
    yyyy: String(date.getFullYear())
  }
  return pattern.replace(/yyyy|MM|DD|HH|mm/g, (token) => tokens[token])
}

/** 剩余分钟数（用于会话到期提示），无到期时间返回 null */
export function remainMinutes(value) {
  if (!value) return null
  const target = new Date(String(value)).getTime()
  if (Number.isNaN(target)) return null
  return Math.max(0, Math.ceil((target - Date.now()) / 60000))
}
