<template>
  <div class="settlement-detail-container">
    <!-- 页面头部 -->
    <div class="page-header">
      <el-page-header @back="handleBack">
        <template #content>
          <span class="page-title">结算单详情 - {{ settlement.settlementCode }}</span>
          <el-tag :type="getStatusType(settlement.status)" size="small" style="margin-left: 12px">
            {{ getStatusLabel(settlement.status) }}
          </el-tag>
        </template>
        <template #extra>
          <el-button v-if="settlement.status === 'DRAFT'" type="success" @click="handleSubmit">提交审批</el-button>
          <el-button type="warning" :icon="Download" @click="handleExport">导出 Excel</el-button>
        </template>
      </el-page-header>
    </div>

    <div v-loading="loading">
      <!-- 收支汇总卡片 -->
      <el-row :gutter="16" class="summary-section">
        <!-- 收入汇总 -->
        <el-col :span="8">
          <el-card shadow="hover" class="summary-card income-card">
            <template #header>
              <div class="card-header">
                <span>收入汇总</span>
              </div>
            </template>
            <el-descriptions :column="1" border size="small">
              <el-descriptions-item label="施工合同总额">{{ formatMoney(settlement.constructionContractAmount) }}</el-descriptions-item>
              <el-descriptions-item label="累计产值">{{ formatMoney(settlement.cumulativeOutput) }}</el-descriptions-item>
              <el-descriptions-item label="累计收款">{{ formatMoney(settlement.cumulativeReceived) }}</el-descriptions-item>
              <el-descriptions-item label="累计开票">{{ formatMoney(settlement.cumulativeInvoiced) }}</el-descriptions-item>
              <el-descriptions-item label="总收入">
                <span class="amount-highlight income-amount">{{ formatMoney(settlement.totalIncome) }}</span>
              </el-descriptions-item>
            </el-descriptions>
          </el-card>
        </el-col>

        <!-- 支出汇总 -->
        <el-col :span="8">
          <el-card shadow="hover" class="summary-card expense-card">
            <template #header>
              <div class="card-header">
                <span>支出汇总</span>
              </div>
            </template>
            <el-descriptions :column="1" border size="small">
              <el-descriptions-item label="分包结算">{{ formatMoney(settlement.subcontractSettled) }}</el-descriptions-item>
              <el-descriptions-item label="劳务结算">{{ formatMoney(settlement.laborSettled) }}</el-descriptions-item>
              <el-descriptions-item label="材料结算">{{ formatMoney(settlement.materialSettled) }}</el-descriptions-item>
              <el-descriptions-item label="机械结算">{{ formatMoney(settlement.machineSettled) }}</el-descriptions-item>
              <el-descriptions-item label="累计付款">{{ formatMoney(settlement.cumulativePaid) }}</el-descriptions-item>
              <el-descriptions-item label="总支出">
                <span class="amount-highlight expense-amount">{{ formatMoney(settlement.totalExpenditure) }}</span>
              </el-descriptions-item>
            </el-descriptions>
          </el-card>
        </el-col>

        <!-- 利润汇总 -->
        <el-col :span="8">
          <el-card shadow="hover" class="summary-card profit-card">
            <template #header>
              <div class="card-header">
                <span>利润分析</span>
              </div>
            </template>
            <el-descriptions :column="1" border size="small">
              <el-descriptions-item label="利润金额">
                <span :class="['amount-highlight', settlement.profit >= 0 ? 'income-amount' : 'expense-amount']">
                  {{ formatMoney(settlement.profit) }}
                </span>
              </el-descriptions-item>
              <el-descriptions-item label="利润率">
                <span :class="['amount-highlight', settlement.profitRate >= 0 ? 'income-amount' : 'expense-amount']">
                  {{ settlement.profitRate }}%
                </span>
              </el-descriptions-item>
            </el-descriptions>
          </el-card>
        </el-col>
      </el-row>

      <!-- 合同明细表格 -->
      <el-card shadow="never" class="contract-detail-section">
        <template #header>
          <div class="card-header">
            <span>合同明细</span>
            <el-tag v-if="unsettledCount > 0" type="danger" size="small">
              {{ unsettledCount }} 个合同未结清
            </el-tag>
          </div>
        </template>
        <el-table :data="contractDetails" border :row-class-name="getRowClassName">
          <el-table-column prop="contractType" label="合同类型" width="110" align="center">
            <template #default="{ row }">
              <el-tag size="small" :type="getContractTypeTag(row.contractType)">
                {{ getContractTypeLabel(row.contractType) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="contractCode" label="合同编号" width="150" show-overflow-tooltip />
          <el-table-column prop="contractName" label="合同名称" min-width="200" show-overflow-tooltip />
          <el-table-column prop="contractAmount" label="合同金额" width="140" align="right">
            <template #default="{ row }">{{ formatMoney(row.contractAmount) }}</template>
          </el-table-column>
          <el-table-column prop="settledAmount" label="已结算" width="130" align="right">
            <template #default="{ row }">{{ formatMoney(row.settledAmount) }}</template>
          </el-table-column>
          <el-table-column prop="paidAmount" label="已付款" width="130" align="right">
            <template #default="{ row }">{{ formatMoney(row.paidAmount) }}</template>
          </el-table-column>
          <el-table-column prop="unsettledAmount" label="未结金额" width="130" align="right">
            <template #default="{ row }">
              <span :class="{ 'text-danger': row.unsettledAmount > 0 }">
                {{ formatMoney(row.unsettledAmount) }}
              </span>
            </template>
          </el-table-column>
          <el-table-column prop="settlementStatus" label="状态" width="100" align="center">
            <template #default="{ row }">
              <el-tag
                :type="row.unsettledAmount > 0 ? 'danger' : 'success'"
                size="small"
              >
                {{ row.unsettledAmount > 0 ? '未结清' : '已结清' }}
              </el-tag>
            </template>
          </el-table-column>
        </el-table>
      </el-card>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Download } from '@element-plus/icons-vue'
import { getSettlement, submitSettlement, exportSettlement, getUnsettledContracts } from '@/api/settlement'

const route = useRoute()
const router = useRouter()
const loading = ref(false)

const settlement = ref<any>({})
const contractDetails = ref<any[]>([])

const unsettledCount = computed(() => {
  return contractDetails.value.filter(c => c.unsettledAmount > 0).length
})

function formatMoney(val: number) {
  if (val === null || val === undefined) return '-'
  return val.toLocaleString('zh-CN', { minimumFractionDigits: 2 })
}

function getStatusType(status: string) {
  const map: Record<string, string> = {
    DRAFT: 'info',
    SUBMITTED: 'warning',
    APPROVED: 'success',
    REJECTED: 'danger'
  }
  return map[status] || 'info'
}

function getStatusLabel(status: string) {
  const map: Record<string, string> = {
    DRAFT: '草稿',
    SUBMITTED: '审批中',
    APPROVED: '已通过',
    REJECTED: '已驳回'
  }
  return map[status] || status
}

function getContractTypeLabel(type: string) {
  const map: Record<string, string> = {
    SUBCONTRACT: '分包',
    LABOR: '劳务',
    MATERIAL: '材料',
    MACHINE: '机械',
    OTHER: '其他'
  }
  return map[type] || type
}

function getContractTypeTag(type: string) {
  const map: Record<string, string> = {
    SUBCONTRACT: '',
    LABOR: 'success',
    MATERIAL: 'warning',
    MACHINE: 'info',
    OTHER: 'danger'
  }
  return map[type] || ''
}

function getRowClassName({ row }: { row: any }) {
  return row.unsettledAmount > 0 ? 'unsettled-row' : ''
}

function handleBack() {
  router.push({ name: 'SettlementList' })
}

async function loadDetail() {
  const id = Number(route.params.id)
  if (!id) return

  loading.value = true
  try {
    const res: any = await getSettlement(id)
    settlement.value = res.data || {}
    contractDetails.value = res.data?.contractDetails || []

    // 若详情接口未返回合同明细，单独查询
    if (contractDetails.value.length === 0) {
      const unsettledRes: any = await getUnsettledContracts(id)
      contractDetails.value = unsettledRes.data || []
    }
  } finally {
    loading.value = false
  }
}

async function handleSubmit() {
  await ElMessageBox.confirm('确定要提交该结算单进行审批吗？提交后不可修改。', '提交审批', { type: 'warning' })
  const id = Number(route.params.id)
  await submitSettlement(id)
  ElMessage.success('提交成功')
  loadDetail()
}

async function handleExport() {
  const id = Number(route.params.id)
  try {
    const res: any = await exportSettlement(id)
    const blob = new Blob([res], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' })
    const url = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `结算报告_${settlement.value.settlementCode || id}.xlsx`
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    window.URL.revokeObjectURL(url)
    ElMessage.success('导出成功')
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

.page-header {
  margin-bottom: 20px;
}

.page-title {
  font-size: 16px;
  font-weight: 600;
}

.summary-section {
  margin-bottom: 20px;
}

.summary-card {
  height: 100%;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-weight: 600;
}

.amount-highlight {
  font-size: 15px;
  font-weight: 600;
}

.income-amount {
  color: #67c23a;
}

.expense-amount {
  color: #f56c6c;
}

.contract-detail-section {
  margin-top: 16px;
}

.text-danger {
  color: #f56c6c;
  font-weight: 600;
}

:deep(.unsettled-row) {
  background-color: #fef0f0 !important;
}

.pagination-wrap {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
</style>
