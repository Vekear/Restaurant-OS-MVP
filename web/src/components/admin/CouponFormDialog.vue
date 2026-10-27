<script setup>
/**
 * 管理后台 · 优惠券新建/编辑弹窗（docs/02-api.md §20.10–20.12）
 * 校验：名称 2–50；门槛 0.01–99999.99；优惠 0.01–99999.99 且 < 门槛；总量 1–1000000；
 *       startAt < endAt（ISO 时间，含 +08:00 偏移）。
 * 编辑：总量不得小于已领取数（total - remaining），否则后端 40918。
 */
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { createCoupon, updateCoupon } from '@/api/admin'
import { formatCurrency } from '@/utils/format'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  coupon: { type: Object, default: null }
})

const emit = defineEmits(['update:modelValue', 'saved'])

const formRef = ref(null)
const saving = ref(false)

const isEdit = computed(() => Boolean(props.coupon?.id))
const claimedCount = computed(() => {
  const coupon = props.coupon
  if (!coupon) return 0
  return Math.max(0, Number(coupon.total || 0) - Number(coupon.remaining || 0))
})

const form = reactive({
  name: '',
  thresholdAmount: 100,
  discountAmount: 10,
  total: 100,
  validity: []
})

const rules = {
  name: [
    { required: true, message: '请输入券名称', trigger: 'blur' },
    { min: 2, max: 50, message: '名称需为 2–50 个字符', trigger: 'blur' }
  ],
  thresholdAmount: [
    {
      validator: (_rule, value, callback) => {
        if (value == null || Number(value) < 0.01 || Number(value) > 99999.99) {
          return callback(new Error('使用门槛需在 0.01–99999.99 之间'))
        }
        callback()
      },
      trigger: 'blur'
    }
  ],
  discountAmount: [
    {
      validator: (_rule, value, callback) => {
        if (value == null || Number(value) < 0.01 || Number(value) > 99999.99) {
          return callback(new Error('优惠金额需在 0.01–99999.99 之间'))
        }
        if (Number(value) >= Number(form.thresholdAmount || 0)) {
          return callback(new Error('优惠金额必须小于使用门槛'))
        }
        callback()
      },
      trigger: 'blur'
    }
  ],
  total: [
    {
      validator: (_rule, value, callback) => {
        if (value == null || Number(value) < 1 || Number(value) > 1000000) {
          return callback(new Error('发行总量需为 1–1000000'))
        }
        if (isEdit.value && Number(value) < claimedCount.value) {
          return callback(new Error(`总量不得小于已领取数（${claimedCount.value} 张）`))
        }
        callback()
      },
      trigger: 'blur'
    }
  ],
  validity: [{ required: true, message: '请选择有效期', trigger: 'change' }]
}

function reset() {
  const coupon = props.coupon || {}
  form.name = coupon.name || ''
  form.thresholdAmount = coupon.thresholdAmount ?? 100
  form.discountAmount = coupon.discountAmount ?? 10
  form.total = coupon.total ?? 100
  const start = coupon.startAt ? String(coupon.startAt).slice(0, 19) : ''
  const end = coupon.endAt ? String(coupon.endAt).slice(0, 19) : ''
  form.validity = start && end ? [start, end] : []
  formRef.value?.clearValidate?.()
}

watch(
  () => props.modelValue,
  (visible) => {
    if (visible) reset()
  }
)

async function submit() {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  const [startAt, endAt] = form.validity
  const payload = {
    name: form.name.trim(),
    thresholdAmount: Number(form.thresholdAmount),
    discountAmount: Number(form.discountAmount),
    total: Number(form.total),
    startAt,
    endAt
  }
  saving.value = true
  try {
    if (isEdit.value) {
      await updateCoupon(props.coupon.id, payload)
      ElMessage.success('优惠券已更新')
    } else {
      await createCoupon(payload)
      ElMessage.success('优惠券已创建')
    }
    emit('saved')
    emit('update:modelValue', false)
  } catch (error) {
    if (error?.code === 40918) {
      ElMessage.error('发行总量不得小于已领取数量，请调整后重试')
    } else {
      ElMessage.error(error?.message || '保存失败，请稍后重试')
    }
  } finally {
    saving.value = false
  }
}

function close() {
  emit('update:modelValue', false)
}
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    :title="isEdit ? '编辑优惠券' : '新建优惠券'"
    width="600px"
    destroy-on-close
    @update:model-value="emit('update:modelValue', $event)"
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-width="92px">
      <el-form-item label="券名称" prop="name">
        <el-input v-model="form.name" maxlength="50" show-word-limit placeholder="例如：满 100 减 15" />
      </el-form-item>
      <div class="form-grid">
        <el-form-item label="使用门槛" prop="thresholdAmount">
          <el-input-number
            v-model="form.thresholdAmount"
            :min="0.01"
            :max="99999.99"
            :precision="2"
            :step="10"
            class="w-full"
          />
        </el-form-item>
        <el-form-item label="优惠金额" prop="discountAmount">
          <el-input-number
            v-model="form.discountAmount"
            :min="0.01"
            :max="99999.99"
            :precision="2"
            :step="1"
            class="w-full"
          />
        </el-form-item>
        <el-form-item label="发行总量" prop="total">
          <el-input-number v-model="form.total" :min="1" :max="1000000" :step="50" class="w-full" />
        </el-form-item>
        <el-form-item label="已领取">
          <el-input :model-value="String(claimedCount)" disabled class="w-full" />
        </el-form-item>
      </div>
      <el-form-item label="有效期" prop="validity">
        <el-date-picker
          v-model="form.validity"
          type="datetimerange"
          value-format="YYYY-MM-DDTHH:mm:ssZ"
          range-separator="至"
          start-placeholder="生效时间"
          end-placeholder="失效时间"
          class="w-full"
        />
      </el-form-item>
      <el-alert
        type="info"
        :closable="false"
        show-icon
        :title="`满 ${formatCurrency(form.thresholdAmount)} 减 ${formatCurrency(form.discountAmount)}`"
        description="本期优惠券仅支持满减（FULL_REDUCTION），单用户每券限领 1 张，全场通用。"
      />
    </el-form>

    <template #footer>
      <el-button @click="close">取消</el-button>
      <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0 var(--space-4);
}

.w-full {
  width: 100%;
}

@media (max-width: 640px) {
  .form-grid {
    grid-template-columns: 1fr;
  }
}
</style>
