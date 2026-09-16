<template>
  <div class="error-page">
    <div class="error-content">
      <div class="error-code">404</div>
      <h2 class="error-title">页面走丢了</h2>
      <p class="error-desc">抱歉，您访问的页面不存在或已被移除。</p>
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
  /* 错误页工业数标巨字：稳固实色，符合工建精密排版规范，杜绝渐变字残次 */
  font-family: var(--zw-font-mono);
  font-size: calc(var(--zw-font-size-4xl) * 3);
  font-weight: 800;
  line-height: 1;
  color: var(--zw-primary);
  letter-spacing: 2px;
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
