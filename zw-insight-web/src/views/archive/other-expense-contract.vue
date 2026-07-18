<template>
  <div class="archive-container">
    <el-card shadow="never">
      <el-alert
        title="其它支出合同档案为只读聚合视图，数据来源于合同模块，此处仅供查询"
        type="info"
        show-icon
        :closable="false"
        style="margin-bottom: 16px"
      />

      <el-form :model="queryParams" inline>
        <el-form-item label="关键字">
          <el-input v-model="queryParams.keyword" placeholder="合同编号/名称" clearable style="width: 200px" />
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

      <el-table :data="tableData" v-loading="loading" border>
        <el-table-column prop="contractCode" label="合同编号" width="180" show-overflow-tooltip />
        <el-table-column prop="contractName" label="合同名称" min-width="200" show-overflow-tooltip />
        <el-table-column prop="projectName" label="关联项目" min-width="180" show-overflow-tooltip />
        <el-table-column prop="contractAmount" label="金额" width="150" align="right">
          <template #default="{ row }">{{ formatMoney(row.contractAmount) }}</template>
        </el-table-column>
        <el-table-column prop="signingDate" label="签约日期" width="130" />
        <el-table-column prop="status" label="状态" width="100" align="center" />
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
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { getOtherExpenseContractArchive } from '@/api/archive'

const loading = ref(false)
const tableData = ref<any[]>([])
const total = ref(0)

const queryParams = ref({
  page: 1,
  size: 10,
  keyword: undefined as string | undefined
})

function formatMoney(val: number) {
  if (!val && val !== 0) return '-'
  return val.toLocaleString('zh-CN', { minimumFractionDigits: 2 })
}

async function loadData() {
  loading.value = true
  try {
    const res: any = await getOtherExpenseContractArchive(queryParams.value)
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
  queryParams.value = { page: 1, size: 10, keyword: undefined }
  loadData()
}

onMounted(() => {
  loadData()
})
</script>

<style scoped>
.archive-container {
  padding: 16px;
}
.pagination-wrap {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
</style>
