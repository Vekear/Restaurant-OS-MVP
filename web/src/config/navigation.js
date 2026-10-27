/**
 * 三端导航配置（顾客 / 商家 / 管理后台）
 * icon 名称取自 @element-plus/icons-vue（已在 main.js 全局注册）
 */

export const customerNav = [
  { title: '首页', path: '/', icon: 'HomeFilled', exact: true },
  { title: '菜单', path: '/menu', icon: 'Dish' },
  { title: '我的订单', path: '/orders', icon: 'List', requiresAuth: true },
  { title: '收货地址', path: '/addresses', icon: 'Location', requiresAuth: true }
]

export const merchantNav = [
  {
    title: '经营概览',
    path: '/merchant/dashboard',
    icon: 'DataLine',
    description: '今日营收、订单结构与出餐效率'
  },
  {
    title: '外卖订单',
    path: '/merchant/orders',
    icon: 'List',
    description: '接单、出餐、配送全流程状态机',
    realtime: true
  },
  {
    title: '堂食订单',
    path: '/merchant/dine-in',
    icon: 'Tickets',
    description: '扫码点餐与桌台状态联动',
    realtime: true
  },
  { title: '菜品管理', path: '/merchant/dishes', icon: 'Dish', description: '分类、规格、库存与上下架' },
  { title: '桌台管理', path: '/merchant/tables', icon: 'Grid', description: '二维码与桌台占用情况' },
  { title: '营业报表', path: '/merchant/stats', icon: 'TrendCharts', description: 'POI 导出与 ECharts 营收分析' }
]

export const adminNav = [
  { title: '平台概览', path: '/admin/dashboard', icon: 'DataAnalysis', description: '全平台核心指标' },
  { title: '用户管理', path: '/admin/users', icon: 'User', description: '顾客与商家账号、角色分配' },
  { title: '商家管理', path: '/admin/merchants', icon: 'Shop', description: '入驻审核与营业状态' },
  { title: '订单管理', path: '/admin/orders', icon: 'List', description: '全平台订单检索与异常处理' },
  { title: '优惠券', path: '/admin/coupons', icon: 'Discount', description: '券模板、发放与核销统计' },
  { title: '审计日志', path: '/admin/audit', icon: 'Memo', description: '关键操作留痕与追踪' },
  { title: '数据报表', path: '/admin/stats', icon: 'TrendCharts', description: '跨商家营收与导出' }
]

export const consoleProfiles = {
  merchant: {
    theme: 'merchant',
    basePath: '/merchant',
    title: '商家工作台',
    subtitle: '外卖 + 堂食一体化经营',
    nav: merchantNav
  },
  admin: {
    theme: 'admin',
    basePath: '/admin',
    title: '平台管理后台',
    subtitle: '用户、商家与全平台订单治理',
    nav: adminNav
  }
}

/** 按当前路由找到导航项（控制台面包屑用） */
export function findNavItem(nav, path) {
  return nav.find((item) => item.path === path) || null
}
