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
const Login = () => import('@/views/auth/Login.vue')
const Register = () => import('@/views/auth/Register.vue')
const Placeholder = () => import('@/views/common/Placeholder.vue')
const Forbidden = () => import('@/views/common/Forbidden.vue')
const NotFound = () => import('@/views/common/NotFound.vue')

const T12 = '阶段 5 · T12'
const T13 = '阶段 5 · T13'
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
        component: Placeholder,
        meta: {
          title: '菜单',
          description: '按分类浏览菜品，选择规格与加料后加入购物车。',
          phase: T12,
          public: true,
          planned: [
            { title: '菜品分类与搜索', desc: '左侧分类锚点 + 关键词搜索，支持上下架状态过滤' },
            { title: '规格与加料', desc: '规格单选、加料多选，实时计算单价与总价' },
            { title: '加入购物车', desc: '购物车抽屉与数量步进器，登录校验后结算' }
          ]
        }
      },
      {
        path: 'menu/:dishId',
        name: 'customer-dish-detail',
        component: Placeholder,
        meta: {
          title: '菜品详情',
          description: '菜品大图、简介、规格选择与用户评价。',
          phase: T12,
          public: true,
          planned: [
            { title: '菜品信息', desc: '图片、描述、辣度与过敏原提示' },
            { title: '规格选择', desc: '规格/加料与库存联动，售罄时禁用下单' },
            { title: '评价片段', desc: '展示最近评价与评分分布' }
          ]
        }
      },
      {
        path: 'cart',
        name: 'customer-cart',
        component: Placeholder,
        meta: {
          title: '购物车',
          description: '确认菜品、数量与金额，进入结算流程。',
          phase: T12,
          roles: ['CUSTOMER'],
          planned: [
            { title: '购物车列表', desc: '菜品明细、数量调整与删除' },
            { title: '金额计算', desc: '商品小计、配送费与优惠券抵扣' },
            { title: '去结算', desc: '校验地址与配送时段后创建订单' }
          ]
        }
      },
      {
        path: 'checkout',
        name: 'customer-checkout',
        component: Placeholder,
        meta: {
          title: '确认订单',
          description: '选择收货地址、配送时段并完成模拟支付（实际扣款 0.01 元）。',
          phase: T12,
          roles: ['CUSTOMER'],
          planned: [
            { title: '收货地址', desc: '地址选择与新增，校验手机号与门牌号' },
            { title: '订单备注', desc: '餐具份数、口味备注与期望送达时间' },
            { title: '模拟支付', desc: '展示真实金额，实际扣款 0.01 元并写入支付流水' }
          ]
        }
      },
      {
        path: 'orders',
        name: 'customer-orders',
        component: Placeholder,
        meta: {
          title: '我的订单',
          description: '按状态查看历史订单与进行中的订单。',
          phase: T12,
          roles: ['CUSTOMER'],
          planned: [
            { title: '订单列表', desc: '状态标签筛选、分页与空态' },
            { title: '订单详情', desc: '菜品明细、金额构成与配送进度' },
            { title: '实时状态', desc: '订阅 /topic/customer/{id}/orders 刷新状态' }
          ]
        }
      },
      {
        path: 'orders/:orderId',
        name: 'customer-order-detail',
        component: Placeholder,
        meta: {
          title: '订单详情',
          description: '查看订单状态流转、配送信息与售后入口。',
          phase: T12,
          roles: ['CUSTOMER'],
          planned: [
            { title: '状态时间线', desc: '待接单 → 制作中 → 配送中 → 已完成' },
            { title: '金额明细', desc: '商品、配送费、优惠与实付金额' },
            { title: '操作入口', desc: '取消订单、联系商家与再次购买' }
          ]
        }
      },
      {
        path: 'addresses',
        name: 'customer-addresses',
        component: Placeholder,
        meta: {
          title: '收货地址',
          description: '维护常用收货地址并设置默认地址。',
          phase: T12,
          roles: ['CUSTOMER'],
          planned: [
            { title: '地址列表', desc: '联系人、电话、详细地址与默认标记' },
            { title: '新增/编辑', desc: '地址表单校验与默认地址切换' },
            { title: '地址选择', desc: '结算页快捷选择最近使用的地址' }
          ]
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
        component: Placeholder,
        meta: {
          title: '经营概览',
          description: '今日营收、订单结构、出餐效率与实时订单看板。',
          phase: T13,
          planned: [
            { title: '核心指标卡', desc: '营业额、订单数、客单价与退款率' },
            { title: '实时订单流', desc: 'WebSocket 推送的新订单与出餐提醒' },
            { title: '趋势图表', desc: 'ECharts 展示近 7 天营业额与订单量' }
          ]
        }
      },
      {
        path: 'orders',
        name: 'merchant-orders',
        component: Placeholder,
        meta: {
          title: '外卖订单',
          description: '接单 → 出餐 → 配送 → 完成 的外部订单状态机操作。',
          phase: T13,
          planned: [
            { title: '订单看板', desc: '按状态分列展示，支持一键接单与出餐' },
            { title: '状态机操作', desc: '仅允许合法流转，非法操作由后端拒绝' },
            { title: '实时提醒', desc: 'WebSocket 新订单铃声与角标提醒' }
          ]
        }
      },
      {
        path: 'dine-in',
        name: 'merchant-dine-in',
        component: Placeholder,
        meta: {
          title: '堂食订单',
          description: '扫码点餐订单处理，与桌台状态机联动。',
          phase: T13,
          planned: [
            { title: '堂食订单列表', desc: '桌号、人数、菜品与加菜记录' },
            { title: '桌台联动', desc: '开台 / 就餐中 / 待清台 状态同步' },
            { title: '结账与清台', desc: '结账后自动释放桌台并生成营收' }
          ]
        }
      },
      {
        path: 'dishes',
        name: 'merchant-dishes',
        component: Placeholder,
        meta: {
          title: '菜品管理',
          description: '菜品分类、规格、库存、上下架与图片直传。',
          phase: T13,
          planned: [
            { title: '菜品列表', desc: '分类树、搜索与批量上下架' },
            { title: '编辑表单', desc: '规格/加料配置与价格库存校验' },
            { title: '图片直传', desc: 'OSS 直传，失败时降级本地 /files 存储' }
          ]
        }
      },
      {
        path: 'tables',
        name: 'merchant-tables',
        component: Placeholder,
        meta: {
          title: '桌台管理',
          description: '桌台区域、二维码与占用状态管理。',
          phase: T13,
          planned: [
            { title: '桌台网格', desc: '按区域展示桌台与实时状态色' },
            { title: '二维码生成', desc: '生成桌台二维码供小程序扫码点餐' },
            { title: '状态维护', desc: '空闲 / 就餐中 / 待清台手动纠偏' }
          ]
        }
      },
      {
        path: 'stats',
        name: 'merchant-stats',
        component: Placeholder,
        meta: {
          title: '营业报表',
          description: '营业额趋势、热销菜品与 POI 报表导出。',
          phase: T13,
          planned: [
            { title: '营收趋势', desc: '按日 / 周 / 月切换的 ECharts 折线图' },
            { title: '热销排行', desc: '菜品销量与销售额 Top N' },
            { title: '导出报表', desc: '后端 POI 生成 Excel，前端下载流式文件' }
          ]
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
