/**
 * 通用上传 composable（docs/02-api.md §13）
 *
 * - 前端预校验：图片类型白名单（JPG/PNG/WebP）与 ≤5 MB，避免无效请求
 * - 上传过程暴露 percent（0–100），失败保留文件用于「重试」
 * - 后端 `/files/policy` 决定走本地 multipart 还是 OSS PostPolicy 直传，uploadImage 已封装
 * - 调用方只需处理 `url`（FileObjectView.url），失败时组件可降级为手填地址
 */
import { ref } from 'vue'
import { uploadImage } from '@/api/files'

export const DEFAULT_ALLOWED_TYPES = Object.freeze(['image/jpeg', 'image/png', 'image/webp'])
export const DEFAULT_MAX_SIZE = 5 * 1024 * 1024

/** 校验上传文件，返回 { ok, message } */
export function validateImageFile(file, options = {}) {
  const allowedTypes = options.allowedTypes || DEFAULT_ALLOWED_TYPES
  const maxSize = options.maxSize ?? DEFAULT_MAX_SIZE
  if (!file) return { ok: false, message: '请选择要上传的图片' }
  if (!allowedTypes.includes(file.type)) {
    return { ok: false, message: '仅支持 JPG/PNG/WebP 格式的图片' }
  }
  if (file.size > maxSize) {
    const mb = maxSize / 1024 / 1024
    const sizeText = mb >= 1 ? `${Math.round(mb * 10) / 10} MB` : `${Math.max(1, Math.round(maxSize / 1024))} KB`
    return { ok: false, message: `图片大小不能超过 ${sizeText}` }
  }
  return { ok: true, message: '' }
}

/**
 * @param {{ bizType?: 'dish'|'merchant'|'avatar', maxSize?: number, allowedTypes?: string[] }} [options]
 */
export function useUpload(options = {}) {
  const bizType = options.bizType || 'dish'

  const file = ref(null)
  const uploading = ref(false)
  const percent = ref(0)
  const error = ref('')
  const mode = ref('')
  const result = ref(null)

  function reset() {
    file.value = null
    percent.value = 0
    error.value = ''
    mode.value = ''
    result.value = null
  }

  /**
   * 上传指定文件；失败时记录 error 并保留 file 供 retry() 使用
   * @param {File} nextFile
   */
  async function upload(nextFile) {
    const check = validateImageFile(nextFile, options)
    if (!check.ok) {
      error.value = check.message
      return null
    }
    file.value = nextFile
    uploading.value = true
    percent.value = 0
    error.value = ''
    try {
      const data = await uploadImage(nextFile, bizType, (value) => {
        percent.value = Math.max(0, Math.min(100, Number(value) || 0))
      }, (policy) => {
        mode.value = policy?.uploadMode === 'direct' ? 'oss' : 'local'
      })
      if (!data?.url) throw new Error('上传结果缺少文件地址')
      percent.value = 100
      result.value = data
      return data
    } catch (err) {
      error.value = err?.message || '上传失败，请稍后重试'
      return null
    } finally {
      uploading.value = false
    }
  }

  /** 用上一次失败的文件重试 */
  function retry() {
    if (!file.value) return Promise.resolve(null)
    return upload(file.value)
  }

  return {
    file,
    uploading,
    percent,
    error,
    mode,
    result,
    upload,
    retry,
    reset
  }
}

export default useUpload
