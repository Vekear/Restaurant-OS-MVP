<script setup>
/**
 * 管理后台 · 商家审核 / 停启用弹窗（docs/02-api.md §20.5）
 * - 审核：APPROVED / REJECTED（驳回必填原因 2–200）
 * - 状态：ENABLED / DISABLED（禁用建议填原因，写审计）
 */
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { updateMerchantStatus } from '@/api/admin'
import {
  MERCHANT_AUDIT_STATUS,
  MERCHANT_AUDIT_STATUS_LABELS,
  MERCHANT_BUSINESS_STATUS_LABELS,
  MERCHANT_STATUS_LABELS
} from '@/utils/constants'
import { formatDateTime } from '@/utils/format'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  merchant: { type: Object, default: null }
})

const emit = defineEmits(['update:modelValue', 'saved'])

const saving = ref(false)
const form = reactive({ action: 'approve', reason: '' })

const actions = computed(() => {
  const merchant = props.merchant || {}
  const auditLabels = MERCHANT_AUDIT_STATUS_LABELS || {}
  const statusLabels = MERCHANT_STATUS_LABELS || {}
  return [
    {
      value: 'approve',
      label: '审核通过',
      desc: '通过后商家可登录商家工作台并接单',
      disabled: merchant.auditStatus === MERCHANT_AUDIT_STATUS.APPROVED,
      current: auditLabels[merchant.auditStatus] || merchant.auditStatus
    },
    {
      value: 'reject',
      label: '审核驳回',
      desc: '驳回后商家需修改资料重新提交（原因必填）',
      disabled: merchant.auditStatus === MERCHANT_AUDIT_STATUS.REJECTED,
      current: auditLabels[merchant.auditStatus] || merchant.auditStatus
    },
    {
      value: 'disable',
      label: '禁用商家',
      desc: '禁用后该商家所有接口返回 40303',
      disabled: merchant.status === 'DISABLED',
      current: statusLabels[merchant.status] || merchant.status
    },
    {
      value: 'enable',
      label: '恢复商家',
      desc: '恢复后商家可正常营业接单',
      disabled: merchant.status === 'ENABLED',
      current: statusLabels[merchant.status] || merchant.status
    }
  ]
})

const reasonRequired = computed(() => form.action === 'reject')

const canSubmit = computed(() => {
  const action = actions.value.find((item) => item.value === form.action)
  if (!action || action.disabled) return false
  if (reasonRequired.value) {
    const length = form.reason.trim().length
    return length >= 2 && length <= 200
  }
  return form.reason.length <= 200
})

watch(
  () => props.modelValue,
  (visible) => {
    if (!visible) return
    const merchant = props.merchant || {}
    form.reason = ''
    form.action = merchant.auditStatus === MERCHANT_AUDIT_STATUS.APPROVED ? 'enable' : 'approve'
    if (merchant.status === 'DISABLED') form.action = 'enable'
  },
  { immediate: true }
)

function close() {
  emit('update:modelValue', false)
}

async function submit() {
  if (!props.merchant?.id || !canSubmit.value) return
  const payload = {}
  if (form.action === 'approve') payload.auditStatus = MERCHANT_AUDIT_STATUS.APPROVED
  if (form.action === 'reject') payload.auditStatus = MERCHANT_AUDIT_STATUS.REJECTED
  if (form.action === 'disable') payload.status = 'DISABLED'
  if (form.action === 'enable') payload.status = 'ENABLED'
  const reason = form.reason.trim()
  if (reason) payload.reason = reason
  saving.value = true
  try {
    await updateMerchantStatus(props.merchant.id, payload)
    ElMessage.success('商家状态已更新')
    emit('saved')
    close()
  } catch (error) {
    ElMessage.error(error?.message || '操作失败，请稍后重试')
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    title="商家审核与状态"
    width="560px"
    destroy-on-close
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div v-if="merchant" class="audit">
      <div class="audit__head">
        <div>
          <p class="audit__name">{{ merchant.name }}</p>
          <p class="audit__meta">
            店主 {{ merchant.ownerUsername || '—' }} · 入驻
            {{ formatDateTime(merchant.createdAt) || '—' }}
          </p>
        </div>
        <div class="audit__tags">
          <el-tag size="small" effect="light">
            {{ MERCHANT_AUDIT_STATUS_LABELS[merchant.auditStatus] || merchant.auditStatus || '—' }}
          </el-tag>
          <el-tag size="small" type="info" effect="light">
            {{ MERCHANT_STATUS_LABELS[merchant.status] || merchant.status || '—' }}
          </el-tag>
          <el-tag v-if="merchant.businessStatus" size="small" type="warning" effect="plain">
            {{ MERCHANT_BUSINESS_STATUS_LABELS[merchant.businessStatus] || merchant.businessStatus }}
          </el-tag>
        </div>
      </div>

      <el-radio-group v-model="form.action" class="audit__actions">
        <label
          v-for="item in actions"
          :key="item.value"
          class="audit__action"
          :class="{ 'is-disabled': item.disabled }"
        >
          <el-radio :value="item.value" :disabled="item.disabled">
            <span class="audit__action-label">{{ item.label }}</span>
            <span class="audit__action-desc">{{ item.desc }}</span>
          </el-radio>
        </label>
      </el-radio-group>

      <el-form label-position="top" class="audit__form">
        <el-form-item :label="reasonRequired ? '驳回原因（必填，2–200 字）' : '操作原因（选填，≤200 字）'">
          <el-input
            v-model="form.reason"
            type="textarea"
            :rows="3"
            maxlength="200"
            show-word-limit
            :placeholder="reasonRequired ? '请说明驳回原因，便于商家修改' : '例如：资质复核、违规经营等'"
          />
        </el-form-item>
      </el-form>

      <el-alert
        v-if="reasonRequired && form.reason.trim().length < 2 && form.reason.length > 0"
        type="warning"
        :closable="false"
        show-icon
        title="驳回原因至少 2 个字符"
        class="audit__hint"
      />
    </div>

    <template #footer>
      <el-button @click="close">取消</el-button>
      <el-button type="primary" :loading="saving" :disabled="!canSubmit" @click="submit">
        确认
      </el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.audit__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-4);
  padding: var(--space-4) var(--space-5);
  border: 1px solid var(--color-border-subtle);
  border-radius: var(--radius-lg);
  background: var(--color-bg-subtle);
}

.audit__name {
  font-size: var(--font-size-lg);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-primary);
}

.audit__meta {
  margin-top: var(--space-2);
  color: var(--color-text-tertiary);
  font-size: var(--font-size-xs);
}

.audit__tags {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
  justify-content: flex-end;
}

.audit__actions {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  width: 100%;
  margin-top: var(--space-5);
}

.audit__action {
  display: block;
  padding: var(--space-3) var(--space-4);
  border: 1px solid var(--color-border-subtle);
  border-radius: var(--radius-md);
  cursor: pointer;
  transition: border-color var(--duration-fast) var(--ease-standard);
}

.audit__action:hover {
  border-color: var(--color-primary);
}

.audit__action.is-disabled {
  cursor: not-allowed;
  opacity: 0.55;
}

.audit__action-label {
  margin-left: var(--space-2);
  font-weight: var(--font-weight-medium);
}

.audit__action-desc {
  margin-left: var(--space-2);
  color: var(--color-text-tertiary);
  font-size: var(--font-size-xs);
}

.audit__form {
  margin-top: var(--space-4);
}

.audit__hint {
  margin-top: var(--space-2);
}
</style>
