<template>
  <div class="user-center-container">
    <el-card shadow="never">
      <el-tabs v-model="activeTab">
        <el-tab-pane label="登录设备" name="devices">
          <div class="tab-toolbar">
            <div class="toolbar-tip">
              <el-icon><Monitor /></el-icon>
              <span>这里展示您账号的所有活跃登录设备。若发现异常设备，可立即远程注销以保护账号安全。</span>
            </div>
            <el-button :icon="Refresh" @click="loadDevices" :loading="loading">刷新</el-button>
          </div>

          <el-table :data="deviceList" v-loading="loading" border>
            <el-table-column label="设备名称" min-width="180" show-overflow-tooltip>
              <template #default="{ row }">
                <span class="device-name">{{ row.deviceName || '未知设备' }}</span>
                <el-tag v-if="row.isCurrent" type="success" size="small" effect="dark" class="current-tag">
                  当前设备
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="os" label="操作系统" width="150" show-overflow-tooltip>
              <template #default="{ row }">{{ row.os || '-' }}</template>
            </el-table-column>
            <el-table-column prop="ipAddress" label="IP 地址" width="150">
              <template #default="{ row }">{{ row.ipAddress || '-' }}</template>
            </el-table-column>
            <el-table-column prop="location" label="登录地点" width="160" show-overflow-tooltip>
              <template #default="{ row }">{{ formatLocation(row.location) }}</template>
            </el-table-column>
            <el-table-column prop="loginTime" label="登录时间" width="170">
              <template #default="{ row }">{{ row.loginTime || '-' }}</template>
            </el-table-column>
            <el-table-column label="状态" width="90" align="center">
              <template #default="{ row }">
                <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
                  {{ row.status === 1 ? '活跃' : '已注销' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="120" fixed="right" align="center">
              <template #default="{ row }">
                <el-tooltip v-if="row.isCurrent" content="当前设备不可注销" placement="top">
                  <span>
                    <el-button link type="info" disabled>当前设备</el-button>
                  </span>
                </el-tooltip>
                <el-button
                  v-else
                  link
                  type="danger"
                  :disabled="row.status !== 1"
                  @click="handleRevoke(row as LoginDevice)"
                >
                  远程注销
                </el-button>
              </template>
            </el-table-column>
            <template #empty>
              <el-empty description="暂无登录设备记录" />
            </template>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Monitor, Refresh } from '@element-plus/icons-vue'
import { getLoginDevices, revokeLoginDevice, type LoginDevice } from '@/api/device'

const activeTab = ref('devices')
const loading = ref(false)
const deviceList = ref<LoginDevice[]>([])

/** 将后端 "省份|城市" 格式化为 "省份 城市" 展示 */
function formatLocation(location?: string) {
  if (!location) return '-'
  return location.split('|').filter(Boolean).join(' ') || '-'
}

async function loadDevices() {
  loading.value = true
  try {
    const res: any = await getLoginDevices()
    deviceList.value = res.data || []
  } finally {
    loading.value = false
  }
}

async function handleRevoke(row: LoginDevice) {
  await ElMessageBox.confirm(
    `确定要远程注销设备「${row.deviceName || '未知设备'}」吗？注销后该设备需重新登录。`,
    '远程注销确认',
    {
      type: 'warning',
      confirmButtonText: '确定注销',
      cancelButtonText: '取消'
    }
  )
  await revokeLoginDevice(row.id)
  ElMessage.success('设备已注销')
  loadDevices()
}

onMounted(() => {
  loadDevices()
})
</script>

<style scoped>
.user-center-container {
  padding: 16px;
}
.tab-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}
.toolbar-tip {
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
.device-name {
  font-weight: 500;
}
.current-tag {
  margin-left: 8px;
}
</style>
