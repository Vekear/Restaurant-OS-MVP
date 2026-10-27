import { IS_MOCK } from '@/utils/config'

/**
 * 演示数据延迟，模拟真实网络耗时，便于观察 loading / 骨架屏。
 * @param {any} data 任意可 JSON 序列化的数据
 * @param {number} ms 延迟毫秒
 */
export function mockDelay(data, ms = 260) {
  return new Promise((resolve) => {
    setTimeout(() => resolve(JSON.parse(JSON.stringify(data))), ms)
  })
}

export { IS_MOCK }
