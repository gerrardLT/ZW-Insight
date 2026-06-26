<template>
  <router-view />
  <!-- 全局二次确认密码对话框：由 axios 拦截器在收到 449 时触发 -->
  <ConfirmPasswordDialog />
</template>

<script setup lang="ts">
import ConfirmPasswordDialog from '@/components/ConfirmPasswordDialog.vue'
</script>
