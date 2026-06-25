<template>
  <div class="message-center-container">
    <el-card shadow="never">
      <div class="card-header">
        <el-tabs v-model="activeTab" @tab-change="handleTabChange">
          <el-tab-pane name="unread">
            <template #label>
              <span>未读消息<el-badge v-if="unreadCount > 0" :value="unreadCount" :max="99" class="tab-badge" /></span>
            </template>
          </el-tab-pane>
          <el-tab-pane label="全部消息" name="all" />
        </el-tabs>
        <el-button type="primary" size="small" @click="handleMarkAllRead" :disabled="unreadCount === 0">全部已读</el-button>
      </div>

      <el-table :data="tableData" v-loading="loading" border>
        <el-table-column prop="title" label="消息标题" min-width="200" show-overflow-tooltip />
        <el-table-column prop="source" label="来源" width="120" />
        <el-table-column prop="createTime" label="时间" width="170" />
        <el-table-column prop="isRead" label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.isRead ? 'info' : 'danger'" size="small">
              {{ row.isRead ? '已读' : '未读' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleMarkRead(row)" :disabled="row.isRead">标记已读</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrap">
        <el-pagination v-model:current-page="queryParams.pageNum" v-model:page-size="queryParams.pageSize" :page-sizes="[10, 20, 50]" :total="total" layout="total, sizes, prev, pager, next, jumper" @size-change="loadData" @current-change="loadData" />
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getUnreadMessages, getAllMessages, markAsRead, markAllAsRead, getUnreadCount } from '@/api/message'

const loading = ref(false)
const tableData = ref<any[]>([])
const total = ref(0)
const unreadCount = ref(0)
const activeTab = ref('unread')

const queryParams = ref({
  pageNum: 1,
  pageSize: 10
})

async function loadData() {
  loading.value = true
  try {
    const fetchFn = activeTab.value === 'unread' ? getUnreadMessages : getAllMessages
    const res: any = await fetchFn(queryParams.value)
    tableData.value = res.data?.records || []
    total.value = res.data?.total || 0
  } finally {
    loading.value = false
  }
}

async function loadUnreadCount() {
  try {
    const res: any = await getUnreadCount()
    unreadCount.value = res.data || 0
  } catch {
    // 获取未读数失败不阻塞页面
  }
}

function handleTabChange() {
  queryParams.value.pageNum = 1
  loadData()
}

async function handleMarkRead(row: any) {
  await markAsRead(row.id)
  ElMessage.success('已标记为已读')
  row.isRead = true
  unreadCount.value = Math.max(0, unreadCount.value - 1)
  if (activeTab.value === 'unread') {
    loadData()
  }
}

async function handleMarkAllRead() {
  await markAllAsRead()
  ElMessage.success('已全部标记为已读')
  unreadCount.value = 0
  loadData()
}

onMounted(() => {
  loadData()
  loadUnreadCount()
})
</script>

<style scoped>
.message-center-container { padding: 16px; }
.card-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.card-header .el-tabs { flex: 1; }
.tab-badge { margin-left: 6px; }
.pagination-wrap { margin-top: 16px; display: flex; justify-content: flex-end; }
</style>
