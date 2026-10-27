<script setup>
/**
 * 我的订单列表（T12）
 * 按 ALL / ACTIVE / FINISHED 分组查询；列表操作包括去支付、取消、确认收货、评价、查看详情。
 */
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import DataState from '@/components/DataState.vue'
import PaymentDialog from '@/components/customer/PaymentDialog.vue'
import ReviewDialog from '@/components/customer/ReviewDialog.vue'
import { listOrders, cancelOrder, confirmReceipt } from '@/api/orders'
import { formatAmount, formatDateTime } from '@/utils/format'
import {
  ORDER_STATUS,
  ORDER_STATUS_GROUP,
  ORDER_STATUS_GROUP_OPTIONS,
  ORDER_STATUS_LABELS,
  ORDER_STATUS_TAG_TYPES,
  ORDER_TYPE_LABELS,
  ORDER_TYPE_TAG_TYPES,
  labelOf,
  tagTypeOf
} from '@/utils/constants'

const router = useRouter()

const statusGroup = ref(ORDER_STATUS_GROUP.ALL)
const orders = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(5)
const loading = ref(false)
const error = ref('')
const actionLoadingId = ref(0)

const paymentVisible = ref(false)
const paymentOrder = ref(null)
const reviewVisible = ref(false)
const reviewOrder = ref(null)

const isEmpty = computed(() => !loading.value && !error.value && orders.value.length === 0)

async function load() {
  loading.value = true
  error.value = ''
  try {
    const data = await listOrders({
      statusGroup: statusGroup.value,
      page: page.value,
      size: size.value
    })
    orders.value = Array.isArray(data?.list) ? data.list : []
    total.value = Number(data?.total || 0)
  } catch (err) {
    error.value = err?.message || '订单加载失败'
    orders.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function changeGroup(value) {
  statusGroup.value = value
  page.value = 1
  load()
}

function onPageChange(value) {
  page.value = value
  load()
}

function canCancel(order) {
  return [ORDER_STATUS.PENDING_PAYMENT, ORDER_STATUS.PAID].includes(order.status)
}

function canPay(order) {
  return order.status === ORDER_STATUS.PENDING_PAYMENT
}

function canReviewOrder(order) {
  return order.status === ORDER_STATUS.COMPLETED && !order.reviewId
}

function openPay(order) {
  paymentOrder.value = {
    id: order.id,
    orderNo: order.orderNo,
    orderType: order.orderType,
    payAmount: order.payAmount
  }
  paymentVisible.value = true
}

async function onPaid() {
  paymentVisible.value = false
  ElMessage.success('支付成功，订单已进入商家队列')
  load()
}

async function handleCancel(order) {
  try {
    await ElMessageBox.confirm(`确定取消订单 ${order.orderNo} 吗？`, '取消订单', {
      type: 'warning',
      confirmButtonText: '取消订单',
      cancelButtonText: '再想想'
    })
  } catch {
    return
  }
  actionLoadingId.value = order.id
  try {
    await cancelOrder(order.id, '顾客主动取消')
    ElMessage.success('订单已取消')
    load()
  } catch (err) {
    ElMessage.error(err?.message || '取消失败')
  } finally {
    actionLoadingId.value = 0
  }
}

async function handleConfirmReceipt(order) {
  actionLoadingId.value = order.id
  try {
    await confirmReceipt(order.id)
    ElMessage.success('已确认收货')
    load()
  } catch (err) {
    ElMessage.error(err?.message || '确认收货失败')
  } finally {
    actionLoadingId.value = 0
  }
}

function openReview(order) {
  reviewOrder.value = { id: order.id }
  reviewVisible.value = true
}

function onReviewed() {
  reviewVisible.value = false
  load()
}

onMounted(load)
</script>

<template>
  <div class="app-container page orders-page">
    <header class="page-header orders-page__header">
      <div>
        <p class="page-eyebrow">Orders</p>
        <h1 class="page-title">我的订单</h1>
        <p class="page-subtitle">查看订单状态与配送进度，待支付订单可继续完成模拟支付</p>
      </div>
      <el-button @click="router.push('/menu')">
        <el-icon><Dish /></el-icon>
        再下一单
      </el-button>
    </header>

    <el-radio-group
      class="orders-page__tabs"
      :model-value="statusGroup"
      @update:model-value="changeGroup"
    >
      <el-radio-button
        v-for="option in ORDER_STATUS_GROUP_OPTIONS"
        :key="option.value"
        :value="option.value"
      >
        {{ option.label }}
      </el-radio-button>
    </el-radio-group>

    <DataState :loading="loading" :error="error" :empty="isEmpty" @retry="load">
      <template #empty>
        <el-empty description="还没有相关订单">
          <el-button type="primary" @click="router.push('/menu')">去点餐</el-button>
        </el-empty>
      </template>

      <ul class="order-list">
        <li v-for="order in orders" :key="order.id" class="order-card surface-card">
          <header class="order-card__head">
            <div class="order-card__meta">
              <el-tag size="small" effect="plain" :type="tagTypeOf(ORDER_TYPE_TAG_TYPES, order.orderType)">
                {{ labelOf(ORDER_TYPE_LABELS, order.orderType) }}
              </el-tag>
              <span class="order-card__no">#{{ order.orderNo }}</span>
              <span class="order-card__time">{{ formatDateTime(order.createdAt) }}</span>
            </div>
            <el-tag :type="tagTypeOf(ORDER_STATUS_TAG_TYPES, order.status)" effect="light" round>
              {{ order.statusText || labelOf(ORDER_STATUS_LABELS, order.status) }}
            </el-tag>
          </header>

          <div class="order-card__body">
            <div class="order-card__summary">
              <p class="order-card__items">
                {{ order.itemCount || 1 }} 件商品
                <span v-if="order.tableNo" class="order-card__table">· 桌号 {{ order.tableNo }}</span>
              </p>
              <p class="order-card__amount">
                实付 <span class="amount">{{ formatAmount(order.payAmount) }}</span>
              </p>
            </div>
            <div class="order-card__actions">
              <el-button text @click="router.push(`/orders/${order.id}`)">查看详情</el-button>
              <el-button
                v-if="canPay(order)"
                type="primary"
                size="small"
                @click="openPay(order)"
              >
                去支付
              </el-button>
              <el-button
                v-if="canCancel(order)"
                size="small"
                :loading="actionLoadingId === order.id"
                @click="handleCancel(order)"
              >
                取消订单
              </el-button>
              <el-button
                v-if="order.status === ORDER_STATUS.DELIVERING"
                type="primary"
                size="small"
                :loading="actionLoadingId === order.id"
                @click="handleConfirmReceipt(order)"
              >
                确认收货
              </el-button>
              <el-button v-if="canReviewOrder(order)" size="small" @click="openReview(order)">
                评价
              </el-button>
            </div>
          </div>
        </li>
      </ul>
    </DataState>

    <div v-if="total > size" class="orders-page__pagination">
      <el-pagination
        background
        layout="prev, pager, next, total"
        :total="total"
        :page-size="size"
        :current-page="page"
        @current-change="onPageChange"
      />
    </div>

    <PaymentDialog v-model="paymentVisible" :order="paymentOrder" @paid="onPaid" />
    <ReviewDialog v-model="reviewVisible" :order="reviewOrder" @success="onReviewed" />
  </div>
</template>

<style scoped>
.page-eyebrow {
  font-size: var(--font-size-xs);
  font-weight: var(--font-weight-medium);
  letter-spacing: 0.24em;
  text-transform: uppercase;
  color: var(--color-primary);
}

.orders-page__header {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  justify-content: space-between;
  gap: var(--space-4);
}

.orders-page__tabs {
  margin-bottom: var(--space-6);
}

.order-list {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}

.order-card {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}

.order-card__head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  padding-bottom: var(--space-4);
  border-bottom: 1px dashed var(--color-border);
}

.order-card__meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-3);
  font-size: var(--font-size-sm);
}

.order-card__no {
  font-family: var(--font-family-mono);
  color: var(--color-text-primary);
}

.order-card__time {
  color: var(--color-text-tertiary);
  font-size: var(--font-size-xs);
}

.order-card__body {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-4);
}

.order-card__items {
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
}

.order-card__table {
  color: var(--color-text-tertiary);
}

.order-card__amount {
  margin-top: var(--space-2);
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
}

.order-card__amount .amount {
  color: var(--color-primary);
  font-size: var(--font-size-lg);
  font-weight: var(--font-weight-semibold);
}

.order-card__actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
}

.orders-page__pagination {
  display: flex;
  justify-content: center;
  margin-top: var(--space-6);
}
</style>
