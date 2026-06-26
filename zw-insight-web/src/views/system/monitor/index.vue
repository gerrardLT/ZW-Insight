<template>
  <div class="monitor-container">
    <el-card shadow="never">
      <template #header>
        <span>系统监控</span>
      </template>
      <el-empty description="系统监控仪表盘页面待实现（任务 10.3）" />
    </el-card>
  </div>
</template>

<script setup lang="ts">
// 系统监控仪表盘页面占位组件，业务实现见任务 10.3
defineOptions({ name: 'SystemMonitor' })
</script>

<style scoped>
.monitor-container {
  padding: 16px;
}
</style>
