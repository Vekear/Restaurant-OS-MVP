<script setup>
/**
 * 收货地址新增/编辑弹窗
 * 校验规则与 docs/02-api.md POST /addresses 保持一致；成功后 emit saved(address)。
 */
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { createAddress, updateAddress } from '@/api/addresses'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  address: { type: Object, default: null }
})

const emit = defineEmits(['update:modelValue', 'saved'])

const visible = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value)
})

const isEdit = computed(() => Boolean(props.address?.id))
const formRef = ref(null)
const submitting = ref(false)

const form = reactive({
  consignee: '',
  phone: '',
  province: '',
  city: '',
  district: '',
  detail: '',
  isDefault: false
})

const rules = {
  consignee: [
    { required: true, message: '请填写收货人', trigger: 'blur' },
    { min: 2, max: 20, message: '收货人姓名 2-20 个字符', trigger: 'blur' }
  ],
  phone: [
    { required: true, message: '请填写手机号', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' }
  ],
  province: [{ required: true, message: '请填写省份', trigger: 'blur' }, { max: 32, message: '省份不超过 32 个字符', trigger: 'blur' }],
  city: [{ required: true, message: '请填写城市', trigger: 'blur' }, { max: 32, message: '城市不超过 32 个字符', trigger: 'blur' }],
  district: [{ required: true, message: '请填写区县', trigger: 'blur' }, { max: 32, message: '区县不超过 32 个字符', trigger: 'blur' }],
  detail: [
    { required: true, message: '请填写详细地址', trigger: 'blur' },
    { min: 5, max: 120, message: '详细地址 5-120 个字符', trigger: 'blur' }
  ]
}

function fillForm() {
  const source = props.address || {}
  form.consignee = source.consignee || ''
  form.phone = source.phone || ''
  form.province = source.province || ''
  form.city = source.city || ''
  form.district = source.district || ''
  form.detail = source.detail || ''
  form.isDefault = Boolean(source.isDefault)
  formRef.value?.clearValidate?.()
}

async function submit() {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  submitting.value = true
  try {
    const payload = {
      consignee: form.consignee.trim(),
      phone: form.phone.trim(),
      province: form.province.trim(),
      city: form.city.trim(),
      district: form.district.trim(),
      detail: form.detail.trim(),
      isDefault: form.isDefault
    }
    const saved = isEdit.value
      ? await updateAddress(props.address.id, payload)
      : await createAddress(payload)
    ElMessage.success(isEdit.value ? '地址已更新' : '地址已添加')
    emit('saved', saved)
    visible.value = false
  } catch (err) {
    if (err?.code === 40903) {
      ElMessage.warning('地址数量已达上限（20 条），请先删除不用的地址')
    }
  } finally {
    submitting.value = false
  }
}

watch(
  () => props.modelValue,
  (open) => {
    if (open) fillForm()
  }
)
</script>

<template>
  <el-dialog
    v-model="visible"
    :title="isEdit ? '编辑收货地址' : '新增收货地址'"
    width="520px"
    append-to-body
    :close-on-click-modal="false"
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="收货人" prop="consignee">
            <el-input v-model="form.consignee" maxlength="20" placeholder="请输入姓名" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="手机号" prop="phone">
            <el-input v-model="form.phone" maxlength="11" placeholder="11 位手机号" />
          </el-form-item>
        </el-col>
      </el-row>
      <el-row :gutter="16">
        <el-col :span="8">
          <el-form-item label="省份" prop="province">
            <el-input v-model="form.province" maxlength="32" placeholder="如 浙江省" />
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="城市" prop="city">
            <el-input v-model="form.city" maxlength="32" placeholder="如 杭州市" />
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="区县" prop="district">
            <el-input v-model="form.district" maxlength="32" placeholder="如 西湖区" />
          </el-form-item>
        </el-col>
      </el-row>
      <el-form-item label="详细地址" prop="detail">
        <el-input
          v-model="form.detail"
          type="textarea"
          :rows="2"
          maxlength="120"
          show-word-limit
          placeholder="街道、门牌号、楼层等"
        />
      </el-form-item>
      <el-form-item>
        <el-checkbox v-model="form.isDefault">设为默认地址</el-checkbox>
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button :disabled="submitting" @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submit">保存地址</el-button>
    </template>
  </el-dialog>
</template>
