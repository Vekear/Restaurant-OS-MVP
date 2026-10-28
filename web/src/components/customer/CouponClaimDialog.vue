<script setup>
/**
 * 领券中心弹窗（docs/02-api.md §8.1 / §8.2）
 * - 列表：GET /coupons?amount=当前商品金额，服务端计算 claimed / claimable / usable
 * - 领取：POST /coupons/{couponId}/claim，原子扣减，重复领取返回 40912
 * - 领取成功后 emit claimed(userCoupon)，由结算页重新 quote 刷新可用券
 */
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { claimCoupon, listCoupons } from '@/api/coupons'
import { formatAmount, formatDate } from '@/utils/format'
import DataState from '@/components/DataState.vue'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  amount: { type: Number, default: 0 }
})

const emit = defineEmits(['update:modelValue', 'claimed'])

const visible = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value)
})

const list = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(5)
const loading = ref(false)
const error = ref('')
const claimingId = ref(0)

function couponState(coupon) {
  if (coupon.claimed) return { text: '已领取', type: 'info', disabled: true }
  if (coupon.remaining <= 0) return { text: '已领完', type: 'info', disabled: true }
  return { text: '可领取', type: 'success', disabled: false }
}

function canClaim(coupon) {
  return Boolean(coupon.claimable) && !coupon.claimed && Number(coupon.remaining || 0) > 0
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    const data = await listCoupons({ amount: props.amount || undefined, page: page.value, size: size.value })
    list.value = Array.isArray(data?.list) ? data.list : []
    total.value = Number(data?.total || 0)
  } catch (err) {
    error.value = err?.message || '优惠券加载失败'
    list.value = []
  } finally {
    loading.value = false
  }
}

async function claim(coupon) {
  claimingId.value = coupon.id
  try {
    const userCoupon = await claimCoupon(coupon.id)
    ElMessage.success(`领取成功：${coupon.name}`)
    emit('claimed', userCoupon)
    await load()
  } catch (err) {
    if (err?.code === 40912) {
      ElMessage.warning('该券已领完或您已领取过')
      await load()
    } else {
      ElMessage.error(err?.message || '领取失败，请稍后重试')
    }
  } finally {
    claimingId.value = 0
  }
}

function onPageChange(next) {
  page.value = next
  load()
}

watch(
  () => props.modelValue,
  (open) => {
    if (open) {
      page.value = 1
      load()
    }
  }
)

// 首次挂载即打开（如测试或深链）时也要拉取
onMounted(() => {
  if (props.modelValue) load()
})
</script>

<template>
  <el-dialog v-model="visible" title="领券中心" width="560px" append-to-body>
    <p class="coupon-dialog__tip">
      优惠券不可叠加使用；当前商品金额
      <strong class="amount">{{ formatAmount(amount) }}</strong>
    </p>

    <DataState :loading="loading" :error="error" :empty="!list.length" :skeleton-rows="3" @retry="load">
      <template #empty>
        <el-empty description="暂无可领取的优惠券" :image-size="80" />
      </template>

      <ul class="coupon-list">
        <li v-for="coupon in list" :key="coupon.id" class="coupon-item">
          <div class="coupon-item__value">
            <strong class="amount">{{ formatAmount(coupon.discountAmount) }}</strong>
            <span>满 {{ formatAmount(coupon.thresholdAmount) }} 可用</span>
          </div>
          <div class="coupon-item__info">
            <p class="coupon-item__name">{{ coupon.name }}</p>
            <p class="coupon-item__meta">
              <el-tag size="small" type="warning" effect="plain">
                {{ couponState(coupon).text }}
              </el-tag>
              <span>有效期至 {{ formatDate(coupon.endAt) }}</span>
            </p>
          </div>
          <el-button
            type="primary"
            plain
            size="small"
            :disabled="couponState(coupon).disabled"
            :loading="claimingId === coupon.id"
            @click="claim(coupon)"
          >
            {{ coupon.claimed ? '已领取' : canClaim(coupon) ? '立即领取' : '暂不可领' }}
          </el-button>
        </li>
      </ul>
    </DataState>

    <div v-if="total > size" class="coupon-dialog__pager">
      <el-pagination
        layout="prev, pager, next"
        :total="total"
        :page-size="size"
        :current-page="page"
        background
        small
        @current-change="onPageChange"
      />
    </div>

    <template #footer>
      <el-button @click="visible = false">关闭</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.coupon-dialog__tip {
  margin-bottom: var(--space-3);
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
}

.coupon-list {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}

.coupon-item {
  display: grid;
  grid-template-columns: 96px minmax(0, 1fr) auto;
  gap: var(--space-4);
  align-items: center;
  padding: var(--space-3) var(--space-4);
  border: 1px dashed var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-bg-subtle);
}

.coupon-item__value {
  display: flex;
  flex-direction: column;
  gap: 2px;
  color: var(--color-primary);
}

.coupon-item__value strong {
  font-size: var(--font-size-xl);
}

.coupon-item__value span {
  color: var(--color-text-tertiary);
  font-size: var(--font-size-xs);
}

.coupon-item__name {
  color: var(--color-text-primary);
  font-size: var(--font-size-md);
  font-weight: var(--font-weight-medium);
}

.coupon-item__meta {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  margin-top: var(--space-2);
  color: var(--color-text-tertiary);
  font-size: var(--font-size-xs);
}

.coupon-dialog__pager {
  display: flex;
  justify-content: flex-end;
  margin-top: var(--space-3);
}
</style>
