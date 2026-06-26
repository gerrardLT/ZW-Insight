<template>
  <div class="backup-container">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span class="title">数据备份</span>
          <el-button
            type="primary"
            :icon="FolderAdd"
            :loading="backuping"
            @click="handleBackup"
          >
            手动备份
          </el-button>
        </div>
      </template>

      <el-alert
        type="info"
        :closable="false"
        show-icon
        class="tip-alert"
        title="手动备份将执行全量 mysqldump 并上传至对象存储，过程可能耗时较长，请耐心等待。恢复操作为高风险操作，需输入登录密码二次确认。"
      />

      <el-table :data="tableData" v-loading="loading" border>
        <el-table-column prop="fileName" label="备份文件名" min-width="240" show-overflow-tooltip>
          <template #default="{ row }">{{ row.fileName || '-' }}</template>
        </el-table-column>
        <el-table-column label="文件大小" width="120">
          <template #default="{ row }">{{ formatSize(row.fileSize) }}</template>
        </el-table-column>
        <el-table-column label="耗时" width="100">
          <template #default="{ row }">{{ formatDuration(row.durationMs) }}</template>
        </el-table-column>
        <el-table-column label="类型" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="row.backupType === 'SCHEDULED' ? 'warning' : 'primary'" size="small">
              {{ row.backupType === 'SCHEDULED' ? '定时' : '手动' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 'SUCCESS' ? 'success' : 'danger'" size="small">
              {{ row.status === 'SUCCESS' ? '成功' : '失败' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="备份时间" width="170" />
        <el-table-column label="操作" width="220" fixed="right" align="center">
          <template #default="{ row }">
            <el-button
              link
              type="primary"
              :disabled="row.status !== 'SUCCESS' || !row.storagePath"
              @click="handleDownload(row)"
            >
              下载
            </el-button>
            <el-button
              link
              type="warning"
              :disabled="row.status !== 'SUCCESS' || !row.storagePath"
              :loading="restoringId === row.id"
              @click="handleRestore(row)"
            >
              恢复
            </el-button>
            <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无备份记录" />
        </template>
      </el-table>

      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="queryParams.pageNum"
          v-model:page-size="queryParams.pageSize"
          :page-sizes="[20, 50, 100]"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="loadData"
          @current-change="loadData"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { FolderAdd } from '@element-plus/icons-vue'
import {
  executeBackup,
  getBackupPage,
  downloadBackup,
  deleteBackup,
  restoreBackup,
  type SysBackupRecord
} from '@/api/backup'

defineOptions({ name: 'BackupManage' })

const loading = ref(false)
const backuping = ref(false)
const restoringId = ref<number | null>(null)
const tableData = ref<SysBackupRecord[]>([])
const total = ref(0)

const queryParams = reactive({
  pageNum: 1,
  pageSize: 20
})

/** 字节大小格式化 */
function formatSize(bytes?: number): string {
  if (!bytes || bytes <= 0) return '-'
  const units = ['B', 'KB', 'MB', 'GB', 'TB']
  let value = bytes
  let i = 0
  while (value >= 1024 && i < units.length - 1) {
    value /= 1024
    i++
  }
  return `${value.toFixed(value >= 100 || i === 0 ? 0 : 1)} ${units[i]}`
}

/** 毫秒耗时格式化 */
function formatDuration(ms?: number): string {
  if (!ms || ms <= 0) return '-'
  if (ms < 1000) return `${ms} ms`
  const seconds = ms / 1000
  if (seconds < 60) return `${seconds.toFixed(1)} s`
  const minutes = Math.floor(seconds / 60)
  return `${minutes}m ${Math.round(seconds % 60)}s`
}

async function loadData() {
  loading.value = true
  try {
    const res: any = await getBackupPage(queryParams)
    tableData.value = res.data?.records || []
    total.value = res.data?.total || 0
  } finally {
    loading.value = false
  }
}

async function handleBackup() {
  await ElMessageBox.confirm(
    '确定要立即执行一次全量数据库备份吗？该操作可能耗时较长。',
    '手动备份确认',
    { type: 'warning', confirmButtonText: '开始备份', cancelButtonText: '取消' }
  )
  backuping.value = true
  try {
    await executeBackup()
    ElMessage.success('备份完成')
    queryParams.pageNum = 1
    loadData()
  } finally {
    backuping.value = false
  }
}

async function handleDownload(row: SysBackupRecord) {
  try {
    const res: any = await downloadBackup(row.id)
    const blob = new Blob([res], { type: 'application/gzip' })
    const url = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = row.fileName || `backup_${row.id}.sql.gz`
    link.click()
    window.URL.revokeObjectURL(url)
  } catch {
    ElMessage.error('备份文件下载失败')
  }
}

async function handleRestore(row: SysBackupRecord) {
  await ElMessageBox.confirm(
    `恢复将使用备份「${row.fileName || row.id}」覆盖当前数据库，且不可撤销！请务必确认。`,
    '数据库恢复确认',
    { type: 'error', confirmButtonText: '确认恢复', cancelButtonText: '取消' }
  )
  restoringId.value = row.id
  try {
    // 后端可能要求二次确认（449），由全局 axios 拦截器弹出密码框并自动重试
    await restoreBackup(row.id)
    ElMessage.success('数据库恢复成功')
    loadData()
  } finally {
    restoringId.value = null
  }
}

async function handleDelete(row: SysBackupRecord) {
  await ElMessageBox.confirm(
    `确定要删除备份记录「${row.fileName || row.id}」吗？对应的存储文件也将一并删除。`,
    '删除确认',
    { type: 'warning', confirmButtonText: '确定删除', cancelButtonText: '取消' }
  )
  await deleteBackup(row.id)
  ElMessage.success('删除成功')
  loadData()
}

onMounted(loadData)
</script>

<style scoped>
.backup-container {
  padding: 16px;
}
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.title {
  font-weight: 600;
  font-size: 15px;
}
.tip-alert {
  margin-bottom: 16px;
}
.pagination-wrap {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
</style>
