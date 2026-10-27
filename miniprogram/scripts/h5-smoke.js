/**
 * H5 冒烟脚本（可选）：用 Chrome DevTools Protocol 在真实浏览器中走查菜单/登录/购物车。
 *
 * 前置：先启动演示模式开发服务器，再启动带远程调试端口的 Chrome：
 *   pnpm dev:h5:mock
 *   chrome --headless=new --remote-debugging-port=9333 --user-data-dir=/tmp/chrome-smoke about:blank
 * 运行：
 *   node scripts/h5-smoke.js [http://127.0.0.1:5174]
 *
 * 仅使用 Node 内置模块（要求 Node >= 22，自带全局 WebSocket 与 fetch）。
 */
const CART_KEY = 'restaurant_mp_cart_v1'
const TOKEN_KEY = 'restaurant_mp_token'
const CART_SEED = [
  { id: 102, name: '本帮红烧肉', price: 48, image: '/static/demo/dish-1.png', categoryId: 1, quantity: 2 },
  { id: 114, name: '鲜榨橙汁', price: 12, image: '/static/demo/dish-6.png', categoryId: 6, quantity: 1 }
]

const BASE = (process.argv[2] || 'http://127.0.0.1:5174').replace(/\/+$/, '')
const CDP_PORT = Number(process.env.CDP_PORT || 9333)
const sleep = (ms) => new Promise((r) => setTimeout(r, ms))

async function main() {
  const targets = await (await fetch(`http://127.0.0.1:${CDP_PORT}/json/list`)).json()
  const page = targets.find((t) => t.type === 'page')
  if (!page) throw new Error('未找到 Chrome 页面 target，请确认已用 --remote-debugging-port 启动')

  const ws = new WebSocket(page.webSocketDebuggerUrl)
  let seq = 0
  const pending = new Map()
  const errors = []

  const send = (method, params = {}) =>
    new Promise((resolve, reject) => {
      const id = ++seq
      pending.set(id, { resolve, reject })
      ws.send(JSON.stringify({ id, method, params }))
    })

  ws.onmessage = (event) => {
    const msg = JSON.parse(event.data)
    if (msg.id && pending.has(msg.id)) {
      const { resolve, reject } = pending.get(msg.id)
      pending.delete(msg.id)
      if (msg.error) reject(new Error(JSON.stringify(msg.error)))
      else resolve(msg.result)
      return
    }
    if (msg.method === 'Runtime.exceptionThrown') {
      const detail = msg.params.exceptionDetails
      errors.push(detail.exception ? detail.exception.description || detail.exception.value : detail.text)
    }
    if (msg.method === 'Runtime.consoleAPICalled' && msg.params.type === 'error') {
      errors.push(msg.params.args.map((a) => a.value || a.description || '').join(' '))
    }
  }
  await new Promise((resolve, reject) => {
    ws.onopen = resolve
    ws.onerror = () => reject(new Error('WebSocket 连接失败'))
  })

  const evaluate = async (expression) => {
    const res = await send('Runtime.evaluate', { expression, returnByValue: true })
    if (res.exceptionDetails) throw new Error('执行脚本失败: ' + JSON.stringify(res.exceptionDetails))
    return res.result.value
  }
  const open = async (hash, wait = 3500) => {
    await send('Page.navigate', { url: `${BASE}/#${hash}` })
    await sleep(wait)
  }

  await send('Runtime.enable')
  await send('Page.enable')
  await send('Emulation.setDeviceMetricsOverride', { width: 390, height: 844, deviceScaleFactor: 2, mobile: true })

  // 干净状态
  await open('/pages/menu/menu', 3000)
  await evaluate(`localStorage.clear(); location.reload(); 'cleared'`)
  await sleep(4500)

  const result = {}
  await open('/pages/menu/menu', 4000)
  result.menu = JSON.parse(
    await evaluate(`JSON.stringify({
      cards: document.querySelectorAll('.dish').length,
      chips: document.querySelectorAll('.cats__item').length,
      overflow: document.body.scrollWidth > window.innerWidth
    })`)
  )

  await evaluate(`uni.setStorageSync(${JSON.stringify(CART_KEY)}, ${JSON.stringify(CART_SEED)}); 'ok'`)
  await send('Page.reload')
  await sleep(4000)
  result.cartBadge = await evaluate(`(document.querySelector('.uni-tabbar__badge') || {}).innerText || ''`)

  await open('/pages/login/login', 2500)
  result.loginPageToken = await evaluate(`localStorage.getItem(${JSON.stringify(TOKEN_KEY)})`)
  result.loginClick = await evaluate(`(() => {
    const btn = Array.from(document.querySelectorAll('button, .login-btn'))
      .find((el) => (el.innerText || '').indexOf('微信一键登录') >= 0)
    if (!btn) return 'button-not-found'
    btn.click()
    return 'clicked'
  })()`)
  await sleep(2500)
  result.afterLogin = JSON.parse(
    await evaluate(`JSON.stringify({
      hash: location.hash,
      token: localStorage.getItem(${JSON.stringify(TOKEN_KEY)})
    })`)
  )

  await open('/pages/cart/cart', 2500)
  result.cart = JSON.parse(
    await evaluate(`JSON.stringify({
      items: document.querySelectorAll('.cart-item').length,
      total: (document.querySelector('.checkout-bar__number') || {}).innerText || '',
      stepperValues: Array.from(document.querySelectorAll('.stepper__value')).map((n) => n.innerText)
    })`)
  )
  await evaluate(`(() => { const b = document.querySelector('.cart-item .stepper__btn--plus'); if (b) b.click() })()`)
  await sleep(800)
  result.cartAfterPlus = JSON.parse(
    await evaluate(`JSON.stringify({
      total: (document.querySelector('.checkout-bar__number') || {}).innerText || '',
      stepperValues: Array.from(document.querySelectorAll('.stepper__value')).map((n) => n.innerText),
      badge: (document.querySelector('.uni-tabbar__badge') || {}).innerText || ''
    })`)
  )
  result.consoleErrors = errors

  const checks = [
    ['菜单卡片 15 张', result.menu.cards === 15],
    ['分类标签 7 个（含全部）', result.menu.chips === 7],
    ['无横向溢出', result.menu.overflow === false],
    ['购物车角标为 3', result.cartBadge === '3'],
    ['登录页初始无 token', !result.loginPageToken],
    ['点击登录成功', result.loginClick === 'clicked'],
    ['登录后获得 mock token', String(result.afterLogin.token || '').indexOf('mock-token-') === 0],
    ['购物车 2 条、合计 108.00', result.cart.items === 2 && result.cart.total === '108.00'],
    ['加购后合计 156.00、角标 4', result.cartAfterPlus.total === '156.00' && result.cartAfterPlus.badge === '4'],
    ['无控制台错误', result.consoleErrors.length === 0]
  ]

  console.log(JSON.stringify(result, null, 2))
  const failed = checks.filter(([, pass]) => !pass)
  if (failed.length) {
    console.error('SMOKE_FAILED:', failed.map(([name]) => name).join(', '))
    process.exitCode = 1
  } else {
    console.log('SMOKE_PASS: ' + checks.map(([name]) => name).join(' | '))
  }
  ws.close()
}

main().catch((err) => {
  console.error('SMOKE_ERROR', err && err.message ? err.message : err)
  process.exitCode = 1
})
