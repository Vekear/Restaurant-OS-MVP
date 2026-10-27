/**
 * 商家上下文 store：店铺资料、营业状态、看板汇总、实时订单事件
 * - 资料/状态由 MerchantLayout 在进入工作台时加载
 * - WebSocket 订阅 /topic/merchant/{merchantId}/orders，新订单累加角标并抛出 lastEvent 供布局 toast
 * - 收到事件后各页面以 GET 快照为准刷新（契约 §14.2）
 */
import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import {
  getMerchantProfile,
  getBusinessStatus,
  updateBusinessStatus,
  getDashboardSummary
} from '@/api/merchant'
import { realtime } from '@/api/ws'
import { useAuthStore } from '@/stores/auth'
import {
  MERCHANT_AUDIT_STATUS,
  MERCHANT_BUSINESS_STATUS,
  WS_EVENT_TYPE
} from '@/utils/constants'

export const useMerchantStore = defineStore('merchant', () => {
  const auth = useAuthStore()

  const profile = ref(null)
  const profileLoading = ref(false)
  const profileError = ref('')
  const businessStatus = ref(MERCHANT_BUSINESS_STATUS.OPEN)
  const statusSaving = ref(false)

  const summary = ref(null)
  const summaryLoading = ref(false)

  const lastEvent = ref(null)
  const newOrderCount = ref(0)
  const subscribedKey = ref('')

  const merchantId = computed(() => profile.value?.id || auth.user?.merchantId || null)
  const storeName = computed(() => profile.value?.name || '我的餐厅')
  const isOpen = computed(() => businessStatus.value === MERCHANT_BUSINESS_STATUS.OPEN)
  const auditStatus = computed(() => profile.value?.auditStatus || MERCHANT_AUDIT_STATUS.APPROVED)
  const isApproved = computed(() => auditStatus.value === MERCHANT_AUDIT_STATUS.APPROVED)

  async function loadProfile(force = false) {
    if (profile.value && !force) return profile.value
    profileLoading.value = true
    profileError.value = ''
    try {
      profile.value = await getMerchantProfile()
      if (profile.value?.businessStatus) businessStatus.value = profile.value.businessStatus
      return profile.value
    } catch (error) {
      profileError.value = error?.message || '店铺资料加载失败'
      return null
    } finally {
      profileLoading.value = false
    }
  }

  async function loadBusinessStatus() {
    try {
      const data = await getBusinessStatus()
      if (data?.businessStatus) businessStatus.value = data.businessStatus
    } catch {
      /* 读取失败时保留当前值，页面另做错误提示 */
    }
  }

  /**
   * 切换营业状态（仅影响新订单拦截 40908）
   * @param {'OPEN'|'CLOSED'} next
   */
  async function setBusinessStatus(next) {
    statusSaving.value = true
    try {
      const data = await updateBusinessStatus(next)
      businessStatus.value = data?.businessStatus || next
      if (profile.value) profile.value.businessStatus = businessStatus.value
      return businessStatus.value
    } finally {
      statusSaving.value = false
    }
  }

  async function loadSummary(date) {
    summaryLoading.value = true
    try {
      summary.value = await getDashboardSummary(date ? { date } : {})
      return summary.value
    } finally {
      summaryLoading.value = false
    }
  }

  function handleRealtimeEvent(payload) {
    if (!payload || typeof payload !== 'object') return
    lastEvent.value = { payload, at: Date.now() }
    if (payload.eventType === WS_EVENT_TYPE.ORDER_CREATED) {
      newOrderCount.value += 1
    }
  }

  /** 订阅本店订单 topic（幂等；断线重连由 api/ws.js 自动重订阅） */
  function bindRealtime() {
    const id = merchantId.value
    if (!id) return false
    const key = `merchant-orders-${id}`
    if (subscribedKey.value === key) return true
    unbindRealtime()
    realtime.subscribe(`/topic/merchant/${id}/orders`, handleRealtimeEvent, { key })
    subscribedKey.value = key
    if (auth.token) realtime.connect({ token: auth.token })
    return true
  }

  function unbindRealtime() {
    if (subscribedKey.value) {
      realtime.unsubscribe(subscribedKey.value)
      subscribedKey.value = ''
    }
  }

  function clearNewOrderCount() {
    newOrderCount.value = 0
  }

  function reset() {
    unbindRealtime()
    profile.value = null
    profileError.value = ''
    businessStatus.value = MERCHANT_BUSINESS_STATUS.OPEN
    summary.value = null
    lastEvent.value = null
    newOrderCount.value = 0
  }

  return {
    profile,
    profileLoading,
    profileError,
    businessStatus,
    statusSaving,
    summary,
    summaryLoading,
    lastEvent,
    newOrderCount,
    merchantId,
    storeName,
    isOpen,
    auditStatus,
    isApproved,
    loadProfile,
    loadBusinessStatus,
    setBusinessStatus,
    loadSummary,
    handleRealtimeEvent,
    bindRealtime,
    unbindRealtime,
    clearNewOrderCount,
    reset
  }
})
