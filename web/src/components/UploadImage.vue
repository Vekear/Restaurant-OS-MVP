<script setup>
/**
 * 图片上传（商家端通用）
 * - 走 /files/policy 自动选择 OSS 直传或后端 multipart（docs/02-api.md §13）
 * - 失败时降级为手填图片地址，保证网络/存储不可用时不阻塞表单
 * - 5 MB 上限，image/jpeg|png|webp
 */
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { uploadImage } from '@/api/files'
import { resolveImageUrl } from '@/utils/media'

const props = defineProps({
  modelValue: { type: String, default: '' },
  bizType: { type: String, default: 'dish' },
  disabled: { type: Boolean, default: false },
  hint: { type: String, default: '支持 JPG/PNG/WebP，≤5 MB' }
})

const emit = defineEmits(['update:modelValue'])

const uploading = ref(false)
const percent = ref(0)
const manualMode = ref(false)

const preview = computed(() => resolveImageUrl(props.modelValue))
const allowedTypes = ['image/jpeg', 'image/png', 'image/webp']
const maxSize = 5 * 1024 * 1024

function setUrl(url) {
  emit('update:modelValue', url || '')
}

async function onFileChange(uploadFile) {
  const file = uploadFile?.raw
  if (!file) return
  if (!allowedTypes.includes(file.type)) {
    ElMessage.warning('仅支持 JPG/PNG/WebP 图片')
    return
  }
  if (file.size > maxSize) {
    ElMessage.warning('图片不能超过 5 MB')
    return
  }
  uploading.value = true
  percent.value = 0
  try {
    const result = await uploadImage(file, props.bizType, (value) => {
      percent.value = value
    })
    if (result?.url) {
      setUrl(result.url)
      ElMessage.success('图片已上传')
    } else {
      throw new Error('上传结果缺少 url')
    }
  } catch (error) {
    manualMode.value = true
    ElMessage.warning(error?.message || '上传失败，可手动填写图片地址')
  } finally {
    uploading.value = false
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
        <img v-if="preview" :src="preview" alt="" />
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
            <el-icon class="mr-1"><Upload /></el-icon>
            {{ preview ? '重新上传' : '上传图片' }}
          </el-button>
        </el-upload>
        <el-button v-if="preview" text type="danger" :disabled="disabled" @click="clearImage">
          移除
        </el-button>
        <el-button text type="primary" @click="manualMode = !manualMode">
          {{ manualMode ? '收起手动输入' : '手动填写地址' }}
        </el-button>
      </div>
    </div>

    <el-progress v-if="uploading" :percentage="percent" :stroke-width="6" class="image-uploader__progress" />

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

.image-uploader__preview img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.image-uploader__actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
  align-items: center;
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

.mr-1 {
  margin-right: var(--space-1);
}
</style>
