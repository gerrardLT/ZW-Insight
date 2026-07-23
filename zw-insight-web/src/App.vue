<template>
  <router-view />
  <!-- 全局二次确认密码对话框：由 axios 拦截器在收到 449 时触发 -->
  <ConfirmPasswordDialog />
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import ConfirmPasswordDialog from '@/components/ConfirmPasswordDialog.vue'
import { useAppStore } from '@/stores/app'

const appStore = useAppStore()

onMounted(() => {
  // 确保主题在应用启动时应用到 DOM
  appStore.applyTheme(appStore.theme)
})
</script>
