<template>
  <div class="material-container">
    <el-card shadow="never">
      <el-form :model="queryParams" inline>
        <el-form-item label="采购合同ID">
          <el-input v-model="queryParams.contractId" placeholder="合同ID" clearable style="width: 160px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">
            <el-icon><Search /></el-icon>搜索
          </el-button>
          <el-button @click="handleReset">
            <el-icon><Refresh /></el-icon>重置
          </el-button>
        </el-form-item>
      </el-form>

      <el-alert
        title="退款记录由退货出库审批通过后自动生成，此处仅供查询"
        type="info"
        show-icon
        :closable="false"
        style="margin-bottom: 16px"
      />

      <el-table :data="tableData" v-loading="loading" border>
        <el-table-column prop="refundCode" label="退款单号" width="180" show-overflow-tooltip />
        <el-table-column prop="contractId" label="采购合同ID" width="120" />
        <el-table-column prop="refundAmount" label="退款金额" width="140" align="right">
          <template #default="{ row }">{{ formatMoney(row.refundAmount) }}</template>
        </el-table-column>
        <el-table-column prop="refundReason" label="退款原因" min-width="180" show-overflow-tooltip />
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)" size="small">{{ getStatusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleViewDetail(row)">明细</el-button>
          </template>
        </el-table-column>
      </el-table>

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

    <el-dialog v-model="detailVisible" title="退款明细" width="700px" destroy-on-close>
      <el-descriptions :column="2" border>
        <el-descriptions-item label="退款单号">{{ detail.refundCode }}</el-descriptions-item>
        <el-descriptions-item label="退款金额">{{ formatMoney(detail.refundAmount) }}</el-descriptions-item>
        <el-descriptions-item label="采购合同ID">{{ detail.contractId }}</el-descriptions-item>
        <el-descriptions-item label="关联出库单ID">{{ detail.outboundId }}</el-descriptions-item>
        <el-descriptions-item label="退款原因" :span="2">{{ detail.refundReason }}</el-descriptions-item>
      </el-descriptions>
      <el-table :data="detail.details || []" border style="margin-top: 16px">
        <el-table-column prop="materialName" label="材料名称" min-width="140" />
        <el-table-column prop="quantity" label="退货数量" width="120" align="right" />
        <el-table-column prop="unitPrice" label="单价" width="120" align="right">
          <template #default="{ row }">{{ formatMoney(row.unitPrice) }}</template>
        </el-table-column>
        <el-table-column prop="amount" label="金额" width="130" align="right">
          <template #default="{ row }">{{ formatMoney(row.amount) }}</template>
        </el-table-column>
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { getMaterialRefundPage, getMaterialRefundDetail } from '@/api/material'

const loading = ref(false)
const tableData = ref<any[]>([])
const total = ref(0)
const detailVisible = ref(false)
const detail = ref<any>({})

const queryParams = ref({
  page: 1,
  size: 10,
  contractId: undefined as number | undefined
})

const statusMap: Record<string, { label: string; type: string }> = {
  DRAFT: { label: '草稿', type: 'info' },
  PENDING: { label: '待审批', type: 'warning' },
  APPROVED: { label: '已通过', type: 'success' },
  REJECTED: { label: '已驳回', type: 'danger' }
}

function getStatusLabel(status: string) {
  return statusMap[status]?.label || status
}
function getStatusType(status: string) {
  return (statusMap[status]?.type || 'info') as any
}
function formatMoney(val: number) {
  if (!val && val !== 0) return '-'
  return val.toLocaleString('zh-CN', { minimumFractionDigits: 2 })
}

async function loadData() {
  loading.value = true
  try {
    const res: any = await getMaterialRefundPage(queryParams.value)
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
  queryParams.value = { page: 1, size: 10, contractId: undefined }
  loadData()
}

async function handleViewDetail(row: any) {
  const res: any = await getMaterialRefundDetail(row.id)
  detail.value = res.data || {}
  detailVisible.value = true
}

onMounted(() => {
  loadData()
})
</script>

<style scoped>
.material-container {
  padding: 16px;
}
.pagination-wrap {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
</style>
