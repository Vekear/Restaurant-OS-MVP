<script setup>
import { onLaunch, onShow } from '@dcloudio/uni-app'
import { useCartStore } from '@/store/cart'
import { useUserStore } from '@/store/user'
import { useTableStore } from '@/store/table'
import { IS_DEV, IS_MOCK } from '@/utils/config'

const cartStore = useCartStore()
const userStore = useUserStore()
const tableStore = useTableStore()

onLaunch(() => {
  if (IS_DEV) {
    console.info('[app] launch', { mock: IS_MOCK })
  }
  // 恢复登录态、桌台会话与购物车角标
  userStore.syncFromStorage()
  tableStore.syncFromStorage()
  cartStore.syncBadge()
  // 请求层捕获 401 后广播事件，这里清理内存态
  uni.$on('auth:unauthorized', () => {
    userStore.signOut()
    uni.showToast({ title: '登录已过期，请重新登录', icon: 'none' })
  })
})

onShow(() => {
  cartStore.syncBadge()
})
</script>

<style lang="scss">
@import './styles/base.scss';
</style>
