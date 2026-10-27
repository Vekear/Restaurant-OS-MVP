<script setup>
/**
 * 商家工作台布局
 * - 复用 ConsoleLayout 注入商家导航与主题
 * - 进入工作台时加载店铺资料与营业状态、订阅本店订单 WebSocket
 * - 全局新订单提示：右上角 ElNotification + 右下角悬浮入口（进入看板后清零）
 */
import { onBeforeUnmount, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElNotification } from 'element-plus'
import ConsoleLayout from '@/layouts/ConsoleLayout.vue'
import { consoleProfiles } from '@/config/navigation'
import { useMerchantStore } from '@/stores/merchant'
import { formatCurrency } from '@/utils/format'
import { WS_EVENT_TYPE } from '@/utils/constants'

const profile = consoleProfiles.merchant
const merchant = useMerchantStore()
const router = useRouter()

function openOrderBoard() {
  router.push('/merchant/orders')
  merchant.clearNewOrderCount()
}

watch(
  () => merchant.lastEvent,
  (event) => {
    const payload = event?.payload
    if (!payload || payload.eventType !== WS_EVENT_TYPE.ORDER_CREATED) return
    ElNotification({
      title: '收到新订单',
      message: `${payload.orderNo || ''} · ${formatCurrency(payload.payAmount || 0)}${
        payload.tableNo ? ` · 桌号 ${payload.tableNo}` : ''
      }`,
      type: 'success',
      duration: 8000,
      onClick: openOrderBoard
    })
  }
)

onMounted(async () => {
  await merchant.loadProfile()
  merchant.loadBusinessStatus()
  merchant.bindRealtime()
})

onBeforeUnmount(() => {
  merchant.unbindRealtime()
})
</script>

<template>
  <div class="merchant-frame">
    <ConsoleLayout
      :theme="profile.theme"
      :title="profile.title"
      :subtitle="profile.subtitle"
      :menu-items="profile.nav"
      :home-path="profile.basePath"
    />

    <transition name="fade-slide">
      <button
        v-if="merchant.newOrderCount > 0"
        type="button"
        class="new-order-pill"
        @click="openOrderBoard"
      >
        <span class="new-order-pill__dot" />
        <el-icon><Bell /></el-icon>
        <span>{{ merchant.newOrderCount }} 个新订单待处理</span>
        <el-icon><ArrowRight /></el-icon>
      </button>
    </transition>
  </div>
</template>

<style scoped>
.merchant-frame {
  min-height: 100vh;
}

.new-order-pill {
  position: fixed;
  right: var(--space-8);
  bottom: var(--space-8);
  z-index: var(--z-sticky);
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-3) var(--space-5);
  border: 0;
  border-radius: var(--radius-pill);
  background: var(--color-primary);
  color: #fff;
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-medium);
  box-shadow: var(--shadow-lg);
  cursor: pointer;
  transition: transform var(--duration-fast) var(--ease-standard),
    box-shadow var(--duration-fast) var(--ease-standard);
}

.new-order-pill:hover {
  transform: translateY(-2px);
  box-shadow: var(--shadow-xl);
}

.new-order-pill__dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #fff;
  box-shadow: 0 0 0 4px rgba(255, 255, 255, 0.28);
}
</style>
