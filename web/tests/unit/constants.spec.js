import { describe, expect, it } from 'vitest'
import {
  ORDER_STATUS,
  ORDER_STATUS_LABELS,
  ORDER_STATUS_TAG_TYPES,
  ORDER_TYPE,
  labelOf,
  optionsOf,
  orderStatusOptions,
  tagTypeOf
} from '@/utils/constants'

describe('constants 状态映射', () => {
  it('labelOf 返回中文文案，未知状态回退占位符', () => {
    expect(labelOf(ORDER_STATUS_LABELS, ORDER_STATUS.PAID)).toBe('待接单')
    expect(labelOf(ORDER_STATUS_LABELS, 'UNKNOWN')).toBe('—')
    expect(labelOf(ORDER_STATUS_LABELS, 'UNKNOWN', '未知')).toBe('未知')
  })

  it('tagTypeOf 返回 Element Plus 标签类型', () => {
    expect(tagTypeOf(ORDER_STATUS_TAG_TYPES, ORDER_STATUS.COMPLETED)).toBe('success')
    expect(tagTypeOf(ORDER_STATUS_TAG_TYPES, ORDER_STATUS.REJECTED)).toBe('danger')
    expect(tagTypeOf(ORDER_STATUS_TAG_TYPES, 'UNKNOWN')).toBe('info')
  })

  it('堂食状态选项不包含配送中', () => {
    const takeout = orderStatusOptions(ORDER_TYPE.TAKEOUT).map((item) => item.value)
    const dineIn = orderStatusOptions(ORDER_TYPE.DINE_IN).map((item) => item.value)
    expect(takeout).toContain(ORDER_STATUS.DELIVERING)
    expect(dineIn).not.toContain(ORDER_STATUS.DELIVERING)
    expect(optionsOf(ORDER_STATUS_LABELS)).toHaveLength(Object.keys(ORDER_STATUS_LABELS).length)
  })
})
