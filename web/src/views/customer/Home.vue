<script setup>
/**
 * 顾客端首页（T11 静态落地页）
 * 展示产品定位、双场景、三端入口与 ECharts 演示图表；
 * 菜品/订单等业务数据在 T12 接入后端，本页数据均标注「演示数据」。
 */
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowRight, Bowl, Shop } from '@element-plus/icons-vue'
import EChartPanel from '@/components/EChartPanel.vue'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const auth = useAuthStore()

const metrics = [
  { value: '32', unit: '款', label: '在售菜品 SKU' },
  { value: '18', unit: '分钟', label: '平均出餐时长' },
  { value: '96.4', unit: '%', label: '准时送达率' },
  { value: '<1', unit: '秒', label: '订单状态推送' }
]

const previewOrders = [
  { code: 'A1024', dish: '藤椒鸡丝凉面 · 2 份', status: '待接单', type: 'warning', amount: '46.00' },
  { code: 'A1023', dish: '香煎小黄鱼 · 1 份', status: '制作中', type: 'info', amount: '68.00' },
  { code: 'A1022', dish: '堂食 · A03 桌 · 4 人', status: '已出餐', type: 'success', amount: '132.00' }
]

const scenes = [
  {
    key: 'delivery',
    icon: 'Bowl',
    tag: 'Web 顾客端',
    title: '外卖点餐',
    desc: '浏览菜单、选择规格与加料、填写收货地址，模拟支付后进入商家接单队列。',
    features: ['菜品分类与规格加料', '收货地址与配送时段', '订单状态实时追踪'],
    cta: '开始点餐',
    path: '/menu',
    highlight: true
  },
  {
    key: 'dinein',
    icon: 'KnifeFork',
    tag: 'UniApp 微信小程序',
    title: '堂食扫码',
    desc: '扫描桌台二维码进入小程序点餐，桌台与订单双状态机联动，后厨实时收到加菜。',
    features: ['桌台二维码与占用状态', '叫号 / 加菜 / 结账', '后厨 KDS 实时刷新'],
    cta: '商家查看堂食订单',
    path: '/merchant/dine-in',
    highlight: false
  }
]

const roleCards = [
  {
    key: 'customer',
    icon: 'User',
    title: '顾客端',
    desc: '首页、菜单、购物车、订单与收货地址，账号体系与商家端打通。',
    features: ['customer / customer123', '模拟支付 0.01 元', '订单进度 WebSocket 推送'],
    cta: '去点餐',
    path: '/menu'
  },
  {
    key: 'merchant',
    icon: 'Shop',
    title: '商家工作台',
    desc: '外卖与堂食订单统一处理，菜品、桌台、营业报表一屏掌握。',
    features: ['merchant / merchant123', '接单 / 出餐 / 配送状态机', 'ECharts 营收报表'],
    cta: '进入工作台',
    path: '/merchant/dashboard'
  },
  {
    key: 'admin',
    icon: 'Monitor',
    title: '管理后台',
    desc: '平台级用户、商家、订单、优惠券与审计日志治理能力。',
    features: ['admin / admin123', '商家入驻审核', '全平台报表导出'],
    cta: '进入后台',
    path: '/admin/dashboard'
  }
]

const capabilities = [
  '订单双状态机',
  '桌台状态机',
  'WebSocket 实时推送',
  'OSS 直传 + 本地降级',
  'Spring Task 定时任务',
  'POI 报表导出',
  'Swagger 接口文档',
  'Nginx + MySQL + Redis'
]

const weekDays = ['周一', '周二', '周三', '周四', '周五', '周六', '周日']

const trendOption = computed(() => ({
  tooltip: { trigger: 'axis' },
  legend: { top: 0, right: 0, data: ['外卖订单', '堂食订单'] },
  grid: { left: 4, right: 8, top: 44, bottom: 4, containLabel: true },
  xAxis: { type: 'category', boundaryGap: false, data: weekDays },
  yAxis: { type: 'value', name: '单', nameTextStyle: { color: '#9a8f85', fontSize: 11 } },
  series: [
    {
      name: '外卖订单',
      type: 'line',
      smooth: true,
      symbolSize: 6,
      data: [42, 51, 47, 63, 78, 96, 88],
      areaStyle: { opacity: 0.14 }
    },
    {
      name: '堂食订单',
      type: 'line',
      smooth: true,
      symbolSize: 6,
      data: [30, 33, 36, 41, 52, 68, 61],
      areaStyle: { opacity: 0.12 }
    }
  ]
}))

function startOrder() {
  if (auth.isMerchant) router.push('/merchant/orders')
  else if (auth.isAdmin) router.push('/admin/dashboard')
  else router.push('/menu')
}

function goMerchant() {
  if (auth.isMerchant) router.push('/merchant/dashboard')
  else router.push({ name: 'login', query: { redirect: '/merchant/dashboard' } })
}
</script>

<template>
  <div class="home">
    <!-- ============ Hero ============ -->
    <section class="hero-wrap">
      <div class="app-container hero">
        <div class="hero__copy">
          <el-tag round effect="light" type="warning" class="hero__badge">
            外卖 + 堂食 · 一体化点餐 MVP
          </el-tag>
          <h1 class="hero__title">
            一桌好菜，
            <span>准时到家。</span>
          </h1>
          <p class="hero__desc">
            拾味点餐把外卖下单、堂食扫码、后厨出餐与经营报表收进同一套系统。订单状态真实流转、实时推送，
            商家后台一眼看清今日生意。
          </p>
          <div class="hero__actions">
            <el-button type="primary" size="large" class="hero__cta" @click="startOrder">
              <el-icon><Bowl /></el-icon>
              <span>开始点餐</span>
            </el-button>
            <el-button size="large" class="hero__cta" @click="goMerchant">
              <el-icon><Shop /></el-icon>
              <span>商家工作台</span>
            </el-button>
          </div>
          <dl class="hero__metrics">
            <div v-for="metric in metrics" :key="metric.label" class="hero__metric">
              <dt class="hero__metric-value">
                {{ metric.value }}<small>{{ metric.unit }}</small>
              </dt>
              <dd class="hero__metric-label">{{ metric.label }}</dd>
            </div>
          </dl>
          <p class="hero__note">以上指标为演示数据，接入后端后由真实订单统计生成。</p>
        </div>

        <div class="hero__visual" aria-hidden="true">
          <div class="preview-card">
            <header class="preview-card__head">
              <div>
                <p class="preview-card__eyebrow">商家工作台 · 实时订单</p>
                <p class="preview-card__title">今日订单</p>
              </div>
              <span class="preview-card__live"><i />LIVE</span>
            </header>
            <ul class="preview-card__list">
              <li v-for="order in previewOrders" :key="order.code" class="preview-row">
                <span class="preview-row__code">#{{ order.code }}</span>
                <span class="preview-row__dish">{{ order.dish }}</span>
                <el-tag size="small" :type="order.type" effect="light" round>
                  {{ order.status }}
                </el-tag>
                <span class="preview-row__amount">¥{{ order.amount }}</span>
              </li>
            </ul>
            <footer class="preview-card__foot">
              <span class="preview-card__dot" />
              后厨已接单 · WebSocket 推送中
            </footer>
          </div>
          <div class="float-chip float-chip--pay">
            <span class="float-chip__label">模拟支付</span>
            <strong>¥0.01</strong>
            <small>展示真实订单金额</small>
          </div>
          <div class="float-chip float-chip--status">
            <span class="float-chip__dot" />
            <span><strong>12</strong> 笔订单制作中</span>
          </div>
          <div class="hero__blob" />
        </div>
      </div>
    </section>

    <!-- ============ 双场景 ============ -->
    <section class="app-container section">
      <header class="section__head">
        <p class="section__eyebrow">Two scenes</p>
        <h2 class="section__title">一套系统，两种用餐场景</h2>
        <p class="section__desc">
          外卖 Web 与堂食小程序共享菜品、价格与订单模型，商家在一个工作台里处理全部订单。
        </p>
      </header>

      <div class="scene-grid">
        <article
          v-for="scene in scenes"
          :key="scene.key"
          class="scene-card"
          :class="{ 'is-highlight': scene.highlight }"
        >
          <span class="scene-card__icon"><el-icon><component :is="scene.icon" /></el-icon></span>
          <el-tag size="small" round effect="plain" class="scene-card__tag">{{ scene.tag }}</el-tag>
          <h3 class="scene-card__title">{{ scene.title }}</h3>
          <p class="scene-card__desc">{{ scene.desc }}</p>
          <ul class="scene-card__features">
            <li v-for="feature in scene.features" :key="feature">
              <el-icon><CircleCheck /></el-icon>{{ feature }}
            </li>
          </ul>
          <router-link :to="scene.path" class="scene-card__cta">
            {{ scene.cta }}
            <el-icon><ArrowRight /></el-icon>
          </router-link>
        </article>
      </div>
    </section>

    <!-- ============ 三端入口 ============ -->
    <section class="app-container section">
      <header class="section__head">
        <p class="section__eyebrow">Three roles</p>
        <h2 class="section__title">三端入口，一套账号体系</h2>
        <p class="section__desc">JWT + RBAC 角色隔离，登录后自动进入对应工作台。</p>
      </header>

      <div class="role-grid">
        <article v-for="role in roleCards" :key="role.key" class="role-card surface-card">
          <span class="role-card__icon"><el-icon><component :is="role.icon" /></el-icon></span>
          <h3 class="role-card__title">{{ role.title }}</h3>
          <p class="role-card__desc">{{ role.desc }}</p>
          <ul class="role-card__features">
            <li v-for="feature in role.features" :key="feature">{{ feature }}</li>
          </ul>
          <button type="button" class="role-card__cta" @click="router.push(role.path)">
            {{ role.cta }}
            <el-icon><ArrowRight /></el-icon>
          </button>
        </article>
      </div>
    </section>

    <!-- ============ 数据与能力 ============ -->
    <section class="app-container section section--last">
      <div class="insight-grid">
        <div class="surface-card chart-card">
          <header class="chart-card__head">
            <div>
              <h2 class="chart-card__title">近 7 天订单趋势</h2>
              <p class="chart-card__desc">演示数据，接入后端后由报表接口提供真实数据</p>
            </div>
            <el-tag type="warning" effect="plain" round>演示数据</el-tag>
          </header>
          <EChartPanel :option="trendOption" :height="292" />
        </div>

        <aside class="surface-card capability-card">
          <h2 class="capability-card__title">MVP 交付能力</h2>
          <p class="capability-card__desc">Web 骨架（T11）已完成，业务页面按阶段接入。</p>
          <ul class="capability-card__list">
            <li v-for="item in capabilities" :key="item">
              <el-icon><CircleCheck /></el-icon>
              {{ item }}
            </li>
          </ul>
          <router-link to="/menu" class="capability-card__cta">
            浏览菜单示例
            <el-icon><ArrowRight /></el-icon>
          </router-link>
        </aside>
      </div>
    </section>
  </div>
</template>

<style scoped>
.home {
  padding-bottom: var(--space-4);
}

/* ============ Hero ============ */
.hero-wrap {
  background:
    radial-gradient(900px 420px at 82% -12%, rgba(228, 87, 46, 0.12), transparent 64%),
    radial-gradient(700px 360px at 8% 0%, rgba(227, 154, 43, 0.1), transparent 60%),
    linear-gradient(180deg, #fffdfb 0%, var(--color-bg-page) 82%);
  border-bottom: 1px solid var(--color-border-subtle);
}

.hero {
  display: grid;
  grid-template-columns: 1.1fr 0.9fr;
  gap: var(--space-11);
  align-items: center;
  padding-top: var(--space-12);
  padding-bottom: var(--space-12);
}

.hero__badge {
  --el-tag-bg-color: var(--color-brand-50);
  --el-tag-text-color: var(--color-primary);
  font-size: var(--font-size-xs);
  letter-spacing: 0.04em;
}

.hero__title {
  margin-top: var(--space-6);
  font-size: clamp(36px, 4.4vw, 56px);
  line-height: 1.12;
  letter-spacing: -0.03em;
  font-weight: var(--font-weight-bold);
}

.hero__title span {
  display: inline-block;
  background: linear-gradient(100deg, var(--color-brand-500), var(--color-accent-500));
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}

.hero__desc {
  margin-top: var(--space-6);
  max-width: 520px;
  font-size: var(--font-size-lg);
  line-height: var(--line-height-loose);
  color: var(--color-text-secondary);
}

.hero__actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-4);
  margin-top: var(--space-8);
}

.hero__cta {
  height: 46px;
  padding: 0 var(--space-7);
  font-size: var(--font-size-md);
}

.hero__metrics {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: var(--space-4);
  margin-top: var(--space-10);
  padding-top: var(--space-7);
  border-top: 1px dashed var(--color-border);
}

.hero__metric-value {
  font-family: var(--font-family-number);
  font-size: var(--font-size-2xl);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-primary);
  font-variant-numeric: tabular-nums;
}

.hero__metric-value small {
  margin-left: 2px;
  font-size: var(--font-size-xs);
  font-weight: var(--font-weight-regular);
  color: var(--color-text-tertiary);
}

.hero__metric-label {
  margin: 2px 0 0;
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}

.hero__note {
  margin-top: var(--space-4);
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}

/* ============ Hero 可视化 ============ */
.hero__visual {
  position: relative;
  display: flex;
  justify-content: center;
  padding: var(--space-8) var(--space-4);
}

.hero__blob {
  position: absolute;
  inset: 8% 4% auto auto;
  width: 340px;
  height: 340px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(228, 87, 46, 0.18), transparent 66%);
  filter: blur(4px);
  z-index: 0;
}

.preview-card {
  position: relative;
  z-index: 1;
  width: 100%;
  max-width: 400px;
  padding: var(--space-6);
  background: rgba(255, 253, 251, 0.92);
  border: 1px solid var(--color-border-subtle);
  border-radius: var(--radius-xl);
  box-shadow: var(--shadow-xl);
  backdrop-filter: blur(6px);
}

.preview-card__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-4);
  padding-bottom: var(--space-5);
  border-bottom: 1px dashed var(--color-border);
}

.preview-card__eyebrow {
  font-size: 11px;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: var(--color-text-tertiary);
}

.preview-card__title {
  margin-top: var(--space-2);
  font-size: var(--font-size-lg);
  font-weight: var(--font-weight-semibold);
}

.preview-card__live {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  padding: 2px var(--space-3);
  border-radius: var(--radius-pill);
  background: rgba(46, 158, 107, 0.1);
  color: var(--color-success);
  font-size: 10px;
  font-weight: var(--font-weight-semibold);
  letter-spacing: 0.1em;
}

.preview-card__live i {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: var(--color-success);
  animation: live-pulse 1.6s ease-in-out infinite;
}

@keyframes live-pulse {
  0%,
  100% {
    opacity: 1;
  }
  50% {
    opacity: 0.3;
  }
}

.preview-card__list {
  display: flex;
  flex-direction: column;
}

.preview-row {
  display: grid;
  grid-template-columns: 56px 1fr auto auto;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-4) 0;
  border-bottom: 1px solid var(--color-border-subtle);
  font-size: var(--font-size-sm);
}

.preview-row__code {
  font-family: var(--font-family-mono);
  font-size: 11px;
  color: var(--color-text-tertiary);
}

.preview-row__dish {
  color: var(--color-text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.preview-row__amount {
  font-family: var(--font-family-number);
  font-weight: var(--font-weight-semibold);
  font-size: var(--font-size-sm);
  color: var(--color-text-primary);
  font-variant-numeric: tabular-nums;
}

.preview-card__foot {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  padding-top: var(--space-4);
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}

.preview-card__dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--color-success);
}

.float-chip {
  position: absolute;
  z-index: 2;
  padding: var(--space-4) var(--space-5);
  background: var(--color-surface);
  border: 1px solid var(--color-border-subtle);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-md);
}

.float-chip--pay {
  left: 0;
  bottom: 12%;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.float-chip__label {
  font-size: 10px;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: var(--color-text-tertiary);
}

.float-chip--pay strong {
  font-family: var(--font-family-number);
  font-size: var(--font-size-xl);
  color: var(--color-primary);
}

.float-chip--pay small {
  font-size: 10px;
  color: var(--color-text-tertiary);
}

.float-chip--status {
  right: 2%;
  top: 6%;
  display: flex;
  align-items: center;
  gap: var(--space-3);
  font-size: var(--font-size-xs);
  color: var(--color-text-secondary);
}

.float-chip--status strong {
  color: var(--color-text-primary);
}

.float-chip__dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--color-warning);
  box-shadow: 0 0 0 4px rgba(227, 154, 43, 0.16);
}

/* ============ 通用 section ============ */
.section {
  padding-top: var(--space-12);
}

.section--last {
  padding-bottom: var(--space-12);
}

.section__head {
  max-width: 620px;
  margin-bottom: var(--space-8);
}

.section__eyebrow {
  font-size: var(--font-size-xs);
  font-weight: var(--font-weight-medium);
  letter-spacing: 0.24em;
  text-transform: uppercase;
  color: var(--color-primary);
}

.section__title {
  margin-top: var(--space-3);
  font-size: var(--font-size-3xl);
  letter-spacing: -0.02em;
}

.section__desc {
  margin-top: var(--space-3);
  color: var(--color-text-secondary);
  line-height: var(--line-height-loose);
}

/* ============ 场景卡片 ============ */
.scene-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--space-6);
}

.scene-card {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
  padding: var(--space-8);
  background: var(--color-surface);
  border: 1px solid var(--color-border-subtle);
  border-radius: var(--radius-xl);
  box-shadow: var(--shadow-xs);
  overflow: hidden;
  transition: transform var(--duration-base) var(--ease-out),
    box-shadow var(--duration-base) var(--ease-out);
}

.scene-card::after {
  content: '';
  position: absolute;
  inset: 0 0 auto 0;
  height: 4px;
  background: var(--color-border);
}

.scene-card.is-highlight::after {
  background: linear-gradient(90deg, var(--color-brand-500), var(--color-accent-500));
}

.scene-card:hover {
  transform: translateY(-3px);
  box-shadow: var(--shadow-md);
}

.scene-card__icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 44px;
  border-radius: var(--radius-md);
  background: var(--color-brand-50);
  color: var(--color-primary);
  font-size: 20px;
}

.scene-card__tag {
  align-self: flex-start;
}

.scene-card__title {
  font-size: var(--font-size-2xl);
}

.scene-card__desc {
  color: var(--color-text-secondary);
  line-height: var(--line-height-loose);
}

.scene-card__features {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
}

.scene-card__features li {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

.scene-card__features .el-icon {
  color: var(--color-success);
  font-size: 13px;
}

.scene-card__cta {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  margin-top: var(--space-2);
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-medium);
}

/* ============ 角色卡片 ============ */
.role-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: var(--space-6);
}

.role-card {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}

.role-card__icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  border-radius: var(--radius-md);
  background: var(--color-bg-subtle);
  color: var(--color-text-primary);
  font-size: 18px;
}

.role-card__title {
  font-size: var(--font-size-xl);
}

.role-card__desc {
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
  line-height: var(--line-height-loose);
}

.role-card__features {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  margin-top: var(--space-2);
  padding-top: var(--space-4);
  border-top: 1px dashed var(--color-border);
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}

.role-card__features li::before {
  content: '·';
  margin-right: var(--space-2);
  color: var(--color-primary);
}

.role-card__cta {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  margin-top: auto;
  padding: var(--space-3) 0 0;
  border: none;
  background: transparent;
  color: var(--color-primary);
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-medium);
  cursor: pointer;
  text-align: left;
}

.role-card__cta:hover {
  color: var(--color-primary-hover);
}

/* ============ 图表与能力 ============ */
.insight-grid {
  display: grid;
  grid-template-columns: 1.55fr 1fr;
  gap: var(--space-6);
  align-items: stretch;
}

.chart-card {
  display: flex;
  flex-direction: column;
  gap: var(--space-5);
}

.chart-card__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-4);
}

.chart-card__title {
  font-size: var(--font-size-xl);
}

.chart-card__desc {
  margin-top: var(--space-2);
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}

.capability-card {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
  background: linear-gradient(165deg, #2a211c, #35251d 62%, #4a2a1c);
  border-color: transparent;
  color: rgba(233, 224, 216, 0.78);
}

.capability-card__title {
  font-size: var(--font-size-xl);
  color: var(--color-text-inverse);
}

.capability-card__desc {
  font-size: var(--font-size-xs);
  color: rgba(207, 197, 187, 0.62);
}

.capability-card__list {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--space-3);
  font-size: var(--font-size-sm);
}

.capability-card__list li {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

.capability-card__list .el-icon {
  color: var(--color-accent-300);
  font-size: 12px;
}

.capability-card__cta {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  margin-top: auto;
  padding-top: var(--space-4);
  border-top: 1px solid rgba(255, 255, 255, 0.1);
  color: var(--color-accent-300);
  font-size: var(--font-size-sm);
}

/* ============ 响应式 ============ */
@media (max-width: 1100px) {
  .hero {
    grid-template-columns: 1fr;
    gap: var(--space-9);
  }

  .hero__visual {
    order: -1;
    padding-top: 0;
  }

  .preview-card {
    max-width: 520px;
  }

  .float-chip--pay {
    left: 2%;
  }

  .role-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 900px) {
  .scene-grid,
  .insight-grid {
    grid-template-columns: 1fr;
  }

  .capability-card__list {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 640px) {
  .hero {
    padding-top: var(--space-9);
    padding-bottom: var(--space-9);
  }

  .hero__metrics {
    grid-template-columns: 1fr 1fr;
    gap: var(--space-5);
  }

  .float-chip--pay {
    display: none;
  }

  .float-chip--status {
    right: 0;
    top: 0;
  }

  .scene-card {
    padding: var(--space-6);
  }
}
</style>
