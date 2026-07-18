<template>
  <div class="archive-container">
    <el-card shadow="never">
      <el-alert
        title="办公用品档案为只读聚合视图，统计库存/入库/领用，此处仅供查询"
        type="info"
        show-icon
        :closable="false"
        style="margin-bottom: 16px"
      />

      <el-form :model="queryParams" inline>
        <el-form-item label="关键字">
          <el-input v-model="queryParams.keyword" placeholder="用品名称" clearable style="width: 200px" />
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
        <el-table-column prop="supplyName" label="用品名称" min-width="200" show-overflow-tooltip />
        <el-table-column prop="currentStock" label="当前库存" width="130" align="right" />
        <el-table-column prop="totalInbound" label="累计入库" width="130" align="right" />
        <el-table-column prop="totalIssued" label="累计领用" width="130" align="right" />
        <el-table-column prop="lastInboundDate" label="最近入库日期" width="150" />
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
import { getOfficeSupplyArchive } from '@/api/archive'

const loading = ref(false)
const tableData = ref<any[]>([])
const total = ref(0)

const queryParams = ref({
  page: 1,
  size: 10,
  keyword: undefined as string | undefined
})

async function loadData() {
  loading.value = true
  try {
    const res: any = await getOfficeSupplyArchive(queryParams.value)
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
