<script setup>
/**
 * 收货地址管理（T12）
 * 列表 / 新增 / 编辑 / 删除 / 设为默认；单用户最多 20 条（后端 40903）。
 */
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import DataState from '@/components/DataState.vue'
import AddressFormDialog from '@/components/customer/AddressFormDialog.vue'
import { deleteAddress, listAddresses, updateAddress } from '@/api/addresses'

const router = useRouter()

const addresses = ref([])
const loading = ref(false)
const error = ref('')
const dialogVisible = ref(false)
const editingAddress = ref(null)
const defaultingId = ref(0)
const deletingId = ref(0)

const isEmpty = computed(() => !loading.value && !error.value && addresses.value.length === 0)
const limitReached = computed(() => addresses.value.length >= 20)

async function load() {
  loading.value = true
  error.value = ''
  try {
    const data = await listAddresses()
    addresses.value = Array.isArray(data) ? data : []
  } catch (err) {
    error.value = err?.message || '地址加载失败'
    addresses.value = []
  } finally {
    loading.value = false
  }
}

function openCreate() {
  if (limitReached.value) {
    ElMessage.warning('最多保存 20 条地址，请先删除不用的地址')
    return
  }
  editingAddress.value = null
  dialogVisible.value = true
}

function openEdit(address) {
  editingAddress.value = address
  dialogVisible.value = true
}

function onSaved() {
  load()
}

async function setDefault(address) {
  if (address.isDefault) return
  defaultingId.value = address.id
  try {
    await updateAddress(address.id, {
      consignee: address.consignee,
      phone: address.phone,
      province: address.province,
      city: address.city,
      district: address.district,
      detail: address.detail,
      isDefault: true
    })
    ElMessage.success('已设为默认地址')
    await load()
  } catch (err) {
    ElMessage.error(err?.message || '设置失败')
  } finally {
    defaultingId.value = 0
  }
}

async function remove(address) {
  try {
    await ElMessageBox.confirm(`确定删除「${address.consignee} · ${address.detail}」吗？`, '删除地址', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  deletingId.value = address.id
  try {
    await deleteAddress(address.id)
    ElMessage.success('地址已删除')
    await load()
  } catch (err) {
    ElMessage.error(err?.message || '删除失败')
  } finally {
    deletingId.value = 0
  }
}

onMounted(load)
</script>

<template>
  <div class="app-container page addresses-page">
    <header class="page-header addresses-page__header">
      <div>
        <p class="page-eyebrow">Addresses</p>
        <h1 class="page-title">收货地址</h1>
        <p class="page-subtitle">
          共 {{ addresses.length }} / 20 条 · 结算时默认使用默认地址，可随时切换
        </p>
      </div>
      <el-button type="primary" :disabled="limitReached" @click="openCreate">
        <el-icon><Plus /></el-icon>
        新增地址
      </el-button>
    </header>

    <el-alert
      v-if="limitReached"
      class="addresses-page__alert"
      type="info"
      title="地址数量已达上限（20 条），请删除不用的地址后再新增"
      :closable="false"
      show-icon
    />

    <DataState :loading="loading" :error="error" :empty="isEmpty" :skeleton-rows="3" @retry="load">
      <template #empty>
        <el-empty description="还没有收货地址">
          <el-button type="primary" @click="openCreate">新增收货地址</el-button>
        </el-empty>
      </template>

      <ul class="address-grid">
        <li
          v-for="address in addresses"
          :key="address.id"
          class="address-item surface-card"
          :class="{ 'is-default': address.isDefault }"
        >
          <div class="address-item__head">
            <strong>{{ address.consignee }}</strong>
            <span class="address-item__phone">{{ address.phone }}</span>
            <el-tag v-if="address.isDefault" type="warning" effect="plain" size="small">默认</el-tag>
          </div>
          <p class="address-item__detail">
            {{ address.province }}{{ address.city }}{{ address.district }}{{ address.detail }}
          </p>
          <div class="address-item__actions">
            <el-button text type="primary" size="small" @click="openEdit(address)">编辑</el-button>
            <el-button
              text
              size="small"
              :loading="defaultingId === address.id"
              :disabled="address.isDefault"
              @click="setDefault(address)"
            >
              设为默认
            </el-button>
            <el-button
              text
              type="danger"
              size="small"
              :loading="deletingId === address.id"
              @click="remove(address)"
            >
              删除
            </el-button>
          </div>
        </li>
      </ul>
    </DataState>

    <div class="addresses-page__foot">
      <router-link to="/menu">去点餐</router-link>
      <router-link to="/orders">我的订单</router-link>
      <el-button text @click="router.push('/checkout')">去结算</el-button>
    </div>

    <AddressFormDialog
      v-model="dialogVisible"
      :address="editingAddress"
      @saved="onSaved"
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

.addresses-page__header {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  justify-content: space-between;
  gap: var(--space-4);
}

.addresses-page__alert {
  margin-bottom: var(--space-5);
}

.address-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-5);
}

.address-item {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
  border-left: 3px solid transparent;
}

.address-item.is-default {
  border-left-color: var(--color-primary);
}

.address-item__head {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  font-size: var(--font-size-md);
}

.address-item__phone {
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
}

.address-item__detail {
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
  line-height: var(--line-height-loose);
}

.address-item__actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
  margin-top: auto;
  padding-top: var(--space-3);
  border-top: 1px dashed var(--color-border-subtle);
}

.addresses-page__foot {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--space-6);
  margin-top: var(--space-8);
  font-size: var(--font-size-sm);
  color: var(--color-text-tertiary);
}

@media (max-width: 760px) {
  .address-grid {
    grid-template-columns: 1fr;
  }
}
</style>
