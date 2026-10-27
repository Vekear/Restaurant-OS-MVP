<script setup>
/**
 * 管理后台 · 用户管理（docs/02-api.md §20.1–20.2）
 * - 检索：keyword（用户名/昵称/手机号）、role、status，分页 ≤100
 * - 启用 / 禁用：禁用需填原因（写审计）；不能操作自己（40918）
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listUsers, updateUserStatus } from '@/api/admin'
import { useAuthStore } from '@/stores/auth'
import { USER_STATUS, USER_STATUS_LABELS, USER_STATUS_TAG_TYPES, ROLE_LABELS } from '@/utils/constants'
import { formatDateTime } from '@/utils/format'
import { DEFAULT_PAGE_SIZE, PAGE_SIZES } from '@/utils/constants'
import DataState from '@/components/DataState.vue'

const auth = useAuthStore()

const filters = reactive({ keyword: '', role: '', status: '' })
const page = ref(1)
const size = ref(DEFAULT_PAGE_SIZE)
const rows = ref([])
const total = ref(0)
const loading = ref(false)
const error = ref('')
const updatingId = ref(null)

const roleOptions = Object.entries(ROLE_LABELS).map(([value, label]) => ({ value, label }))
const statusOptions = Object.entries(USER_STATUS_LABELS).map(([value, label]) => ({ value, label }))
const isEmpty = computed(() => !loading.value && !error.value && rows.value.length === 0)

function statusMutation(row) {
  return row.status === USER_STATUS.ENABLED ? USER_STATUS.DISABLED : USER_STATUS.ENABLED
}

function actionLabel(row) {
  return row.status === USER_STATUS.ENABLED ? '禁用' : '启用'
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    const data = await listUsers({
      keyword: filters.keyword.trim() || undefined,
      role: filters.role || undefined,
      status: filters.status || undefined,
      page: page.value,
      size: size.value
    })
    rows.value = data?.list || []
    total.value = data?.total || 0
  } catch (err) {
    error.value = err?.message || '用户列表加载失败'
  } finally {
    loading.value = false
  }
}

function search() {
  page.value = 1
  load()
}

function resetFilters() {
  filters.keyword = ''
  filters.role = ''
  filters.status = ''
  page.value = 1
  load()
}

function changeSize(value) {
  size.value = value
  page.value = 1
  load()
}

async function toggleStatus(row) {
  const next = statusMutation(row)
  const isDisable = next === USER_STATUS.DISABLED
  let reason = ''
  try {
    if (isDisable) {
      const result = await ElMessageBox.prompt(
        `将禁用用户「${row.username}」，禁用后其既有 JWT 立即失效。请填写原因（2–200 字）。`,
        '禁用用户',
        {
          confirmButtonText: '确认禁用',
          cancelButtonText: '取消',
          inputPlaceholder: '例如：违规刷单、恶意评价',
          inputValidator: (value) => {
            const text = String(value || '').trim()
            if (text.length < 2 || text.length > 200) return '原因需为 2–200 个字符'
            return true
          },
          inputType: 'textarea'
        }
      )
      reason = result.value.trim()
    } else {
      await ElMessageBox.confirm(`确认恢复用户「${row.username}」的使用权限？`, '启用用户', {
        confirmButtonText: '确认启用',
        cancelButtonText: '取消',
        type: 'warning'
      })
    }
  } catch {
    return
  }
  updatingId.value = row.id
  try {
    const payload = { status: next }
    if (reason) payload.reason = reason
    const updated = await updateUserStatus(row.id, payload)
    row.status = updated?.status || next
    ElMessage.success(isDisable ? '用户已禁用' : '用户已启用')
  } catch (err) {
    if (err?.code === 40918) {
      ElMessage.error('不能操作自己的账号，请使用其他管理员账号')
    } else {
      ElMessage.error(err?.message || '操作失败，请稍后重试')
    }
  } finally {
    updatingId.value = null
  }
}

onMounted(load)
</script>

<template>
  <div class="page admin-users">
    <header class="page-header">
      <div>
        <h1 class="page-title">用户管理</h1>
        <p class="page-subtitle">检索全部账号并启用 / 禁用；禁用立即失效其登录态，操作写审计日志。</p>
      </div>
      <el-button @click="load">刷新</el-button>
    </header>

    <section class="filter-bar surface-card">
      <el-input
        v-model="filters.keyword"
        placeholder="用户名 / 昵称 / 手机号"
        clearable
        maxlength="30"
        class="filter-bar__keyword"
        @keyup.enter="search"
        @clear="search"
      />
      <el-select v-model="filters.role" placeholder="全部角色" clearable class="filter-bar__select">
        <el-option v-for="item in roleOptions" :key="item.value" :label="item.label" :value="item.value" />
      </el-select>
      <el-select v-model="filters.status" placeholder="全部状态" clearable class="filter-bar__select">
        <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
      </el-select>
      <el-button type="primary" @click="search">查询</el-button>
      <el-button @click="resetFilters">重置</el-button>
    </section>

    <DataState :loading="loading" :error="error" :empty="isEmpty" @retry="load">
      <template #empty>
        <el-empty description="没有符合条件的用户" />
      </template>
      <div class="surface-card table-card">
        <el-table :data="rows" stripe border size="default">
          <el-table-column prop="id" label="ID" width="70" />
          <el-table-column prop="username" label="用户名" min-width="130" />
          <el-table-column prop="nickname" label="昵称" min-width="120" />
          <el-table-column prop="phone" label="手机号" min-width="130">
            <template #default="{ row }">{{ row.phone || '—' }}</template>
          </el-table-column>
          <el-table-column label="角色" width="100">
            <template #default="{ row }">
              <el-tag size="small" effect="light">{{ ROLE_LABELS[row.role] || row.role }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag
                size="small"
                :type="USER_STATUS_TAG_TYPES[row.status] || 'info'"
                effect="light"
              >
                {{ USER_STATUS_LABELS[row.status] || row.status }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="商家 ID" width="100">
            <template #default="{ row }">{{ row.merchantId ?? '—' }}</template>
          </el-table-column>
          <el-table-column label="最近登录" min-width="170">
            <template #default="{ row }">{{ formatDateTime(row.lastLoginAt) }}</template>
          </el-table-column>
          <el-table-column label="注册时间" min-width="170">
            <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="110" fixed="right">
            <template #default="{ row }">
              <el-button
                link
                :type="row.status === USER_STATUS.ENABLED ? 'danger' : 'success'"
                :loading="updatingId === row.id"
                :disabled="row.id === auth.user?.id"
                @click="toggleStatus(row)"
              >
                {{ actionLabel(row) }}
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <div class="pagination">
        <span class="pagination__total">共 {{ total }} 条</span>
        <el-pagination
          :current-page="page"
          :page-size="size"
          :page-sizes="PAGE_SIZES"
          :total="total"
          layout="sizes, prev, pager, next, jumper"
          background
          @current-change="(value) => { page = value; load() }"
          @size-change="changeSize"
        />
      </div>
    </DataState>
  </div>
</template>

<style scoped>
.filter-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-4);
  margin-bottom: var(--space-5);
}

.filter-bar__keyword {
  width: 260px;
}

.filter-bar__select {
  width: 160px;
}

.table-card {
  padding: var(--space-2) var(--space-3) var(--space-3);
}

.pagination {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  margin-top: var(--space-4);
}

.pagination__total {
  color: var(--color-text-tertiary);
  font-size: var(--font-size-sm);
}
</style>
