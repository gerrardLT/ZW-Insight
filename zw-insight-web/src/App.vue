<template>
  <router-view />
  <!-- 全局二次确认密码对话框：由 axios 拦截器在收到 449 时触发 -->
  <ConfirmPasswordDialog />
</template>

<script setup lang="ts">
import { onMounted, watch } from 'vue'
import ConfirmPasswordDialog from '@/components/ConfirmPasswordDialog.vue'
import { useAppStore } from '@/stores/app'
import { useBrandStore } from '@/stores/brand'

const appStore = useAppStore()
const brandStore = useBrandStore()

onMounted(() => {
  // 确保主题在应用启动时应用到 DOM
  appStore.applyTheme(appStore.theme)
  // 拉取当前部署环境的品牌配置（系统名称/Logo/版权/主色），驱动浏览器标题与登录页/侧边栏展示；
  // 免登录可读，未登录状态（如刷新停留在登录页）同样生效
  brandStore.fetchBrandConfig()
})

// 主题切换后重放品牌主色：派生面板色（light/lighter）依赖明暗基调
watch(() => appStore.theme, () => {
  if (brandStore.loaded) brandStore.applyBrandTheme()
})
</script>
