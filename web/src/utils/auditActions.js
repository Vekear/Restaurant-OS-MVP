/**
 * 审计日志动作字典（docs/02-api.md §20.20 / 01-architecture.md §16）
 * 后端 action 为枚举字符串；前端只做展示映射，未知动作回退原值。
 */
export const AUDIT_ACTION_LABELS = Object.freeze({
  LOGIN_SUCCESS: '登录成功',
  LOGIN_FAILURE: '登录失败',
  LOGOUT: '退出登录',
  USER_REGISTER: '用户注册',
  USER_STATUS_CHANGE: '用户状态变更',
  MERCHANT_AUDIT: '商家审核',
  MERCHANT_STATUS_CHANGE: '商家状态变更',
  MERCHANT_PROFILE_UPDATE: '商家资料更新',
  BUSINESS_STATUS_CHANGE: '营业状态变更',
  CATEGORY_CREATE: '新增分类',
  CATEGORY_UPDATE: '更新分类',
  CATEGORY_DELETE: '删除分类',
  DISH_CREATE: '新增菜品',
  DISH_UPDATE: '更新菜品',
  DISH_DELETE: '删除菜品',
  DISH_STATUS_CHANGE: '菜品上下架',
  DISH_STOCK_CHANGE: '库存调整',
  ORDER_CREATE: '创建订单',
  ORDER_STATUS_CHANGE: '订单状态变更',
  ORDER_CANCEL: '取消订单',
  ORDER_REVIEW: '订单评价',
  PAYMENT_CREATE: '创建支付单',
  PAYMENT_SUCCESS: '模拟支付成功',
  PAYMENT_REFUND: '模拟退款',
  TABLE_CREATE: '新增桌台',
  TABLE_UPDATE: '更新桌台',
  TABLE_DELETE: '删除桌台',
  TABLE_QR_REGENERATE: '桌台二维码重签',
  TABLE_SESSION_OPEN: '开台',
  TABLE_SESSION_CLOSE: '关台',
  COUPON_CREATE: '新增优惠券',
  COUPON_UPDATE: '更新优惠券',
  COUPON_DELETE: '删除优惠券',
  COUPON_STATUS_CHANGE: '优惠券状态变更',
  COUPON_CLAIM: '领取优惠券',
  FILE_UPLOAD: '文件上传',
  REPORT_EXPORT: '报表导出'
})

export const AUDIT_ACTION_OPTIONS = Object.freeze(
  Object.entries(AUDIT_ACTION_LABELS).map(([value, label]) => ({ value, label }))
)

/** 角色中文（审计日志行展示用） */
export const AUDIT_ROLE_LABELS = Object.freeze({
  ADMIN: '管理员',
  MERCHANT: '商家',
  CUSTOMER: '顾客',
  SYSTEM: '系统'
})

export function auditActionLabel(action) {
  return AUDIT_ACTION_LABELS[action] || action || '—'
}

export function auditRoleLabel(role) {
  return AUDIT_ROLE_LABELS[role] || role || '—'
}
