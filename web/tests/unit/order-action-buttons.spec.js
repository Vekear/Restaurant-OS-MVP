import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { ElButton } from 'element-plus'

const mocks = vi.hoisted(() => ({ executeOrderAction: vi.fn() }))

vi.mock('@/utils/merchantOrders', async (importOriginal) => {
  const actual = await importOriginal()
  return { ...actual, executeOrderAction: mocks.executeOrderAction }
})

import OrderActionButtons from '@/components/merchant/OrderActionButtons.vue'

const paidOrder = {
  id: 101,
  orderNo: 'T20261028001',
  orderType: 'TAKEOUT',
  status: 'PAID'
}

/** 读取传给 el-button 的 disabled prop（Boolean），并同时返回原生 disabled 属性 */
function buttonState(wrapper, index) {
  const button = wrapper.findAllComponents(ElButton)[index]
  return {
    label: button.text(),
    disabledProp: button.props('disabled'),
    nativeDisabled: wrapper.findAll('button')[index].attributes('disabled') !== undefined,
    loading: button.props('loading')
  }
}

beforeEach(() => {
  mocks.executeOrderAction.mockReset()
})

describe('OrderActionButtons 按钮禁用态（T28 Boolean casting 回归）', () => {
  it('空闲态：接单/拒单按钮均可用（running=null，不再被空串 cast 成 true）', () => {
    const wrapper = mount(OrderActionButtons, { props: { order: paidOrder } })
    expect(wrapper.findAllComponents(ElButton)).toHaveLength(2)

    const accept = buttonState(wrapper, 0)
    const reject = buttonState(wrapper, 1)
    expect(accept.label).toContain('接单')
    expect(reject.label).toContain('拒单')
    expect(accept.disabledProp).toBe(false)
    expect(reject.disabledProp).toBe(false)
    expect(accept.nativeDisabled).toBe(false)
    expect(reject.nativeDisabled).toBe(false)
  })

  it('执行中：仅当前动作保持可用（loading），其他动作禁用，完成后恢复', async () => {
    let resolveRequest
    mocks.executeOrderAction.mockImplementation(
      () => new Promise((resolve) => { resolveRequest = resolve })
    )

    const wrapper = mount(OrderActionButtons, { props: { order: paidOrder } })
    await wrapper.findAll('button')[0].trigger('click')
    await wrapper.vm.$nextTick()

    expect(mocks.executeOrderAction).toHaveBeenCalledTimes(1)
    const runningAccept = buttonState(wrapper, 0)
    const reject = buttonState(wrapper, 1)
    // 当前动作：组件守卫不放行 disabled（由 loading 阻止重复点击），另一动作被守卫禁用
    expect(runningAccept.disabledProp).toBe(false)
    expect(runningAccept.loading).toBe(true)
    expect(reject.disabledProp).toBe(true)
    expect(reject.nativeDisabled).toBe(true)

    resolveRequest({ id: 101, status: 'ACCEPTED' })
    await flushPromises()
    await wrapper.vm.$nextTick()

    expect(buttonState(wrapper, 0).loading).toBe(false)
    expect(buttonState(wrapper, 0).disabledProp).toBe(false)
  })

  it('disabled prop 为 true 时所有动作禁用', () => {
    const wrapper = mount(OrderActionButtons, { props: { order: paidOrder, disabled: true } })
    const buttons = wrapper.findAllComponents(ElButton)
    expect(buttons.length).toBeGreaterThan(0)
    buttons.forEach((_, index) => {
      const state = buttonState(wrapper, index)
      expect(state.disabledProp).toBe(true)
      expect(state.nativeDisabled).toBe(true)
    })
  })
})
