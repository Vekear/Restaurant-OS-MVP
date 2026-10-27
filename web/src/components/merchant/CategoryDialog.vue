<script setup>
/**
 * 菜品分类新建/编辑弹窗（docs/02-api.md §16.1）
 */
import { reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { createCategory, updateCategory } from '@/api/merchantDishes'
import { COMMON_STATUS } from '@/utils/constants'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  category: { type: Object, default: null }
})

const emit = defineEmits(['update:modelValue', 'saved'])

const formRef = ref(null)
const saving = ref(false)

const form = reactive({ name: '', sort: 0, status: COMMON_STATUS.ENABLED })

const statusOptions = [
  { value: COMMON_STATUS.ENABLED, label: '启用' },
  { value: COMMON_STATUS.DISABLED, label: '停用' }
]

const rules = {
  name: [
    { required: true, message: '请输入分类名称', trigger: 'blur' },
    { min: 1, max: 20, message: '名称需为 1–20 个字符', trigger: 'blur' }
  ],
  sort: [{ type: 'number', min: 0, max: 999, message: '排序值需为 0–999', trigger: 'blur' }]
}

watch(
  () => props.modelValue,
  (visible) => {
    if (!visible) return
    const source = props.category || {}
    form.name = source.name || ''
    form.sort = source.sort ?? 0
    form.status = source.status || COMMON_STATUS.ENABLED
    formRef.value?.clearValidate?.()
  }
)

async function submit() {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  const payload = { name: form.name.trim(), sort: Number(form.sort) || 0 }
  saving.value = true
  try {
    if (props.category?.id) {
      payload.status = form.status
      await updateCategory(props.category.id, payload)
      ElMessage.success('分类已更新')
    } else {
      await createCategory(payload)
      ElMessage.success('分类已创建')
    }
    emit('saved')
    emit('update:modelValue', false)
  } catch (error) {
    ElMessage.error(error?.message || '保存失败，请稍后重试')
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    :title="category?.id ? '编辑分类' : '新建分类'"
    width="440px"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-width="76px">
      <el-form-item label="名称" prop="name">
        <el-input v-model="form.name" maxlength="20" show-word-limit placeholder="例如：招牌热菜" />
      </el-form-item>
      <el-form-item label="排序" prop="sort">
        <el-input-number v-model="form.sort" :min="0" :max="999" class="w-full" />
      </el-form-item>
      <el-form-item v-if="category?.id" label="状态" prop="status">
        <el-radio-group v-model="form.status">
          <el-radio v-for="item in statusOptions" :key="item.value" :value="item.value">
            {{ item.label }}
          </el-radio>
        </el-radio-group>
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.w-full {
  width: 100%;
}
</style>
