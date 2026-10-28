import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'

const mocks = vi.hoisted(() => ({
  listCoupons: vi.fn(),
  claimCoupon: vi.fn()
}))

vi.mock('@/api/coupons', () => ({
  listCoupons: mocks.listCoupons,
  claimCoupon: mocks.claimCoupon
}))

import CouponClaimDialog from '@/components/customer/CouponClaimDialog.vue'

function pageResult(list) {
  return { list, total: list.length, page: 1, size: 5, pages: 1 }
}

beforeEach(() => {
  document.body.innerHTML = ''
  mocks.listCoupons.mockReset()
  mocks.claimCoupon.mockReset()
})

describe('CouponClaimDialog 领券中心', () => {
  it('打开时按商品金额拉取可领取券，并展示状态', async () => {
    mocks.listCoupons.mockResolvedValue(
      pageResult([
        {
          id: 1,
          name: '新客满 50 减 10',
          thresholdAmount: 50,
          discountAmount: 10,
          endAt: '2028-12-31T23:59:59+08:00',
          claimed: false,
          claimable: true,
          remaining: 5
        },
        {
          id: 2,
          name: '满 100 减 25',
          thresholdAmount: 100,
          discountAmount: 25,
          endAt: '2028-12-31T23:59:59+08:00',
          claimed: true,
          claimable: false,
          remaining: 5
        }
      ])
    )

    const wrapper = mount(CouponClaimDialog, {
      props: { modelValue: true, amount: 64 },
      attachTo: document.body
    })
    await flushPromises()

    expect(mocks.listCoupons).toHaveBeenCalledWith({ amount: 64, page: 1, size: 5 })
    const text = document.body.textContent
    expect(text).toContain('新客满 50 减 10')
    expect(text).toContain('满 100 减 25')
    expect(text).toContain('立即领取')
    expect(text).toContain('已领取')
    wrapper.unmount()
  })

  it('领取成功后 emit claimed 并刷新列表', async () => {
    const coupon = {
      id: 1,
      name: '新客满 50 减 10',
      thresholdAmount: 50,
      discountAmount: 10,
      endAt: '2028-12-31T23:59:59+08:00',
      claimed: false,
      claimable: true,
      remaining: 5
    }
    mocks.listCoupons.mockResolvedValue(pageResult([coupon]))
    mocks.claimCoupon.mockResolvedValue({ id: 88, couponId: 1, name: coupon.name, status: 'UNUSED' })

    const wrapper = mount(CouponClaimDialog, {
      props: { modelValue: true, amount: 64 },
      attachTo: document.body
    })
    await flushPromises()

    const claimButton = [...document.body.querySelectorAll('button')].find((btn) =>
      btn.textContent.includes('立即领取')
    )
    expect(claimButton).toBeTruthy()
    claimButton.click()
    await flushPromises()

    expect(mocks.claimCoupon).toHaveBeenCalledWith(1)
    expect(wrapper.emitted('claimed')).toHaveLength(1)
    expect(wrapper.emitted('claimed')[0][0]).toMatchObject({ couponId: 1, status: 'UNUSED' })
    expect(mocks.listCoupons).toHaveBeenCalledTimes(2)
    wrapper.unmount()
  })
})
