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
      </div>
    </div>
  </div>
</template>

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
