/**
 * 真实后端 E2E：图片上传（T19）
 *
 * 运行：`E2E_MOCK=0 npx playwright test tests/real-file-upload.spec.js`
 *
 * 覆盖：
 *  - GET /files/policy：本地存储兜底策略（storageType=local、uploadMode=backend）
 *  - POST /files/upload：商家 token 上传 PNG，返回可访问的 /files/** URL
 *  - GET 返回的 URL：HTTP 200 且 Content-Type=image/png
 *
 * 未覆盖（标注「未验证」）：OSS 直传（本机无 OSS 凭证，storage.type=local）、
 * 浏览器内 <input type=file> 表单上传交互（接口层已验证，UI 上传未验证）。
 */
import { test, expect } from '@playwright/test'
import {
  API,
  DEMO,
  apiLogin,
  authHeaders,
  backendBaseURL,
  isRealMode,
  uploadPng
} from '../support/real-stack.js'

test.describe('真实后端 · 文件上传', () => {
  test.skip(!isRealMode(), '真实后端用例：设置 E2E_MOCK=0 且本地栈在线后运行')

  test.beforeEach(async ({ request }) => {
    const health = await request.get(`${backendBaseURL()}/actuator/health`)
    test.skip(health.status() !== 200, '本地后端未就绪：/actuator/health 非 200')
  })

  test('商家上传 PNG：policy → upload → 文件 URL 可访问', async ({ request }) => {
    const merchant = await apiLogin(request, DEMO.merchant.username, DEMO.merchant.password)

    const policyResponse = await request.get(`${backendBaseURL()}${API}/files/policy`, {
      headers: authHeaders(merchant.token),
      params: { bizType: 'dish', ext: 'png' }
    })
    const policy = await policyResponse.json()
    expect(policyResponse.status()).toBe(200)
    expect(policy.code, JSON.stringify(policy)).toBe(0)
    expect(policy.data.storageType).toBe('local')
    expect(policy.data.uploadMode).toBe('backend')
    expect(policy.data.allowedTypes).toContain('image/png')

    const uploaded = await uploadPng(request, merchant.token, 'dish')
    expect(uploaded.status, JSON.stringify(uploaded.body)).toBe(200)
    expect(uploaded.body.code, JSON.stringify(uploaded.body)).toBe(0)
    const file = uploaded.body.data
    expect(file.storageType).toBe('local')
    expect(file.url).toMatch(/^\/files\/dish\//)
    expect(file.contentType).toBe('image/png')

    const fileResponse = await request.get(`${backendBaseURL()}${file.url}`)
    expect(fileResponse.status()).toBe(200)
    expect(fileResponse.headers()['content-type']).toContain('image/png')
    const bytes = await fileResponse.body()
    expect(bytes.length).toBeGreaterThan(0)
  })

  test('未登录上传被拒绝（401）', async ({ request }) => {
    const png = Buffer.from(
      'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==',
      'base64'
    )
    const response = await request.post(`${backendBaseURL()}${API}/files/upload`, {
      multipart: {
        file: { name: 't19-anon.png', mimeType: 'image/png', buffer: png },
        bizType: 'dish'
      }
    })
    expect(response.status()).toBe(401)
  })
})
