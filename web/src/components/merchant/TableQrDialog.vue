<script setup>
/**
 * 桌台二维码弹窗（docs/02-api.md §17.1）
 * - 展示后端生成的二维码图片 / 内容 / 版本 / 签名时间
 * - 支持复制扫码链接、下载 PNG、重签（旧码立即失效）
 */
import { computed, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getTableQr, regenerateTableQr } from '@/api/merchantTables'
import { resolveImageUrl } from '@/utils/media'
import { formatDateTime } from '@/utils/format'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  table: { type: Object, default: null }
})

const emit = defineEmits(['update:modelValue', 'renewed'])

const qr = ref(null)
const loading = ref(false)
const error = ref('')
const renewing = ref(false)

const imageUrl = computed(() => resolveImageUrl(qr.value?.qrImageUrl || ''))
const scanUrl = computed(() => qr.value?.qrContent || qr.value?.qrUrl || '')

async function load() {
  if (!props.table?.id) return
  loading.value = true
  error.value = ''
  try {
    qr.value = await getTableQr(props.table.id)
  } catch (err) {
    error.value = err?.message || '二维码加载失败'
  } finally {
    loading.value = false
  }
}

watch(
  () => [props.modelValue, props.table?.id],
  ([visible]) => {
    if (visible) load()
  },
  { immediate: true }
)

async function copyScanUrl() {
  if (!scanUrl.value) return
  try {
    await navigator.clipboard.writeText(scanUrl.value)
    ElMessage.success('扫码链接已复制')
  } catch {
    ElMessage.warning('浏览器不允许复制，请手动选择文本复制')
  }
}

async function downloadImage() {
  if (!imageUrl.value) {
    ElMessage.warning('暂无二维码图片')
    return
  }
  const filename = `table-${props.table?.tableNo || qr.value?.tableNo || 'qr'}-qr.png`
  try {
    const response = await fetch(imageUrl.value)
    if (!response.ok) throw new Error(`HTTP ${response.status}`)
    const blob = await response.blob()
    const link = document.createElement('a')
    link.href = URL.createObjectURL(blob)
    link.download = filename
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    URL.revokeObjectURL(link.href)
    ElMessage.success('二维码已下载')
  } catch {
    window.open(imageUrl.value, '_blank')
    ElMessage.info('已在新窗口打开图片，可右键另存为')
  }
}

async function renew() {
  try {
    await ElMessageBox.confirm('重签后旧二维码立即失效，已张贴的旧码将无法点餐，确认重签？', '重签二维码', {
      confirmButtonText: '确认重签',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch {
    return
  }
  renewing.value = true
  try {
    qr.value = await regenerateTableQr(props.table.id)
    ElMessage.success('二维码已重签')
    emit('renewed', qr.value)
  } catch (err) {
    ElMessage.error(err?.message || '重签失败，请稍后重试')
  } finally {
    renewing.value = false
  }
}

function close() {
  emit('update:modelValue', false)
}
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    :title="`桌台二维码 · ${table?.tableNo || ''}`"
    width="460px"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div v-loading="loading" class="qr-panel">
      <el-alert v-if="error" type="warning" :title="error" :closable="false" show-icon>
        <el-button size="small" text type="primary" @click="load">重试</el-button>
      </el-alert>

      <template v-else-if="qr">
        <div class="qr-panel__image">
          <img v-if="imageUrl" :src="imageUrl" :alt="`桌台 ${qr.tableNo} 二维码`" />
          <el-empty v-else description="二维码图片生成中，请稍后刷新" :image-size="90" />
        </div>

        <el-descriptions :column="1" size="small" border>
          <el-descriptions-item label="桌号">{{ qr.tableNo }}</el-descriptions-item>
          <el-descriptions-item label="二维码版本">v{{ qr.qrVersion }}</el-descriptions-item>
          <el-descriptions-item label="签名时间">{{ formatDateTime(qr.signedAt, true) }}</el-descriptions-item>
          <el-descriptions-item label="扫码内容">
            <span class="qr-panel__content">{{ scanUrl || '—' }}</span>
          </el-descriptions-item>
        </el-descriptions>

        <div class="qr-panel__actions">
          <el-button @click="copyScanUrl">复制扫码链接</el-button>
          <el-button @click="downloadImage">下载二维码</el-button>
          <el-button type="warning" plain :loading="renewing" @click="renew">重签</el-button>
        </div>
        <p class="qr-panel__hint">提示：重签会使旧二维码失效；下载后可打印张贴到对应桌台。</p>
      </template>
    </div>

    <template #footer>
      <el-button @click="close">关闭</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.qr-panel {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
  min-height: 120px;
}

.qr-panel__image {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: var(--space-4);
  border: 1px solid var(--color-border-subtle);
  border-radius: var(--radius-lg);
  background: var(--color-surface);
}

.qr-panel__image img {
  width: 220px;
  height: 220px;
  object-fit: contain;
}

.qr-panel__content {
  word-break: break-all;
  color: var(--color-text-secondary);
  font-size: var(--font-size-xs);
}

.qr-panel__actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
}

.qr-panel__hint {
  color: var(--color-text-tertiary);
  font-size: var(--font-size-xs);
}
</style>
