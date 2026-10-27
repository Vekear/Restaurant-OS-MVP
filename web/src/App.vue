<template>
  <!--
    根出口：只负责布局级过渡。
    key 取一级匹配路由的 path（/、/merchant、/admin、/login...），
    保证同一布局内部切换页面时布局实例不重挂，切换角色空间时才重建。
  -->
  <router-view v-slot="{ Component, route }">
    <transition name="fade-slide" mode="out-in">
      <component :is="Component" :key="route.matched[0]?.path || route.path" />
    </transition>
  </router-view>
</template>
