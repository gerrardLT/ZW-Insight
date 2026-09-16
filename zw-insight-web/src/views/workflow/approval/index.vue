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
        <ColumnSettingPopover
          :columns="approvalColumns"
          :visible="columnVisible"
          @update:visible="setVisible"
          @reset="resetColumns"
        />
      </div>

      <!-- 首屏骨架屏：loading 期间显示骨架，完成后渲染表格（感知性能优化 S1.4） -->
      <el-skeleton :loading="loading" :rows="5" animated>
        <el-table
          :data="tableData"
          border
          @selection-change="handleSelectionChange"
        >
            <el-table-column v-if="activeTab === 'todo'" type="selection" width="50" align="center" />
            <el-table-column v-if="columnVisible[0]" prop="taskName" label="任务名称" min-width="150" />
            <el-table-column v-if="columnVisible[1]" prop="businessType" label="业务类型" width="120" />
            <el-table-column v-if="columnVisible[2]" prop="initiator" label="发起人" width="100" />
            <el-table-column v-if="columnVisible[3]" prop="createTime" label="创建时间" width="170" />
            <el-table-column label="操作" width="220" fixed="right" v-if="activeTab === 'todo'">
              <template #default="{ row }">
                <el-button link type="success" @click="handleApprove(row)">通过</el-button>
                <el-button link type="warning" @click="handleReject(row)">退回</el-button>
                <el-button link type="danger" @click="handleTerminate(row)">终止</el-button>
              </template>
            </el-table-column>
          </el-table>
      </el-skeleton>

      <!-- 分页 -->
      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="queryParams.page"
          v-model:page-size="queryParams.size"
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
import { ref, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import ColumnSettingPopover from '@/components/ColumnSettingPopover.vue'
import { useColumnSetting } from '@/composables/useColumnSetting'
import {
  getTodoTasks,
  getDoneTasks,
  completeTask,
  rejectToPrevious,
  rejectToStart,
  terminateProcess,
  batchApprove
} from '@/api/workflow'

// 列显隐配置（S2.1）：按 approval-table 持久化 localStorage
const approvalColumns = [
  { key: 'taskName', label: '任务名称' },
  { key: 'businessType', label: '业务类型' },
  { key: 'initiator', label: '发起人' },
  { key: 'createTime', label: '创建时间' },
]
const { visible: columnVisible, setVisible, reset: resetColumns } = useColumnSetting('approval-table', approvalColumns)

const loading = ref(false)
const tableData = ref<any[]>([])
const total = ref(0)
const activeTab = ref('todo')
const selectedRows = ref<any[]>([])
const approveDialogVisible = ref(false)
const rejectDialogVisible = ref(false)
const submitLoading = ref(false)
const currentRowIndex = ref(-1) // 当前选中行索引
const tbodyRef = ref<HTMLElement | null>(null) // 表格 tbody 引用

// 后端 /todo /done 收 page/size（ApprovalController SoT），
// 原传 pageNum/pageSize 致后端永用默认值，翻页/改页大小实际失效（2026-08-17 真实浏览器实测修复）
const queryParams = ref({
  page: 1,
  size: 10
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

// P1 Keyboard Shortcuts：全局快捷键处理
function handleGlobalKeydown(event: KeyboardEvent) {
  // 忽略在输入框、文本域中的按键事件
  if (event.target instanceof HTMLInputElement || event.target instanceof HTMLTextAreaElement || event.target instanceof HTMLSelectElement) {
    return
  }

  // Ctrl+Enter 提交审批/驳回表单
  if ((event.ctrlKey || event.metaKey) && event.key === 'Enter') {
    if (approveDialogVisible.value) {
      event.preventDefault()
      submitApprove()
    } else if (rejectDialogVisible.value) {
      event.preventDefault()
      submitReject()
    }
    return
  }

  // 仅当在待办 Tab 时启用行导航
  if (activeTab.value !== 'todo') return

  // 方向键导航
  if (event.key === 'ArrowDown') {
    event.preventDefault()
    navigateRow(1)
  } else if (event.key === 'ArrowUp') {
    event.preventDefault()
    navigateRow(-1)
  } else if (event.key === ' ') {
    // 空格选择行
    event.preventDefault()
    toggleRowSelection()
  } else if (event.key === 'Enter') {
    // Enter 快速通过/退回
    event.preventDefault()
    quickAction()
  } else if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'a') {
    // Ctrl+A 全选
    event.preventDefault()
    selectAll()
  }
}

function navigateRow(direction: number) {
  const newIndex = currentRowIndex.value + direction
  if (newIndex >= 0 && newIndex < tableData.value.length) {
    currentRowIndex.value = newIndex
    highlightRow(newIndex)
  }
}

function highlightRow(index: number) {
  // 获取当前页面的所有行元素并高亮选中行
  nextTick(() => {
    const rows = document.querySelectorAll('.el-table__body-wrapper tbody tr.el-table__row')
    rows.forEach((row, i) => {
      if (i === index) {
        row.classList.add('keyboard-focused')
        row.scrollIntoView({ block: 'nearest' })
      } else {
        row.classList.remove('keyboard-focused')
      }
    })
  })
}

function toggleRowSelection() {
  if (currentRowIndex.value >= 0 && currentRowIndex.value < tableData.value.length) {
    const row = tableData.value[currentRowIndex.value]
    const isSelected = selectedRows.value.some(r => r.taskId === row.taskId)
    if (isSelected) {
      selectedRows.value = selectedRows.value.filter(r => r.taskId !== row.taskId)
    } else {
      selectedRows.value.push(row)
    }
  }
}

function quickAction() {
  if (currentRowIndex.value >= 0 && currentRowIndex.value < tableData.value.length) {
    const row = tableData.value[currentRowIndex.value]
    if (!row) return

    // 如果有行被选中，执行批量操作；否则对当前行执行通过
    if (selectedRows.value.length > 0) {
      handleBatchApprove()
    } else {
      handleApprove(row)
    }
  }
}

function selectAll() {
  if (tableData.value.length > 0) {
    selectedRows.value = [...tableData.value]
  } else {
    selectedRows.value = []
  }
}

async function loadData() {
  loading.value = true
  try {
    const api = activeTab.value === 'todo' ? getTodoTasks : getDoneTasks
    const res: any = await api(queryParams.value)
    tableData.value = res.data?.records || []
    total.value = res.data?.total || 0
    currentRowIndex.value = -1 // 数据刷新时重置行索引
  } finally {
    loading.value = false
  }
}

function handleTabChange() {
  queryParams.value.page = 1
  selectedRows.value = []
  currentRowIndex.value = -1
  loadData()
}

function handleSelectionChange(rows: any[]) {
  selectedRows.value = rows
  currentRowIndex.value = -1 // 手动勾选时重置行导航状态
}

function handleApprove(row: any) {
  approveForm.value = { taskId: row.taskId, comment: '' }
  approveDialogVisible.value = true
  // 对话框打开后聚焦到第一个输入框
  nextTick(() => {
    const input = document.querySelector('.el-dialog__input textarea') as HTMLTextAreaElement
    input?.focus()
  })
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
  nextTick(() => {
    const input = document.querySelector('.el-dialog__reject textarea') as HTMLTextAreaElement
    input?.focus()
  })
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

  // 乐观更新试点（S2.3）：先本地移除选中行给即时反馈；失败还原快照，不静默丢数据
  const snapshot = [...tableData.value]
  const approvedIds = new Set(taskIds)
  tableData.value = tableData.value.filter((row) => !approvedIds.has(row.taskId))
  selectedRows.value = []
  currentRowIndex.value = -1

  try {
    await batchApprove({ taskIds })
    ElMessage.success(`批量审批成功（${taskIds.length} 条）`)
    // 与服务端对齐：静默刷新拿回权威分页数据（页码/总数校正），失败仅告警不阻断
    await loadData()
  } catch {
    // 失败还原快照（拦截器已弹具体错误提示，此处补充数据已恢复的说明）
    tableData.value = snapshot
    ElMessage.warning('批量审批未完成，列表数据已恢复')
  }
}

onMounted(() => {
  document.addEventListener('keydown', handleGlobalKeydown)
  loadData()
})

// 卸载时移除全局键盘监听（防内存泄漏与跨页残留操作，S2.2 修复）
onBeforeUnmount(() => {
  document.removeEventListener('keydown', handleGlobalKeydown)
})
</script>

<style scoped>
.approval-container {
  padding: var(--zw-space-md);
}
.table-toolbar {
  margin-bottom: var(--zw-space-md);
}
.pagination-wrap {
  margin-top: var(--zw-space-md);
  display: flex;
  justify-content: flex-end;
}

/* P1 Keyboard Navigation Styles */
.el-table__row.keyboard-focused {
  background-color: var(--zw-info-light) !important;
  outline: 2px solid var(--el-color-primary) !important;
  outline-offset: -2px;
}

/* Focus visible for accessibility */
.el-table__body-wrapper:focus-within .el-table__row.el-table__row--focus-visible {
  background-color: var(--zw-info-light);
}
</style>
