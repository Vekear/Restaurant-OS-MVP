import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import MoneyText from '@/components/MoneyText.vue'
import EmptyState from '@/components/EmptyState.vue'
import ErrorState from '@/components/ErrorState.vue'
import DataState from '@/components/DataState.vue'
import LoadingBlock from '@/components/LoadingBlock.vue'
import ImageUploader from '@/components/ImageUploader.vue'

describe('统一展示组件', () => {
  it('MoneyText 输出两位小数金额与货币符号', () => {
    const wrapper = mount(MoneyText, { props: { value: 1234.5 } })
    expect(wrapper.text()).toContain('¥')
    expect(wrapper.text()).toContain('1,234.50')
    expect(wrapper.find('.money-text__symbol').exists()).toBe(true)
  })

  it('MoneyText 支持隐藏货币符号', () => {
    const wrapper = mount(MoneyText, { props: { value: 0.01, showSymbol: false } })
    expect(wrapper.text()).toBe('0.01')
  })

  it('EmptyState 展示自定义空态文案', () => {
    const wrapper = mount(EmptyState, { props: { title: '购物车还是空的', description: '去菜单挑几道菜吧' } })
    expect(wrapper.text()).toContain('去菜单挑几道菜吧')
  })

  it('ErrorState 点击重新加载触发 retry', async () => {
    const wrapper = mount(ErrorState, { props: { message: '网络开小差了' } })
    expect(wrapper.text()).toContain('网络开小差了')
    await wrapper.find('button').trigger('click')
    expect(wrapper.emitted('retry')).toHaveLength(1)
  })

  it('DataState 在 loading/空态/正常内容间切换', () => {
    const loading = mount(DataState, { props: { loading: true } })
    expect(loading.findComponent(LoadingBlock).exists()).toBe(true)
    loading.unmount()

    const empty = mount(DataState, { props: { empty: true, emptyText: '暂无订单' } })
    expect(empty.text()).toContain('暂无订单')
    empty.unmount()

    const content = mount(DataState, { slots: { default: '<p class="content">正常内容</p>' } })
    expect(content.find('.content').exists()).toBe(true)
  })

  it('ImageUploader 展示上传入口与校验提示', () => {
    const wrapper = mount(ImageUploader, { props: { modelValue: '' } })
    expect(wrapper.text()).toContain('上传图片')
    expect(wrapper.text()).toContain('支持 JPG/PNG/WebP')
    expect(wrapper.find('.image-uploader__preview').exists()).toBe(true)
  })
})
