import { http, absoluteUrl } from './request'
import { IS_MOCK, mockDelay } from '@/mock'
import { getToken } from '@/utils/session'

/**
 * 文件上传（契约 docs/02-api.md §13，brief §4.6）。
 * - GET  /files/policy?bizType=&ext=  取上传策略：storage.type=local → backend 上传；
 *                                     storage.type=oss   → PostPolicy 直传凭证（不含 Secret）
 * - POST /files/upload                local 降级：multipart（uni.uploadFile）
 * - POST /files/confirm               oss 直传成功后登记 file_object
 *
 * bizType：dish / merchant / avatar（客户端三种；table 仅后端内部使用）。
 */

const CONTENT_TYPES = {
  jpg: 'image/jpeg',
  jpeg: 'image/jpeg',
  png: 'image/png',
  webp: 'image/webp'
}

/** 从临时文件路径推断扩展名（默认 png） */
export function guessExt(filePath) {
  const matched = /\.([A-Za-z0-9]+)(?:\?|#|$)/.exec(String(filePath || ''))
  const ext = matched ? matched[1].toLowerCase() : ''
  return CONTENT_TYPES[ext] ? ext : 'png'
}

export function contentTypeOf(filePath) {
  return CONTENT_TYPES[guessExt(filePath)] || 'image/png'
}

export function normalizePolicy(raw) {
  const item = raw || {}
  return {
    storageType: item.storageType || 'local',
    uploadMode: item.uploadMode || 'backend',
    uploadUrl: item.uploadUrl || '',
    host: item.host || '',
    dir: item.dir || '',
    objectKey: item.objectKey || '',
    policy: item.policy || '',
    signature: item.signature || '',
    accessKeyId: item.accessKeyId || '',
    expireAt: item.expireAt || '',
    maxSizeMb: Number(item.maxSizeMb || 5),
    allowedTypes: Array.isArray(item.allowedTypes) ? item.allowedTypes : ['image/jpeg', 'image/png', 'image/webp'],
    successActionStatus: item.successActionStatus || '200'
  }
}

export function normalizeFileObject(raw) {
  const item = raw || {}
  return {
    id: item.id !== undefined ? item.id : null,
    bizType: item.bizType || '',
    objectKey: item.objectKey || '',
    url: item.url || '',
    storageType: item.storageType || 'local',
    size: Number(item.size || 0),
    contentType: item.contentType || '',
    originalName: item.originalName || '',
    createdAt: item.createdAt || ''
  }
}

export function fetchFilePolicy(bizType, ext) {
  if (IS_MOCK) {
    return mockDelay(
      {
        storageType: 'local',
        uploadMode: 'backend',
        uploadUrl: '/files/upload',
        maxSizeMb: 5,
        allowedTypes: ['image/jpeg', 'image/png', 'image/webp']
      },
      140
    ).then(normalizePolicy)
  }
  return http
    .get('/files/policy', { bizType, ext }, { silent: true })
    .then(normalizePolicy)
}

function parseUploadResponse(body) {
  if (typeof body !== 'string') return body
  try {
    return JSON.parse(body)
  } catch (err) {
    return null
  }
}

/** local 模式：multipart 上传到后端（uni.uploadFile） */
export function uploadLocal(filePath, bizType, policy) {
  if (IS_MOCK) {
    return mockDelay(
      { bizType, url: filePath, storageType: 'local', size: 0, contentType: contentTypeOf(filePath) },
      260
    ).then(normalizeFileObject)
  }
  const uploadUrl = absoluteUrl((policy && policy.uploadUrl) || '/files/upload')
  const header = {}
  const token = getToken()
  if (token) header.Authorization = `Bearer ${token}`
  return new Promise((resolve, reject) => {
    uni.uploadFile({
      url: uploadUrl,
      filePath,
      name: 'file',
      formData: { bizType },
      header,
      timeout: 60000,
      success: (response) => {
        const statusCode = Number(response.statusCode) || 0
        const body = parseUploadResponse(response.data)
        if (statusCode >= 200 && statusCode < 300 && body && Number(body.code) === 0) {
          resolve(normalizeFileObject(body.data))
          return
        }
        const error = new Error((body && body.message) || '图片上传失败，请重试')
        error.code = body ? body.code : null
        error.statusCode = statusCode
        reject(error)
      },
      fail: () => {
        reject(new Error('图片上传失败，请检查网络后重试'))
      }
    })
  })
}

/** oss 模式：PostPolicy 表单直传后调用 /files/confirm 登记 */
export function uploadOss(filePath, bizType, policy) {
  if (IS_MOCK) {
    return mockDelay({ bizType, url: filePath, storageType: 'oss', size: 0 }, 260).then(normalizeFileObject)
  }
  const formData = {
    key: policy.objectKey,
    policy: policy.policy,
    OSSAccessKeyId: policy.accessKeyId,
    signature: policy.signature,
    success_action_status: policy.successActionStatus
  }
  return new Promise((resolve, reject) => {
    uni.uploadFile({
      url: policy.host,
      filePath,
      name: 'file',
      formData,
      timeout: 60000,
      success: (response) => {
        const statusCode = Number(response.statusCode) || 0
        if (statusCode >= 200 && statusCode < 300) {
          confirmUpload({
            bizType,
            objectKey: policy.objectKey,
            size: 0,
            contentType: contentTypeOf(filePath)
          })
            .then(resolve)
            .catch(reject)
          return
        }
        reject(new Error('图片上传失败（OSS 直传返回异常）'))
      },
      fail: () => reject(new Error('图片上传失败，请检查网络后重试'))
    })
  })
}

export function confirmUpload({ bizType, objectKey, size, contentType, originalName }) {
  return http
    .post(
      '/files/confirm',
      { bizType, objectKey, size: size || 0, contentType, originalName: originalName || '' },
      { silent: true }
    )
    .then(normalizeFileObject)
}

/**
 * 统一图片上传入口：按后端下发的 storage.type 选择 local / OSS 直传。
 * @param {string} filePath uni.chooseImage 返回的临时路径
 * @param {string} bizType dish | merchant | avatar
 * @returns {Promise<{url:string,bizType:string,storageType:string}>}
 */
export async function uploadImage(filePath, bizType) {
  const ext = guessExt(filePath)
  const policy = await fetchFilePolicy(bizType, ext)
  if (policy.storageType === 'oss' && policy.uploadMode === 'direct' && policy.host) {
    return uploadOss(filePath, bizType, policy)
  }
  return uploadLocal(filePath, bizType, policy)
}

export function uploadErrorMessage(error) {
  const code = Number(error && error.code)
  if (code === 40003) return '仅支持 JPG/PNG/WEBP，单张不超过 5MB'
  if (code === 50002) return '文件存储失败，请稍后重试'
  return (error && error.message) || '图片上传失败，请重试'
}
