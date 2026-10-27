/**
 * 真实后端 E2E：堂食桌台二维码与开桌会话（T19）
 *
 * 运行：`E2E_MOCK=0 npx playwright test tests/real-dine-in.spec.js`
 * 前置：本地栈在线（后端 8080 + MySQL/Redis），种子账号可用。
 *
 * 覆盖：
 *  - 商家创建桌台并读取二维码（GET /merchant/tables/{id}/qr）→ 顾客扫码开桌（POST /dine-in/scan）
 *  - 会话查询（GET /dine-in/session + X-Table-Session）与关台（POST /dine-in/session/close）
 *  - 桌码签名篡改 / 缺失被拒绝（40004 / 40001），不产生会话
 *  - 手输桌号开桌（POST /dine-in/bind）可用
 *
 * 清理：用例结束前关台并删除本用例创建的桌台（DELETE /merchant/tables/{id}），
 * 不删除历史订单；使用独立的 T19 桌号，重复运行互不影响。
 */
import { test, expect } from '@playwright/test'
import { API, DEMO, apiLogin, authHeaders, backendBaseURL, isRealMode } from '../support/real-stack.js'

const url = (path) => `${backendBaseURL()}${API}${path}`

async function createTable(request, merchantToken, tableNo) {
  const response = await request.post(url('/merchant/tables'), {
    headers: authHeaders(merchantToken),
    data: { tableNo, name: 'T19 E2E 桌台', capacity: 4 }
  })
  const body = await response.json()
  expect(response.status(), JSON.stringify(body)).toBe(200)
  expect(body.code, JSON.stringify(body)).toBe(0)
  return body.data
}

async function fetchQr(request, merchantToken, tableId) {
  const response = await request.get(url(`/merchant/tables/${tableId}/qr`), {
    headers: authHeaders(merchantToken)
  })
  const body = await response.json()
  expect(response.status(), JSON.stringify(body)).toBe(200)
  expect(body.code, JSON.stringify(body)).toBe(0)
  return body.data
}

/** 从 qrContent（落地页 URL）中解析扫码参数 p/s/t */
function parseQrContent(qrContent) {
  const parsed = new URL(qrContent)
  return {
    p: parsed.searchParams.get('p'),
    s: parsed.searchParams.get('s'),
    t: parsed.searchParams.get('t')
  }
}

async function scan(request, customerToken, payload) {
  const response = await request.post(url('/dine-in/scan'), {
    headers: authHeaders(customerToken),
    data: payload
  })
  return { response, body: await response.json() }
}

async function bind(request, customerToken, payload) {
  const response = await request.post(url('/dine-in/bind'), {
    headers: authHeaders(customerToken),
    data: payload
  })
  return { response, body: await response.json() }
}

async function closeSession(request, customerToken, sessionToken) {
  const response = await request.post(url('/dine-in/session/close'), {
    headers: { ...authHeaders(customerToken), 'X-Table-Session': sessionToken }
  })
  return { response, body: await response.json() }
}

async function deleteTable(request, merchantToken, tableId) {
  const response = await request.delete(url(`/merchant/tables/${tableId}`), {
    headers: authHeaders(merchantToken)
  })
  return { response, body: await response.json() }
}

test.describe('真实后端 · 堂食桌台二维码与开桌会话', () => {
  test.skip(!isRealMode(), '真实后端用例：设置 E2E_MOCK=0 且本地栈在线后运行')

  test.beforeEach(async ({ request }) => {
    const health = await request.get(`${backendBaseURL()}/actuator/health`)
    test.skip(health.status() !== 200, '本地后端未就绪：/actuator/health 非 200')
  })

  test('商家生成桌码 → 顾客扫码开桌 → 查询会话 → 关台', async ({ request }) => {
    const merchant = await apiLogin(request, DEMO.merchant.username, DEMO.merchant.password)
    const customer = await apiLogin(request, DEMO.customer.username, DEMO.customer.password)
    const tableNo = `T19D${Date.now().toString().slice(-7)}`
    const table = await createTable(request, merchant.token, tableNo)

    try {
      const qr = await fetchQr(request, merchant.token, table.id)
      expect(qr.tableId).toBe(table.id)
      expect(qr.qrContent).toContain(`p=${table.id}`)
      const payload = parseQrContent(qr.qrContent)

      const opened = await scan(request, customer.token, payload)
      expect(opened.response.status(), JSON.stringify(opened.body)).toBe(200)
      expect(opened.body.code, JSON.stringify(opened.body)).toBe(0)
      expect(opened.body.data.tableId).toBe(table.id)
      expect(opened.body.data.tableNo).toBe(tableNo)
      expect(opened.body.data.status).toBe('ACTIVE')
      expect(opened.body.data.sessionToken).toBeTruthy()

      const sessionToken = opened.body.data.sessionToken
      const session = await request.get(url('/dine-in/session'), {
        headers: { ...authHeaders(customer.token), 'X-Table-Session': sessionToken }
      })
      const sessionBody = await session.json()
      expect(session.status(), JSON.stringify(sessionBody)).toBe(200)
      expect(sessionBody.data.sessionId).toBe(opened.body.data.sessionId)
      expect(sessionBody.data.tableId).toBe(table.id)

      const closed = await closeSession(request, customer.token, sessionToken)
      expect(closed.response.status(), JSON.stringify(closed.body)).toBe(200)
      expect(closed.body.data.closed).toBe(true)
      expect(closed.body.data.sessionId).toBe(opened.body.data.sessionId)

      // 关台后该 token 不再可用（会话无效 40911 → HTTP 409）
      const afterClose = await request.get(url('/dine-in/session'), {
        headers: { ...authHeaders(customer.token), 'X-Table-Session': sessionToken }
      })
      const afterCloseBody = await afterClose.json()
      expect(afterClose.status(), JSON.stringify(afterCloseBody)).toBe(409)
      expect(afterCloseBody.code).toBe(40911)
      expect(afterCloseBody.message).toContain('会话无效或已关闭')
    } finally {
      await deleteTable(request, merchant.token, table.id)
    }
  })

  test('桌码签名篡改/缺失被拒绝，手输桌号开桌可用', async ({ request }) => {
    const merchant = await apiLogin(request, DEMO.merchant.username, DEMO.merchant.password)
    const customer = await apiLogin(request, DEMO.customer.username, DEMO.customer.password)
    const tableNo = `T19B${Date.now().toString().slice(-7)}`
    const table = await createTable(request, merchant.token, tableNo)

    try {
      const qr = await fetchQr(request, merchant.token, table.id)
      const payload = parseQrContent(qr.qrContent)
      const tampered = `${payload.s.slice(0, -1)}${payload.s.slice(-1) === 'A' ? 'B' : 'A'}`

      const badSignature = await scan(request, customer.token, { ...payload, s: tampered })
      expect(badSignature.response.status(), JSON.stringify(badSignature.body)).toBe(400)
      expect(badSignature.body.code).toBe(40004)
      expect(badSignature.body.message).toContain('二维码签名校验失败')

      const missingSignature = await scan(request, customer.token, { p: payload.p })
      expect(missingSignature.response.status()).toBe(400)
      expect(missingSignature.body.code).toBe(40001)

      const manual = await bind(request, customer.token, { tableNo })
      expect(manual.response.status(), JSON.stringify(manual.body)).toBe(200)
      expect(manual.body.code, JSON.stringify(manual.body)).toBe(0)
      expect(manual.body.data.tableNo).toBe(tableNo)
      expect(manual.body.data.status).toBe('ACTIVE')

      const closed = await closeSession(request, customer.token, manual.body.data.sessionToken)
      expect(closed.response.status(), JSON.stringify(closed.body)).toBe(200)
      expect(closed.body.data.closed).toBe(true)
    } finally {
      await deleteTable(request, merchant.token, table.id)
    }
  })

  test('顾客无权创建/删除桌台（403）', async ({ request }) => {
    const customer = await apiLogin(request, DEMO.customer.username, DEMO.customer.password)
    const created = await request.post(url('/merchant/tables'), {
      headers: authHeaders(customer.token),
      data: { tableNo: 'T19X000', name: 'T19 越权' }
    })
    expect(created.status()).toBe(403)
    expect((await created.json()).code).toBe(40301)

    const removed = await request.delete(url('/merchant/tables/1'), {
      headers: authHeaders(customer.token)
    })
    expect(removed.status()).toBe(403)
    expect((await removed.json()).code).toBe(40301)
  })
})
