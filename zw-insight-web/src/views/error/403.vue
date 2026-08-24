<template>
  <div class="error-page">
    <div class="error-content">
      <div class="error-code danger">403</div>
      <h2 class="error-title">无访问权限</h2>
      <p class="error-desc">抱歉，您没有权限访问该页面，请联系管理员。</p>
      <div class="error-actions">
        <el-button type="primary" @click="$router.push('/')">
          <el-icon><HomeFilled /></el-icon>返回首页
        </el-button>
        <el-button @click="$router.back()">返回上一页</el-button>
        <el-button type="success" @click="reLogin">
          <el-icon><RefreshRight /></el-icon>重新登录
        </el-button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()

/**
 * 重新登录（403 自愈入口，2026-08-24）：
 * 陈旧持久化态（token 存在但 permissions 为空）命中路由守卫会循环跳 /403，
 * 此处清除登录态回登录页重新加载权限，作为用户自助恢复手段。
 */
function reLogin() {
  userStore.logout()
  router.push('/login')
}
</script>

<style scoped>
.error-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--zw-bg-page);
}

.error-content {
  text-align: center;
  padding: var(--zw-space-xl);
}

.error-code {
  font-size: 120px;
  font-weight: 800;
  line-height: 1;
  letter-spacing: 4px;
}

.error-code.danger {
  color: var(--zw-danger);
}

.error-title {
  margin-top: var(--zw-space-md);
  font-size: var(--zw-font-size-xl);
  font-weight: var(--zw-font-weight-semibold);
  color: var(--zw-text-primary);
}

.error-desc {
  margin-top: var(--zw-space-xs);
  font-size: var(--zw-font-size-sm);
  color: var(--zw-text-tertiary);
}

.error-actions {
  margin-top: var(--zw-space-lg);
  display: flex;
  gap: var(--zw-space-sm);
  justify-content: center;
}
</style>
