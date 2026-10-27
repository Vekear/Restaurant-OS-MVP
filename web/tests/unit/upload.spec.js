import { describe, expect, it } from 'vitest'
import { validateImageFile } from '@/composables/useUpload'

function makeFile({ name = 'dish.png', type = 'image/png', size = 10 } = {}) {
  return new File([new Uint8Array(size)], name, { type })
}

describe('useUpload 上传校验', () => {
  it('放行 JPG/PNG/WebP', () => {
    expect(validateImageFile(makeFile()).ok).toBe(true)
    expect(validateImageFile(makeFile({ type: 'image/jpeg' })).ok).toBe(true)
    expect(validateImageFile(makeFile({ type: 'image/webp' })).ok).toBe(true)
  })

  it('拒绝非白名单格式并提示中文原因', () => {
    const result = validateImageFile(makeFile({ type: 'image/gif' }))
    expect(result.ok).toBe(false)
    expect(result.message).toContain('JPG/PNG/WebP')
  })

  it('超限大小拒绝上传，上限可配置', () => {
    const result = validateImageFile(makeFile({ size: 2048 }), { maxSize: 1024 })
    expect(result.ok).toBe(false)
    expect(result.message).toContain('1 KB')
    expect(validateImageFile(makeFile({ size: 6 * 1024 * 1024 })).message).toContain('5 MB')
  })

  it('未选择文件时给出提示', () => {
    expect(validateImageFile(null)).toEqual({ ok: false, message: '请选择要上传的图片' })
  })
})
