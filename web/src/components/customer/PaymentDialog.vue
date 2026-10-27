<script setup>
/**
 * 模拟支付弹窗
 *
 * 展示：订单真实应付金额（orderAmount） + 实际模拟扣款金额（paidAmount，通常 0.01）。
 * 流程：打开 → POST /payments 创建支付单（待支付单会复用）→ 点击「模拟支付」→ mock-pay → 成功。
 * 幂等：创建与支付都自动携带 Idempotency-Key（由 utils/idempotency 保证重试复用）。
 */
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { createPayment, mockPay } from '@/api/payments'
import { createIdempotencyKey } from '@/utils/idempotency'
import { formatAmount } from '@/utils/format'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  order: { type: Object, default: null }
})

const emit = defineEmits(['update:modelValue', 'paid'])

const visible = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value)
})

const loading = ref(false)
const paying = ref(false)
const error = ref('')
const payment = ref(null)
const success = ref(null)
let createKey = ''
let payKey = ''

const orderAmount = computed(
  () =>
    success.value?.orderAmount ??
    payment.value?.orderAmount ??
    props.order?.payAmount ??
    props.order?.amount ??
    0
)
const paidAmount = computed(
  () => success.value?.paidAmount ?? payment.value?.paidAmount ?? Math.min(0.01, Number(orderAmount.value))
)
const mockHint = computed(
  () =>
    payment.value?.mockHint ||
    `订单金额 ¥${formatAmount(orderAmount.value)}；模拟支付扣款 ¥${formatAmount(paidAmount.value)}（演示环境）`
)

function resetState() {
  loading.value = false
  paying.value = false
  error.value = ''
  payment.value = null
  success.value = null
  createKey = ''
  payKey = ''
}

async function loadPayment() {
  if (!props.order?.id) return
  loading.value = true
  error.value = ''
  createKey = createKey || createIdempotencyKey('pay-create')
  try {
    payment.value = await createPayment(props.order.id, props.order.orderType || 'TAKEOUT', createKey)
    if (payment.value?.status === 'SUCCESS') {
      success.value = payment.value
    }
  } catch (err) {
    // 40913：已有成功支付单（重复支付），直接视为已支付
    if (err?.code === 40913) {
      success.value = { ...(props.order || {}), status: 'SUCCESS' }
      emit('paid', success.value)
    } else {
      error.value = err?.message || '支付单创建失败'
    }
  } finally {
    loading.value = false
  }
}

async function handlePay() {
  if (!payment.value?.payNo) return
  paying.value = true
  error.value = ''
  payKey = payKey || createIdempotencyKey('pay-mock')
  try {
    const result = await mockPay(payment.value.payNo, payKey)
    success.value = result
    ElMessage.success('模拟支付成功')
    emit('paid', result)
  } catch (err) {
    error.value = err?.message || '支付失败，请重试'
  } finally {
    paying.value = false
  }
}

function close() {
  visible.value = false
}

function handleClosed() {
  resetState()
}

watch(
  () => props.modelValue,
  (open) => {
    if (open) {
      resetState()
      loadPayment()
    }
  }
)
</script>

<template>
  <el-dialog
    v-model="visible"
    class="payment-dialog"
    width="460px"
    :close-on-click-modal="false"
    append-to-body
    @closed="handleClosed"
  >
    <template #header>
      <div class="payment-dialog__header">
        <h3>模拟支付</h3>
        <el-tag size="small" type="warning" effect="plain">演示环境</el-tag>
      </div>
    </template>

    <div v-if="loading" class="payment-dialog__loading">
      <el-skeleton :rows="3" animated />
    </div>

    <el-result
      v-else-if="success"
      icon="success"
      title="支付成功"
      :sub-title="`已模拟扣款 ¥${formatAmount(paidAmount)}，订单已进入商家待接单队列`"
    >
      <template #extra>
        <el-button type="primary" @click="close">查看订单</el-button>
      </template>
    </el-result>

    <template v-else>
      <el-alert
        v-if="error"
        class="payment-dialog__error"
        type="error"
        :title="error"
        :closable="false"
        show-icon
      />

      <div class="payment-dialog__amount">
        <span class="payment-dialog__label">订单应付金额</span>
        <strong class="payment-dialog__value amount">{{ formatAmount(orderAmount) }}</strong>
      </div>

      <el-descriptions :column="1" border size="small" class="payment-dialog__info">
        <el-descriptions-item label="订单号">{{ order?.orderNo || payment?.orderNo || '—' }}</el-descriptions-item>
        <el-descriptions-item label="支付单号">{{ payment?.payNo || '生成中…' }}</el-descriptions-item>
        <el-descriptions-item label="实际扣款">
          <span class="payment-dialog__paid">¥{{ formatAmount(paidAmount) }}</span>
        </el-descriptions-item>
        <el-descriptions-item v-if="payment?.expireAt" label="支付截止">
          {{ payment.expireAt }}
        </el-descriptions-item>
      </el-descriptions>

      <el-alert
        class="payment-dialog__hint"
        type="warning"
        :title="mockHint"
        :closable="false"
        show-icon
      />
    </template>

    <template v-if="!success" #footer>
      <el-button :disabled="paying" @click="close">稍后支付</el-button>
      <el-button
        type="primary"
        :loading="paying"
        :disabled="loading || !payment || Boolean(error)"
        @click="handlePay"
      >
        模拟支付 ¥{{ formatAmount(paidAmount) }}
      </el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.payment-dialog__header {
  display: flex;
  align-items: center;
  gap: var(--space-3);
}

.payment-dialog__header h3 {
  font-size: var(--font-size-lg);
}

.payment-dialog__loading {
  padding: var(--space-4) 0;
}

.payment-dialog__error,
.payment-dialog__hint {
  margin-bottom: var(--space-4);
}

.payment-dialog__amount {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  padding: var(--space-4) 0;
  border-bottom: 1px dashed var(--color-border);
}

.payment-dialog__label {
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
}

.payment-dialog__value {
  color: var(--color-primary);
  font-size: var(--font-size-3xl);
  font-weight: var(--font-weight-bold);
}

.payment-dialog__info {
  margin: var(--space-4) 0;
}

.payment-dialog__paid {
  color: var(--color-warning);
  font-weight: var(--font-weight-semibold);
}
</style>
