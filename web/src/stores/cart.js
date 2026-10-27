/**
 * 购物车 Store（服务端持久化，docs/02-api.md §7）
 *
 * - 所有写操作直接用接口返回的 CartView 覆盖本地状态，避免前端重复计算
 * - 未登录时购物车为空且不请求接口；退出登录自动清空
 * - quantityOf()/subtotal() 供菜品卡片与结算页复用
 */
import { computed, ref, watch } from 'vue'
import { defineStore } from 'pinia'
import { cartApi } from '@/api/cart'
import { useAuthStore } from '@/stores/auth'

function emptyCart() {
  return {
    items: [],
    totalQuantity: 0,
    totalAmount: 0,
    invalidCount: 0,
    maxItems: 50
  }
}

export const useCartStore = defineStore('cart', () => {
  const auth = useAuthStore()

  const items = ref([])
  const totalQuantity = ref(0)
  const totalAmount = ref(0)
  const invalidCount = ref(0)
  const maxItems = ref(50)

  const loading = ref(false)
  const loaded = ref(false)
  const mutating = ref(false)
  const error = ref('')

  const isEmpty = computed(() => items.value.length === 0)
  const isReady = computed(() => auth.isLoggedIn && loaded.value)
  const estimatedTotal = computed(() => Number(totalAmount.value || 0))
  const amountOf = computed(() => {
    /** dishId -> 数量，便于卡片快速显示步进器 */
    const map = new Map()
    items.value.forEach((item) => map.set(Number(item.dishId), item))
    return map
  })

  function apply(cart) {
    const data = cart && typeof cart === 'object' ? cart : emptyCart()
    items.value = Array.isArray(data.items) ? data.items : []
    totalQuantity.value = Number(data.totalQuantity || 0)
    totalAmount.value = Number(data.totalAmount || 0)
    invalidCount.value = Number(data.invalidCount || 0)
    maxItems.value = Number(data.maxItems || 50)
    loaded.value = true
    error.value = ''
  }

  function reset() {
    items.value = []
    totalQuantity.value = 0
    totalAmount.value = 0
    invalidCount.value = 0
    maxItems.value = 50
    loaded.value = false
    loading.value = false
    error.value = ''
  }

  /** 拉取购物车（需登录） */
  async function fetchCart(options = {}) {
    const { force = false } = options
    if (!auth.isLoggedIn) {
      reset()
      return emptyCart()
    }
    if (!force && loaded.value && !error.value) return null
    loading.value = true
    error.value = ''
    try {
      const data = await cartApi.getCart()
      apply(data)
      return data
    } catch (err) {
      error.value = err?.message || '购物车加载失败'
      throw err
    } finally {
      loading.value = false
    }
  }

  /** 首次访问时懒加载，已被加载则跳过 */
  async function ensureLoaded() {
    if (!auth.isLoggedIn) return
    if (loaded.value) return
    try {
      await fetchCart()
    } catch {
      /* 错误已写入 error，页面展示重试入口 */
    }
  }

  async function addItem(dishId, quantity = 1) {
    if (!auth.isLoggedIn) {
      const error = new Error('请先登录后再加入购物车')
      error.code = 'NEED_LOGIN'
      throw error
    }
    mutating.value = true
    try {
      const data = await cartApi.addCartItem(dishId, quantity)
      apply(data)
      return data
    } finally {
      mutating.value = false
    }
  }

  async function updateQuantity(dishId, quantity) {
    mutating.value = true
    try {
      const data = await cartApi.updateCartItem(dishId, quantity)
      apply(data)
      return data
    } finally {
      mutating.value = false
    }
  }

  async function removeItem(dishId) {
    mutating.value = true
    try {
      const data = await cartApi.removeCartItem(dishId)
      apply(data)
      return data
    } finally {
      mutating.value = false
    }
  }

  async function clear() {
    mutating.value = true
    try {
      const data = await cartApi.clearCart()
      apply(data)
      return data
    } finally {
      mutating.value = false
    }
  }

  /** 菜品卡片/详情页的数量 */
  function itemOf(dishId) {
    return amountOf.value.get(Number(dishId)) || null
  }

  function quantityOf(dishId) {
    return itemOf(dishId)?.quantity || 0
  }

  // 退出登录后清空本地购物车（服务端购物车仍保留，下次登录可继续）
  watch(
    () => auth.token,
    (next, prev) => {
      if (!next && prev) reset()
    }
  )

  return {
    items,
    totalQuantity,
    totalAmount,
    invalidCount,
    maxItems,
    loading,
    loaded,
    mutating,
    error,
    isEmpty,
    isReady,
    estimatedTotal,
    fetchCart,
    ensureLoaded,
    addItem,
    updateQuantity,
    removeItem,
    clear,
    itemOf,
    quantityOf,
    reset
  }
})
