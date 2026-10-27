<script setup>
/**
 * 商家订单动作按钮组
 * - 按订单状态渲染合法动作（接单/拒单/开始制作/出餐配送/已完成…）
 * - 拒单需填写 2–100 字理由；完成订单二次确认
 * - 40905（状态已变化）提示刷新，并抛出 refresh 让父级重拉快照
 */
import { computed, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { buildOrderActions, executeOrderAction, ORDER_ACTION_LABELS } from '@/utils/merchantOrders'

const props = defineProps({
  order: { type: Object, required: true },
  size: { type: String, default: 'default' },
  disabled: { type: Boolean, default: false }
})

const emit = defineEmits(['done', 'refresh'])

const running = ref(null)

const actions = computed(() => buildOrderActions(props.order))

/**
 * 按钮禁用判定：基准确（外部 disabled）+ 执行中只保留当前动作可点。
 * 注意：running 空闲态必须是 null（而非 ''），否则 el-button 的 Boolean prop 会把空串 cast 成 true。
 */
function isActionDisabled(action) {
  const isBusy = running.value !== null
  return props.disabled || (isBusy && running.value !== action.key)
}

function confirmText(action) {
  if (action.key === 'complete') return '完成后订单进入终态，且堂食单会同时关闭桌台会话，确认继续？'
  if (action.key === 'deliver') return '确认餐品已出餐并进入配送？'
  return ''
}

async function run(action) {
  let payload = {}
  if (action.key === 'reject') {
    try {
      const { value } = await ElMessageBox.prompt('请填写拒单理由（2–100 字），系统将回补库存与优惠券并模拟退款。', '拒单', {
        confirmButtonText: '确认拒单',
        cancelButtonText: '取消',
        inputPlaceholder: '例如：食材售罄，无法出餐',
        inputValidator: (value) => {
          const length = (value || '').trim().length
          if (length < 2 || length > 100) return '拒单理由需为 2–100 个字符'
          return true
        }
      })
      payload = { reason: value.trim() }
    } catch {
      return
    }
  } else {
    const message = confirmText(action)
    if (message) {
      try {
        await ElMessageBox.confirm(message, ORDER_ACTION_LABELS[action.key] || '确认操作', {
          confirmButtonText: '确认',
          cancelButtonText: '取消',
          type: 'warning'
        })
      } catch {
        return
      }
    }
  }

  running.value = action.key
  try {
    const result = await executeOrderAction(props.order, action.key, payload)
    ElMessage.success(`${ORDER_ACTION_LABELS[action.key] || '操作'}成功`)
    emit('done', result)
  } catch (error) {
    if (error?.code === 40905) {
      ElMessage.warning('订单状态已变化，已为你刷新最新状态')
      emit('refresh')
    } else if (error?.code === 40909) {
      ElMessage.warning('桌台存在进行中会话，暂时无法完成该操作')
    } else {
      ElMessage.error(error?.message || '操作失败，请稍后重试')
    }
  } finally {
    running.value = null
  }
}
</script>

<template>
  <div class="order-actions">
    <el-button
      v-for="action in actions"
      :key="action.key"
      :type="action.type"
      :plain="action.plain"
      :size="size"
      :loading="running === action.key"
      :disabled="isActionDisabled(action)"
      @click="run(action)"
    >
      {{ action.label }}
    </el-button>
    <span v-if="!actions.length" class="order-actions__empty">当前状态无可执行操作</span>
  </div>
</template>

<style scoped>
.order-actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
  align-items: center;
}

.order-actions__empty {
  color: var(--color-text-tertiary);
  font-size: var(--font-size-xs);
}
</style>
