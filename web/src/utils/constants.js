/**
 * 全局枚举与文案映射（与 docs/02-api.md、docs/01-architecture.md §11 / §16.4 一一对应）
 *
 * 约定（架构 §6）：页面禁止散落中文状态判断，统一通过 labelOf / tagTypeOf / optionsOf 取文案与配色。
 * 新增枚举时同步更新本文件与 docs/02-api.md，避免三端（Web 顾客/商家/管理）文案漂移。
 */

/* ===================== 用户与角色 ===================== */

export const USER_ROLE = Object.freeze({
  ADMIN: 'ADMIN',
  MERCHANT: 'MERCHANT',
  CUSTOMER: 'CUSTOMER'
})

export const ROLE_LABELS = Object.freeze({
  ADMIN: '管理员',
  MERCHANT: '商家',
  CUSTOMER: '顾客'
})

/** 登录后各角色的默认落地路由 */
export const ROLE_HOME = Object.freeze({
  ADMIN: '/admin/dashboard',
  MERCHANT: '/merchant/dashboard',
  CUSTOMER: '/'
})

/** Element Plus el-tag type 映射 */
export const ROLE_TAG_TYPES = Object.freeze({
  ADMIN: 'danger',
  MERCHANT: 'warning',
  CUSTOMER: 'success'
})

export const USER_STATUS = Object.freeze({
  ENABLED: 'ENABLED',
  DISABLED: 'DISABLED'
})

export const USER_STATUS_LABELS = Object.freeze({
  ENABLED: '正常',
  DISABLED: '已禁用'
})

export const USER_STATUS_TAG_TYPES = Object.freeze({
  ENABLED: 'success',
  DISABLED: 'danger'
})

/** 通用启用/停用状态（菜品、分类、优惠券、商家等） */
export const COMMON_STATUS_LABELS = Object.freeze({
  ENABLED: '启用',
  DISABLED: '停用'
})

export const COMMON_STATUS_TAG_TYPES = Object.freeze({
  ENABLED: 'success',
  DISABLED: 'info'
})

/* ===================== 订单 ===================== */

export const ORDER_TYPE = Object.freeze({
  TAKEOUT: 'TAKEOUT',
  DINE_IN: 'DINE_IN'
})

export const ORDER_TYPE_LABELS = Object.freeze({
  TAKEOUT: '外卖',
  DINE_IN: '堂食'
})

export const ORDER_TYPE_TAG_TYPES = Object.freeze({
  TAKEOUT: 'primary',
  DINE_IN: 'warning'
})

/**
 * 双状态机共用一套订单状态（架构 §11）：
 *   外卖：PENDING_PAYMENT → PAID → ACCEPTED → MAKING → DELIVERING → COMPLETED
 *   堂食：PENDING_PAYMENT → PAID → ACCEPTED → MAKING → SERVED → COMPLETED
 */
export const ORDER_STATUS = Object.freeze({
  PENDING_PAYMENT: 'PENDING_PAYMENT',
  PAID: 'PAID',
  ACCEPTED: 'ACCEPTED',
  MAKING: 'MAKING',
  DELIVERING: 'DELIVERING',
  SERVED: 'SERVED',
  COMPLETED: 'COMPLETED',
  CANCELLED: 'CANCELLED',
  REJECTED: 'REJECTED'
})

export const ORDER_STATUS_LABELS = Object.freeze({
  PENDING_PAYMENT: '待支付',
  PAID: '待接单',
  ACCEPTED: '已接单',
  MAKING: '制作中',
  DELIVERING: '配送中',
  SERVED: '已出餐',
  COMPLETED: '已完成',
  CANCELLED: '已取消',
  REJECTED: '已拒单'
})

export const ORDER_STATUS_TAG_TYPES = Object.freeze({
  PENDING_PAYMENT: 'warning',
  PAID: 'warning',
  ACCEPTED: 'primary',
  MAKING: 'primary',
  DELIVERING: 'primary',
  SERVED: 'success',
  COMPLETED: 'success',
  CANCELLED: 'info',
  REJECTED: 'danger'
})

/** 终态：不可再流转 */
export const ORDER_TERMINAL_STATUSES = Object.freeze([
  ORDER_STATUS.COMPLETED,
  ORDER_STATUS.CANCELLED,
  ORDER_STATUS.REJECTED
])

/** 进行中（未终结）状态，用于顾客端「进行中订单」过滤 */
export const ORDER_ACTIVE_STATUSES = Object.freeze([
  ORDER_STATUS.PENDING_PAYMENT,
  ORDER_STATUS.PAID,
  ORDER_STATUS.ACCEPTED,
  ORDER_STATUS.MAKING,
  ORDER_STATUS.DELIVERING,
  ORDER_STATUS.SERVED
])

/** 堂食订单不会出现的状态 */
export const DINE_IN_EXCLUDED_STATUSES = Object.freeze([ORDER_STATUS.DELIVERING])

export function orderStatusOptions(orderType) {
  return optionsOf(ORDER_STATUS_LABELS, {
    exclude: orderType === ORDER_TYPE.DINE_IN ? DINE_IN_EXCLUDED_STATUSES : []
  })
}

/* ===================== 支付 ===================== */

export const PAYMENT_STATUS = Object.freeze({
  PENDING: 'PENDING',
  SUCCESS: 'SUCCESS',
  FAILED: 'FAILED',
  REFUNDED: 'REFUNDED'
})

export const PAYMENT_STATUS_LABELS = Object.freeze({
  PENDING: '待支付',
  SUCCESS: '支付成功',
  FAILED: '支付失败',
  REFUNDED: '已退款'
})

export const PAYMENT_STATUS_TAG_TYPES = Object.freeze({
  PENDING: 'warning',
  SUCCESS: 'success',
  FAILED: 'danger',
  REFUNDED: 'info'
})

export const PAYMENT_CHANNEL = Object.freeze({
  MOCK: 'MOCK'
})

/** 演示环境：订单金额按实展示，实际模拟扣款 ¥0.01（brief §8） */
export const MOCK_PAYMENT_AMOUNT = 0.01
export const MOCK_PAYMENT_HINT = '演示环境：订单金额按实展示，实际模拟扣款 ¥0.01'

/* ===================== 桌台与堂食会话 ===================== */

export const TABLE_STATUS = Object.freeze({
  FREE: 'FREE',
  OCCUPIED: 'OCCUPIED'
})

export const TABLE_STATUS_LABELS = Object.freeze({
  FREE: '空闲',
  OCCUPIED: '就餐中'
})

export const TABLE_STATUS_TAG_TYPES = Object.freeze({
  FREE: 'success',
  OCCUPIED: 'warning'
})

export const TABLE_SESSION_STATUS = Object.freeze({
  ACTIVE: 'ACTIVE',
  CLOSED: 'CLOSED',
  EXPIRED: 'EXPIRED'
})

export const TABLE_SESSION_STATUS_LABELS = Object.freeze({
  ACTIVE: '进行中',
  CLOSED: '已结束',
  EXPIRED: '已过期'
})

export const TABLE_SESSION_STATUS_TAG_TYPES = Object.freeze({
  ACTIVE: 'success',
  CLOSED: 'info',
  EXPIRED: 'info'
})

/* ===================== 商家 ===================== */

export const MERCHANT_BUSINESS_STATUS = Object.freeze({
  OPEN: 'OPEN',
  CLOSED: 'CLOSED'
})

export const MERCHANT_BUSINESS_STATUS_LABELS = Object.freeze({
  OPEN: '营业中',
  CLOSED: '休息中'
})

export const MERCHANT_BUSINESS_STATUS_TAG_TYPES = Object.freeze({
  OPEN: 'success',
  CLOSED: 'info'
})

export const MERCHANT_AUDIT_STATUS = Object.freeze({
  PENDING: 'PENDING',
  APPROVED: 'APPROVED',
  REJECTED: 'REJECTED'
})

export const MERCHANT_AUDIT_STATUS_LABELS = Object.freeze({
  PENDING: '待审核',
  APPROVED: '已通过',
  REJECTED: '已驳回'
})

export const MERCHANT_AUDIT_STATUS_TAG_TYPES = Object.freeze({
  PENDING: 'warning',
  APPROVED: 'success',
  REJECTED: 'danger'
})

/* ===================== WebSocket 事件（02-api §14.2） ===================== */

export const WS_EVENT_TYPE = Object.freeze({
  ORDER_CREATED: 'ORDER_CREATED',
  ORDER_STATUS_CHANGED: 'ORDER_STATUS_CHANGED',
  PAYMENT_SUCCEEDED: 'PAYMENT_SUCCEEDED',
  PAYMENT_REFUNDED: 'PAYMENT_REFUNDED',
  TABLE_SESSION_OPENED: 'TABLE_SESSION_OPENED',
  TABLE_SESSION_CLOSED: 'TABLE_SESSION_CLOSED'
})

/** 事件 → 中文提示（用于实时消息 toast） */
export const WS_EVENT_LABELS = Object.freeze({
  ORDER_CREATED: '新订单',
  ORDER_STATUS_CHANGED: '订单状态更新',
  PAYMENT_SUCCEEDED: '支付成功',
  PAYMENT_REFUNDED: '支付已退款',
  TABLE_SESSION_OPENED: '开台',
  TABLE_SESSION_CLOSED: '清台'
})

/* ===================== 菜品 / 优惠券 ===================== */

export const DISH_STATUS = Object.freeze({
  ON_SALE: 'ON_SALE',
  OFF_SHELF: 'OFF_SHELF'
})

export const DISH_STATUS_LABELS = Object.freeze({
  ON_SALE: '在售',
  OFF_SHELF: '已下架'
})

export const DISH_STATUS_TAG_TYPES = Object.freeze({
  ON_SALE: 'success',
  OFF_SHELF: 'info'
})

/** 菜单排序（docs/02-api.md §6.2 sort 枚举） */
export const DISH_SORT = Object.freeze({
  DEFAULT: 'default',
  PRICE_ASC: 'price_asc',
  PRICE_DESC: 'price_desc',
  SALES_DESC: 'sales_desc'
})

export const DISH_SORT_OPTIONS = Object.freeze([
  { label: '综合排序', value: DISH_SORT.DEFAULT },
  { label: '价格从低到高', value: DISH_SORT.PRICE_ASC },
  { label: '价格从高到低', value: DISH_SORT.PRICE_DESC },
  { label: '销量优先', value: DISH_SORT.SALES_DESC }
])

export const COUPON_TYPE = Object.freeze({
  FULL_REDUCTION: 'FULL_REDUCTION'
})

export const COUPON_TYPE_LABELS = Object.freeze({
  FULL_REDUCTION: '满减券'
})

export const USER_COUPON_STATUS = Object.freeze({
  UNUSED: 'UNUSED',
  USED: 'USED',
  EXPIRED: 'EXPIRED'
})

export const USER_COUPON_STATUS_LABELS = Object.freeze({
  UNUSED: '未使用',
  USED: '已使用',
  EXPIRED: '已过期'
})

export const USER_COUPON_STATUS_TAG_TYPES = Object.freeze({
  UNUSED: 'success',
  USED: 'info',
  EXPIRED: 'info'
})

/** 订单列表一次查询的状态分组（docs/02-api.md §0.7 OrderStatusGroup） */
export const ORDER_STATUS_GROUP = Object.freeze({
  ALL: 'ALL',
  ACTIVE: 'ACTIVE',
  FINISHED: 'FINISHED'
})

export const ORDER_STATUS_GROUP_OPTIONS = Object.freeze([
  { label: '全部', value: ORDER_STATUS_GROUP.ALL },
  { label: '进行中', value: ORDER_STATUS_GROUP.ACTIVE },
  { label: '已完成', value: ORDER_STATUS_GROUP.FINISHED }
])

/* ===================== 分页 ===================== */

export const DEFAULT_PAGE_SIZE = 10
export const PAGE_SIZES = Object.freeze([10, 20, 50, 100])

/* ===================== 通用工具 ===================== */

/**
 * 按映射取中文文案
 * @param {Record<string, string>} labels
 * @param {string} value
 * @param {string} fallback
 */
export function labelOf(labels, value, fallback = '—') {
  if (!value) return fallback
  return labels[value] ?? fallback
}

/**
 * 按映射取 el-tag type
 * @param {Record<string, string>} tagTypes
 * @param {string} value
 * @param {string} fallback
 */
export function tagTypeOf(tagTypes, value, fallback = 'info') {
  if (!value) return fallback
  return tagTypes[value] ?? fallback
}

/**
 * 生成 el-select / el-radio-group 的选项
 * @param {Record<string, string>} labels
 * @param {{ exclude?: string[], include?: string[] }} options
 * @returns {{ label: string, value: string }[]}
 */
export function optionsOf(labels, options = {}) {
  const { exclude = [], include } = options
  return Object.entries(labels)
    .filter(([value]) => (include ? include.includes(value) : !exclude.includes(value)))
    .map(([value, label]) => ({ label, value }))
}
