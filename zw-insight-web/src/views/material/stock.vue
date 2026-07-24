<template>
  <div class="material-stock-container">
    <el-card shadow="never">
      <el-form :model="queryParams" inline>
        <el-form-item label="材料名称">
          <el-input v-model="queryParams.materialName" placeholder="材料名称" clearable style="width: 180px" />
        </el-form-item>
        <el-form-item label="项目">
          <el-input v-model="queryParams.projectName" placeholder="所属项目" clearable style="width: 180px" />
        </el-form-item>
        <el-form-item label="库存预警">
          <el-select v-model="queryParams.warning" placeholder="全部" clearable style="width: 120px">
            <el-option label="正常" value="NORMAL" />
            <el-option label="不足" value="LOW" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="tableData" v-loading="loading" border>
        <el-table-column prop="materialName" label="材料名称" min-width="160" show-overflow-tooltip />
        <el-table-column prop="specification" label="规格型号" width="130" />
        <el-table-column prop="unit" label="单位" width="70" align="center" />
        <el-table-column prop="stockQuantity" label="当前库存" width="110" align="right" />
        <el-table-column prop="minStock" label="最低库存" width="100" align="right" />
        <el-table-column prop="projectName" label="所属项目" min-width="160" show-overflow-tooltip />
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.minStock != null && row.stockQuantity <= row.minStock ? 'danger' : 'success'" size="small">
              {{ row.minStock != null && row.stockQuantity <= row.minStock ? '库存不足' : '正常' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="updatedAt" label="更新时间" width="160" />
      </el-table>

      <div class="pagination-wrap">
        <el-pagination v-model:current-page="queryParams.pageNum" v-model:page-size="queryParams.pageSize" :page-sizes="[10, 20, 50]" :total="total" layout="total, sizes, prev, pager, next, jumper" @size-change="loadData" @current-change="loadData" />
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { getMaterialStockPage } from '@/api/material'

const loading = ref(false)
const tableData = ref<any[]>([])
const total = ref(0)

const queryParams = ref({ pageNum: 1, pageSize: 10, materialName: '', projectName: '', warning: '' })

async function loadData() { loading.value = true; try { const res: any = await getMaterialStockPage(queryParams.value); tableData.value = res.data?.records || []; total.value = res.data?.total || 0 } finally { loading.value = false } }
function handleSearch() { queryParams.value.pageNum = 1; loadData() }
function handleReset() { queryParams.value = { pageNum: 1, pageSize: 10, materialName: '', projectName: '', warning: '' }; loadData() }
onMounted(() => { loadData() })
</script>

<style scoped>
.material-stock-container { padding: 16px; }
.pagination-wrap { margin-top: 16px; display: flex; justify-content: flex-end; }
</style>
