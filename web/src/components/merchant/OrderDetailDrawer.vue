<script setup>
/**
 * 商家订单详情抽屉：拉取详情快照 + 状态时间线 + 可执行动作
 * 事件：done（动作成功）、refresh（父级需刷新列表）
 */
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { getMerchantOrder } from '@/api/merchantOrders'
import { formatAmount, formatDateTime } from '@/utils/format'
import {
  ORDER_STATUS_LABELS,
  ORDER_STATUS_TAG_TYPES,
  ORDER_TYPE,
  tagTypeOf
} from '@/utils/constants'
import { labelOf } from '@/utils/constants'
import OrderStatusTimeline from '@/components/customer/OrderStatusTimeline.vue'
import OrderActionButtons from '@/components/merchant/OrderActionButtons.vue'
import DataState from '@/components/DataState.vue'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  orderId: { type: [Number, String], default: null },
  orderType: { type: String, default: ORDER_TYPE.TAKEOUT }
})

const emit = defineEmits(['update:modelValue', 'done'])

const order = ref(null)
const loading = ref(false)
const error = ref('')

const typeLabel = (type) => (type === ORDER_TYPE.DINE_IN ? '堂食' : '外卖')

async function load() {
  if (!props.orderId) return
  loading.value = true
  error.value = ''
  try {
    order.value = await getMerchantOrder(props.orderType, props.orderId)
  } catch (err) {
    error.value = err?.message || '订单详情加载失败'
  } finally {
    loading.value = false
  }
}

watch(
  () => [props.modelValue, props.orderId, props.orderType],
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

function handleDone() {
  ElMessage.success('订单状态已更新')
  emit('done')
  load()
}
</script>

<template>
  <el-drawer
    :model-value="modelValue"
    :title="order ? `订单 ${order.orderNo}` : '订单详情'"
    size="620px"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <DataState :loading="loading" :error="error" @retry="load">
      <div v-if="order" class="order-detail">
        <div class="order-detail__head">
          <div class="order-detail__badges">
            <el-tag effect="plain">{{ typeLabel(order.orderType) }}</el-tag>
            <el-tag :type="tagTypeOf(ORDER_STATUS_TAG_TYPES, order.status)" effect="light">
              {{ labelOf(ORDER_STATUS_LABELS, order.status, order.statusText) }}
            </el-tag>
            <el-tag v-if="order.tableNo" type="warning" effect="plain">桌号 {{ order.tableNo }}</el-tag>
          </div>
          <div class="order-detail__actions">
            <OrderActionButtons :order="order" size="small" @done="handleDone" @refresh="load" />
          </div>
        </div>

        <el-descriptions :column="2" border size="small" class="order-detail__meta">
          <el-descriptions-item label="下单时间">
            {{ formatDateTime(order.createdAt, true) }}
          </el-descriptions-item>
          <el-descriptions-item label="支付时间">
            {{ order.paidAt ? formatDateTime(order.paidAt, true) : '—' }}
          </el-descriptions-item>
          <el-descriptions-item label="实付金额">
            <span class="amount">{{ formatAmount(order.payAmount) }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="商品金额">
            {{ formatAmount(order.totalAmount) }}
          </el-descriptions-item>
          <el-descriptions-item label="配送费">
            {{ formatAmount(order.deliveryFee) }}
          </el-descriptions-item>
          <el-descriptions-item label="优惠金额">
            {{ formatAmount(order.discountAmount) }}
          </el-descriptions-item>
          <el-descriptions-item label="收货人">
            {{ order.consignee || '—' }}
          </el-descriptions-item>
          <el-descriptions-item label="联系电话">
            {{ order.consigneePhone || '—' }}
          </el-descriptions-item>
          <el-descriptions-item label="收货地址" :span="2">
            {{ order.deliveryAddress || '—' }}
          </el-descriptions-item>
          <el-descriptions-item label="备注" :span="2">
            {{ order.remark || '无' }}
          </el-descriptions-item>
          <el-descriptions-item v-if="order.cancelReason" label="取消原因" :span="2">
            {{ order.cancelReason }}
          </el-descriptions-item>
        </el-descriptions>

        <h4 class="order-detail__title">菜品明细</h4>
        <el-table :data="order.items || []" size="small" border>
          <el-table-column prop="dishName" label="菜品" min-width="140" />
          <el-table-column prop="price" label="单价" width="90">
            <template #default="{ row }">{{ formatAmount(row.price) }}</template>
          </el-table-column>
          <el-table-column prop="quantity" label="数量" width="70" />
          <el-table-column prop="subtotal" label="小计" width="100">
            <template #default="{ row }">{{ formatAmount(row.subtotal) }}</template>
          </el-table-column>
        </el-table>

        <h4 class="order-detail__title">状态流转</h4>
        <OrderStatusTimeline :order="order" />
      </div>
    </DataState>

    <template #footer>
      <el-button @click="close">关闭</el-button>
    </template>
  </el-drawer>
</template>

<style scoped>
.order-detail {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}

.order-detail__head {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-3);
  align-items: center;
  justify-content: space-between;
}

.order-detail__badges {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
}

.order-detail__title {
  margin: var(--space-2) 0 0;
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-semibold);
}
</style>
