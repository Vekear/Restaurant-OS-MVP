/**
 * 文件上传接口（docs/02-api.md §13）
 * - local 模式：GET /files/policy 返回 uploadMode=backend + uploadUrl，前端走 multipart 13.2
 * - oss 模式：返回 PostPolicy 表单字段，前端直传 OSS 后调 /files/confirm 登记
 * 上传大小 ≤5 MB，白名单 image/jpeg|png|webp（含文件头魔数由后端校验）。
 */
import { http } from './http'

const API_BASE = import.meta.env.VITE_API_BASE_URL || '/api/v1'

/**
 * GET /files/policy
 * @param {{ bizType:'dish'|'merchant'|'avatar', ext:'jpg'|'jpeg'|'png'|'webp' }} params
 */
export function getFilePolicy(params) {
  return http.get('/files/policy', { params, silent: true })
}

/**
 * POST /files/upload —— multipart 本地兜底上传
 * @param {File} file
 * @param {'dish'|'merchant'|'avatar'} bizType
 * @param {(percent:number)=>void} [onProgress]
 */
export function uploadFile(file, bizType, onProgress) {
  return http.upload('/files/upload', file, {
    data: { bizType },
    onProgress,
    silent: true
  })
}

/**
 * POST /files/confirm —— OSS 直传完成后登记
 * @param {{ bizType:string, objectKey:string, size:number, contentType:string, originalName?:string }} payload
 */
export function confirmFile(payload) {
  return http.post('/files/confirm', payload, { silent: true })
}

/**
 * OSS PostPolicy 直传（不经过 axios，避免覆盖 Content-Type）
 * @param {{ host:string, dir:string, objectKey:string, policy:string, signature:string,
 *           accessKeyId:string, successActionStatus?:string }} policy
 * @param {File} file
 * @param {(percent:number)=>void} [onProgress]
 * @returns {Promise<void>}
 */
export function uploadToOss(policy, file, onProgress) {
  return new Promise((resolve, reject) => {
    const formData = new FormData()
    const key = `${policy.dir || ''}${policy.objectKey}`
    formData.append('key', key)
    formData.append('policy', policy.policy)
    formData.append('OSSAccessKeyId', policy.accessKeyId)
    formData.append('signature', policy.signature)
    formData.append('success_action_status', policy.successActionStatus || '200')
    formData.append('file', file)

    const xhr = new XMLHttpRequest()
    xhr.open('POST', policy.host, true)
    xhr.upload.onprogress = (event) => {
      if (onProgress && event.total) onProgress(Math.round((event.loaded / event.total) * 100))
    }
    xhr.onload = () => {
      if (xhr.status >= 200 && xhr.status < 300) resolve()
      else reject(new Error(`OSS 上传失败（HTTP ${xhr.status}）`))
    }
    xhr.onerror = () => reject(new Error('OSS 上传网络异常'))
    xhr.send(formData)
  })
}

/**
 * 统一上传入口：按 policy 自动选择直传 OSS 或后端 multipart，返回 FileObjectView（含 url）
 * @param {File} file
 * @param {'dish'|'merchant'|'avatar'} bizType
 * @param {(percent:number)=>void} [onProgress]
 * @param {(policy:object)=>void} [onPolicy] 供 UI 展示当前模式（local / oss）
 */
export async function uploadImage(file, bizType, onProgress, onPolicy) {
  const ext = (file.name.split('.').pop() || 'png').toLowerCase()
  const policy = await getFilePolicy({ bizType, ext })
  if (typeof onPolicy === 'function') onPolicy(policy)

  if (policy?.uploadMode === 'direct') {
    await uploadToOss(policy, file, onProgress)
    const objectKey = policy.objectKey || `${policy.dir || ''}${file.name}`
    return confirmFile({
      bizType,
      objectKey,
      size: file.size,
      contentType: file.type,
      originalName: file.name
    })
  }

  const uploadUrl = policy?.uploadUrl || '/files/upload'
  const url = uploadUrl.startsWith('/api') ? uploadUrl.replace(API_BASE, '') : uploadUrl
  return http.upload(url, file, { data: { bizType }, onProgress, silent: true })
}

export const fileApi = {
  getFilePolicy,
  uploadFile,
  confirmFile,
  uploadToOss,
  uploadImage
}

export default fileApi
