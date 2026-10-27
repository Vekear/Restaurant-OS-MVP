import { computed, reactive } from 'vue'
import { readStorage, writeStorage } from '@/utils/storage'
import { formatBadge } from '@/utils/format'

const CART_KEY = 'restaurant_mp_cart_v1'
const CART_TAB_INDEX = 1
const MAX_PER_DISH = 99

function sanitize(items) {
  if (!Array.isArray(items)) return []
  return items
    .filter((item) => item && item.id !== undefined && item.id !== null && item.name)
    .map((item) => ({
      id: item.id,
      name: String(item.name),
      price: Number(item.price) || 0,
      image: item.image || '',
      categoryId: item.categoryId,
      quantity: Math.min(MAX_PER_DISH, Math.max(1, Number(item.quantity) || 1))
    }))
}

const state = reactive({
  items: sanitize(readStorage(CART_KEY, []))
})

const items = computed(() => state.items)
const totalQuantity = computed(() =>
  state.items.reduce((sum, item) => sum + Number(item.quantity || 0), 0)
)
const totalAmount = computed(() =>
  state.items.reduce((sum, item) => sum + Number(item.price || 0) * Number(item.quantity || 0), 0)
)

let lastBadge = -1
let badgeTimer = null

/**
 * 同步购物车 tabBar 角标（H5/小程序均支持；非 tabBar 环境静默失败）。
 * 首次启动时 tabBar 可能尚未挂载，失败后做有限次重试，避免首屏角标丢失。
 */
function syncBadge(retry = 0) {
  const total = totalQuantity.value
  if (total === lastBadge) return
  const finish = (ok) => {
    if (ok) {
      lastBadge = total
      return
    }
    lastBadge = -1
    if (retry >= 3) return
    if (badgeTimer) clearTimeout(badgeTimer)
    badgeTimer = setTimeout(() => syncBadge(retry + 1), 300)
  }
  try {
    if (total > 0) {
      uni.setTabBarBadge({
        index: CART_TAB_INDEX,
        text: formatBadge(total),
        success: () => finish(true),
        fail: () => finish(false)
      })
    } else {
      uni.removeTabBarBadge({
        index: CART_TAB_INDEX,
        success: () => finish(true),
        fail: () => finish(false)
      })
    }
  } catch (error) {
    finish(false)
  }
}

function persist() {
  writeStorage(CART_KEY, state.items)
  syncBadge()
}

function findItem(id) {
  return state.items.find((item) => String(item.id) === String(id)) || null
}

function quantityOf(id) {
  const item = findItem(id)
  return item ? item.quantity : 0
}

/** 加入购物车；已存在则累加，单品上限 MAX_PER_DISH */
function addItem(dish, quantity = 1) {
  if (!dish || dish.id === undefined || dish.id === null) return
  const step = Math.max(1, Number(quantity) || 1)
  const existing = findItem(dish.id)
  if (existing) {
    existing.quantity = Math.min(MAX_PER_DISH, existing.quantity + step)
  } else {
    state.items.push({
      id: dish.id,
      name: dish.name || '未命名菜品',
      price: Number(dish.price) || 0,
      image: dish.image || '',
      categoryId: dish.categoryId,
      quantity: Math.min(MAX_PER_DISH, step)
    })
  }
  persist()
}

function setQuantity(id, quantity) {
  const next = Number(quantity) || 0
  const existing = findItem(id)
  if (!existing) return
  if (next <= 0) {
    removeItem(id)
    return
  }
  existing.quantity = Math.min(MAX_PER_DISH, next)
  persist()
}

function increase(id) {
  const existing = findItem(id)
  if (!existing) return
  if (existing.quantity >= MAX_PER_DISH) {
    uni.showToast({ title: `单品最多 ${MAX_PER_DISH} 份`, icon: 'none' })
    return
  }
  existing.quantity += 1
  persist()
}

function decrease(id) {
  const existing = findItem(id)
  if (!existing) return
  if (existing.quantity <= 1) {
    removeItem(id)
    return
  }
  existing.quantity -= 1
  persist()
}

function removeItem(id) {
  const index = state.items.findIndex((item) => String(item.id) === String(id))
  if (index === -1) return
  state.items.splice(index, 1)
  persist()
}

function clearCart() {
  state.items.splice(0, state.items.length)
  persist()
}

const cartStore = {
  state,
  items,
  totalQuantity,
  totalAmount,
  quantityOf,
  addItem,
  setQuantity,
  increase,
  decrease,
  removeItem,
  clearCart,
  syncBadge
}

export function useCartStore() {
  return cartStore
}
