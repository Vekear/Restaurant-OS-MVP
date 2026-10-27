<script setup>
/**
 * 403：角色不匹配时的统一落地页
 */
import { useRouter } from 'vue-router'
import StatePanel from '@/components/StatePanel.vue'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const router = useRouter()

function goHome() {
  router.replace(auth.homePath)
}

async function switchAccount() {
  if (auth.isLoggedIn) await auth.logout()
  router.replace({ name: 'login' })
}
</script>

<template>
  <StatePanel
    code="403"
    title="没有访问该页面的权限"
    description="当前账号的角色无法访问此空间：商家工作台仅限 MERCHANT，平台管理后台仅限 ADMIN。请切换对应角色的账号后重试。"
  >
    <el-button type="primary" @click="goHome">回到我的空间</el-button>
    <el-button @click="switchAccount">切换账号</el-button>
    <el-button text @click="router.replace('/')">顾客端首页</el-button>
  </StatePanel>
</template>
