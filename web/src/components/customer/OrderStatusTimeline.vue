<script setup>
/**
 * 订单状态时间线
 * - 顶部：进度条（外卖含配送中，堂食到「已出餐」为止）
 * - 底部：order_status_log 流转记录（from → to、操作方、备注、时间）
 */
import { computed } from 'vue'
import { formatDateTime } from '@/utils/format'
import { ORDER_STATUS, ORDER_STATUS_LABELS } from '@/utils/constants'

const props = defineProps({
  order: { type: Object, required: true }
})

const TAKEOUT_STEPS = [
  { status: ORDER_STATUS.PENDING_PAYMENT, title: '待支付' },
  { status: ORDER_STATUS.PAID, title: '待接单' },
  { status: ORDER_STATUS.ACCEPTED, title: '已接单' },
  { status: ORDER_STATUS.MAKING, title: '制作中' },
  { status: ORDER_STATUS.DELIVERING, title: '配送中' },
  { status: ORDER_STATUS.COMPLETED, title: '已完成' }
]

const DINE_IN_STEPS = [
  { status: ORDER_STATUS.PENDING_PAYMENT, title: '待支付' },
  { status: ORDER_STATUS.PAID, title: '待接单' },
  { status: ORDER_STATUS.ACCEPTED, title: '已接单' },
  { status: ORDER_STATUS.MAKING, title: '制作中' },
  { status: ORDER_STATUS.SERVED, title: '已出餐' },
  { status: ORDER_STATUS.COMPLETED, title: '已完成' }
]

const steps = computed(() =>
  props.order?.orderType === 'DINE_IN' ? DINE_IN_STEPS : TAKEOUT_STEPS
)

const activeStep = computed(() => {
  const index = steps.value.findIndex((step) => step.status === props.order?.status)
  return index < 0 ? 0 : index
})

const isTerminalAbnormal = computed(() =>
  [ORDER_STATUS.CANCELLED, ORDER_STATUS.REJECTED].includes(props.order?.status)
)

const OPERATOR_LABELS = {
  CUSTOMER: '顾客',
  MERCHANT: '商家',
  ADMIN: '管理员',
  SYSTEM: '系统'
}

const logs = computed(() => {
  const list = Array.isArray(props.order?.statusLogs) ? [...props.order.statusLogs] : []
  return list.reverse().map((log) => ({
    key: `${log.fromStatus || 'init'}-${log.toStatus}-${log.createdAt}`,
    from: log.fromStatus ? ORDER_STATUS_LABELS[log.fromStatus] || log.fromStatus : '创建订单',
    to: ORDER_STATUS_LABELS[log.toStatus] || log.toStatus,
    operator: OPERATOR_LABELS[log.operatorRole] || log.operatorRole || '系统',
    remark: log.remark,
    createdAt: log.createdAt
  }))
})
</script>

<template>
  <section class="order-timeline">
    <el-alert
      v-if="isTerminalAbnormal"
      class="order-timeline__alert"
      :title="order.status === 'CANCELLED' ? '订单已取消' : '订单已被商家拒单'"
      :description="order.cancelReason || '如有疑问请联系商家'"
      type="info"
      :closable="false"
      show-icon
    />
    <el-steps v-else :active="activeStep" finish-status="success" align-center class="order-timeline__steps">
      <el-step v-for="(step, index) in steps" :key="step.status" :title="step.title">
        <template #description>
          <span v-if="index === activeStep" class="order-timeline__current">当前</span>
        </template>
      </el-step>
    </el-steps>

    <div v-if="logs.length" class="order-timeline__logs">
      <h4 class="order-timeline__title">状态流转</h4>
      <el-timeline>
        <el-timeline-item
          v-for="log in logs"
          :key="log.key"
          :timestamp="formatDateTime(log.createdAt, true)"
          placement="top"
        >
          <p class="order-timeline__log">
            <span>{{ log.from }}</span>
            <el-icon><Right /></el-icon>
            <strong>{{ log.to }}</strong>
            <el-tag size="small" effect="plain" type="info">{{ log.operator }}</el-tag>
          </p>
          <p v-if="log.remark" class="order-timeline__remark">{{ log.remark }}</p>
        </el-timeline-item>
      </el-timeline>
    </div>
  </section>
</template>

<style scoped>
.order-timeline {
  display: flex;
  flex-direction: column;
  gap: var(--space-6);
}

.order-timeline__steps {
  padding: var(--space-5) var(--space-2) var(--space-2);
}

.order-timeline__current {
  color: var(--color-primary);
  font-size: var(--font-size-xs);
}

.order-timeline__title {
  margin-bottom: var(--space-4);
  font-size: var(--font-size-md);
  color: var(--color-text-secondary);
}

.order-timeline__log {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  color: var(--color-text-primary);
  font-size: var(--font-size-sm);
}

.order-timeline__log strong {
  color: var(--color-primary);
}

.order-timeline__remark {
  margin-top: var(--space-2);
  color: var(--color-text-tertiary);
  font-size: var(--font-size-xs);
}
</style>
