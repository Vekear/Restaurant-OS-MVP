import { describe, expect, it } from 'vitest'
import {
  formatAmount,
  formatCountdown,
  formatCurrency,
  formatDateTime,
  remainingMs
} from '@/utils/format'

describe('format 金额/时间格式化', () => {
  it('金额保留两位小数并使用千分位', () => {
    expect(formatAmount(12)).toBe('12.00')
    expect(formatAmount(1234.5)).toBe('1,234.50')
    expect(formatAmount('0.01')).toBe('0.01')
    expect(formatAmount(null)).toBe('0.00')
    expect(formatAmount('abc')).toBe('0.00')
  })

  it('金额前缀货币符号', () => {
    expect(formatCurrency(0.01)).toBe('¥0.01')
    expect(formatCurrency(99)).toBe('¥99.00')
  })

  it('倒计时按 mm:ss 展示并向下取整', () => {
    expect(formatCountdown(332000)).toBe('05:32')
    expect(formatCountdown(900)).toBe('00:00')
    expect(formatCountdown(-5000)).toBe('00:00')
    expect(formatCountdown(61000)).toBe('01:01')
  })

  it('时间字符串兼容空格分隔并按本地时区输出', () => {
    expect(formatDateTime('2026-10-27T08:05:00')).toBe('2026-10-27 08:05')
    expect(formatDateTime('2026-10-27 08:05:09', true)).toBe('2026-10-27 08:05:09')
    expect(formatDateTime('')).toBe('-')
  })

  it('剩余毫秒数基于超时时间计算', () => {
    const future = new Date(Date.now() + 60000).toISOString()
    expect(remainingMs(future)).toBeGreaterThan(55000)
    expect(remainingMs('')).toBe(0)
  })
})
