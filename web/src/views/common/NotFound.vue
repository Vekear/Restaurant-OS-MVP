<script setup>
/**
 * 404：未匹配路由
 */
import { useRouter } from 'vue-router'
import StatePanel from '@/components/StatePanel.vue'

const router = useRouter()

function goBack() {
  if (window.history.length > 1) router.back()
  else router.replace('/')
}
</script>

<template>
  <StatePanel
    code="404"
    title="页面走丢了"
    description="你访问的地址不存在，可能是链接已失效或输入有误。可以返回首页继续点餐，或回到工作台处理订单。"
  >
    <el-button type="primary" @click="router.replace('/')">返回顾客端首页</el-button>
    <el-button @click="goBack">返回上一页</el-button>
    <el-button text @click="router.replace('/login')">去登录</el-button>
  </StatePanel>
</template>
