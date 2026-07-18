<template>
  <div class="finance-container">
    <el-card shadow="never">
      <el-form :model="queryParams" inline>
        <el-form-item label="项目">
          <el-select
            v-model="queryParams.projectId"
            placeholder="全部项目"
            filterable
            remote
            :remote-method="searchProject"
            clearable
            style="width: 220px"
          >
            <el-option v-for="item in projectList" :key="item.id" :label="item.projectName" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="日期范围">
          <el-date-picker
            v-model="dateRange"
            type="daterange"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            value-format="YYYY-MM-DD"
            style="width: 260px"
          />
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

      <el-table :data="tableData" v-loading="loading" border show-summary :summary-method="getSummaries">
        <el-table-column prop="projectName" label="项目名称" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ row.projectName || '-' }}</template>
        </el-table-column>
        <el-table-column label="已开票" align="center">
          <el-table-column prop="invoicedCount" label="笔数" width="90" align="right" />
          <el-table-column prop="invoicedAmount" label="金额" width="140" align="right">
            <template #default="{ row }">{{ formatMoney(row.invoicedAmount) }}</template>
          </el-table-column>
          <el-table-column prop="invoicedTaxAmount" label="税额" width="130" align="right">
            <template #default="{ row }">{{ formatMoney(row.invoicedTaxAmount) }}</template>
          </el-table-column>
        </el-table-column>
        <el-table-column label="已收票" align="center">
          <el-table-column prop="receivedCount" label="笔数" width="90" align="right" />
          <el-table-column prop="receivedAmount" label="金额" width="140" align="right">
            <template #default="{ row }">{{ formatMoney(row.receivedAmount) }}</template>
          </el-table-column>
          <el-table-column prop="receivedTaxAmount" label="税额" width="130" align="right">
            <template #default="{ row }">{{ formatMoney(row.receivedTaxAmount) }}</template>
          </el-table-column>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import type { TableColumnCtx } from 'element-plus'
import { getInvoiceSummary } from '@/api/finance'
import { getProjectList } from '@/api/project'
import type { InvoiceSummary } from '@/types/finance'

const loading = ref(false)
const tableData = ref<InvoiceSummary[]>([])
const projectList = ref<any[]>([])
const dateRange = ref<[string, string] | null>(null)

const queryParams = ref({
  projectId: undefined as number | undefined
})

function formatMoney(val: number) {
  if (!val && val !== 0) return '-'
  return Number(val).toLocaleString('zh-CN', { minimumFractionDigits: 2 })
}

// 金额/税额列做合计，笔数列做求和
function getSummaries(param: { columns: TableColumnCtx<InvoiceSummary>[]; data: InvoiceSummary[] }) {
  const { columns, data } = param
  const sums: string[] = []
  const numberProps = ['invoicedCount', 'invoicedAmount', 'invoicedTaxAmount', 'receivedCount', 'receivedAmount', 'receivedTaxAmount']
  const moneyProps = ['invoicedAmount', 'invoicedTaxAmount', 'receivedAmount', 'receivedTaxAmount']
  columns.forEach((column, index) => {
    if (index === 0) {
      sums[index] = '合计'
      return
    }
    const prop = column.property as string
    if (numberProps.includes(prop)) {
      const total = data.reduce((acc, cur) => acc + (Number((cur as any)[prop]) || 0), 0)
      sums[index] = moneyProps.includes(prop) ? formatMoney(total) : String(total)
    } else {
      sums[index] = ''
    }
  })
  return sums
}

async function searchProject(query: string) {
  const res: any = await getProjectList({ projectName: query })
  projectList.value = res.data || []
}

async function loadData() {
  loading.value = true
  try {
    const res: any = await getInvoiceSummary({
      projectId: queryParams.value.projectId,
      startDate: dateRange.value?.[0],
      endDate: dateRange.value?.[1]
    })
    tableData.value = res.data || []
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  loadData()
}

function handleReset() {
  queryParams.value = { projectId: undefined }
  dateRange.value = null
  loadData()
}

onMounted(() => {
  loadData()
  searchProject('')
})
</script>

<style scoped>
.finance-container {
  padding: 16px;
}
</style>
