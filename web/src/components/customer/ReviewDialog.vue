<script setup>
/**
 * 订单评价弹窗
 * 仅在订单 COMPLETED 且 canReview=true 时由父组件打开；重复评价后端返回 40906。
 */
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { reviewOrder } from '@/api/orders'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  order: { type: Object, default: null }
})

const emit = defineEmits(['update:modelValue', 'success'])

const visible = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value)
})

const formRef = ref(null)
const submitting = ref(false)
const form = ref({ rating: 5, content: '' })

const rules = {
  rating: [{ required: true, message: '请选择评分', trigger: 'change' }],
  content: [{ max: 200, message: '评价内容不超过 200 字', trigger: 'blur' }]
}

const dishNames = computed(() =>
  (props.order?.items || []).map((item) => item.dishName).filter(Boolean).join('、')
)

const ratingText = computed(() => {
  const map = { 1: '很不满意', 2: '不太满意', 3: '一般', 4: '满意', 5: '非常满意' }
  return map[form.value.rating] || ''
})

function reset() {
  form.value = { rating: 5, content: '' }
  formRef.value?.clearValidate?.()
}

async function submit() {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  if (!props.order?.id) return
  submitting.value = true
  try {
    const review = await reviewOrder(props.order.id, {
      rating: form.value.rating,
      content: form.value.content.trim()
    })
    ElMessage.success('评价已提交，感谢你的反馈')
    emit('success', review)
    visible.value = false
  } catch (err) {
    if (err?.code === 40906) {
      ElMessage.info('该订单已评价过')
      visible.value = false
    }
  } finally {
    submitting.value = false
  }
}

watch(
  () => props.modelValue,
  (open) => {
    if (open) reset()
  }
)
</script>

<template>
  <el-dialog
    v-model="visible"
    title="评价订单"
    width="480px"
    append-to-body
    :close-on-click-modal="false"
  >
    <div class="review-dialog">
      <p v-if="dishNames" class="review-dialog__dishes">{{ dishNames }}</p>

      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <el-form-item label="评分" prop="rating">
          <div class="review-dialog__rating">
            <el-rate v-model="form.rating" :max="5" show-score score-template="{value} 分" />
            <span class="review-dialog__rating-text">{{ ratingText }}</span>
          </div>
        </el-form-item>
        <el-form-item label="评价内容（选填）" prop="content">
          <el-input
            v-model="form.content"
            type="textarea"
            :rows="4"
            maxlength="200"
            show-word-limit
            placeholder="说说菜品口味、分量和配送体验吧"
          />
        </el-form-item>
      </el-form>
    </div>

    <template #footer>
      <el-button :disabled="submitting" @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submit">提交评价</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.review-dialog__dishes {
  margin-bottom: var(--space-4);
  padding: var(--space-3) var(--space-4);
  border-radius: var(--radius-md);
  background: var(--color-bg-subtle);
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
}

.review-dialog__rating {
  display: flex;
  align-items: center;
  gap: var(--space-3);
}

.review-dialog__rating-text {
  color: var(--color-text-tertiary);
  font-size: var(--font-size-sm);
}
</style>
