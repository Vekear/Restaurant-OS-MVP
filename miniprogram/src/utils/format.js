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
