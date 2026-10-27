<script setup>
/**
 * 购物车页（T12）
 * 数据来自服务端 /cart，数量调整即时写回；金额与配送费在结算页由服务端试算。
 */
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import DishThumb from '@/components/DishThumb.vue'
import DataState from '@/components/DataState.vue'
import { formatAmount } from '@/utils/format'
import { useCartStore } from '@/stores/cart'

const router = useRouter()
const cart = useCartStore()
const clearing = ref(false)

const canSubmit = computed(() => !cart.isEmpty && cart.totalQuantity > 0)

async function load() {
  try {
    await cart.fetchCart({ force: true })
  } catch {
    /* 错误由 store.error 展示 */
  }
}

async function changeQuantity(item, quantity) {
  if (quantity <= 0) {
    await removeItem(item)
    return
  }
  try {
    await cart.updateQuantity(item.dishId, quantity)
  } catch (err) {
    ElMessage.error(err?.message || '数量更新失败')
  }
}

async function removeItem(item) {
  try {
    await cart.removeItem(item.dishId)
    ElMessage.success(`已移除：${item.dishName}`)
  } catch (err) {
    ElMessage.error(err?.message || '移除失败')
  }
}

async function clearAll() {
  try {
    await ElMessageBox.confirm('确定清空购物车吗？', '清空购物车', {
      type: 'warning',
      confirmButtonText: '清空',
      cancelButtonText: '再想想'
    })
  } catch {
    return
  }
  clearing.value = true
  try {
    await cart.clear()
    ElMessage.success('购物车已清空')
  } catch (err) {
    ElMessage.error(err?.message || '清空失败')
  } finally {
    clearing.value = false
  }
}

function goCheckout() {
  if (!canSubmit.value) {
    ElMessage.info('购物车是空的，先挑几道菜吧')
    return
  }
  router.push('/checkout')
}

onMounted(load)
</script>

<template>
  <div class="app-container page cart-page">
    <header class="page-header cart-page__header">
      <div>
        <p class="page-eyebrow">Cart</p>
        <h1 class="page-title">购物车</h1>
        <p class="page-subtitle">
          共 {{ cart.totalQuantity }} 件 · 商品金额
          <span class="amount text-strong">{{ formatAmount(cart.totalAmount) }}</span>
        </p>
      </div>
      <el-button v-if="!cart.isEmpty" :loading="clearing" @click="clearAll">
        <el-icon><Delete /></el-icon>
        清空购物车
      </el-button>
    </header>

    <el-alert
      v-if="cart.invalidCount > 0"
      class="cart-page__alert"
      type="warning"
      :title="`有 ${cart.invalidCount} 件菜品已下架或库存不足，未计入合计金额`"
      :closable="false"
      show-icon
    />

    <DataState
      :loading="cart.loading && !cart.loaded"
      :error="cart.error"
      :empty="cart.isEmpty && cart.loaded"
      :skeleton-rows="5"
      @retry="load"
    >
      <template #empty>
        <el-empty description="购物车还空着">
          <el-button type="primary" @click="router.push('/menu')">去逛逛菜单</el-button>
        </el-empty>
      </template>

      <div class="cart-layout">
        <ul class="cart-list surface-card">
          <li v-for="item in cart.items" :key="item.dishId" class="cart-item" :class="{ 'is-invalid': !item.available }">
            <router-link :to="`/menu/${item.dishId}`" class="cart-item__media">
              <DishThumb :src="item.dishImage" :alt="item.dishName" radius="md" />
            </router-link>
            <div class="cart-item__info">
              <router-link :to="`/menu/${item.dishId}`" class="cart-item__name">{{ item.dishName }}</router-link>
              <p class="cart-item__price amount">{{ formatAmount(item.price) }}</p>
              <el-tag v-if="!item.available" type="info" size="small" effect="plain">已下架 / 库存不足</el-tag>
              <span v-else class="cart-item__stock">库存 {{ item.stock }} 份</span>
            </div>
            <div class="cart-item__actions">
              <el-input-number
                :model-value="item.quantity"
                :min="0"
                :max="Math.max(item.quantity, Math.min(99, Number(item.stock) || 99))"
                :disabled="!item.available || cart.mutating"
                size="small"
                label="数量"
                @change="changeQuantity(item, $event)"
              />
              <span class="cart-item__subtotal amount">{{ formatAmount(item.subtotal) }}</span>
              <el-button text type="danger" :disabled="cart.mutating" @click="removeItem(item)">
                删除
              </el-button>
            </div>
          </li>
        </ul>

        <aside class="cart-summary surface-card">
          <h2 class="cart-summary__title">金额汇总</h2>
          <dl class="cart-summary__rows">
            <div>
              <dt>商品金额</dt>
              <dd class="amount">{{ formatAmount(cart.totalAmount) }}</dd>
            </div>
            <div>
              <dt>配送费</dt>
              <dd class="text-tertiary">结算页计算</dd>
            </div>
            <div>
              <dt>优惠</dt>
              <dd class="text-tertiary">结算页选择优惠券</dd>
            </div>
          </dl>
          <div class="cart-summary__total">
            <span>合计</span>
            <strong class="amount">{{ formatAmount(cart.totalAmount) }}</strong>
          </div>
          <el-button type="primary" size="large" class="cart-summary__submit" :disabled="!canSubmit" @click="goCheckout">
            去结算
          </el-button>
          <router-link to="/menu" class="cart-summary__continue">继续点餐</router-link>
          <p class="cart-summary__note">演示环境：订单金额按实展示，模拟支付实际扣款 ¥0.01。</p>
        </aside>
      </div>
    </DataState>
  </div>
</template>

<style scoped>
.page-eyebrow {
  font-size: var(--font-size-xs);
  font-weight: var(--font-weight-medium);
  letter-spacing: 0.24em;
  text-transform: uppercase;
  color: var(--color-primary);
}

.cart-page__alert {
  margin-bottom: var(--space-5);
}

.cart-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 320px;
  gap: var(--space-6);
  align-items: start;
}

.cart-list {
  padding: 0 var(--space-6);
}

.cart-item {
  display: grid;
  grid-template-columns: 96px minmax(0, 1fr) auto;
  gap: var(--space-5);
  align-items: center;
  padding: var(--space-5) 0;
  border-bottom: 1px solid var(--color-border-subtle);
}

.cart-item:last-child {
  border-bottom: none;
}

.cart-item.is-invalid {
  opacity: 0.62;
}

.cart-item__media {
  width: 96px;
  height: 72px;
  overflow: hidden;
  border-radius: var(--radius-md);
}

.cart-item__info {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.cart-item__name {
  color: var(--color-text-primary);
  font-size: var(--font-size-md);
  font-weight: var(--font-weight-medium);
}

.cart-item__price {
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
}

.cart-item__stock {
  color: var(--color-text-tertiary);
  font-size: var(--font-size-xs);
}

.cart-item__actions {
  display: flex;
  align-items: center;
  gap: var(--space-5);
}

.cart-item__subtotal {
  min-width: 76px;
  color: var(--color-text-primary);
  font-weight: var(--font-weight-semibold);
  text-align: right;
}

.cart-summary {
  position: sticky;
  top: calc(var(--layout-header-height) + var(--space-6));
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}

.cart-summary__title {
  font-size: var(--font-size-lg);
}

.cart-summary__rows {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
  padding-bottom: var(--space-4);
  border-bottom: 1px dashed var(--color-border);
  font-size: var(--font-size-sm);
}

.cart-summary__rows div {
  display: flex;
  justify-content: space-between;
}

.cart-summary__rows dd {
  color: var(--color-text-secondary);
}

.cart-summary__total {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  font-size: var(--font-size-md);
}

.cart-summary__total strong {
  color: var(--color-primary);
  font-size: var(--font-size-2xl);
}

.cart-summary__submit {
  width: 100%;
}

.cart-summary__continue {
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
  text-align: center;
}

.cart-summary__note {
  color: var(--color-text-tertiary);
  font-size: var(--font-size-xs);
  line-height: var(--line-height-loose);
}

@media (max-width: 1000px) {
  .cart-layout {
    grid-template-columns: 1fr;
  }

  .cart-summary {
    position: static;
  }
}

@media (max-width: 640px) {
  .cart-list {
    padding: 0 var(--space-4);
  }

  .cart-item {
    grid-template-columns: 72px minmax(0, 1fr);
  }

  .cart-item__media {
    width: 72px;
    height: 60px;
  }

  .cart-item__actions {
    grid-column: 1 / -1;
    justify-content: space-between;
  }
}
</style>
