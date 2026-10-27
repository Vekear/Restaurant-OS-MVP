<script setup>
/**
 * 商家菜品与分类管理（docs/02-api.md §16）
 * - 左侧分类维护（新增/编辑/删除，删除受 40915 约束）
 * - 右侧菜品列表：关键词/状态/分类筛选、上下架、库存调整、编辑、删除、图片上传
 * - 库存 ≤5 高亮，可一键筛出低库存菜品
 */
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  deleteCategory,
  deleteDish,
  listCategories,
  listDishes,
  updateDishStatus,
  updateDishStock
} from '@/api/merchantDishes'
import { useMerchantStore } from '@/stores/merchant'
import { formatAmount } from '@/utils/format'
import { DISH_STATUS, DISH_STATUS_LABELS, DISH_STATUS_TAG_TYPES, tagTypeOf } from '@/utils/constants'
import DataState from '@/components/DataState.vue'
import DishThumb from '@/components/DishThumb.vue'
import DishFormDialog from '@/components/merchant/DishFormDialog.vue'
import CategoryDialog from '@/components/merchant/CategoryDialog.vue'

const merchant = useMerchantStore()

/* ==================== 分类 ==================== */
const categories = ref([])
const categoriesLoading = ref(false)
const activeCategoryId = ref(null)
const categoryDialogVisible = ref(false)
const editingCategory = ref(null)

const allDishesCount = computed(() =>
  categories.value.reduce((sum, item) => sum + Number(item.dishCount || 0), 0)
)

async function loadCategories() {
  categoriesLoading.value = true
  try {
    categories.value = await listCategories()
  } catch (error) {
    ElMessage.warning(error?.message || '分类加载失败')
    categories.value = []
  } finally {
    categoriesLoading.value = false
  }
}

function openCreateCategory() {
  editingCategory.value = null
  categoryDialogVisible.value = true
}

function openEditCategory(category) {
  editingCategory.value = category
  categoryDialogVisible.value = true
}

async function removeCategory(category) {
  try {
    await ElMessageBox.confirm(
      `删除分类「${category.name}」？分类下仍有菜品时将无法删除。`,
      '删除分类',
      { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning' }
    )
  } catch {
    return
  }
  try {
    await deleteCategory(category.id)
    ElMessage.success('分类已删除')
    if (activeCategoryId.value === category.id) activeCategoryId.value = null
    await loadCategories()
    await loadDishes()
  } catch (error) {
    if (error?.code === 40915) {
      ElMessage.error('该分类下仍有菜品，请先移出或删除菜品')
    } else {
      ElMessage.error(error?.message || '删除失败')
    }
  }
}

/* ==================== 菜品列表 ==================== */
const rows = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const loading = ref(false)
const error = ref('')
const keyword = ref('')
const statusFilter = ref('')
const lowStockOnly = ref(false)

const isEmpty = computed(() => !loading.value && !error.value && rows.value.length === 0)

const statusOptions = Object.entries(DISH_STATUS_LABELS).map(([value, label]) => ({ value, label }))

const dishDialogVisible = ref(false)
const editingDish = ref(null)
const statusSaving = ref(null)

const stockDialogVisible = ref(false)
const stockForm = reactive({ dish: null, stock: 0, reason: '' })
const stockSaving = ref(false)

let keywordTimer = null

async function loadDishes() {
  loading.value = true
  error.value = ''
  try {
    const params = { page: page.value, size: size.value }
    if (activeCategoryId.value) params.categoryId = activeCategoryId.value
    if (keyword.value.trim()) params.keyword = keyword.value.trim()
    if (statusFilter.value) params.status = statusFilter.value
    const result = await listDishes(params)
    let list = result?.list || []
    if (lowStockOnly.value) list = list.filter((dish) => Number(dish.stock) <= 5)
    rows.value = list
    total.value = lowStockOnly.value ? list.length : result?.total || 0
  } catch (err) {
    error.value = err?.message || '菜品加载失败'
    rows.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

watch(keyword, () => {
  if (keywordTimer) window.clearTimeout(keywordTimer)
  keywordTimer = window.setTimeout(() => {
    page.value = 1
    loadDishes()
  }, 320)
})

function selectCategory(id) {
  activeCategoryId.value = id
  page.value = 1
  loadDishes()
}

function changeStatusFilter() {
  page.value = 1
  loadDishes()
}

function onPageChange(next) {
  page.value = next
  loadDishes()
}

function openCreateDish() {
  editingDish.value = null
  dishDialogVisible.value = true
}

function openEditDish(dish) {
  editingDish.value = dish
  dishDialogVisible.value = true
}

async function toggleStatus(dish, nextStatus) {
  statusSaving.value = dish.id
  try {
    await updateDishStatus(dish.id, nextStatus)
    dish.status = nextStatus
    ElMessage.success(nextStatus === DISH_STATUS.ON_SALE ? '菜品已上架' : '菜品已下架')
  } catch (error) {
    ElMessage.error(error?.message || '状态更新失败')
    await loadDishes()
  } finally {
    statusSaving.value = null
  }
}

async function removeDish(dish) {
  try {
    await ElMessageBox.confirm(`删除菜品「${dish.name}」？删除后不可恢复。`, '删除菜品', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch {
    return
  }
  try {
    await deleteDish(dish.id)
    ElMessage.success('菜品已删除')
    await Promise.all([loadDishes(), loadCategories()])
  } catch (error) {
    ElMessage.error(error?.message || '删除失败')
  }
}

function openStockDialog(dish) {
  stockForm.dish = dish
  stockForm.stock = Number(dish.stock || 0)
  stockForm.reason = ''
  stockDialogVisible.value = true
}

async function saveStock() {
  if (!stockForm.dish) return
  stockSaving.value = true
  try {
    const result = await updateDishStock(stockForm.dish.id, Number(stockForm.stock))
    const dish = rows.value.find((item) => item.id === stockForm.dish.id)
    if (dish) {
      dish.stock = result?.stock ?? Number(stockForm.stock)
      dish.soldOut = dish.stock <= 0
    }
    ElMessage.success(result?.lowStock ? '库存已更新（低库存预警）' : '库存已更新')
    stockDialogVisible.value = false
  } catch (error) {
    if (error?.code === 40901) {
      ElMessage.error('库存调整失败：库存不足')
    } else {
      ElMessage.error(error?.message || '库存更新失败')
    }
  } finally {
    stockSaving.value = false
  }
}

function handleSaved() {
  loadCategories()
  loadDishes()
}

function handleCategorySaved() {
  loadCategories()
  loadDishes()
}

onMounted(async () => {
  await merchant.loadProfile()
  await Promise.all([loadCategories(), loadDishes()])
})

onBeforeUnmount(() => {
  if (keywordTimer) window.clearTimeout(keywordTimer)
})
</script>

<template>
  <div class="dishes">
    <div class="page-header">
      <div>
        <h1 class="page-title">菜品管理</h1>
        <p class="page-subtitle">维护分类、价格、库存与上下架状态；图片走 OSS 直传，失败自动降级本地存储</p>
      </div>
      <div class="dishes__actions">
        <el-button @click="openCreateCategory">
          <el-icon><Folder /></el-icon>
          新建分类
        </el-button>
        <el-button type="primary" @click="openCreateDish">
          <el-icon><Plus /></el-icon>
          新建菜品
        </el-button>
      </div>
    </div>

    <div class="dishes__layout">
      <aside class="surface-card dishes__aside">
        <header class="aside-head">
          <span class="aside-head__title">菜品分类</span>
          <el-button text type="primary" size="small" @click="openCreateCategory">新增</el-button>
        </header>
        <div v-loading="categoriesLoading" class="category-list">
          <button
            type="button"
            class="category-item"
            :class="{ 'is-active': activeCategoryId === null }"
            @click="selectCategory(null)"
          >
            <span class="category-item__name">全部菜品</span>
            <span class="category-item__count">{{ allDishesCount }}</span>
          </button>
          <div
            v-for="category in categories"
            :key="category.id"
            class="category-item"
            :class="{ 'is-active': activeCategoryId === category.id }"
            role="button"
            tabindex="0"
            @click="selectCategory(category.id)"
            @keyup.enter="selectCategory(category.id)"
          >
            <span class="category-item__name">{{ category.name }}</span>
            <span class="category-item__count">{{ category.dishCount ?? 0 }}</span>
            <span class="category-item__ops" @click.stop>
              <el-button text size="small" @click="openEditCategory(category)">
                <el-icon><Edit /></el-icon>
              </el-button>
              <el-button text size="small" type="danger" @click="removeCategory(category)">
                <el-icon><Delete /></el-icon>
              </el-button>
            </span>
          </div>
          <el-empty
            v-if="!categoriesLoading && categories.length === 0"
            description="还没有分类，先建一个吧"
            :image-size="70"
          />
        </div>
      </aside>

      <section class="dishes__main">
        <div class="surface-card dishes__filters">
          <el-input
            v-model="keyword"
            placeholder="搜索菜品名称"
            clearable
            class="dishes__search"
          >
            <template #prefix><el-icon><Search /></el-icon></template>
          </el-input>
          <el-select v-model="statusFilter" placeholder="全部状态" clearable class="dishes__select" @change="changeStatusFilter">
            <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
          <el-checkbox v-model="lowStockOnly" @change="changeStatusFilter">只看库存 ≤5</el-checkbox>
          <el-button :loading="loading" @click="loadDishes">
            <el-icon><Refresh /></el-icon>
            刷新
          </el-button>
        </div>

        <DataState :loading="loading" :error="error" :empty="isEmpty" @retry="loadDishes">
          <template #empty>
            <div class="surface-card">
              <el-empty description="暂无菜品，点击右上角「新建菜品」开始" />
            </div>
          </template>

          <div class="surface-card dishes__table">
            <el-table :data="rows" size="default">
              <el-table-column label="菜品" min-width="240">
                <template #default="{ row }">
                  <div class="dish-cell">
                    <DishThumb :src="row.imageUrl" :alt="row.name" radius="sm" class="dish-cell__img" />
                    <div class="dish-cell__body">
                      <span class="dish-cell__name">{{ row.name }}</span>
                      <span class="dish-cell__meta">{{ row.categoryName || '未分类' }}</span>
                    </div>
                  </div>
                </template>
              </el-table-column>
              <el-table-column label="售价" width="140">
                <template #default="{ row }">
                  <span class="amount">{{ formatAmount(row.price) }}</span>
                  <span v-if="row.originalPrice && Number(row.originalPrice) > Number(row.price)" class="dish-price__original">
                    ¥{{ formatAmount(row.originalPrice) }}
                  </span>
                </template>
              </el-table-column>
              <el-table-column label="库存" width="130">
                <template #default="{ row }">
                  <el-tag v-if="Number(row.stock) <= 5" type="danger" effect="plain" size="small">
                    仅剩 {{ row.stock }}
                  </el-tag>
                  <span v-else class="dish-stock">{{ row.stock }}</span>
                  <el-button text type="primary" size="small" @click="openStockDialog(row)">调整</el-button>
                </template>
              </el-table-column>
              <el-table-column prop="sales" label="销量" width="80" align="center" />
              <el-table-column label="状态" width="130">
                <template #default="{ row }">
                  <el-switch
                    :model-value="row.status === DISH_STATUS.ON_SALE"
                    :loading="statusSaving === row.id"
                    :disabled="statusSaving === row.id"
                    inline-prompt
                    active-text="上架"
                    inactive-text="下架"
                    @change="(value) => toggleStatus(row, value ? DISH_STATUS.ON_SALE : DISH_STATUS.OFF_SHELF)"
                  />
                </template>
              </el-table-column>
              <el-table-column label="排序" width="80" align="center">
                <template #default="{ row }">{{ row.sort ?? 0 }}</template>
              </el-table-column>
              <el-table-column label="操作" width="160" fixed="right">
                <template #default="{ row }">
                  <el-button text type="primary" size="small" @click="openEditDish(row)">编辑</el-button>
                  <el-button text type="danger" size="small" @click="removeDish(row)">删除</el-button>
                </template>
              </el-table-column>
            </el-table>

            <div class="dishes__pager">
              <el-pagination
                layout="prev, pager, next, total"
                :total="total"
                :page-size="size"
                :current-page="page"
                background
                @current-change="onPageChange"
              />
            </div>
          </div>
        </DataState>
      </section>
    </div>

    <DishFormDialog
      v-model="dishDialogVisible"
      :dish="editingDish"
      :categories="categories"
      @saved="handleSaved"
    />
    <CategoryDialog v-model="categoryDialogVisible" :category="editingCategory" @saved="handleCategorySaved" />

    <el-dialog v-model="stockDialogVisible" title="调整库存" width="420px">
      <div v-if="stockForm.dish" class="stock-dialog">
        <p class="stock-dialog__dish">{{ stockForm.dish.name }}</p>
        <el-form label-width="76px">
          <el-form-item label="库存">
            <el-input-number v-model="stockForm.stock" :min="0" :max="99999" />
          </el-form-item>
        </el-form>
        <p class="stock-dialog__hint">库存 ≤5 时前台会标记「仅剩 N 份」，0 表示售罄。</p>
      </div>
      <template #footer>
        <el-button @click="stockDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="stockSaving" @click="saveStock">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.dishes {
  display: flex;
  flex-direction: column;
  gap: var(--layout-gap);
}

.dishes__actions {
  display: flex;
  gap: var(--space-2);
}

.dishes__layout {
  display: grid;
  grid-template-columns: 240px minmax(0, 1fr);
  gap: var(--space-5);
  align-items: start;
}

.dishes__aside {
  position: sticky;
  top: var(--space-5);
  padding: var(--space-4);
}

.aside-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-bottom: var(--space-2);
  border-bottom: 1px solid var(--color-border-subtle);
}

.aside-head__title {
  font-weight: var(--font-weight-semibold);
  font-size: var(--font-size-sm);
}

.category-list {
  display: flex;
  flex-direction: column;
  gap: 2px;
  margin-top: var(--space-2);
}

.category-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-2);
  width: 100%;
  padding: var(--space-2) var(--space-3);
  border: 0;
  border-radius: var(--radius-md);
  background: transparent;
  color: var(--color-text-secondary);
  font: inherit;
  font-size: var(--font-size-sm);
  text-align: left;
  cursor: pointer;
  transition: background var(--duration-fast), color var(--duration-fast);
}

.category-item:hover {
  background: var(--color-bg-subtle);
  color: var(--color-text-primary);
}

.category-item.is-active {
  background: var(--color-brand-50);
  color: var(--color-primary);
  font-weight: var(--font-weight-medium);
}

.category-item__name {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.category-item__count {
  font-family: var(--font-family-number);
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}

.category-item__ops {
  display: flex;
  opacity: 0.55;
}

.category-item:hover .category-item__ops {
  opacity: 1;
}

.category-item__ops :deep(.el-button) {
  padding: 0 4px;
}

.dishes__main {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}

.dishes__filters {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-4) var(--space-5);
}

.dishes__search {
  width: 260px;
}

.dishes__select {
  width: 150px;
}

.dishes__table {
  padding: var(--space-2) var(--space-4) var(--space-4);
}

.dishes__pager {
  display: flex;
  justify-content: flex-end;
  padding-top: var(--space-4);
}

.dish-cell {
  display: flex;
  align-items: center;
  gap: var(--space-3);
}

.dish-cell__img {
  width: 48px;
  height: 48px;
  flex: none;
}

.dish-cell__body {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.dish-cell__name {
  font-weight: var(--font-weight-medium);
}

.dish-cell__meta {
  color: var(--color-text-tertiary);
  font-size: var(--font-size-xs);
}

.dish-price__original {
  margin-left: var(--space-2);
  color: var(--color-text-tertiary);
  font-size: var(--font-size-xs);
  text-decoration: line-through;
}

.dish-stock {
  margin-right: var(--space-2);
  font-family: var(--font-family-number);
}

.stock-dialog__dish {
  margin-bottom: var(--space-3);
  font-weight: var(--font-weight-semibold);
}

.stock-dialog__hint {
  color: var(--color-text-tertiary);
  font-size: var(--font-size-xs);
}

@media (max-width: 1100px) {
  .dishes__layout {
    grid-template-columns: 1fr;
  }

  .dishes__aside {
    position: static;
  }
}
</style>
