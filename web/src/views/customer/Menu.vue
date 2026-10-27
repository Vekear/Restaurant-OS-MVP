<script setup>
/**
 * 顾客端菜单页（T12）
 * - 分类切换 / 关键词搜索 / 综合·价格·销量排序 / 分页
 * - 菜品卡片直接加购，数量来自服务端购物车
 * - 未登录用户可浏览菜单，加购时引导登录
 */
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import DishCard from '@/components/customer/DishCard.vue'
import DataState from '@/components/DataState.vue'
import { getPublicConfig, getCategories, getDishes } from '@/api/menu'
import { DISH_SORT, DISH_SORT_OPTIONS } from '@/utils/constants'
import { useAuthStore } from '@/stores/auth'
import { useCartStore } from '@/stores/cart'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const cart = useCartStore()

const merchantId = ref(null)
const categories = ref([])
const dishes = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(12)
const keyword = ref('')
const activeCategory = ref(0)
const sort = ref(DISH_SORT.DEFAULT)
const loading = ref(false)
const error = ref('')
const busyDishId = ref(0)

const cartQuantityOf = (dishId) => cart.quantityOf(dishId)

const activeCategoryName = computed(() => {
  if (!activeCategory.value) return '全部菜品'
  return categories.value.find((item) => item.id === activeCategory.value)?.name || '全部菜品'
})

let searchTimer = null
function onKeywordInput() {
  clearTimeout(searchTimer)
  searchTimer = setTimeout(() => {
    page.value = 1
    loadDishes()
  }, 320)
}

async function loadDishes() {
  loading.value = true
  error.value = ''
  try {
    const data = await getDishes({
      merchantId: merchantId.value || undefined,
      categoryId: activeCategory.value || undefined,
      keyword: keyword.value.trim() || undefined,
      sort: sort.value,
      page: page.value,
      size: size.value
    })
    dishes.value = Array.isArray(data?.list) ? data.list : []
    total.value = Number(data?.total || 0)
  } catch (err) {
    error.value = err?.message || '菜品加载失败'
    dishes.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

async function loadCategories() {
  try {
    const data = await getCategories({ merchantId: merchantId.value || undefined })
    categories.value = Array.isArray(data) ? data : []
  } catch {
    categories.value = []
  }
}

async function bootstrap() {
  if (auth.isLoggedIn) cart.ensureLoaded()
  try {
    const config = await getPublicConfig()
    merchantId.value = config?.defaultMerchantId || config?.merchantId || null
  } catch {
    merchantId.value = null
  }
  await Promise.all([loadCategories(), loadDishes()])
}

function selectCategory(id) {
  if (activeCategory.value === id) return
  activeCategory.value = id
  page.value = 1
  loadDishes()
}

function onSortChange() {
  page.value = 1
  loadDishes()
}

function onPageChange(value) {
  page.value = value
  loadDishes()
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

function requireLogin() {
  if (auth.isLoggedIn) return true
  ElMessage.info('登录后即可加入购物车')
  router.push({ name: 'login', query: { redirect: route.fullPath } })
  return false
}

function describeCartError(err) {
  if (!err) return '操作失败'
  if (err.code === 40902) return '购物车已达 50 行上限，请先清理后再添加'
  if (err.code === 40901) return err.message || '菜品库存不足'
  if (err.code === 40918) return '该菜品已下架'
  if (err.code === 40908) return '商家休息中，暂不接单'
  return err.message || '操作失败，请稍后重试'
}

async function handleAdd(dish) {
  if (!requireLogin()) return
  busyDishId.value = dish.id
  try {
    await cart.addItem(dish.id, 1)
    ElMessage.success(`已加入：${dish.name}`)
  } catch (err) {
    ElMessage.error(describeCartError(err))
  } finally {
    busyDishId.value = 0
  }
}

async function handleChange(dish, quantity) {
  if (!auth.isLoggedIn) return
  busyDishId.value = dish.id
  try {
    if (quantity <= 0) await cart.removeItem(dish.id)
    else await cart.updateQuantity(dish.id, quantity)
  } catch (err) {
    ElMessage.error(describeCartError(err))
  } finally {
    busyDishId.value = 0
  }
}

watch(keyword, onKeywordInput)

onMounted(bootstrap)
</script>

<template>
  <div class="app-container page menu-page">
    <header class="page-header menu-page__header">
      <div>
        <p class="page-eyebrow">Menu</p>
        <h1 class="page-title">今日菜单</h1>
        <p class="page-subtitle">共 {{ total }} 道菜 · 点击卡片查看详情，支持按分类、价格与销量筛选</p>
      </div>
      <div class="menu-page__tools">
        <el-input
          v-model="keyword"
          class="menu-page__search"
          placeholder="搜索菜品名称"
          clearable
          :prefix-icon="Search"
        />
        <el-select v-model="sort" class="menu-page__sort" @change="onSortChange">
          <el-option
            v-for="option in DISH_SORT_OPTIONS"
            :key="option.value"
            :label="option.label"
            :value="option.value"
          />
        </el-select>
        <el-badge :value="cart.totalQuantity" :hidden="!cart.totalQuantity" class="menu-page__cart-badge">
          <el-button @click="router.push('/cart')">
            <el-icon><ShoppingCart /></el-icon>
            购物车
          </el-button>
        </el-badge>
      </div>
    </header>

    <div class="menu-layout">
      <aside class="menu-categories" aria-label="菜品分类">
        <button
          type="button"
          class="menu-category"
          :class="{ 'is-active': activeCategory === 0 }"
          @click="selectCategory(0)"
        >
          <span>全部菜品</span>
          <small>{{ total }}</small>
        </button>
        <button
          v-for="category in categories"
          :key="category.id"
          type="button"
          class="menu-category"
          :class="{ 'is-active': activeCategory === category.id }"
          @click="selectCategory(category.id)"
        >
          <span>{{ category.name }}</span>
          <small v-if="category.dishCount != null">{{ category.dishCount }}</small>
        </button>
      </aside>

      <section class="menu-results">
        <header class="menu-results__head">
          <h2>{{ activeCategoryName }}</h2>
          <span class="text-tertiary text-sm">{{ loading ? '加载中…' : `${total} 道菜` }}</span>
        </header>

        <DataState :loading="loading" :error="error" :empty="!dishes.length" @retry="loadDishes">
          <div class="dish-grid">
            <DishCard
              v-for="dish in dishes"
              :key="dish.id"
              :dish="dish"
              :quantity="cartQuantityOf(dish.id)"
              :busy="busyDishId === dish.id"
              @add="handleAdd(dish)"
              @change="handleChange(dish, $event)"
            />
          </div>
        </DataState>

        <div v-if="total > size" class="menu-results__pagination">
          <el-pagination
            background
            layout="prev, pager, next, total"
            :total="total"
            :page-size="size"
            :current-page="page"
            @current-change="onPageChange"
          />
        </div>
      </section>
    </div>
  </div>
</template>

<style scoped>
.menu-page__header {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  justify-content: space-between;
  gap: var(--space-5);
}

.page-eyebrow {
  font-size: var(--font-size-xs);
  font-weight: var(--font-weight-medium);
  letter-spacing: 0.24em;
  text-transform: uppercase;
  color: var(--color-primary);
}

.menu-page__tools {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-3);
}

.menu-page__search {
  width: 240px;
}

.menu-page__sort {
  width: 160px;
}

.menu-layout {
  display: grid;
  grid-template-columns: 188px 1fr;
  gap: var(--space-8);
  align-items: start;
}

.menu-categories {
  position: sticky;
  top: calc(var(--layout-header-height) + var(--space-6));
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  padding: var(--space-3);
  border: 1px solid var(--color-border-subtle);
  border-radius: var(--radius-lg);
  background: var(--color-surface);
}

.menu-category {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-2);
  padding: var(--space-3) var(--space-4);
  border: none;
  border-radius: var(--radius-md);
  background: transparent;
  color: var(--color-text-secondary);
  font-size: var(--font-size-md);
  cursor: pointer;
  text-align: left;
  transition: background var(--duration-fast) var(--ease-standard);
}

.menu-category small {
  color: var(--color-text-tertiary);
  font-size: var(--font-size-xs);
}

.menu-category:hover {
  background: var(--color-bg-subtle);
  color: var(--color-text-primary);
}

.menu-category.is-active {
  background: var(--color-brand-50);
  color: var(--color-primary);
  font-weight: var(--font-weight-medium);
}

.menu-results__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: var(--space-5);
}

.menu-results__head h2 {
  font-size: var(--font-size-xl);
}

.dish-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--space-6);
}

.menu-results__pagination {
  display: flex;
  justify-content: center;
  margin-top: var(--space-8);
}

@media (max-width: 1100px) {
  .dish-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 900px) {
  .menu-layout {
    grid-template-columns: 1fr;
  }

  .menu-categories {
    position: static;
    flex-direction: row;
    overflow-x: auto;
    padding: var(--space-2);
  }

  .menu-category {
    white-space: nowrap;
  }
}

@media (max-width: 640px) {
  .menu-page__search,
  .menu-page__sort {
    width: 100%;
  }

  .dish-grid {
    grid-template-columns: 1fr;
  }
}
</style>
