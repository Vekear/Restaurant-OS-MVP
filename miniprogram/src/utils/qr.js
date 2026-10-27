/**
 * 桌台二维码解析。
 *
 * 二维码内容（契约 §2.7）：`{app.table.qr-base-url}?p={tableId}&s={signature}`
 * 例：http://localhost/dine-in?p=3&s=J8k...
 * 兼容：纯 query 字符串 `p=3&s=...`、JSON `{"p":"3","s":"..."}`。
 * 返回 `{ p, s }`（字符串），解析失败返回 null。
 */

function readParam(text, name) {
  const matched = new RegExp(`[?&]${name}=([^&#]+)`).exec(text)
  if (!matched) return ''
  try {
    return decodeURIComponent(matched[1])
  } catch (err) {
    return matched[1]
  }
}

export function parseTableQr(rawText) {
  const text = String(rawText || '').trim()
  if (!text) return null

  if (text.charAt(0) === '{') {
    try {
      const json = JSON.parse(text)
      const p = json.p !== undefined ? String(json.p) : json.tableId !== undefined ? String(json.tableId) : ''
      const s = json.s !== undefined ? String(json.s) : json.sign !== undefined ? String(json.sign) : ''
      return p ? { p, s } : null
    } catch (err) {
      return null
    }
  }

  const p = readParam(text, 'p') || readParam(text, 'tableId')
  const s = readParam(text, 's') || readParam(text, 'sign')
  if (!p) return null
  return { p, s }
}

/** 校验 tableId 形态（契约：1–20 位数字） */
export function isTableId(value) {
  return /^\d{1,20}$/.test(String(value || ''))
}

/** 校验签名形态（契约：16–256 位 Base64URL 无填充） */
export function isQrSignature(value) {
  return /^[A-Za-z0-9_-]{16,256}$/.test(String(value || ''))
}

/** 手输桌号规范化：去空格、大写 */
export function normalizeTableNo(value) {
  return String(value || '').trim().toUpperCase()
}
