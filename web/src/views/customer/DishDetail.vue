<script setup>
/**
 * 菜品详情页（T12）
 * 展示菜品信息、评分聚合与最近评价；支持选择数量加入购物车 / 立即结算。
 */
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import DishThumb from '@/components/DishThumb.vue'
import DataState from '@/components/DataState.vue'
import { getDish, getDishReviews } from '@/api/menu'
import { formatAmount, formatDateTime } from '@/utils/format'
import { DISH_STATUS, DISH_STATUS_LABELS, labelOf } from '@/utils/constants'
import { useAuthStore } from '@/stores/auth'
import { useCartStore } from '@/stores/cart'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const cart = useCartStore()

const dishId = computed(() => Number(route.params.dishId))
const dish = ref(null)
const reviews = ref([])
const reviewTotal = ref(0)
const loading = ref(true)
const error = ref('')
const reviewsLoading = ref(false)
const reviewsError = ref('')
const quantity = ref(1)
const busy = ref(false)

const soldOut = computed(() => {
  const item = dish.value
  if (!item) return false
  return Boolean(item.soldOut) || Number(item.stock) <= 0 || item.status === DISH_STATUS.OFF_SHELF
})
const maxQuantity = computed(() => Math.max(1, Math.min(99, Number(dish.value?.stock) || 1)))
const rating = computed(() => Number(dish.value?.rating ?? dish.value?.avgRating ?? 0))
const reviewCount = computed(() =>
  Number(dish.value?.reviewCount ?? dish.value?.ratingCount ?? reviewTotal.value ?? 0)
)
const originalPrice = computed(() =>
  Number(dish.value?.originalPrice) > Number(dish.value?.price) ? dish.value.originalPrice : null
)
const inCartQuantity = computed(() => (dish.value ? cart.quantityOf(dish.value.id) : 0))

async function loadDish() {
  loading.value = true
  error.value = ''
  try {
    dish.value = await getDish(dishId.value)
    quantity.value = 1
    loadReviews()
  } catch (err) {
    error.value = err?.message || '菜品加载失败'
    dish.value = null
  } finally {
    loading.value = false
  }
}

async function loadReviews() {
  reviewsLoading.value = true
  reviewsError.value = ''
  try {
    const data = await getDishReviews(dishId.value, { page: 1, size: 5 })
    reviews.value = Array.isArray(data?.list) ? data.list : []
    reviewTotal.value = Number(data?.total || 0)
  } catch (err) {
    reviewsError.value = err?.message || '评价加载失败'
    reviews.value = []
  } finally {
    reviewsLoading.value = false
  }
}

function requireLogin() {
  if (auth.isLoggedIn) return true
  ElMessage.info('登录后即可加入购物车')
  router.push({ name: 'login', query: { redirect: route.fullPath } })
  return false
}

async function addToCart(goCheckout = false) {
  if (!requireLogin()) return
  if (soldOut.value) return
  busy.value = true
  try {
    await cart.addItem(dish.value.id, quantity.value)
    if (goCheckout) {
      router.push('/cart')
    } else {
      ElMessage.success(`已加入购物车：${dish.value.name} × ${quantity.value}`)
    }
  } catch (err) {
    if (err?.code === 40902) ElMessage.error('购物车已达 50 行上限，请先清理后再添加')
    else if (err?.code === 40901) ElMessage.error(err.message || '库存不足')
    else ElMessage.error(err?.message || '加入购物车失败')
  } finally {
    busy.value = false
  }
}

watch(dishId, loadDish)
onMounted(loadDish)
</script>

<template>
  <div class="app-container page dish-detail">
    <el-breadcrumb class="dish-detail__crumb" separator="/">
      <el-breadcrumb-item :to="{ path: '/menu' }">菜单</el-breadcrumb-item>
      <el-breadcrumb-item>{{ dish?.name || '菜品详情' }}</el-breadcrumb-item>
    </el-breadcrumb>

    <DataState :loading="loading" :error="error" :empty="!dish && !loading" @retry="loadDish">
      <template #empty>
        <el-empty description="菜品不存在或已下架">
          <el-button type="primary" plain @click="router.push('/menu')">返回菜单</el-button>
        </el-empty>
      </template>

      <div v-if="dish" class="dish-detail__layout">
        <div class="dish-detail__media">
          <DishThumb :src="dish.imageUrl" :alt="dish.name" radius="lg" />
          <span v-if="soldOut" class="dish-detail__media-badge">已售罄</span>
        </div>

        <div class="dish-detail__info">
          <div class="dish-detail__headline">
            <h1 class="dish-detail__title">{{ dish.name }}</h1>
            <el-tag
              :type="dish.status === DISH_STATUS.ON_SALE ? 'success' : 'info'"
              effect="plain"
              round
            >
              {{ labelOf(DISH_STATUS_LABELS, dish.status, '在售') }}
            </el-tag>
          </div>

          <p class="dish-detail__meta">
            <span v-if="dish.categoryName">{{ dish.categoryName }}</span>
            <span>月售 {{ dish.sales || 0 }}</span>
            <span v-if="dish.stock != null">库存 {{ dish.stock }} 份</span>
            <span v-if="reviewCount" class="dish-detail__rating">
              <el-rate :model-value="rating" disabled allow-half size="small" />
              {{ rating.toFixed(1) }} · {{ reviewCount }} 条评价
            </span>
          </p>

          <div class="dish-detail__price">
            <span class="amount">{{ formatAmount(dish.price) }}</span>
            <s v-if="originalPrice" class="dish-detail__original">¥{{ formatAmount(originalPrice) }}</s>
          </div>

          <p v-if="dish.description" class="dish-detail__desc">{{ dish.description }}</p>

          <el-alert
            v-if="dish.status === DISH_STATUS.OFF_SHELF"
            class="dish-detail__alert"
            type="info"
            title="该菜品已下架，暂时无法购买"
            :closable="false"
            show-icon
          />

          <div class="dish-detail__actions">
            <el-input-number
              v-model="quantity"
              :min="1"
              :max="maxQuantity"
              :disabled="soldOut"
              label="购买数量"
            />
            <el-button
              type="primary"
              size="large"
              :loading="busy"
              :disabled="soldOut"
              @click="addToCart(false)"
            >
              <el-icon><ShoppingCart /></el-icon>
              加入购物车
            </el-button>
            <el-button size="large" :disabled="soldOut" @click="addToCart(true)">立即结算</el-button>
          </div>
          <p v-if="inCartQuantity" class="dish-detail__in-cart">
            购物车已有 {{ inCartQuantity }} 份，
            <router-link to="/cart">去查看</router-link>
          </p>
        </div>
      </div>

      <section v-if="dish" class="dish-detail__reviews surface-card">
        <header class="dish-detail__reviews-head">
          <h2>用户评价</h2>
          <span class="text-tertiary text-sm">{{ reviewTotal }} 条</span>
        </header>

        <DataState
          :loading="reviewsLoading"
          :error="reviewsError"
          :empty="!reviews.length"
          :skeleton-rows="3"
          @retry="loadReviews"
        >
          <template #empty>
            <el-empty description="还没有评价，等你来尝鲜" :image-size="90" />
          </template>
          <ul class="review-list">
            <li v-for="review in reviews" :key="review.id" class="review-item">
              <div class="review-item__head">
                <strong>{{ review.userName || '匿名顾客' }}</strong>
                <el-rate :model-value="review.rating" disabled size="small" />
                <span class="review-item__time">{{ formatDateTime(review.createdAt) }}</span>
              </div>
              <p class="review-item__content">{{ review.content || '该用户未填写评价内容' }}</p>
            </li>
          </ul>
        </DataState>
      </section>
    </DataState>
  </div>
</template>

<style scoped>
.dish-detail__crumb {
  margin-bottom: var(--space-6);
}

.dish-detail__layout {
  display: grid;
  grid-template-columns: minmax(0, 0.95fr) minmax(0, 1.05fr);
  gap: var(--space-10);
  align-items: start;
}

.dish-detail__media {
  position: relative;
  overflow: hidden;
  aspect-ratio: 4 / 3;
  border: 1px solid var(--color-border-subtle);
  border-radius: var(--radius-xl);
  box-shadow: var(--shadow-md);
}

.dish-detail__media-badge {
  position: absolute;
  top: var(--space-4);
  left: var(--space-4);
  padding: var(--space-1) var(--space-4);
  border-radius: var(--radius-pill);
  background: rgba(34, 28, 23, 0.72);
  color: #fff;
  font-size: var(--font-size-xs);
  letter-spacing: 0.1em;
}

.dish-detail__headline {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-4);
}

.dish-detail__title {
  font-size: var(--font-size-3xl);
  letter-spacing: -0.02em;
}

.dish-detail__meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-4);
  margin-top: var(--space-4);
  color: var(--color-text-tertiary);
  font-size: var(--font-size-sm);
}

.dish-detail__rating {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
}

.dish-detail__price {
  display: flex;
  align-items: baseline;
  gap: var(--space-3);
  margin-top: var(--space-5);
  color: var(--color-primary);
  font-size: var(--font-size-3xl);
  font-weight: var(--font-weight-bold);
}

.dish-detail__original {
  color: var(--color-text-tertiary);
  font-size: var(--font-size-md);
  font-weight: var(--font-weight-regular);
}

.dish-detail__desc {
  margin-top: var(--space-5);
  color: var(--color-text-secondary);
  line-height: var(--line-height-loose);
}

.dish-detail__alert {
  margin-top: var(--space-5);
}

.dish-detail__actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-4);
  margin-top: var(--space-8);
  padding-top: var(--space-6);
  border-top: 1px dashed var(--color-border);
}

.dish-detail__in-cart {
  margin-top: var(--space-3);
  color: var(--color-text-tertiary);
  font-size: var(--font-size-sm);
}

.dish-detail__reviews {
  margin-top: var(--space-12);
}

.dish-detail__reviews-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: var(--space-5);
}

.dish-detail__reviews-head h2 {
  font-size: var(--font-size-xl);
}

.review-list {
  display: flex;
  flex-direction: column;
}

.review-item {
  padding: var(--space-5) 0;
  border-bottom: 1px solid var(--color-border-subtle);
}

.review-item:last-child {
  border-bottom: none;
}

.review-item__head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-3);
  font-size: var(--font-size-sm);
}

.review-item__time {
  margin-left: auto;
  color: var(--color-text-tertiary);
  font-size: var(--font-size-xs);
}

.review-item__content {
  margin-top: var(--space-3);
  color: var(--color-text-secondary);
  line-height: var(--line-height-loose);
}

@media (max-width: 900px) {
  .dish-detail__layout {
    grid-template-columns: 1fr;
    gap: var(--space-6);
  }
}
</style>
