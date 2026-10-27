<script setup>
/**
 * 统一图片上传组件（docs/01-architecture.md §6：ImageUploader.vue）
 *
 * - 走 /files/policy 自动选择 OSS 直传或后端 multipart（docs/02-api.md §13）
 * - 前端预校验：仅 JPG/PNG/WebP，≤5 MB
 * - 上传中显示进度条；失败可一键重试，也可切到「手动填写地址」不阻塞表单
 * - 上传成功回写 FileObjectView.url，预览统一走 ImageWithFallback（含破图兜底）
 */
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useUpload, validateImageFile } from '@/composables/useUpload'
import ImageWithFallback from '@/components/ImageWithFallback.vue'

const props = defineProps({
  modelValue: { type: String, default: '' },
  bizType: { type: String, default: 'dish' },
  disabled: { type: Boolean, default: false },
  hint: { type: String, default: '支持 JPG/PNG/WebP，≤5 MB' },
  maxSize: { type: Number, default: 5 * 1024 * 1024 }
})

const emit = defineEmits(['update:modelValue', 'uploaded'])

const manualMode = ref(false)
const {
  uploading,
  percent,
  error,
  mode,
  upload,
  retry
} = useUpload({ bizType: props.bizType, maxSize: props.maxSize })

const preview = computed(() => props.modelValue || '')
const modeLabel = computed(() => {
  if (mode.value === 'oss') return 'OSS 直传'
  if (mode.value === 'local') return '本地上传'
  return ''
})

watch(
  () => props.bizType,
  () => {
    // bizType 变化（如从菜品图切到店铺 logo）时重置内部状态
    manualMode.value = false
  }
)

function setUrl(url) {
  emit('update:modelValue', url || '')
}

async function onFileChange(uploadFile) {
  const file = uploadFile?.raw
  if (!file) return
  const check = validateImageFile(file, { maxSize: props.maxSize })
  if (!check.ok) {
    ElMessage.warning(check.message)
    return
  }
  const result = await upload(file)
  if (result?.url) {
    setUrl(result.url)
    emit('uploaded', result)
    ElMessage.success('图片已上传')
  } else {
    ElMessage.warning(error.value || '上传失败，可重试或手动填写图片地址')
  }
}

async function onRetry() {
  const result = await retry()
  if (result?.url) {
    setUrl(result.url)
    emit('uploaded', result)
    ElMessage.success('重试成功，图片已上传')
  }
}

function clearImage() {
  setUrl('')
}
</script>

<template>
  <div class="image-uploader">
    <div class="image-uploader__row">
      <div class="image-uploader__preview">
        <ImageWithFallback v-if="preview" :src="preview" alt="已上传图片" radius="md" />
        <el-icon v-else class="image-uploader__placeholder"><Picture /></el-icon>
      </div>

      <div class="image-uploader__actions">
        <el-upload
          :auto-upload="false"
          :show-file-list="false"
          :disabled="disabled || uploading"
          accept="image/jpeg,image/png,image/webp"
          :on-change="onFileChange"
        >
          <el-button :loading="uploading" :disabled="disabled">
            <el-icon class="image-uploader__icon"><Upload /></el-icon>
            {{ preview ? '重新上传' : '上传图片' }}
          </el-button>
        </el-upload>
        <el-button v-if="preview" text type="danger" :disabled="disabled" @click="clearImage">
          移除
        </el-button>
        <el-button v-if="error && !uploading" text type="warning" @click="onRetry">
          <el-icon class="image-uploader__icon"><Refresh /></el-icon>
          重试
        </el-button>
        <el-button text type="primary" @click="manualMode = !manualMode">
          {{ manualMode ? '收起手动输入' : '手动填写地址' }}
        </el-button>
        <el-tag v-if="modeLabel" size="small" type="info" effect="plain">{{ modeLabel }}</el-tag>
      </div>
    </div>

    <el-progress
      v-if="uploading"
      :percentage="percent"
      :stroke-width="6"
      class="image-uploader__progress"
    />

    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" />

    <el-input
      v-if="manualMode"
      :model-value="modelValue"
      placeholder="/files/dish/2026/10/xxx.png 或 https://..."
      clearable
      class="image-uploader__input"
      @update:model-value="setUrl"
    >
      <template #prepend>URL</template>
    </el-input>

    <p class="image-uploader__hint">{{ hint }}</p>
  </div>
</template>

<style scoped>
.image-uploader {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
  width: 100%;
}

.image-uploader__row {
  display: flex;
  gap: var(--space-4);
  align-items: center;
}

.image-uploader__preview {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 96px;
  height: 96px;
  overflow: hidden;
  border: 1px dashed var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-bg-subtle);
  color: var(--color-text-tertiary);
  font-size: 24px;
}

.image-uploader__actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
  align-items: center;
}

.image-uploader__icon {
  margin-right: var(--space-1);
}

.image-uploader__progress {
  max-width: 320px;
}

.image-uploader__input {
  max-width: 460px;
}

.image-uploader__hint {
  color: var(--color-text-tertiary);
  font-size: var(--font-size-xs);
}
</style>
