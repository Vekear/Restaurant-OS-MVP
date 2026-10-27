/**
 * 商家订单动作映射（docs/02-api.md §18）
 *
 * 外卖：PAID →(accept)→ ACCEPTED →(prepare)→ MAKING →(deliver)→ DELIVERING →(complete)→ COMPLETED
 * 堂食：PAID →(accept)→ ACCEPTED →(prepare)→ MAKING →(serve)→ SERVED →(complete)→ COMPLETED
 *
 * 说明：堂食单仅 prepare/serve/complete 有独立路径；接单/拒单复用 §18.3/§18.4 的
 * /merchant/orders/{id}/accept|reject（契约将它们归在「商家 · 订单（外卖 / 堂食）」下）。
 */
import {
  acceptTakeoutOrder,
  rejectTakeoutOrder,
  prepareTakeoutOrder,
  deliverTakeoutOrder,
  completeTakeoutOrder,
  prepareDineInOrder,
  serveDineInOrder,
  completeDineInOrder
} from '@/api/merchantOrders'
import { ORDER_STATUS, ORDER_TYPE } from '@/utils/constants'

/** 订单看板状态页签（按订单类型过滤不可用状态） */
export const ORDER_BOARD_TABS = Object.freeze([
  { key: 'ACTIVE', label: '全部进行中', statusGroup: 'ACTIVE' },
  { key: 'PAID', label: '待接单', status: ORDER_STATUS.PAID },
  { key: 'ACCEPTED', label: '已接单', status: ORDER_STATUS.ACCEPTED },
  { key: 'MAKING', label: '制作中', status: ORDER_STATUS.MAKING },
  { key: 'DELIVERING', label: '配送中', status: ORDER_STATUS.DELIVERING, orderType: ORDER_TYPE.TAKEOUT },
  { key: 'SERVED', label: '已出餐', status: ORDER_STATUS.SERVED, orderType: ORDER_TYPE.DINE_IN },
  { key: 'FINISHED', label: '已完成', statusGroup: 'FINISHED' },
  { key: 'ALL', label: '全部订单', statusGroup: 'ALL' }
])

/**
 * 按订单类型过滤页签
 * @param {'TAKEOUT'|'DINE_IN'} orderType
 */
export function boardTabsFor(orderType) {
  return ORDER_BOARD_TABS.filter((tab) => !tab.orderType || tab.orderType === orderType)
}

/**
 * 当前订单可执行的动作（前端仅做展示；合法性最终由后端状态机裁决，非法 → 40905）
 * @param {{ id:number, orderType?:string, status:string }} order
 * @returns {{ key:string, label:string, type?:string, plain?:boolean, icon?:string }[]}
 */
export function buildOrderActions(order) {
  const type = order?.orderType || ORDER_TYPE.TAKEOUT
  const status = order?.status
  const actions = []
  if (!status || status === ORDER_STATUS.PENDING_PAYMENT) return actions

  if (status === ORDER_STATUS.PAID) {
    actions.push({ key: 'accept', label: '接单', type: 'primary', icon: 'Check' })
    actions.push({ key: 'reject', label: '拒单', type: 'danger', plain: true, icon: 'Close' })
    return actions
  }
  if (status === ORDER_STATUS.ACCEPTED) {
    actions.push({ key: 'prepare', label: '开始制作', type: 'primary', icon: 'KnifeFork' })
    return actions
  }
  if (status === ORDER_STATUS.MAKING) {
    actions.push(
      type === ORDER_TYPE.DINE_IN
        ? { key: 'serve', label: '已出餐', type: 'primary', icon: 'Bowl' }
        : { key: 'deliver', label: '出餐配送', type: 'primary', icon: 'Promotion' }
    )
    return actions
  }
  if (status === ORDER_STATUS.DELIVERING) {
    actions.push({ key: 'complete', label: '完成订单', type: 'success', icon: 'CircleCheck' })
    return actions
  }
  if (status === ORDER_STATUS.SERVED) {
    actions.push({ key: 'complete', label: '结账完成', type: 'success', icon: 'CircleCheck' })
  }
  return actions
}

export const ORDER_ACTION_LABELS = Object.freeze({
  accept: '接单',
  reject: '拒单',
  prepare: '开始制作',
  deliver: '出餐配送',
  complete: '完成订单',
  serve: '已出餐'
})

/**
 * 执行动作
 * @param {{ id:number, orderType?:string }} order
 * @param {string} action
 * @param {{ reason?:string }} [extra]
 */
export function executeOrderAction(order, action, extra = {}) {
  const type = order?.orderType || ORDER_TYPE.TAKEOUT
  const id = order.id
  switch (action) {
    case 'accept':
      return acceptTakeoutOrder(id)
    case 'reject':
      return rejectTakeoutOrder(id, extra.reason)
    case 'prepare':
      return type === ORDER_TYPE.DINE_IN ? prepareDineInOrder(id) : prepareTakeoutOrder(id)
    case 'deliver':
      return deliverTakeoutOrder(id)
    case 'serve':
      return serveDineInOrder(id)
    case 'complete':
      return type === ORDER_TYPE.DINE_IN ? completeDineInOrder(id) : completeTakeoutOrder(id)
    default:
      return Promise.reject(new Error(`未知操作：${action}`))
  }
}
