<script setup>
/**
 * 管理后台 · 订单详情抽屉（docs/02-api.md §20.7 → OrderDetailView）
 * 只读：展示订单快照、金额构成、支付信息与状态流转时间线。
 */
import { computed, ref, watch } from 'vue'
import { getOrder } from '@/api/admin'
import { formatAmount, formatCurrency, formatDateTime } from '@/utils/format'
import {
  ORDER_STATUS_LABELS,
  ORDER_STATUS_TAG_TYPES,
  ORDER_TYPE,
  PAYMENT_STATUS_LABELS,
  PAYMENT_STATUS_TAG_TYPES,
  labelOf,
  tagTypeOf
} from '@/utils/constants'
import DataState from '@/components/DataState.vue'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  orderId: { type: [Number, String], default: null }
})

const emit = defineEmits(['update:modelValue'])

const order = ref(null)
const loading = ref(false)
const error = ref('')

const isDineIn = computed(() => order.value?.orderType === ORDER_TYPE.DINE_IN)
const statusLogs = computed(() => {
  const logs = order.value?.statusLogs || []
  return [...logs].sort((a, b) => String(b.createdAt).localeCompare(String(a.createdAt)))
})

const operatorLabels = { CUSTOMER: '顾客', MERCHANT: '商家', ADMIN: '管理员', SYSTEM: '系统' }

async function load() {
  if (!props.orderId) return
  loading.value = true
  error.value = ''
  try {
    order.value = await getOrder(props.orderId)
  } catch (err) {
    error.value = err?.message || '订单详情加载失败'
  } finally {
    loading.value = false
  }
}

watch(
  () => [props.modelValue, props.orderId],
  ([visible]) => {
    if (visible) {
      order.value = null
      load()
    }
  },
  { immediate: true }
)

function close() {
  emit('update:modelValue', false)
}
</script>

<template>
  <el-drawer
    :model-value="modelValue"
    :title="order ? `订单 ${order.orderNo}` : '订单详情'"
    size="640px"
    destroy-on-close
    @update:model-value="emit('update:modelValue', $event)"
  >
    <DataState :loading="loading" :error="error" :empty="!loading && !error && !order" @retry="load">
      <template #empty>
        <el-empty description="请选择订单查看详情" />
      </template>

      <div v-if="order" class="order-detail">
        <div class="order-detail__head">
          <div>
            <p class="order-detail__no">{{ order.orderNo }}</p>
            <p class="order-detail__meta">
              {{ order.orderType === 'DINE_IN' ? '堂食' : '外卖' }} ·
              {{ order.merchantName || '—' }} · {{ formatDateTime(order.createdAt) }}
            </p>
          </div>
          <div class="order-detail__tags">
            <el-tag :type="tagTypeOf(ORDER_STATUS_TAG_TYPES, order.status, 'info')" effect="light">
              {{ order.statusText || labelOf(ORDER_STATUS_LABELS, order.status) }}
            </el-tag>
            <el-tag v-if="order.payment" type="info" effect="plain" size="small">
              支付 {{ labelOf(PAYMENT_STATUS_LABELS, order.payment.status) }}
            </el-tag>
          </div>
        </div>

        <el-descriptions :column="2" border size="small" class="order-detail__desc">
          <el-descriptions-item label="顾客">
            {{ order.username || (order.userId ? `用户 #${order.userId}` : '—') }}
          </el-descriptions-item>
          <el-descriptions-item label="商家">{{ order.merchantName || '—' }}</el-descriptions-item>
          <el-descriptions-item v-if="!isDineIn" label="收货人">
            {{ order.consignee || '—' }}
          </el-descriptions-item>
          <el-descriptions-item v-if="!isDineIn" label="联系电话">
            {{ order.consigneePhone || '—' }}
          </el-descriptions-item>
          <el-descriptions-item v-if="isDineIn" label="桌号">{{ order.tableNo || '—' }}</el-descriptions-item>
          <el-descriptions-item v-if="isDineIn" label="会话 ID">
            {{ order.tableSessionId || '—' }}
          </el-descriptions-item>
          <el-descriptions-item label="支付时间">{{ formatDateTime(order.paidAt) }}</el-descriptions-item>
          <el-descriptions-item label="完成时间">{{ formatDateTime(order.finishedAt) }}</el-descriptions-item>
          <el-descriptions-item v-if="!isDineIn" label="收货地址" :span="2">
            {{ order.deliveryAddress || '—' }}
          </el-descriptions-item>
          <el-descriptions-item label="备注" :span="2">{{ order.remark || '—' }}</el-descriptions-item>
          <el-descriptions-item v-if="order.cancelReason" label="取消/拒单原因" :span="2">
            {{ order.cancelReason }}
          </el-descriptions-item>
        </el-descriptions>

        <section class="order-detail__section">
          <h4>菜品明细</h4>
          <el-table :data="order.items || []" size="small" border>
            <el-table-column prop="dishName" label="菜品" min-width="160" />
            <el-table-column label="单价" width="110" align="right">
              <template #default="{ row }">{{ formatCurrency(row.price) }}</template>
            </el-table-column>
            <el-table-column prop="quantity" label="数量" width="70" align="center" />
            <el-table-column label="小计" width="110" align="right">
              <template #default="{ row }">{{ formatCurrency(row.subtotal) }}</template>
            </el-table-column>
          </el-table>
        </section>

        <section class="order-detail__section">
          <h4>金额与支付</h4>
          <div class="order-detail__amounts">
            <div><span>商品金额</span><b>{{ formatCurrency(order.totalAmount) }}</b></div>
            <div><span>配送费</span><b>{{ formatCurrency(order.deliveryFee) }}</b></div>
            <div><span>优惠金额</span><b>-{{ formatAmount(order.discountAmount) }}</b></div>
            <div class="is-total"><span>订单实付</span><b>{{ formatCurrency(order.payAmount) }}</b></div>
          </div>
          <el-descriptions v-if="order.payment" :column="2" border size="small" class="order-detail__payment">
            <el-descriptions-item label="支付单号">{{ order.payment.payNo }}</el-descriptions-item>
            <el-descriptions-item label="状态">
              <el-tag :type="tagTypeOf(PAYMENT_STATUS_TAG_TYPES, order.payment.status, 'info')" size="small">
                {{ labelOf(PAYMENT_STATUS_LABELS, order.payment.status) }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="订单金额">
              {{ formatCurrency(order.payment.orderAmount) }}
            </el-descriptions-item>
            <el-descriptions-item label="模拟扣款">
              {{ formatCurrency(order.payment.paidAmount) }}
            </el-descriptions-item>
            <el-descriptions-item label="交易号" :span="2">
              {{ order.payment.transactionNo || '—' }}
            </el-descriptions-item>
          </el-descriptions>
          <el-alert
            v-else
            type="info"
            :closable="false"
            show-icon
            title="暂无支付记录（待支付或未支付）"
          />
        </section>

        <section class="order-detail__section">
          <h4>状态流转</h4>
          <el-timeline v-if="statusLogs.length">
            <el-timeline-item
              v-for="(log, index) in statusLogs"
              :key="index"
              :timestamp="formatDateTime(log.createdAt, true)"
              placement="top"
              type="primary"
            >
              <p class="order-detail__log">
                {{ labelOf(ORDER_STATUS_LABELS, log.fromStatus, '—') }} →
                <b>{{ labelOf(ORDER_STATUS_LABELS, log.toStatus, '—') }}</b>
                <span class="order-detail__operator">
                  {{ operatorLabels[log.operatorRole] || log.operatorRole || '—' }}
                </span>
              </p>
              <p v-if="log.remark" class="order-detail__remark">{{ log.remark }}</p>
            </el-timeline-item>
          </el-timeline>
          <el-empty v-else description="暂无状态流转记录" :image-size="80" />
        </section>
      </div>
    </DataState>
  </el-drawer>
</template>

<style scoped>
.order-detail__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-4);
}

.order-detail__no {
  font-family: var(--font-family-number);
  font-size: var(--font-size-lg);
  font-weight: var(--font-weight-semibold);
}

.order-detail__meta {
  margin-top: var(--space-2);
  color: var(--color-text-tertiary);
  font-size: var(--font-size-xs);
}

.order-detail__tags {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
  justify-content: flex-end;
}

.order-detail__desc {
  margin-top: var(--space-5);
}

.order-detail__section {
  margin-top: var(--space-6);
}

.order-detail__section h4 {
  margin-bottom: var(--space-3);
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
}

.order-detail__amounts {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-2) var(--space-5);
  padding: var(--space-4);
  border-radius: var(--radius-md);
  background: var(--color-bg-subtle);
}

.order-detail__amounts div {
  display: flex;
  justify-content: space-between;
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
}

.order-detail__amounts .is-total {
  grid-column: 1 / -1;
  padding-top: var(--space-2);
  border-top: 1px dashed var(--color-border-subtle);
  color: var(--color-text-primary);
  font-size: var(--font-size-md);
}

.order-detail__payment {
  margin-top: var(--space-4);
}

.order-detail__log {
  font-size: var(--font-size-sm);
}

.order-detail__operator {
  margin-left: var(--space-2);
  color: var(--color-text-tertiary);
  font-size: var(--font-size-xs);
}

.order-detail__remark {
  margin-top: var(--space-1);
  color: var(--color-text-secondary);
  font-size: var(--font-size-xs);
}
</style>
