<script setup>
/**
 * 商家工作台布局：复用 ConsoleLayout，注入商家导航与主题
 */
import ConsoleLayout from '@/layouts/ConsoleLayout.vue'
import { consoleProfiles } from '@/config/navigation'

const profile = consoleProfiles.merchant
</script>

<template>
  <ConsoleLayout
    :theme="profile.theme"
    :title="profile.title"
    :subtitle="profile.subtitle"
    :menu-items="profile.nav"
    :home-path="profile.basePath"
  />
</template>
