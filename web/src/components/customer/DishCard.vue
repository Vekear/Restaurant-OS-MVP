<script setup>
/**
 * 菜品卡片：菜单页/首页推荐共用
 * - 图片兜底、售罄置灰、原价划线
 * - 已加购时显示步进器（数量来自服务端购物车），否则展示「加入购物车」
 */
import { computed } from 'vue'
import DishThumb from '@/components/DishThumb.vue'
import { formatAmount } from '@/utils/format'

const props = defineProps({
  dish: { type: Object, required: true },
  quantity: { type: Number, default: 0 },
  busy: { type: Boolean, default: false },
  compact: { type: Boolean, default: false }
})

const emit = defineEmits(['add', 'change'])

const soldOut = computed(() => props.dish.soldOut || Number(props.dish.stock) <= 0)
const maxQuantity = computed(() => Math.max(0, Math.min(99, Number(props.dish.stock) || 0)))
const originalPrice = computed(() =>
  Number(props.dish.originalPrice) > Number(props.dish.price) ? props.dish.originalPrice : null
)

function add() {
  if (soldOut.value || props.busy) return
  emit('add')
}

function increase() {
  if (soldOut.value || props.busy) return
  if (props.quantity >= maxQuantity.value) {
    emit('change', maxQuantity.value)
    return
  }
  emit('change', props.quantity + 1)
}

function decrease() {
  if (props.busy) return
  emit('change', props.quantity - 1)
}
</script>

<template>
  <article class="dish-card" :class="{ 'is-sold-out': soldOut, 'is-compact': compact }">
    <router-link :to="`/menu/${dish.id}`" class="dish-card__media" :aria-label="`查看 ${dish.name} 详情`">
      <DishThumb :src="dish.imageUrl" :alt="dish.name" radius="md" />
      <span v-if="soldOut" class="dish-card__badge">售罄</span>
    </router-link>

    <div class="dish-card__body">
      <router-link :to="`/menu/${dish.id}`" class="dish-card__title">{{ dish.name }}</router-link>
      <p class="dish-card__meta">
        <span v-if="dish.categoryName">{{ dish.categoryName }}</span>
        <span>月售 {{ dish.sales || 0 }}</span>
        <span v-if="!soldOut && dish.stock != null" class="dish-card__stock">剩 {{ dish.stock }} 份</span>
      </p>

      <div class="dish-card__footer">
        <div class="dish-card__price">
          <span class="amount">{{ formatAmount(dish.price) }}</span>
          <s v-if="originalPrice" class="dish-card__original">¥{{ formatAmount(originalPrice) }}</s>
        </div>

        <div v-if="quantity > 0" class="dish-card__stepper">
          <el-button circle size="small" :disabled="busy" aria-label="减少数量" @click="decrease">
            <el-icon><Minus /></el-icon>
          </el-button>
          <span class="dish-card__qty">{{ quantity }}</span>
          <el-button
            circle
            size="small"
            type="primary"
            :disabled="busy || soldOut || quantity >= maxQuantity"
            aria-label="增加数量"
            @click="increase"
          >
            <el-icon><Plus /></el-icon>
          </el-button>
        </div>
        <el-button
          v-else
          type="primary"
          size="small"
          round
          :loading="busy"
          :disabled="soldOut"
          @click="add"
        >
          {{ soldOut ? '已售罄' : '加入购物车' }}
        </el-button>
      </div>
    </div>
  </article>
</template>

<style scoped>
.dish-card {
  display: flex;
  flex-direction: column;
  overflow: hidden;
  border: 1px solid var(--color-border-subtle);
  border-radius: var(--radius-lg);
  background: var(--color-surface);
  box-shadow: var(--shadow-xs);
  transition:
    transform var(--duration-base) var(--ease-out),
    box-shadow var(--duration-base) var(--ease-out);
}

.dish-card:hover {
  transform: translateY(-2px);
  box-shadow: var(--shadow-md);
}

.dish-card.is-sold-out {
  opacity: 0.78;
}

.dish-card__media {
  position: relative;
  display: block;
  aspect-ratio: 4 / 3;
  overflow: hidden;
}

.dish-card__badge {
  position: absolute;
  top: var(--space-3);
  left: var(--space-3);
  padding: 2px var(--space-3);
  border-radius: var(--radius-pill);
  background: rgba(34, 28, 23, 0.72);
  color: #fff;
  font-size: var(--font-size-xs);
  letter-spacing: 0.08em;
}

.dish-card__body {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: var(--space-2);
  padding: var(--space-4) var(--space-5) var(--space-5);
}

.dish-card__title {
  color: var(--color-text-primary);
  font-size: var(--font-size-lg);
  font-weight: var(--font-weight-semibold);
  line-height: var(--line-height-snug);
}

.dish-card__title:hover {
  color: var(--color-primary);
}

.dish-card__meta {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-3);
  color: var(--color-text-tertiary);
  font-size: var(--font-size-xs);
}

.dish-card__stock {
  color: var(--color-warning);
}

.dish-card__footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  margin-top: auto;
  padding-top: var(--space-3);
}

.dish-card__price {
  display: flex;
  align-items: baseline;
  gap: var(--space-2);
  color: var(--color-primary);
  font-size: var(--font-size-lg);
  font-weight: var(--font-weight-semibold);
}

.dish-card__original {
  color: var(--color-text-tertiary);
  font-size: var(--font-size-xs);
  font-weight: var(--font-weight-regular);
}

.dish-card__stepper {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
}

.dish-card__qty {
  min-width: 20px;
  color: var(--color-text-primary);
  font-size: var(--font-size-md);
  font-variant-numeric: tabular-nums;
  text-align: center;
}

.is-compact .dish-card__media {
  aspect-ratio: 16 / 10;
}

.is-compact .dish-card__body {
  padding: var(--space-3) var(--space-4) var(--space-4);
}

.is-compact .dish-card__title {
  font-size: var(--font-size-md);
}
</style>
