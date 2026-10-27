<script setup>
/**
 * 订单详情（T12）
 * 展示双状态机进度、菜品明细、收货信息、金额构成、状态日志与评价；
 * 按后端返回的 canPay/canCancel/canConfirmReceipt/canReview 渲染操作按钮。
 */
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import DishThumb from '@/components/DishThumb.vue'
import DataState from '@/components/DataState.vue'
import OrderStatusTimeline from '@/components/customer/OrderStatusTimeline.vue'
import PaymentDialog from '@/components/customer/PaymentDialog.vue'
import ReviewDialog from '@/components/customer/ReviewDialog.vue'
import { getOrder, cancelOrder, confirmReceipt, getOrderReview } from '@/api/orders'
import {
  formatAmount,
  formatCountdown,
  formatDateTime,
  remainingMs
} from '@/utils/format'
import {
  ORDER_STATUS,
  ORDER_STATUS_LABELS,
  ORDER_STATUS_TAG_TYPES,
  ORDER_TYPE_LABELS,
  ORDER_TYPE_TAG_TYPES,
  labelOf,
  tagTypeOf
} from '@/utils/constants'

const route = useRoute()
const router = useRouter()

const order = ref(null)
const loading = ref(true)
const error = ref('')
const actionLoading = ref(false)
const paymentVisible = ref(false)
const reviewVisible = ref(false)
const review = ref(null)
const countdown = ref('')
let timer = null

const orderId = computed(() => Number(route.params.orderId))
const isPendingPayment = computed(() => order.value?.status === ORDER_STATUS.PENDING_PAYMENT)

function startCountdown() {
  stopCountdown()
  if (!isPendingPayment.value || !order.value?.timeoutAt) {
    countdown.value = ''
    return
  }
  const tick = () => {
    const ms = remainingMs(order.value?.timeoutAt)
    countdown.value = ms > 0 ? formatCountdown(ms) : ''
    if (ms <= 0) stopCountdown()
  }
  tick()
  timer = setInterval(tick, 1000)
}

function stopCountdown() {
  if (timer) {
    clearInterval(timer)
    timer = null
  }
}

async function loadOrder() {
  loading.value = true
  error.value = ''
  try {
    order.value = await getOrder(orderId.value)
    startCountdown()
    loadReviewIfNeeded()
  } catch (err) {
    error.value = err?.message || '订单加载失败'
    order.value = null
  } finally {
    loading.value = false
  }
}

async function loadReviewIfNeeded() {
  if (!order.value?.reviewId) {
    review.value = null
    return
  }
  try {
    review.value = await getOrderReview(orderId.value)
  } catch {
    review.value = null
  }
}

function errorText(err) {
  const map = {
    40905: '当前状态不允许该操作',
    40913: '订单已支付'
  }
  return map[err?.code] || err?.message || '操作失败，请稍后重试'
}

async function handleCancel() {
  try {
    await ElMessageBox.confirm('取消后无法恢复，确定取消该订单吗？', '取消订单', {
      type: 'warning',
      confirmButtonText: '取消订单',
      cancelButtonText: '再想想'
    })
  } catch {
    return
  }
  actionLoading.value = true
  try {
    await cancelOrder(orderId.value, '顾客主动取消')
    ElMessage.success('订单已取消')
    await loadOrder()
  } catch (err) {
    ElMessage.error(errorText(err))
  } finally {
    actionLoading.value = false
  }
}

async function handleConfirmReceipt() {
  actionLoading.value = true
  try {
    await confirmReceipt(orderId.value)
    ElMessage.success('已确认收货')
    await loadOrder()
  } catch (err) {
    ElMessage.error(errorText(err))
  } finally {
    actionLoading.value = false
  }
}

async function onPaid() {
  paymentVisible.value = false
  ElMessage.success('支付成功')
  if (route.query.pay) {
    router.replace({ path: route.path })
  }
  await loadOrder()
}

function onReviewed() {
  reviewVisible.value = false
  loadOrder()
}

watch(
  () => route.fullPath,
  () => {
    if (route.name === 'customer-order-detail') loadOrder()
  }
)

onMounted(loadOrder)
onBeforeUnmount(stopCountdown)
</script>

<template>
  <div class="app-container page order-detail-page">
    <header class="order-detail__top">
      <el-button text @click="router.push('/orders')">
        <el-icon><Back /></el-icon>
        返回订单列表
      </el-button>
      <el-button text :loading="loading" @click="loadOrder">
        <el-icon><Refresh /></el-icon>
        刷新
      </el-button>
    </header>

    <DataState :loading="loading" :error="error" :empty="!order && !loading" @retry="loadOrder">
      <template #empty>
        <el-empty description="订单不存在">
          <el-button type="primary" @click="router.push('/orders')">返回我的订单</el-button>
        </el-empty>
      </template>

      <template v-if="order">
        <section class="order-hero surface-card">
          <div>
            <div class="order-hero__meta">
              <el-tag size="small" effect="plain" :type="tagTypeOf(ORDER_TYPE_TAG_TYPES, order.orderType)">
                {{ labelOf(ORDER_TYPE_LABELS, order.orderType) }}
              </el-tag>
              <span class="order-hero__no">#{{ order.orderNo }}</span>
            </div>
            <h1 class="order-hero__status">
              {{ order.statusText || labelOf(ORDER_STATUS_LABELS, order.status) }}
            </h1>
            <p class="order-hero__time">下单时间 {{ formatDateTime(order.createdAt, true) }}</p>
            <p v-if="countdown" class="order-hero__countdown">
              剩余支付时间 <strong>{{ countdown }}</strong>，超时订单将自动取消
            </p>
          </div>
          <div class="order-hero__side">
            <span class="order-hero__amount-label">实付金额</span>
            <strong class="order-hero__amount amount">{{ formatAmount(order.payAmount) }}</strong>
            <el-tag :type="tagTypeOf(ORDER_STATUS_TAG_TYPES, order.status)" effect="light" round>
              {{ labelOf(ORDER_STATUS_LABELS, order.status) }}
            </el-tag>
          </div>
        </section>

        <div class="order-detail__layout">
          <div class="order-detail__main">
            <section class="surface-card order-section">
              <h2 class="order-section__title">订单进度</h2>
              <OrderStatusTimeline :order="order" />
            </section>

            <section class="surface-card order-section">
              <h2 class="order-section__title">菜品明细</h2>
              <ul class="order-items">
                <li v-for="item in order.items" :key="item.dishId" class="order-item">
                  <div class="order-item__media">
                    <DishThumb :src="item.dishImage" :alt="item.dishName" radius="sm" />
                  </div>
                  <div class="order-item__info">
                    <span>{{ item.dishName }}</span>
                    <small class="text-tertiary">¥{{ formatAmount(item.price) }} × {{ item.quantity }}</small>
                  </div>
                  <span class="amount">{{ formatAmount(item.subtotal) }}</span>
                </li>
              </ul>
            </section>

            <section class="surface-card order-section">
              <h2 class="order-section__title">收货与备注</h2>
              <dl class="order-facts">
                <div v-if="order.orderType === 'DINE_IN'">
                  <dt>桌号</dt>
                  <dd>{{ order.tableNo || '—' }}</dd>
                </div>
                <template v-else>
                  <div>
                    <dt>收货人</dt>
                    <dd>{{ order.consignee || '—' }} {{ order.consigneePhone || '' }}</dd>
                  </div>
                  <div>
                    <dt>收货地址</dt>
                    <dd>{{ order.deliveryAddress || '—' }}</dd>
                  </div>
                </template>
                <div v-if="order.merchantName">
                  <dt>商家</dt>
                  <dd>{{ order.merchantName }}</dd>
                </div>
                <div>
                  <dt>订单备注</dt>
                  <dd>{{ order.remark || '无' }}</dd>
                </div>
                <div v-if="order.cancelReason">
                  <dt>取消原因</dt>
                  <dd class="text-danger">{{ order.cancelReason }}</dd>
                </div>
              </dl>
            </section>

            <section v-if="review" class="surface-card order-section">
              <h2 class="order-section__title">我的评价</h2>
              <div class="order-review">
                <el-rate :model-value="review.rating" disabled />
                <p class="order-review__content">{{ review.content || '未填写评价内容' }}</p>
                <p class="order-review__time">{{ formatDateTime(review.createdAt) }}</p>
              </div>
            </section>
          </div>

          <aside class="order-detail__side">
            <section class="surface-card order-section order-summary">
              <h2 class="order-section__title">金额明细</h2>
              <dl class="order-summary__rows">
                <div>
                  <dt>商品金额</dt>
                  <dd class="amount">{{ formatAmount(order.totalAmount) }}</dd>
                </div>
                <div>
                  <dt>配送费</dt>
                  <dd class="amount">{{ formatAmount(order.deliveryFee || 0) }}</dd>
                </div>
                <div>
                  <dt>优惠抵扣</dt>
                  <dd class="amount text-danger">-{{ formatAmount(order.discountAmount || 0) }}</dd>
                </div>
              </dl>
              <div class="order-summary__total">
                <span>实付金额</span>
                <strong class="amount">{{ formatAmount(order.payAmount) }}</strong>
              </div>

              <div class="order-actions">
                <el-button
                  v-if="order.canPay"
                  type="primary"
                  size="large"
                  class="order-actions__btn"
                  @click="paymentVisible = true"
                >
                  去支付
                </el-button>
                <el-button
                  v-if="order.canCancel"
                  size="large"
                  class="order-actions__btn"
                  :loading="actionLoading"
                  @click="handleCancel"
                >
                  取消订单
                </el-button>
                <el-button
                  v-if="order.canConfirmReceipt"
                  type="primary"
                  size="large"
                  class="order-actions__btn"
                  :loading="actionLoading"
                  @click="handleConfirmReceipt"
                >
                  确认收货
                </el-button>
                <el-button
                  v-if="order.canReview"
                  type="primary"
                  plain
                  size="large"
                  class="order-actions__btn"
                  @click="reviewVisible = true"
                >
                  评价订单
                </el-button>
                <el-button size="large" class="order-actions__btn" @click="router.push('/menu')">
                  再来一单
                </el-button>
              </div>
              <p class="order-actions__note">演示环境：模拟支付实际扣款 ¥0.01，订单金额按实展示。</p>
            </section>
          </aside>
        </div>
      </template>
    </DataState>

    <PaymentDialog v-model="paymentVisible" :order="order" @paid="onPaid" />
    <ReviewDialog v-model="reviewVisible" :order="order" @success="onReviewed" />
  </div>
</template>

<style scoped>
.order-detail__top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--space-5);
}

.order-hero {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-6);
  padding: var(--space-8);
  background:
    radial-gradient(600px 220px at 100% 0%, rgba(228, 87, 46, 0.1), transparent 70%),
    var(--color-surface);
}

.order-hero__meta {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  font-size: var(--font-size-sm);
}

.order-hero__no {
  font-family: var(--font-family-mono);
  color: var(--color-text-secondary);
}

.order-hero__status {
  margin-top: var(--space-3);
  font-size: var(--font-size-3xl);
  letter-spacing: -0.02em;
}

.order-hero__time {
  margin-top: var(--space-2);
  color: var(--color-text-tertiary);
  font-size: var(--font-size-sm);
}

.order-hero__countdown {
  margin-top: var(--space-3);
  color: var(--color-warning);
  font-size: var(--font-size-sm);
}

.order-hero__countdown strong {
  font-variant-numeric: tabular-nums;
}

.order-hero__side {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: var(--space-2);
}

.order-hero__amount-label {
  color: var(--color-text-tertiary);
  font-size: var(--font-size-xs);
}

.order-hero__amount {
  color: var(--color-primary);
  font-size: var(--font-size-4xl);
  font-weight: var(--font-weight-bold);
}

.order-detail__layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 320px;
  gap: var(--space-6);
  margin-top: var(--space-5);
  align-items: start;
}

.order-detail__main {
  display: flex;
  flex-direction: column;
  gap: var(--space-5);
}

.order-section__title {
  margin-bottom: var(--space-4);
  font-size: var(--font-size-lg);
}

.order-items {
  display: flex;
  flex-direction: column;
}

.order-item {
  display: flex;
  align-items: center;
  gap: var(--space-4);
  padding: var(--space-3) 0;
  border-bottom: 1px solid var(--color-border-subtle);
}

.order-item:last-child {
  border-bottom: none;
}

.order-item__media {
  width: 56px;
  height: 46px;
  overflow: hidden;
  border-radius: var(--radius-sm);
}

.order-item__info {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 2px;
  font-size: var(--font-size-md);
}

.order-facts {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
  font-size: var(--font-size-sm);
}

.order-facts div {
  display: flex;
  gap: var(--space-4);
}

.order-facts dt {
  min-width: 72px;
  color: var(--color-text-tertiary);
}

.order-facts dd {
  flex: 1;
  color: var(--color-text-primary);
}

.order-review__content {
  margin-top: var(--space-3);
  color: var(--color-text-secondary);
  line-height: var(--line-height-loose);
}

.order-review__time {
  margin-top: var(--space-2);
  color: var(--color-text-tertiary);
  font-size: var(--font-size-xs);
}

.order-detail__side {
  position: sticky;
  top: calc(var(--layout-header-height) + var(--space-6));
}

.order-summary__rows {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
  padding-bottom: var(--space-4);
  border-bottom: 1px dashed var(--color-border);
  font-size: var(--font-size-sm);
}

.order-summary__rows div {
  display: flex;
  justify-content: space-between;
}

.order-summary__rows dd {
  color: var(--color-text-secondary);
}

.order-summary__total {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-top: var(--space-4);
}

.order-summary__total strong {
  color: var(--color-primary);
  font-size: var(--font-size-2xl);
}

.order-actions {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
  margin-top: var(--space-5);
}

.order-actions__btn {
  width: 100%;
  margin-left: 0;
}

.order-actions__note {
  margin-top: var(--space-3);
  color: var(--color-text-tertiary);
  font-size: var(--font-size-xs);
  line-height: var(--line-height-loose);
}

@media (max-width: 1000px) {
  .order-detail__layout {
    grid-template-columns: 1fr;
  }

  .order-detail__side {
    position: static;
  }
}
</style>
