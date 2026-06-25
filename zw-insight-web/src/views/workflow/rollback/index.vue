<template>
  <div class="rollback-container">
    <el-card shadow="never">
      <!-- 筛选栏 -->
      <el-form :model="queryParams" inline>
        <el-form-item label="业务类型">
          <el-select v-model="queryParams.bizType" placeholder="全部类型" clearable style="width: 160px">
            <el-option label="劳务结算" value="LABOR_SETTLEMENT" />
            <el-option label="机械结算" value="MACHINE_SETTLEMENT" />
            <el-option label="采购结算" value="PURCHASE_SETTLEMENT" />
            <el-option label="分包结算" value="SUBCONTRACT_SETTLEMENT" />
            <el-option label="付款申请" value="PAYMENT_APPLY" />
            <el-option label="开票申请" value="INVOICE_APPLY" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryParams.rollbackStatus" placeholder="全部状态" clearable style="width: 130px">
            <el-option label="回滚成功" :value="0" />
            <el-option label="回滚失败" :value="1" />
            <el-option label="冲突待确认" :value="2" />
            <el-option label="重试中" :value="3" />
          </el-select>
        </el-form-item>
        <el-form-item label="时间范围">
          <el-date-picker
            v-model="queryParams.dateRange"
            type="daterange"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            value-format="YYYY-MM-DD"
            style="width: 260px"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <!-- 表格 -->
      <el-table :data="tableData" v-loading="loading" border>
        <el-table-column prop="workflowInstanceId" label="流程实例ID" width="200" show-overflow-tooltip />
        <el-table-column prop="bizType" label="业务类型" width="130" align="center">
          <template #default="{ row }">
            <el-tag size="small">{{ bizTypeNameMap[row.bizType] || row.bizType }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="bizId" label="业务单据ID" width="120" align="center" />
        <el-table-column label="状态" width="120" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.rollbackStatus)" size="small">
              {{ statusNameMap[row.rollbackStatus] || '-' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="retryCount" label="重试次数" width="90" align="center" />
        <el-table-column prop="errorMessage" label="错误信息" min-width="200" show-overflow-tooltip />
        <el-table-column prop="createdAt" label="创建时间" width="170" />
        <el-table-column prop="updatedAt" label="更新时间" width="170" />
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.rollbackStatus === 2"
              link
              type="primary"
              @click="handleConfirmConflict(row)"
            >
              处理冲突
            </el-button>
            <span v-else class="no-action">-</span>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="queryParams.page"
          v-model:page-size="queryParams.size"
          :page-sizes="[10, 20, 50]"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="loadData"
          @current-change="loadData"
        />
      </div>
    </el-card>

    <!-- 冲突确认弹窗 -->
    <el-dialog v-model="conflictDialogVisible" title="冲突处理确认" width="500px" destroy-on-close>
      <el-alert type="warning" show-icon :closable="false" style="margin-bottom: 16px">
        该回滚操作遇到数据冲突，请选择处理方式并确认。
      </el-alert>
      <el-descriptions :column="1" border size="small">
        <el-descriptions-item label="流程实例ID">{{ conflictRow?.workflowInstanceId }}</el-descriptions-item>
        <el-descriptions-item label="业务类型">{{ bizTypeNameMap[conflictRow?.bizType] || conflictRow?.bizType }}</el-descriptions-item>
        <el-descriptions-item label="错误信息">{{ conflictRow?.errorMessage }}</el-descriptions-item>
      </el-descriptions>
      <el-form :model="conflictForm" label-width="80px" style="margin-top: 16px">
        <el-form-item label="处理方式">
          <el-radio-group v-model="conflictForm.resolution">
            <el-radio value="FORCE_ROLLBACK">强制回滚（忽略冲突数据）</el-radio>
            <el-radio value="SKIP">跳过本次回滚（保持当前状态）</el-radio>
            <el-radio value="MANUAL">标记为人工处理</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="conflictDialogVisible = false">取消</el-button>
        <el-button
          type="primary"
          :loading="conflictSubmitting"
          :disabled="!conflictForm.resolution"
          @click="submitConflictConfirm"
        >
          确认处理
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getRollbackLogs, confirmRollbackConflict } from '@/api/workflow'

// ==================== 常量 ====================
type TagType = 'success' | 'warning' | 'info' | 'danger' | 'primary'

const bizTypeNameMap: Record<string, string> = {
  LABOR_SETTLEMENT: '劳务结算',
  MACHINE_SETTLEMENT: '机械结算',
  PURCHASE_SETTLEMENT: '采购结算',
  SUBCONTRACT_SETTLEMENT: '分包结算',
  PAYMENT_APPLY: '付款申请',
  INVOICE_APPLY: '开票申请'
}

const statusNameMap: Record<number, string> = {
  0: '回滚成功',
  1: '回滚失败',
  2: '冲突待确认',
  3: '重试中'
}

const statusTagTypeMap: Record<number, TagType> = {
  0: 'success',
  1: 'danger',
  2: 'warning',
  3: 'primary'
}

function statusTagType(status: number): TagType {
  return statusTagTypeMap[status] || 'info'
}

// ==================== 列表状态 ====================
const loading = ref(false)
const tableData = ref<any[]>([])
const total = ref(0)

const queryParams = ref({
  page: 1,
  size: 10,
  bizType: undefined as string | undefined,
  rollbackStatus: undefined as number | undefined,
  dateRange: null as string[] | null
})

// ==================== 冲突确认弹窗 ====================
const conflictDialogVisible = ref(false)
const conflictSubmitting = ref(false)
const conflictRow = ref<any>(null)

const conflictForm = ref({
  resolution: '' as string
})

// ==================== 方法 ====================
async function loadData() {
  loading.value = true
  try {
    const params: any = {
      page: queryParams.value.page,
      size: queryParams.value.size,
      bizType: queryParams.value.bizType,
      rollbackStatus: queryParams.value.rollbackStatus
    }
    if (queryParams.value.dateRange && queryParams.value.dateRange.length === 2) {
      params.startDate = queryParams.value.dateRange[0]
      params.endDate = queryParams.value.dateRange[1]
    }
    const res: any = await getRollbackLogs(params)
    tableData.value = res.data?.records || []
    total.value = res.data?.total || 0
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  queryParams.value.page = 1
  loadData()
}

function handleReset() {
  queryParams.value = { page: 1, size: 10, bizType: undefined, rollbackStatus: undefined, dateRange: null }
  loadData()
}

function handleConfirmConflict(row: any) {
  conflictRow.value = row
  conflictForm.value = { resolution: '' }
  conflictDialogVisible.value = true
}

async function submitConflictConfirm() {
  if (!conflictForm.value.resolution) {
    ElMessage.warning('请选择处理方式')
    return
  }
  conflictSubmitting.value = true
  try {
    await confirmRollbackConflict(conflictRow.value.id, { resolution: conflictForm.value.resolution })
    ElMessage.success('冲突处理完成')
    conflictDialogVisible.value = false
    loadData()
  } finally {
    conflictSubmitting.value = false
  }
}

onMounted(() => {
  loadData()
})
</script>

<style scoped>
.rollback-container {
  padding: 16px;
}
.pagination-wrap {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
.no-action {
  color: #c0c4cc;
}
</style>
