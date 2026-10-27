<script setup>
/**
 * 管理后台布局：复用 ConsoleLayout，注入平台治理导航与墨青主题
 */
import ConsoleLayout from '@/layouts/ConsoleLayout.vue'
import { consoleProfiles } from '@/config/navigation'

const profile = consoleProfiles.admin
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
