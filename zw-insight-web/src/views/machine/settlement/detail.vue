<template>
  <div class="settlement-detail-container">
    <el-card shadow="never" v-loading="loading">
      <template #header>
        <div class="card-header">
          <span>结算单详情</span>
          <div>
            <el-button
              v-if="detail.status === 0 || detail.status === 3"
              type="warning"
              @click="handleSubmit"
            >
              提交审批
            </el-button>
            <el-button type="success" @click="handleExport">导出Excel</el-button>
            <el-button @click="router.back()">返回</el-button>
          </div>
        </div>
      </template>

      <!-- 基本信息 -->
      <el-descriptions :column="3" border>
        <el-descriptions-item label="结算编号">{{ detail.settlementCode }}</el-descriptions-item>
        <el-descriptions-item label="结算周期">
          {{ detail.periodStart }} ~ {{ detail.periodEnd }}
        </el-descriptions-item>
        <el-descriptions-item label="结算金额">
          <span class="amount-text">¥ {{ detail.totalAmount?.toLocaleString() }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="当前状态">
          <el-tag :type="statusTagType(detail.status)">{{ statusLabel(detail.status) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="审批流程ID" :span="2">
          {{ detail.workflowInstanceId || '-' }}
        </el-descriptions-item>
      </el-descriptions>

      <!-- 机械明细列表 -->
      <el-divider content-position="left">机械结算明细</el-divider>
      <el-table :data="detail.details || []" border size="small">
        <el-table-column type="index" label="序号" width="60" align="center" />
        <el-table-column prop="machineName" label="机械名称" min-width="140" />
        <el-table-column prop="machineModel" label="规格型号" width="120" />
        <el-table-column prop="workDays" label="工作天数" width="100" align="center" />
        <el-table-column prop="unitPrice" label="单价(元)" width="120" align="right">
          <template #default="{ row }">{{ row.unitPrice?.toLocaleString() }}</template>
        </el-table-column>
        <el-table-column prop="amount" label="金额(元)" width="120" align="right">
          <template #default="{ row }">{{ row.amount?.toLocaleString() }}</template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="150" show-overflow-tooltip />
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getMachineSettlementDetail,
  submitMachineSettlement,
  exportMachineSettlement
} from '@/api/machine'

const router = useRouter()
const route = useRoute()
const loading = ref(false)
const detail = ref<any>({})

type TagType = 'success' | 'warning' | 'info' | 'danger' | 'primary'

const statusMap: Record<number, { label: string; type: TagType }> = {
  0: { label: '草稿', type: 'info' },
  1: { label: '审批中', type: 'primary' },
  2: { label: '已审批', type: 'success' },
  3: { label: '已驳回', type: 'danger' }
}

function statusLabel(status: number) {
  return statusMap[status]?.label || '未知'
}

function statusTagType(status: number): TagType {
  return statusMap[status]?.type || 'info'
}

async function loadDetail() {
  const id = Number(route.params.id)
  if (!id) return
  loading.value = true
  try {
    const res: any = await getMachineSettlementDetail(id)
    detail.value = res.data || {}
  } finally {
    loading.value = false
  }
}

async function handleSubmit() {
  await ElMessageBox.confirm('确定要提交审批吗？提交后将进入审批流程。', '提交审批', { type: 'warning' })
  await submitMachineSettlement(detail.value.id)
  ElMessage.success('提交成功')
  loadDetail()
}

async function handleExport() {
  try {
    const res: any = await exportMachineSettlement(detail.value.id)
    const blob = new Blob([res], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' })
    const url = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `机械结算单_${detail.value.settlementCode}.xlsx`
    link.click()
    window.URL.revokeObjectURL(url)
  } catch {
    ElMessage.error('导出失败')
  }
}

onMounted(() => {
  loadDetail()
})
</script>

<style scoped>
.settlement-detail-container {
  padding: 16px;
}
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.amount-text {
  font-weight: bold;
  color: #409eff;
  font-size: 16px;
}
</style>
