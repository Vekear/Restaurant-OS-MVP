<template>
  <view class="menu-page">
    <!-- 顶部品牌区 -->
    <view class="hero">
      <view class="hero__deco hero__deco--a"></view>
      <view class="hero__deco hero__deco--b"></view>

      <view class="hero__brand">
        <image class="hero__logo" src="/static/logo.png" mode="aspectFill" />
        <view class="hero__meta">
          <view class="hero__name-row">
            <text class="hero__name">小馆食堂</text>
            <view class="hero__state">
              <view class="hero__state-dot"></view>
              <text>营业中</text>
            </view>
          </view>
          <text class="hero__desc">现点现做 · 堂食点餐</text>
        </view>
      </view>

      <view class="search">
        <view class="search__icon">
          <view class="search__icon-circle"></view>
          <view class="search__icon-handle"></view>
        </view>
        <input
          v-model="keyword"
          class="search__input"
          type="text"
          confirm-type="search"
          placeholder="搜索菜品，如「酸菜鱼」"
          placeholder-class="search__placeholder"
          @confirm="reload"
        />
        <view v-if="keyword" class="search__clear" @tap="clearKeyword">
          <view class="search__clear-line search__clear-line--a"></view>
          <view class="search__clear-line search__clear-line--b"></view>
        </view>
      </view>
    </view>

    <!-- 分类 -->
    <view class="cats-wrap">
      <scroll-view class="cats" scroll-x :show-scrollbar="false" enable-flex>
        <view
          v-for="cat in categoryTabs"
          :key="cat.id"
          class="cats__item"
          :class="{ 'cats__item--active': String(activeCategory) === String(cat.id) }"
          @tap="selectCategory(cat.id)"
        >
          <text>{{ cat.name }}</text>
        </view>
      </scroll-view>
    </view>

    <!-- 菜品列表 -->
    <view class="list">
      <view v-if="loading && dishes.length === 0" class="skeletons">
        <view v-for="n in 4" :key="n" class="skeleton">
          <view class="skeleton__img"></view>
          <view class="skeleton__body">
            <view class="skeleton__line skeleton__line--title"></view>
            <view class="skeleton__line"></view>
            <view class="skeleton__line skeleton__line--short"></view>
          </view>
        </view>
      </view>

      <EmptyState
        v-else-if="error"
        title="菜单加载失败"
        :description="error"
        action-text="重新加载"
        @action="reload"
      />

      <EmptyState
        v-else-if="dishes.length === 0"
        :title="keyword ? '没有找到相关菜品' : '该分类暂无菜品'"
        :description="keyword ? '换个关键词试试吧' : '店家正在准备中，稍后再来看看'"
        :action-text="keyword ? '清空搜索' : ''"
        @action="clearKeyword"
      />

      <template v-else>
        <view v-for="dish in dishes" :key="dish.id" class="dish">
          <AppImage
            :src="dish.image"
            width="176rpx"
            height="176rpx"
            radius="20rpx"
          />
          <view class="dish__main">
            <view class="dish__title-row">
              <text class="dish__name">{{ dish.name }}</text>
              <view v-if="dish.hot" class="tag tag--hot">热销</view>
            </view>
            <text class="dish__desc">{{ dish.description }}</text>
            <view class="dish__tags">
              <view v-for="tag in dish.tags" :key="tag" class="tag">{{ tag }}</view>
            </view>
            <view class="dish__footer">
              <view class="price">
                <text class="price__symbol">¥</text>
                <text class="price__amount">{{ formatAmount(dish.price) }}</text>
              </view>
              <text class="dish__sales">已售 {{ dish.sales }}</text>
              <view class="dish__stepper">
                <text v-if="dish.soldOut" class="dish__soldout">已售罄</text>
                <QuantityStepper
                  v-else
                  :quantity="quantityOf(dish.id)"
                  @increase="addDish(dish)"
                  @decrease="cartStore.decrease(dish.id)"
                />
              </view>
            </view>
          </view>
        </view>
      </template>
    </view>

    <!-- 购物车悬浮条 -->
    <view v-if="totalQuantity > 0" class="cart-bar" @tap="goCart">
      <view class="cart-bar__basket">
        <view class="cart-bar__badge">{{ totalQuantity }}</view>
        <view class="cart-bar__basket-body"></view>
        <view class="cart-bar__basket-handle"></view>
      </view>
      <view class="cart-bar__info">
        <view class="cart-bar__total">
          <text class="cart-bar__symbol">¥</text>
          <text class="cart-bar__amount">{{ formatAmount(totalAmount) }}</text>
        </view>
        <text class="cart-bar__hint">共 {{ totalQuantity }} 件 · 堂食</text>
      </view>
      <view class="cart-bar__btn" @tap.stop="goCart">去结算</view>
    </view>
  </view>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { onLoad, onPullDownRefresh } from '@dcloudio/uni-app'
import AppImage from '@/components/AppImage.vue'
import EmptyState from '@/components/EmptyState.vue'
import QuantityStepper from '@/components/QuantityStepper.vue'
import { fetchCategories, fetchDishes } from '@/api/menu'
import { useCartStore } from '@/store/cart'
import { formatAmount } from '@/utils/format'

const cartStore = useCartStore()

const categories = ref([])
const dishes = ref([])
const activeCategory = ref('all')
const keyword = ref('')
const loading = ref(false)
const error = ref('')

let requestSeq = 0
let searchTimer = null

const categoryTabs = computed(() => [{ id: 'all', name: '全部' }].concat(categories.value))
const totalQuantity = computed(() => cartStore.totalQuantity.value)
const totalAmount = computed(() => cartStore.totalAmount.value)

function quantityOf(id) {
  return cartStore.quantityOf(id)
}

async function loadCategories() {
  try {
    categories.value = await fetchCategories()
  } catch (err) {
    // 分类失败不阻塞菜品展示，仅保留「全部」标签
    console.warn('[menu] 分类加载失败', err)
  }
}

async function loadDishes() {
  const seq = ++requestSeq
  loading.value = true
  error.value = ''
  try {
    const list = await fetchDishes({
      categoryId: activeCategory.value === 'all' ? undefined : activeCategory.value,
      keyword: keyword.value.trim() || undefined
    })
    if (seq !== requestSeq) return
    dishes.value = list.map((dish) => Object.assign({}, dish, { hot: Number(dish.sales) >= 200 }))
  } catch (err) {
    if (seq !== requestSeq) return
    error.value = err && err.message ? err.message : '菜单加载失败，请稍后重试'
    dishes.value = []
  } finally {
    if (seq === requestSeq) loading.value = false
    uni.stopPullDownRefresh()
  }
}

function reload() {
  loadDishes()
}

function selectCategory(id) {
  if (String(activeCategory.value) === String(id)) return
  activeCategory.value = id
  loadDishes()
}

function clearKeyword() {
  keyword.value = ''
  loadDishes()
}

function addDish(dish) {
  cartStore.addItem(dish, 1)
}

function goCart() {
  uni.switchTab({ url: '/pages/cart/cart' })
}

watch(keyword, () => {
  if (searchTimer) clearTimeout(searchTimer)
  searchTimer = setTimeout(() => {
    loadDishes()
  }, 320)
})

onLoad(() => {
  loadCategories()
  loadDishes()
})

onPullDownRefresh(() => {
  loadCategories()
  loadDishes()
})
</script>

<style lang="scss" scoped>
.menu-page {
  min-height: 100vh;
  padding-bottom: 320rpx;
}

/* ---------- 品牌区 ---------- */
.hero {
  position: relative;
  padding: $space-lg $space-lg $space-md;
  background: linear-gradient(135deg, #ff7a45 0%, $color-brand 52%, $color-brand-dark 100%);
  overflow: hidden;

  &__deco {
    position: absolute;
    border-radius: 50%;

    &--a {
      width: 260rpx;
      height: 260rpx;
      right: -80rpx;
      top: -110rpx;
      background: rgba(255, 255, 255, 0.14);
    }

    &--b {
      width: 180rpx;
      height: 180rpx;
      left: -70rpx;
      bottom: -90rpx;
      background: rgba(255, 183, 3, 0.28);
    }
  }

  &__brand {
    position: relative;
    display: flex;
    align-items: center;
    z-index: 1;
  }

  &__logo {
    width: 96rpx;
    height: 96rpx;
    border-radius: 28rpx;
    border: 4rpx solid rgba(255, 255, 255, 0.55);
    box-shadow: 0 10rpx 24rpx rgba(120, 40, 8, 0.22);
  }

  &__meta {
    margin-left: $space-md;
    flex: 1;
    min-width: 0;
  }

  &__name-row {
    display: flex;
    align-items: center;
  }

  &__name {
    font-size: 38rpx;
    font-weight: 700;
    color: #ffffff;
    letter-spacing: 1rpx;
  }

  &__state {
    display: flex;
    align-items: center;
    margin-left: $space-sm;
    padding: 4rpx 14rpx;
    border-radius: $radius-pill;
    background: rgba(255, 255, 255, 0.2);
    font-size: $font-xs;
    color: #ffffff;
  }

  &__state-dot {
    width: 10rpx;
    height: 10rpx;
    border-radius: 50%;
    background: #b8ffd1;
    margin-right: 8rpx;
    box-shadow: 0 0 8rpx rgba(184, 255, 209, 0.9);
  }

  &__desc {
    display: block;
    margin-top: 6rpx;
    font-size: $font-sm;
    color: rgba(255, 255, 255, 0.86);
  }
}

/* ---------- 搜索 ---------- */
.search {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
  height: 76rpx;
  margin-top: $space-md;
  padding: 0 $space-md;
  border-radius: $radius-pill;
  background: rgba(255, 255, 255, 0.94);
  box-shadow: 0 10rpx 24rpx rgba(140, 48, 10, 0.16);

  &__icon {
    position: relative;
    width: 32rpx;
    height: 32rpx;
    margin-right: $space-sm;
  }

  &__icon-circle {
    position: absolute;
    width: 22rpx;
    height: 22rpx;
    border: 3rpx solid $color-text-muted;
    border-radius: 50%;
  }

  &__icon-handle {
    position: absolute;
    width: 12rpx;
    height: 3rpx;
    border-radius: 2rpx;
    background: $color-text-muted;
    right: 1rpx;
    bottom: 4rpx;
    transform: rotate(45deg);
  }

  &__input {
    flex: 1;
    height: 76rpx;
    font-size: $font-md;
    color: $color-text;
  }

  &__placeholder {
    color: #b6aaa3;
  }

  &__clear {
    position: relative;
    width: 36rpx;
    height: 36rpx;
    border-radius: 50%;
    background: #efe6e0;
  }

  &__clear-line {
    position: absolute;
    width: 18rpx;
    height: 3rpx;
    border-radius: 2rpx;
    background: #8a7f79;
    top: 16rpx;
    left: 9rpx;

    &--a {
      transform: rotate(45deg);
    }

    &--b {
      transform: rotate(-45deg);
    }
  }
}

/* ---------- 分类 ---------- */
.cats-wrap {
  position: sticky;
  top: 0;
  z-index: 10;
  padding: $space-md 0 12rpx;
  background: $color-bg;
}

.cats {
  white-space: nowrap;
  padding: 0 $space-lg;

  &__item {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    height: 62rpx;
    padding: 0 $space-md;
    margin-right: $space-sm;
    border-radius: $radius-pill;
    background: #ffffff;
    border: 2rpx solid $color-border;
    font-size: $font-sm;
    color: $color-text-secondary;

    &--active {
      background: $color-text;
      border-color: $color-text;
      color: #ffffff;
      font-weight: 600;
      box-shadow: 0 10rpx 20rpx rgba(31, 26, 23, 0.18);
    }
  }
}

/* ---------- 列表 ---------- */
.list {
  padding: $space-xs $space-lg 0;
}

.dish {
  display: flex;
  padding: $space-md;
  margin-bottom: $space-md;
  background: $color-card;
  border-radius: $radius-lg;
  box-shadow: $shadow-card;

  &__main {
    flex: 1;
    min-width: 0;
    margin-left: $space-md;
    display: flex;
    flex-direction: column;
  }

  &__title-row {
    display: flex;
    align-items: center;
  }

  &__name {
    font-size: $font-lg;
    font-weight: 600;
    color: $color-text;
    margin-right: $space-xs;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  &__desc {
    margin-top: 6rpx;
    font-size: $font-xs;
    color: $color-text-muted;
    line-height: 1.45;
    display: -webkit-box;
    -webkit-line-clamp: 2;
    -webkit-box-orient: vertical;
    overflow: hidden;
  }

  &__tags {
    display: flex;
    flex-wrap: wrap;
    margin-top: 8rpx;
  }

  &__footer {
    display: flex;
    align-items: center;
    margin-top: auto;
    padding-top: $space-sm;
  }

  &__sales {
    margin-left: $space-sm;
    font-size: $font-xs;
    color: #b6aaa3;
  }

  &__stepper {
    margin-left: auto;
  }

  &__soldout {
    font-size: $font-xs;
    color: #b6aaa3;
  }
}

.tag {
  padding: 4rpx 12rpx;
  margin-right: 8rpx;
  border-radius: $radius-sm;
  background: $color-brand-soft;
  color: $color-brand;
  font-size: 20rpx;
  line-height: 1.4;

  &--hot {
    background: #ffece2;
    color: #e8501f;
    font-weight: 600;
  }
}

/* ---------- 骨架屏 ---------- */
.skeleton {
  display: flex;
  padding: $space-md;
  margin-bottom: $space-md;
  background: $color-card;
  border-radius: $radius-lg;

  &__img {
    width: 176rpx;
    height: 176rpx;
    border-radius: 20rpx;
    background: #f1e8e2;
  }

  &__body {
    flex: 1;
    margin-left: $space-md;
    padding-top: $space-xs;
  }

  &__line {
    height: 24rpx;
    border-radius: $radius-sm;
    background: #f1e8e2;
    margin-bottom: 18rpx;

    &--title {
      width: 50%;
      height: 30rpx;
    }

    &--short {
      width: 34%;
    }
  }
}

/* ---------- 购物车悬浮条 ---------- */
.cart-bar {
  position: fixed;
  left: $space-lg;
  right: $space-lg;
  bottom: calc(100rpx + #{$space-md} + env(safe-area-inset-bottom));
  z-index: 20;
  display: flex;
  align-items: center;
  height: 104rpx;
  padding: 0 12rpx 0 $space-md;
  border-radius: $radius-pill;
  background: #241d19;
  box-shadow: $shadow-float;

  &__basket {
    position: relative;
    width: 72rpx;
    height: 72rpx;
    border-radius: 50%;
    background: linear-gradient(135deg, $color-brand, $color-brand-dark);
    display: flex;
    align-items: center;
    justify-content: center;
  }

  &__basket-body {
    width: 34rpx;
    height: 26rpx;
    border: 3rpx solid #ffffff;
    border-top: none;
    border-radius: 0 0 8rpx 8rpx;
  }

  &__basket-handle {
    position: absolute;
    top: 20rpx;
    width: 20rpx;
    height: 12rpx;
    border: 3rpx solid #ffffff;
    border-bottom: none;
    border-radius: 10rpx 10rpx 0 0;
  }

  &__badge {
    position: absolute;
    top: -8rpx;
    right: -10rpx;
    min-width: 34rpx;
    height: 34rpx;
    padding: 0 8rpx;
    border-radius: $radius-pill;
    background: $color-accent;
    color: #3a2408;
    font-size: 20rpx;
    font-weight: 700;
    line-height: 34rpx;
    text-align: center;
  }

  &__info {
    flex: 1;
    margin-left: $space-md;
  }

  &__total {
    display: flex;
    align-items: baseline;
    color: #ffffff;
  }

  &__symbol {
    font-size: $font-xs;
    margin-right: 2rpx;
  }

  &__amount {
    font-size: 38rpx;
    font-weight: 700;
    letter-spacing: -0.5rpx;
  }

  &__hint {
    display: block;
    margin-top: 2rpx;
    font-size: 20rpx;
    color: rgba(255, 255, 255, 0.55);
  }

  &__btn {
    height: 80rpx;
    padding: 0 40rpx;
    border-radius: $radius-pill;
    background: linear-gradient(135deg, $color-brand, $color-brand-dark);
    color: #ffffff;
    font-size: $font-md;
    font-weight: 700;
    display: flex;
    align-items: center;
    justify-content: center;
  }
}
</style>
