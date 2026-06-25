<template>
  <div class="approval-container">
    <el-card shadow="never">
      <!-- Tab 切换 -->
      <el-tabs v-model="activeTab" @tab-change="handleTabChange">
        <el-tab-pane label="待办任务" name="todo" />
        <el-tab-pane label="已办任务" name="done" />
      </el-tabs>

      <!-- 操作栏 -->
      <div class="table-toolbar" v-if="activeTab === 'todo'">
        <el-button type="success" :disabled="selectedRows.length === 0" @click="handleBatchApprove">
          <el-icon><Check /></el-icon>批量通过
        </el-button>
      </div>

      <!-- 表格 -->
      <el-table
        :data="tableData"
        v-loading="loading"
        border
        @selection-change="handleSelectionChange"
      >
        <el-table-column v-if="activeTab === 'todo'" type="selection" width="50" align="center" />
        <el-table-column prop="taskName" label="任务名称" min-width="150" />
        <el-table-column prop="businessType" label="业务类型" width="120" />
        <el-table-column prop="initiator" label="发起人" width="100" />
        <el-table-column prop="createTime" label="创建时间" width="170" />
        <el-table-column label="操作" width="220" fixed="right" v-if="activeTab === 'todo'">
          <template #default="{ row }">
            <el-button link type="success" @click="handleApprove(row)">通过</el-button>
            <el-button link type="warning" @click="handleReject(row)">退回</el-button>
            <el-button link type="danger" @click="handleTerminate(row)">终止</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="queryParams.pageNum"
          v-model:page-size="queryParams.pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="loadData"
          @current-change="loadData"
        />
      </div>
    </el-card>

    <!-- 审批弹窗 -->
    <el-dialog v-model="approveDialogVisible" title="审批意见" width="500px" destroy-on-close>
      <el-form :model="approveForm" label-width="80px">
        <el-form-item label="审批意见">
          <el-input
            v-model="approveForm.comment"
            type="textarea"
            placeholder="请输入审批意见"
            :rows="4"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="approveDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="submitApprove">确定</el-button>
      </template>
    </el-dialog>

    <!-- 退回弹窗 -->
    <el-dialog v-model="rejectDialogVisible" title="退回任务" width="500px" destroy-on-close>
      <el-form :model="rejectForm" label-width="80px">
        <el-form-item label="退回方式">
          <el-radio-group v-model="rejectForm.type">
            <el-radio value="previous">退回上一步</el-radio>
            <el-radio value="start">退回发起人</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="退回原因">
          <el-input
            v-model="rejectForm.comment"
            type="textarea"
            placeholder="请输入退回原因"
            :rows="4"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="rejectDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="submitReject">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getTodoTasks,
  getDoneTasks,
  completeTask,
  rejectToPrevious,
  rejectToStart,
  terminateProcess,
  batchApprove
} from '@/api/workflow'

const loading = ref(false)
const tableData = ref<any[]>([])
const total = ref(0)
const activeTab = ref('todo')
const selectedRows = ref<any[]>([])
const approveDialogVisible = ref(false)
const rejectDialogVisible = ref(false)
const submitLoading = ref(false)

const queryParams = ref({
  pageNum: 1,
  pageSize: 10
})

const approveForm = ref({
  taskId: '',
  comment: ''
})

const rejectForm = ref({
  taskId: '',
  type: 'previous',
  comment: ''
})

async function loadData() {
  loading.value = true
  try {
    const api = activeTab.value === 'todo' ? getTodoTasks : getDoneTasks
    const res: any = await api(queryParams.value)
    tableData.value = res.data?.records || []
    total.value = res.data?.total || 0
  } finally {
    loading.value = false
  }
}

function handleTabChange() {
  queryParams.value.pageNum = 1
  selectedRows.value = []
  loadData()
}

function handleSelectionChange(rows: any[]) {
  selectedRows.value = rows
}

function handleApprove(row: any) {
  approveForm.value = { taskId: row.taskId, comment: '' }
  approveDialogVisible.value = true
}

async function submitApprove() {
  submitLoading.value = true
  try {
    await completeTask(approveForm.value)
    ElMessage.success('审批通过')
    approveDialogVisible.value = false
    loadData()
  } finally {
    submitLoading.value = false
  }
}

function handleReject(row: any) {
  rejectForm.value = { taskId: row.taskId, type: 'previous', comment: '' }
  rejectDialogVisible.value = true
}

async function submitReject() {
  submitLoading.value = true
  try {
    const data = { taskId: rejectForm.value.taskId, comment: rejectForm.value.comment }
    if (rejectForm.value.type === 'previous') {
      await rejectToPrevious(data)
    } else {
      await rejectToStart(data)
    }
    ElMessage.success('退回成功')
    rejectDialogVisible.value = false
    loadData()
  } finally {
    submitLoading.value = false
  }
}

async function handleTerminate(row: any) {
  await ElMessageBox.confirm('确定要终止该流程吗？终止后不可恢复。', '提示', { type: 'warning' })
  await terminateProcess({ taskId: row.taskId })
  ElMessage.success('已终止')
  loadData()
}

async function handleBatchApprove() {
  await ElMessageBox.confirm(`确定要批量通过选中的 ${selectedRows.value.length} 条任务吗？`, '提示', { type: 'info' })
  const taskIds = selectedRows.value.map((row) => row.taskId)
  await batchApprove({ taskIds })
  ElMessage.success('批量审批成功')
  selectedRows.value = []
  loadData()
}

onMounted(() => {
  loadData()
})
</script>

<style scoped>
.approval-container {
  padding: 16px;
}
.table-toolbar {
  margin-bottom: 16px;
}
.pagination-wrap {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
</style>
