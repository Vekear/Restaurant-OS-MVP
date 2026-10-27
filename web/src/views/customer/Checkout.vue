<script setup>
/**
 * 确认订单 / 结算页（T12）
 * 流程：购物车 → 选择地址（可新增）→ 选择优惠券（服务端试算）→ 备注 → 创建订单（幂等）→ 模拟支付。
 * 金额全部以服务端 /coupons/quote 试算结果为准，前端不自行计算优惠。
 */
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import DishThumb from '@/components/DishThumb.vue'
import AddressFormDialog from '@/components/customer/AddressFormDialog.vue'
import PaymentDialog from '@/components/customer/PaymentDialog.vue'
import { getMerchant, getPublicConfig } from '@/api/menu'
import { listAddresses } from '@/api/addresses'
import { quoteCoupon } from '@/api/coupons'
import { createOrder } from '@/api/orders'
import { formatAmount } from '@/utils/format'
import { createIdempotencyGuard } from '@/utils/idempotency'
import { MERCHANT_BUSINESS_STATUS, MERCHANT_BUSINESS_STATUS_LABELS, labelOf } from '@/utils/constants'
import { useAuthStore } from '@/stores/auth'
import { useCartStore } from '@/stores/cart'

const router = useRouter()
const auth = useAuthStore()
const cart = useCartStore()

const loading = ref(true)
const error = ref('')
const addresses = ref([])
const selectedAddressId = ref(0)
const merchant = ref(null)
const merchantId = ref(null)
const quote = ref(null)
const quoteLoading = ref(false)
const selectedCouponId = ref(0)
const remark = ref('')
const submitting = ref(false)
const addressDialogVisible = ref(false)
const editingAddress = ref(null)
const paymentVisible = ref(false)
const createdOrder = ref(null)

const guard = createIdempotencyGuard('order-create')

const availableItems = computed(() => cart.items.filter((item) => item.available))
const selectedAddress = computed(
  () => addresses.value.find((item) => item.id === selectedAddressId.value) || null
)
const couponOptions = computed(() => quote.value?.usableCoupons || [])
const reachMinOrder = computed(() => {
  if (!quote.value) return true
  if (typeof quote.value.reachMinOrder === 'boolean') return quote.value.reachMinOrder
  return Number(quote.value.totalAmount || 0) >= Number(quote.value.minOrderAmount || 0)
})
const minOrderGap = computed(() =>
  Math.max(0, Number(quote.value?.minOrderAmount || 0) - Number(quote.value?.totalAmount || 0))
)
const merchantClosed = computed(() => merchant.value?.businessStatus === MERCHANT_BUSINESS_STATUS.CLOSED)
const canSubmit = computed(
  () =>
    Boolean(quote.value) &&
    !submitting.value &&
    Boolean(selectedAddress.value) &&
    reachMinOrder.value &&
    !merchantClosed.value &&
    availableItems.value.length > 0
)

async function loadQuote() {
  if (!merchantId.value) return
  quoteLoading.value = true
  try {
    const data = await quoteCoupon({
      merchantId: merchantId.value,
      totalAmount: Number(cart.totalAmount || 0),
      userCouponId: selectedCouponId.value || undefined
    })
    quote.value = data
    if (data?.userCoupon?.id && !selectedCouponId.value) {
      selectedCouponId.value = data.userCoupon.id
    }
  } catch (err) {
    if (err?.code === 40912) {
      ElMessage.warning('该优惠券已不可用，已为你取消选择')
      selectedCouponId.value = 0
      await loadQuote()
      return
    }
    error.value = err?.message || '金额试算失败'
  } finally {
    quoteLoading.value = false
  }
}

async function loadAddresses() {
  try {
    const data = await listAddresses()
    addresses.value = Array.isArray(data) ? data : []
    const preferred =
      addresses.value.find((item) => item.isDefault) || addresses.value[0] || null
    if (preferred) selectedAddressId.value = preferred.id
  } catch (err) {
    if (err?.code === 40101 || err?.code === 40301) return
    ElMessage.error(err?.message || '收货地址加载失败')
  }
}

async function bootstrap() {
  loading.value = true
  error.value = ''
  try {
    if (!cart.loaded) await cart.fetchCart({ force: true })
    if (cart.isEmpty) {
      ElMessage.info('购物车是空的，先去挑选菜品吧')
      await router.replace('/cart')
      return
    }
    const config = await getPublicConfig()
    merchantId.value = config?.defaultMerchantId || config?.merchantId || null
    if (!merchantId.value) {
      error.value = '未获取到默认商家，请稍后重试'
      return
    }
    merchant.value = await getMerchant(merchantId.value)
    await Promise.all([loadAddresses(), loadQuote()])
  } catch (err) {
    error.value = err?.message || '结算信息加载失败'
  } finally {
    loading.value = false
  }
}

function retryAll() {
  bootstrap()
}

function onCouponChange() {
  loadQuote()
}

function openAddressDialog(address = null) {
  editingAddress.value = address
  addressDialogVisible.value = true
}

async function onAddressSaved(saved) {
  await loadAddresses()
  if (saved?.id) selectedAddressId.value = saved.id
}

function errorText(err) {
  const map = {
    40005: '订单金额发生变化，请确认后重新提交',
    40901: '部分菜品库存不足，请调整数量',
    40902: '购物车行数超过上限',
    40904: '重复提交，请刷新页面后重试',
    40905: '订单状态不允许该操作',
    40908: '商家休息中，暂不接单',
    40912: '优惠券不可用，请重新选择',
    40918: '部分菜品已下架，请返回购物车调整'
  }
  return map[err?.code] || err?.message || '下单失败，请稍后重试'
}

async function submitOrder() {
  if (!selectedAddress.value) {
    ElMessage.warning('请选择收货地址')
    return
  }
  if (!reachMinOrder.value) {
    ElMessage.warning(`还差 ¥${formatAmount(minOrderGap.value)} 起送，再加几道菜吧`)
    return
  }
  if (!availableItems.value.length) {
    ElMessage.warning('没有可结算的菜品')
    return
  }

  const payload = {
    merchantId: merchantId.value,
    addressId: selectedAddress.value.id,
    items: availableItems.value.map((item) => ({ dishId: item.dishId, quantity: item.quantity })),
    userCouponId: quote.value?.userCoupon?.id || selectedCouponId.value || undefined,
    remark: remark.value.trim() || undefined,
    expectedPayAmount: Number(quote.value?.payAmount || 0)
  }

  submitting.value = true
  try {
    const order = await createOrder(payload, guard.keyFor(payload))
    guard.reset()
    createdOrder.value = order
    ElMessage.success('订单创建成功，请完成模拟支付')
    paymentVisible.value = true
    try {
      await cart.fetchCart({ force: true })
    } catch {
      /* 忽略：订单已创建，购物车刷新失败不影响支付 */
    }
  } catch (err) {
    ElMessage.error(errorText(err))
    if (err?.code === 40005 || err?.code === 40912 || err?.code === 40901 || err?.code === 40918) {
      await loadQuote()
      await cart.fetchCart({ force: true }).catch(() => {})
    }
  } finally {
    submitting.value = false
  }
}

function onPaymentVisibleChange(visible) {
  paymentVisible.value = visible
  if (!visible && createdOrder.value) {
    router.replace(`/orders/${createdOrder.value.id}`)
  }
}

function onPaid() {
  paymentVisible.value = false
  if (createdOrder.value) router.replace(`/orders/${createdOrder.value.id}`)
}

function backToCart() {
  router.push('/cart')
}

onMounted(bootstrap)
</script>

<template>
  <div class="app-container page checkout-page">
    <header class="page-header checkout-page__header">
      <div>
        <p class="page-eyebrow">Checkout</p>
        <h1 class="page-title">确认订单</h1>
        <p class="page-subtitle">核对地址与菜品，提交后完成模拟支付（实际扣款 ¥0.01）</p>
      </div>
      <el-button text @click="backToCart">
        <el-icon><Back /></el-icon>
        返回购物车
      </el-button>
    </header>

    <el-alert
      v-if="merchantClosed"
      class="checkout-page__alert"
      type="warning"
      :title="`商家${labelOf(MERCHANT_BUSINESS_STATUS_LABELS, merchant?.businessStatus)}，暂时无法下单`"
      :closable="false"
      show-icon
    />

    <el-skeleton v-if="loading" :rows="8" animated />
    <DataState v-else-if="error" :error="error" :loading="false" @retry="retryAll" />
    <div v-else class="checkout-layout">
      <div class="checkout-main">
        <section class="surface-card checkout-section">
          <header class="checkout-section__head">
            <h2>收货地址</h2>
            <el-button text type="primary" @click="openAddressDialog()">
              <el-icon><Plus /></el-icon>
              新增地址
            </el-button>
          </header>

          <div v-if="!addresses.length" class="checkout-address-empty">
            <el-empty description="还没有收货地址" :image-size="90">
              <el-button type="primary" plain @click="openAddressDialog()">新增收货地址</el-button>
            </el-empty>
          </div>
          <ul v-else class="address-list">
            <li
              v-for="address in addresses"
              :key="address.id"
              class="address-card"
              :class="{ 'is-selected': address.id === selectedAddressId }"
              tabindex="0"
              role="radio"
              :aria-checked="address.id === selectedAddressId"
              @click="selectedAddressId = address.id"
              @keyup.enter="selectedAddressId = address.id"
            >
              <div class="address-card__head">
                <strong>{{ address.consignee }}</strong>
                <span>{{ address.phone }}</span>
                <el-tag v-if="address.isDefault" size="small" type="warning" effect="plain">默认</el-tag>
              </div>
              <p class="address-card__detail">
                {{ address.province }}{{ address.city }}{{ address.district }}{{ address.detail }}
              </p>
              <el-button
                text
                size="small"
                class="address-card__edit"
                @click.stop="openAddressDialog(address)"
              >
                编辑
              </el-button>
            </li>
          </ul>
        </section>

        <section class="surface-card checkout-section">
          <header class="checkout-section__head">
            <h2>菜品明细</h2>
            <span class="text-tertiary text-sm">{{ cart.totalQuantity }} 件</span>
          </header>
          <ul class="checkout-items">
            <li v-for="item in cart.items" :key="item.dishId" class="checkout-item" :class="{ 'is-invalid': !item.available }">
              <div class="checkout-item__media">
                <DishThumb :src="item.dishImage" :alt="item.dishName" radius="sm" />
              </div>
              <div class="checkout-item__info">
                <span class="checkout-item__name">{{ item.dishName }}</span>
                <span v-if="!item.available" class="checkout-item__invalid">已下架 / 库存不足，不参与结算</span>
              </div>
              <span class="checkout-item__qty">×{{ item.quantity }}</span>
              <span class="checkout-item__amount amount">{{ formatAmount(item.subtotal) }}</span>
            </li>
          </ul>
        </section>

        <section class="surface-card checkout-section">
          <header class="checkout-section__head">
            <h2>优惠与备注</h2>
          </header>
          <el-form label-width="88px" class="checkout-form">
            <el-form-item label="优惠券">
              <el-select
                v-model="selectedCouponId"
                placeholder="不使用优惠券"
                clearable
                :loading="quoteLoading"
                class="checkout-form__control"
                @change="onCouponChange"
              >
                <el-option :value="0" label="不使用优惠券" />
                <el-option
                  v-for="coupon in couponOptions"
                  :key="coupon.id"
                  :value="coupon.id"
                  :label="`${coupon.name || '满减券'} · 满 ¥${formatAmount(coupon.thresholdAmount)} 减 ¥${formatAmount(coupon.discountAmount)}`"
                  :disabled="coupon.usable === false"
                />
              </el-select>
              <p v-if="!couponOptions.length" class="checkout-form__hint">
                暂无可用优惠券，可直接提交订单。
              </p>
            </el-form-item>
            <el-form-item label="订单备注">
              <el-input
                v-model="remark"
                type="textarea"
                :rows="2"
                maxlength="200"
                show-word-limit
                placeholder="口味、餐具、配送等备注（选填）"
                class="checkout-form__control"
              />
            </el-form-item>
          </el-form>
        </section>
      </div>

      <aside class="checkout-side">
        <section class="surface-card checkout-summary">
          <h2 class="checkout-summary__title">订单金额</h2>
          <dl class="checkout-summary__rows">
            <div>
              <dt>商品金额</dt>
              <dd class="amount">{{ formatAmount(quote?.totalAmount ?? cart.totalAmount) }}</dd>
            </div>
            <div>
              <dt>配送费</dt>
              <dd class="amount">{{ formatAmount(quote?.deliveryFee ?? merchant?.deliveryFee ?? 0) }}</dd>
            </div>
            <div>
              <dt>优惠抵扣</dt>
              <dd class="amount text-danger">
                -{{ formatAmount(quote?.discountAmount || 0) }}
              </dd>
            </div>
          </dl>
          <div class="checkout-summary__total">
            <span>应付金额</span>
            <strong class="amount">{{ formatAmount(quote?.payAmount || 0) }}</strong>
          </div>
          <p v-if="!reachMinOrder" class="checkout-summary__tip">
            还差 ¥{{ formatAmount(minOrderGap) }} 起送（起送价 ¥{{ formatAmount(quote?.minOrderAmount || 0) }}）
          </p>
          <p v-else-if="quote?.minOrderAmount" class="checkout-summary__tip">
            已满足起送价 ¥{{ formatAmount(quote.minOrderAmount) }}
          </p>

          <el-button
            type="primary"
            size="large"
            class="checkout-summary__submit"
            :loading="submitting"
            :disabled="!canSubmit"
            @click="submitOrder"
          >
            提交订单并支付
          </el-button>
          <p class="checkout-summary__note">
            演示环境：界面展示真实订单金额，模拟支付实际扣款 ¥0.01。
          </p>
        </section>
      </aside>
    </div>

    <AddressFormDialog
      v-model="addressDialogVisible"
      :address="editingAddress"
      @saved="onAddressSaved"
    />
    <PaymentDialog
      :model-value="paymentVisible"
      :order="createdOrder"
      @update:model-value="onPaymentVisibleChange"
      @paid="onPaid"
    />
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

.checkout-page__header {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  justify-content: space-between;
  gap: var(--space-4);
}

.checkout-page__alert {
  margin-bottom: var(--space-5);
}

.checkout-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 320px;
  gap: var(--space-6);
  align-items: start;
}

.checkout-main {
  display: flex;
  flex-direction: column;
  gap: var(--space-5);
}

.checkout-section__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--space-4);
}

.checkout-section__head h2 {
  font-size: var(--font-size-lg);
}

.checkout-address-empty {
  padding: var(--space-4) 0;
}

.address-list {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-4);
}

.address-card {
  position: relative;
  padding: var(--space-4) var(--space-5);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-surface);
  cursor: pointer;
  transition: border-color var(--duration-fast) var(--ease-standard),
    box-shadow var(--duration-fast) var(--ease-standard);
}

.address-card:hover {
  border-color: var(--color-brand-300);
}

.address-card.is-selected {
  border-color: var(--color-primary);
  box-shadow: var(--shadow-focus);
}

.address-card__head {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  font-size: var(--font-size-md);
}

.address-card__head span {
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
}

.address-card__detail {
  margin-top: var(--space-2);
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
  line-height: var(--line-height-snug);
}

.address-card__edit {
  position: absolute;
  top: var(--space-2);
  right: var(--space-2);
}

.checkout-items {
  display: flex;
  flex-direction: column;
}

.checkout-item {
  display: flex;
  align-items: center;
  gap: var(--space-4);
  padding: var(--space-4) 0;
  border-bottom: 1px solid var(--color-border-subtle);
}

.checkout-item:last-child {
  border-bottom: none;
}

.checkout-item.is-invalid {
  opacity: 0.6;
}

.checkout-item__media {
  width: 56px;
  height: 46px;
  overflow: hidden;
  border-radius: var(--radius-sm);
}

.checkout-item__info {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 2px;
}

.checkout-item__name {
  font-size: var(--font-size-md);
}

.checkout-item__invalid {
  color: var(--color-danger);
  font-size: var(--font-size-xs);
}

.checkout-item__qty {
  color: var(--color-text-tertiary);
  font-variant-numeric: tabular-nums;
}

.checkout-item__amount {
  min-width: 72px;
  text-align: right;
}

.checkout-form__control {
  width: 100%;
}

.checkout-form__hint {
  margin-top: var(--space-2);
  color: var(--color-text-tertiary);
  font-size: var(--font-size-xs);
}

.checkout-side {
  position: sticky;
  top: calc(var(--layout-header-height) + var(--space-6));
}

.checkout-summary {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}

.checkout-summary__title {
  font-size: var(--font-size-lg);
}

.checkout-summary__rows {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
  padding-bottom: var(--space-4);
  border-bottom: 1px dashed var(--color-border);
  font-size: var(--font-size-sm);
}

.checkout-summary__rows div {
  display: flex;
  justify-content: space-between;
}

.checkout-summary__rows dd {
  color: var(--color-text-secondary);
}

.checkout-summary__total {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
}

.checkout-summary__total strong {
  color: var(--color-primary);
  font-size: var(--font-size-2xl);
}

.checkout-summary__tip {
  color: var(--color-warning);
  font-size: var(--font-size-xs);
}

.checkout-summary__submit {
  width: 100%;
}

.checkout-summary__note {
  color: var(--color-text-tertiary);
  font-size: var(--font-size-xs);
  line-height: var(--line-height-loose);
}

@media (max-width: 1000px) {
  .checkout-layout {
    grid-template-columns: 1fr;
  }

  .checkout-side {
    position: static;
  }
}

@media (max-width: 640px) {
  .address-list {
    grid-template-columns: 1fr;
  }
}
</style>
