<script setup>
/**
 * 桌台新建/编辑弹窗（docs/02-api.md §17）
 * 新建时后端自动生成签名二维码；修改桌号会自动重签（旧码失效）。
 */
import { reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { createTable, updateTable } from '@/api/merchantTables'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  table: { type: Object, default: null }
})

const emit = defineEmits(['update:modelValue', 'saved'])

const formRef = ref(null)
const saving = ref(false)

const form = reactive({ tableNo: '', name: '', capacity: 4, zone: '' })

const rules = {
  tableNo: [
    { required: true, message: '请输入桌号', trigger: 'blur' },
    { min: 1, max: 20, message: '桌号需为 1–20 个字符', trigger: 'blur' }
  ],
  name: [{ max: 20, message: '名称不能超过 20 个字符', trigger: 'blur' }],
  capacity: [{ type: 'number', min: 1, max: 20, message: '容量需为 1–20 人', trigger: 'blur' }],
  zone: [{ max: 20, message: '区域不能超过 20 个字符', trigger: 'blur' }]
}

watch(
  () => props.modelValue,
  (visible) => {
    if (!visible) return
    const source = props.table || {}
    form.tableNo = source.tableNo || ''
    form.name = source.name || ''
    form.capacity = source.capacity ?? 4
    form.zone = source.zone || ''
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
  const payload = {
    tableNo: form.tableNo.trim(),
    name: form.name?.trim() || null,
    capacity: Number(form.capacity) || 4,
    zone: form.zone?.trim() || null
  }
  saving.value = true
  try {
    if (props.table?.id) {
      await updateTable(props.table.id, payload)
      ElMessage.success('桌台已更新，桌号变更时会自动重签二维码')
    } else {
      await createTable(payload)
      ElMessage.success('桌台已创建，二维码已生成')
    }
    emit('saved')
    emit('update:modelValue', false)
  } catch (error) {
    if (error?.code === 40909) {
      ElMessage.error('桌台存在进行中会话，暂时无法修改')
    } else if (error?.code === 40914) {
      ElMessage.error('桌号已存在，请换一个桌号')
    } else {
      ElMessage.error(error?.message || '保存失败，请稍后重试')
    }
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    :title="table?.id ? '编辑桌台' : '新建桌台'"
    width="460px"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-width="76px">
      <el-form-item label="桌号" prop="tableNo">
        <el-input v-model="form.tableNo" maxlength="20" placeholder="例如：A01" />
      </el-form-item>
      <el-form-item label="名称" prop="name">
        <el-input v-model="form.name" maxlength="20" placeholder="例如：靠窗双人桌（选填）" />
      </el-form-item>
      <el-form-item label="容量" prop="capacity">
        <el-input-number v-model="form.capacity" :min="1" :max="20" />
        <span class="form-hint">人</span>
      </el-form-item>
      <el-form-item label="区域" prop="zone">
        <el-input v-model="form.zone" maxlength="20" placeholder="例如：大厅 / 包间（选填）" />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.form-hint {
  margin-left: var(--space-2);
  color: var(--color-text-tertiary);
  font-size: var(--font-size-xs);
}
</style>
