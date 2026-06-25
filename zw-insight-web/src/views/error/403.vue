<template>
  <div class="error-page">
    <h1>403</h1>
    <p>抱歉，您没有权限访问该页面</p>
    <el-button type="primary" @click="$router.push('/')">返回首页</el-button>
  </div>
</template>

<style scoped>
.error-page {
  height: 100vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}
.error-page h1 {
  font-size: 80px;
  color: #f56c6c;
}
.error-page p {
  font-size: 16px;
  color: #606266;
  margin: 16px 0 32px;
}
</style>
