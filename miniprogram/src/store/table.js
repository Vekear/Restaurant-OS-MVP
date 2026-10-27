import { computed, reactive } from 'vue'
import {
  getStoredTableSession,
  saveTableSession,
  clearStoredTableSession,
  isTableSessionUsable
} from '@/utils/tableSession'

/** 堂食会话（扫码/手输绑定后由后端下发，含 X-Table-Session 凭据） */
const state = reactive({
  session: getStoredTableSession()
})

const hasSession = computed(() => isTableSessionUsable(state.session))
const tableLabel = computed(() => {
  const session = state.session
  if (!session) return ''
  const no = session.tableNo || ''
  const name = session.tableName || ''
  if (no && name) return `${no} · ${name}`
  return no || name
})
const merchantName = computed(() => (state.session && state.session.merchantName) || '小馆食堂')

function setSession(session) {
  state.session = session && typeof session === 'object' ? session : null
  if (state.session) saveTableSession(state.session)
  else clearStoredTableSession()
}

function clearSession() {
  state.session = null
  clearStoredTableSession()
}

function syncFromStorage() {
  state.session = getStoredTableSession()
}

export const tableStore = {
  state,
  hasSession,
  tableLabel,
  merchantName,
  setSession,
  clearSession,
  syncFromStorage
}

export function useTableStore() {
  return tableStore
}
