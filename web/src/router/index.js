/**
 * 路由表：顾客端（CustomerLayout）、商家工作台（MerchantLayout）、管理后台（AdminLayout）、
 * 登录注册以及 403/404。
 *
 * 约定：
 * - meta.public     无需登录即可访问
 * - meta.guestOnly  已登录用户访问时跳回自己的 homePath（登录/注册页）
 * - meta.roles      RBAC 允许的角色（子路由自动合并父级 meta）
 * - meta.phase / planned  占位页展示的所属阶段与计划能力
 */
import { createRouter, createWebHistory } from 'vue-router'
import AdminLayout from '@/layouts/AdminLayout.vue'
import CustomerLayout from '@/layouts/CustomerLayout.vue'
import MerchantLayout from '@/layouts/MerchantLayout.vue'
import { setupRouterGuards } from '@/router/guards'

const Home = () => import('@/views/customer/Home.vue')
const Menu = () => import('@/views/customer/Menu.vue')
const DishDetail = () => import('@/views/customer/DishDetail.vue')
const Cart = () => import('@/views/customer/Cart.vue')
const Checkout = () => import('@/views/customer/Checkout.vue')
const Orders = () => import('@/views/customer/Orders.vue')
const OrderDetail = () => import('@/views/customer/OrderDetail.vue')
const Addresses = () => import('@/views/customer/Addresses.vue')
const MerchantDashboard = () => import('@/views/merchant/Dashboard.vue')
const MerchantOrders = () => import('@/views/merchant/Orders.vue')
const MerchantDishes = () => import('@/views/merchant/Dishes.vue')
const MerchantTables = () => import('@/views/merchant/Tables.vue')
const MerchantStats = () => import('@/views/merchant/Stats.vue')
const Login = () => import('@/views/auth/Login.vue')
const Register = () => import('@/views/auth/Register.vue')
const Placeholder = () => import('@/views/common/Placeholder.vue')
const Forbidden = () => import('@/views/common/Forbidden.vue')
const NotFound = () => import('@/views/common/NotFound.vue')

const T14 = '阶段 5 · T14'

const routes = [
  {
    path: '/login',
    name: 'login',
    component: Login,
    meta: { title: '登录', public: true, guestOnly: true }
  },
  {
    path: '/register',
    name: 'register',
    component: Register,
    meta: { title: '注册', public: true, guestOnly: true }
  },

  /* ==================== 顾客端 ==================== */
  {
    path: '/',
    component: CustomerLayout,
    children: [
      {
        path: '',
        name: 'customer-home',
        component: Home,
        meta: { title: '首页', public: true }
      },
      {
        path: 'menu',
        name: 'customer-menu',
        component: Menu,
        meta: {
          title: '菜单',
          description: '按分类浏览菜品，支持关键词搜索与价格、销量排序。',
          public: true
        }
      },
      {
        path: 'menu/:dishId',
        name: 'customer-dish-detail',
        component: DishDetail,
        meta: {
          title: '菜品详情',
          description: '菜品图片、简介、库存与顾客评价。',
          public: true
        }
      },
      {
        path: 'cart',
        name: 'customer-cart',
        component: Cart,
        meta: {
          title: '购物车',
          description: '确认菜品与数量后进入结算。',
          roles: ['CUSTOMER']
        }
      },
      {
        path: 'checkout',
        name: 'customer-checkout',
        component: Checkout,
        meta: {
          title: '确认订单',
          description: '选择收货地址、使用优惠券并完成模拟支付（实际扣款 0.01 元）。',
          roles: ['CUSTOMER']
        }
      },
      {
        path: 'orders',
        name: 'customer-orders',
        component: Orders,
        meta: {
          title: '我的订单',
          description: '按状态查看历史订单与进行中的订单。',
          roles: ['CUSTOMER']
        }
      },
      {
        path: 'orders/:orderId',
        name: 'customer-order-detail',
        component: OrderDetail,
        meta: {
          title: '订单详情',
          description: '查看订单状态流转、金额明细与操作入口。',
          roles: ['CUSTOMER']
        }
      },
      {
        path: 'addresses',
        name: 'customer-addresses',
        component: Addresses,
        meta: {
          title: '收货地址',
          description: '维护常用收货地址并设置默认地址。',
          roles: ['CUSTOMER']
        }
      }
    ]
  },

  /* ==================== 商家工作台 ==================== */
  {
    path: '/merchant',
    component: MerchantLayout,
    redirect: '/merchant/dashboard',
    meta: { title: '商家工作台', roles: ['MERCHANT'] },
    children: [
      {
        path: 'dashboard',
        name: 'merchant-dashboard',
        component: MerchantDashboard,
        meta: {
          title: '经营概览',
          description: '今日营业额、订单结构、待接单数量与低库存预警，WebSocket 实时刷新。'
        }
      },
      {
        path: 'orders',
        name: 'merchant-orders',
        component: MerchantOrders,
        props: { orderType: 'TAKEOUT' },
        meta: {
          title: '外卖订单',
          description: '外卖订单看板：接单 → 制作中 → 出餐配送 → 完成，非法流转由后端拒绝。'
        }
      },
      {
        path: 'dine-in',
        name: 'merchant-dine-in',
        component: MerchantOrders,
        props: { orderType: 'DINE_IN' },
        meta: {
          title: '堂食订单',
          description: '按桌号处理扫码订单：接单 → 制作中 → 已出餐 → 完成（关闭桌台会话）。'
        }
      },
      {
        path: 'dishes',
        name: 'merchant-dishes',
        component: MerchantDishes,
        meta: {
          title: '菜品管理',
          description: '分类与菜品维护、库存调整、上下架及图片上传（OSS 直传降级本地）。'
        }
      },
      {
        path: 'tables',
        name: 'merchant-tables',
        component: MerchantTables,
        meta: {
          title: '桌台管理',
          description: '桌台 CRUD、占用状态与二维码查看 / 下载 / 重签。'
        }
      },
      {
        path: 'stats',
        name: 'merchant-stats',
        component: MerchantStats,
        meta: {
          title: '营业报表',
          description: '营业额趋势、热销菜品、订单类型占比与 Excel 导出。'
        }
      }
    ]
  },

  /* ==================== 平台管理后台 ==================== */
  {
    path: '/admin',
    component: AdminLayout,
    redirect: '/admin/dashboard',
    meta: { title: '平台管理后台', roles: ['ADMIN'] },
    children: [
      {
        path: 'dashboard',
        name: 'admin-dashboard',
        component: Placeholder,
        meta: {
          title: '平台概览',
          description: '全平台用户、商家、订单与营收核心指标。',
          phase: T14,
          planned: [
            { title: '平台指标', desc: '用户数、商家数、订单量与成交额' },
            { title: '商家排行', desc: '按营收与订单量排序的商家榜' },
            { title: '趋势图表', desc: 'ECharts 展示平台整体走势' }
          ]
        }
      },
      {
        path: 'users',
        name: 'admin-users',
        component: Placeholder,
        meta: {
          title: '用户管理',
          description: '顾客与商家账号检索、角色调整与禁用。',
          phase: T14,
          planned: [
            { title: '用户检索', desc: '用户名、手机号、角色筛选' },
            { title: '角色分配', desc: 'RBAC 角色变更与权限校验' },
            { title: '状态管理', desc: '启用 / 禁用与操作记录' }
          ]
        }
      },
      {
        path: 'merchants',
        name: 'admin-merchants',
        component: Placeholder,
        meta: {
          title: '商家管理',
          description: '商家入驻审核、营业状态与店铺信息。',
          phase: T14,
          planned: [
            { title: '入驻审核', desc: '资质信息查看与通过 / 驳回' },
            { title: '营业状态', desc: '营业中 / 休息中 与平台强制下架' },
            { title: '店铺详情', desc: '店铺信息、菜品与订单概览' }
          ]
        }
      },
      {
        path: 'orders',
        name: 'admin-orders',
        component: Placeholder,
        meta: {
          title: '订单管理',
          description: '全平台订单检索、状态追踪与异常处理。',
          phase: T14,
          planned: [
            { title: '订单检索', desc: '按订单号、商家、状态与时间范围筛选' },
            { title: '状态追踪', desc: '查看双状态机流转历史' },
            { title: '异常处理', desc: '退款、取消与人工纠正' }
          ]
        }
      },
      {
        path: 'coupons',
        name: 'admin-coupons',
        component: Placeholder,
        meta: {
          title: '优惠券',
          description: '券模板、发放与核销统计。',
          phase: T14,
          planned: [
            { title: '券模板', desc: '满减 / 折扣、有效期与库存' },
            { title: '定向发放', desc: '按用户或商家发放优惠券' },
            { title: '核销统计', desc: '领取、使用与过期数据' }
          ]
        }
      },
      {
        path: 'audit',
        name: 'admin-audit',
        component: Placeholder,
        meta: {
          title: '审计日志',
          description: '关键操作留痕，支持按操作者与时间检索。',
          phase: T14,
          planned: [
            { title: '日志列表', desc: '操作人、动作、目标与时间' },
            { title: '检索筛选', desc: '按模块与关键字过滤' },
            { title: '详情快照', desc: '请求前后数据对比（脱敏）' }
          ]
        }
      },
      {
        path: 'stats',
        name: 'admin-stats',
        component: Placeholder,
        meta: {
          title: '数据报表',
          description: '跨商家营收分析、导出与对账。',
          phase: T14,
          planned: [
            { title: '平台营收', desc: '商家维度的成交额与退款额' },
            { title: '对比分析', desc: '同比 / 环比与品类结构' },
            { title: '报表导出', desc: 'POI 导出 Excel 并记录导出审计' }
          ]
        }
      }
    ]
  },

  /* ==================== 错误页 ==================== */
  {
    path: '/403',
    name: 'forbidden',
    component: Forbidden,
    meta: { title: '无权访问', public: true }
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'not-found',
    component: NotFound,
    meta: { title: '页面不存在', public: true }
  }
]

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes,
  scrollBehavior(to, from, savedPosition) {
    if (savedPosition) return savedPosition
    if (to.hash) return { el: to.hash, top: 72, behavior: 'smooth' }
    return { top: 0 }
  }
})

setupRouterGuards(router)

export default router
