<script setup>
/**
 * 商家菜品新建/编辑弹窗（docs/02-api.md §16）
 * - 图片走 UploadImage（/files/policy + /files/upload，OSS 直传自动降级）
 * - 校验：名称 2–50、价格 0.01–9999.99、原价 ≥ 售价、库存 0–99999、排序 0–999
 */
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { createDish, updateDish } from '@/api/merchantDishes'
import { DISH_STATUS, DISH_STATUS_LABELS } from '@/utils/constants'
import UploadImage from '@/components/UploadImage.vue'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  dish: { type: Object, default: null },
  categories: { type: Array, default: () => [] }
})

const emit = defineEmits(['update:modelValue', 'saved'])

const formRef = ref(null)
const saving = ref(false)

const isEdit = computed(() => Boolean(props.dish?.id))

const form = reactive({
  categoryId: null,
  name: '',
  price: null,
  originalPrice: null,
  imageUrl: '',
  description: '',
  stock: 0,
  sort: 0,
  status: DISH_STATUS.ON_SALE
})

const statusOptions = Object.entries(DISH_STATUS_LABELS).map(([value, label]) => ({ value, label }))

const rules = {
  categoryId: [{ required: true, message: '请选择分类', trigger: 'change' }],
  name: [
    { required: true, message: '请输入菜品名称', trigger: 'blur' },
    { min: 2, max: 50, message: '名称需为 2–50 个字符', trigger: 'blur' }
  ],
  price: [
    { required: true, message: '请输入售价', trigger: 'blur' },
    {
      validator: (_rule, value, callback) => {
        if (value == null || value === '') return callback(new Error('请输入售价'))
        if (Number(value) < 0.01 || Number(value) > 9999.99) {
          return callback(new Error('售价需在 0.01–9999.99 之间'))
        }
        callback()
      },
      trigger: 'blur'
    }
  ],
  originalPrice: [
    {
      validator: (_rule, value, callback) => {
        if (value == null || value === '') return callback()
        if (Number(value) < Number(form.price || 0)) {
          return callback(new Error('原价不能低于售价'))
        }
        if (Number(value) > 9999.99) return callback(new Error('原价不能超过 9999.99'))
        callback()
      },
      trigger: 'blur'
    }
  ],
  stock: [
    {
      validator: (_rule, value, callback) => {
        if (value == null || value === '') return callback(new Error('请输入库存'))
        if (Number(value) < 0 || Number(value) > 99999) return callback(new Error('库存需为 0–99999'))
        callback()
      },
      trigger: 'blur'
    }
  ],
  sort: [
    {
      validator: (_rule, value, callback) => {
        if (value != null && (Number(value) < 0 || Number(value) > 999)) {
          return callback(new Error('排序值需为 0–999'))
        }
        callback()
      },
      trigger: 'blur'
    }
  ],
  description: [{ max: 500, message: '描述不能超过 500 个字符', trigger: 'blur' }]
}

function reset() {
  const source = props.dish || {}
  form.categoryId = source.categoryId ?? props.categories[0]?.id ?? null
  form.name = source.name || ''
  form.price = source.price ?? null
  form.originalPrice = source.originalPrice ?? null
  form.imageUrl = source.imageUrl || ''
  form.description = source.description || ''
  form.stock = source.stock ?? 0
  form.sort = source.sort ?? 0
  form.status = source.status || DISH_STATUS.ON_SALE
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
  const payload = {
    categoryId: form.categoryId,
    name: form.name.trim(),
    price: Number(form.price),
    imageUrl: form.imageUrl || null,
    description: form.description?.trim() || null,
    stock: Number(form.stock),
    sort: Number(form.sort) || 0,
    status: form.status
  }
  if (form.originalPrice != null && form.originalPrice !== '') {
    payload.originalPrice = Number(form.originalPrice)
  }
  saving.value = true
  try {
    if (isEdit.value) {
      await updateDish(props.dish.id, payload)
      ElMessage.success('菜品已更新')
    } else {
      await createDish(payload)
      ElMessage.success('菜品已创建')
    }
    emit('saved')
    emit('update:modelValue', false)
  } catch (error) {
    if (error?.code === 40914) {
      ElMessage.error('菜品名称已存在，请换一个名称')
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
    :title="isEdit ? '编辑菜品' : '新建菜品'"
    width="640px"
    destroy-on-close
    @update:model-value="emit('update:modelValue', $event)"
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-width="92px">
      <el-form-item label="菜品名称" prop="name">
        <el-input v-model="form.name" maxlength="50" show-word-limit placeholder="例如：招牌辣椒炒肉" />
      </el-form-item>
      <div class="form-grid">
        <el-form-item label="分类" prop="categoryId">
          <el-select v-model="form.categoryId" placeholder="请选择分类" class="w-full">
            <el-option v-for="item in categories" :key="item.id" :label="item.name" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-select v-model="form.status" class="w-full">
            <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="售价" prop="price">
          <el-input-number v-model="form.price" :min="0.01" :max="9999.99" :precision="2" :step="1" class="w-full" />
        </el-form-item>
        <el-form-item label="原价" prop="originalPrice">
          <el-input-number
            v-model="form.originalPrice"
            :min="0"
            :max="9999.99"
            :precision="2"
            :step="1"
            class="w-full"
            placeholder="选填，用于划线价"
          />
        </el-form-item>
        <el-form-item label="库存" prop="stock">
          <el-input-number v-model="form.stock" :min="0" :max="99999" :step="1" class="w-full" />
        </el-form-item>
        <el-form-item label="排序" prop="sort">
          <el-input-number v-model="form.sort" :min="0" :max="999" :step="1" class="w-full" />
        </el-form-item>
      </div>
      <el-form-item label="菜品图片">
        <UploadImage v-model="form.imageUrl" biz-type="dish" />
      </el-form-item>
      <el-form-item label="菜品描述" prop="description">
        <el-input
          v-model="form.description"
          type="textarea"
          :rows="3"
          maxlength="500"
          show-word-limit
          placeholder="口味、食材、做法等，选填"
        />
      </el-form-item>
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
