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
          <el-button @click="handleExport">导出</el-button>
          <el-button @click="openWarningConfig">预警配置</el-button>
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
        <el-pagination v-model:current-page="queryParams.page" v-model:page-size="queryParams.size" :page-sizes="[10, 20, 50]" :total="total" layout="total, sizes, prev, pager, next, jumper" @size-change="loadData" @current-change="loadData" />
      </div>
    </el-card>

    <async-export-dialog
      v-model:visible="exportVisible"
      module-code="STOCK"
      :params="exportParams"
    />

    <!-- 预警配置弹窗：维护安全库存阈值（项目+材料维度，项目留空=全局默认） -->
    <el-dialog v-model="configVisible" title="库存预警配置" width="760px" destroy-on-close>
      <el-form :model="configForm" inline>
        <el-form-item label="材料">
          <el-select
            v-model="configForm.materialId"
            placeholder="搜索并选择材料"
            filterable
            remote
            clearable
            :remote-method="searchMaterialDict"
            style="width: 220px"
            @change="handleMaterialChange"
          >
            <el-option v-for="m in materialOptions" :key="m.id" :label="m.materialName" :value="m.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="项目">
          <el-select
            v-model="configForm.projectId"
            placeholder="全局默认（不限项目）"
            filterable
            remote
            clearable
            :remote-method="searchProject"
            style="width: 220px"
          >
            <el-option v-for="p in projectOptions" :key="p.id" :label="p.projectName" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="安全库存">
          <el-input-number v-model="configForm.safetyStock" :min="0" :precision="2" style="width: 140px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="configSaving" @click="handleConfigSave">保存</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="configList" v-loading="configLoading" border size="small">
        <el-table-column prop="materialName" label="材料名称" min-width="140" show-overflow-tooltip />
        <el-table-column label="适用项目" min-width="160">
          <template #default="{ row }">{{ row.projectId ? (row.projectName || ('项目#' + row.projectId)) : '全局默认' }}</template>
        </el-table-column>
        <el-table-column prop="safetyStock" label="安全库存" width="110" align="right" />
        <el-table-column label="启用" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.enabled === 1 ? 'success' : 'info'" size="small">{{ row.enabled === 1 ? '启用' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="80" align="center">
          <template #default="{ row }">
            <el-button link type="danger" size="small" @click="handleConfigDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-wrap">
        <el-pagination v-model:current-page="configPage.page" v-model:page-size="configPage.size" :page-sizes="[10, 20, 50]" :total="configTotal" layout="total, prev, pager, next" @size-change="loadConfigs" @current-change="loadConfigs" />
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getMaterialStockPage, getStockWarningConfigPage, saveStockWarningConfig, deleteStockWarningConfig } from '@/api/material'
import { getMaterialDictPage } from '@/api/basedata'
import { getProjectList } from '@/api/project'
import AsyncExportDialog from '@/components/AsyncExportDialog.vue'

const loading = ref(false)
const tableData = ref<any[]>([])
const total = ref(0)
const exportVisible = ref(false)

const queryParams = ref({ page: 1, size: 10, materialName: '', projectName: '', warning: '' })

// 导出口径与当前筛选一致；空串参数不下发，后端按全量处理
const exportParams = ref<Record<string, string>>({})

async function loadData() { loading.value = true; try { const res: any = await getMaterialStockPage(queryParams.value); tableData.value = res.data?.records || []; total.value = res.data?.total || 0 } finally { loading.value = false } }
function handleSearch() { queryParams.value.page = 1; loadData() }
function handleReset() { queryParams.value = { page: 1, size: 10, materialName: '', projectName: '', warning: '' }; loadData() }
function handleExport() {
  const params: Record<string, string> = {}
  if (queryParams.value.materialName) params.materialName = queryParams.value.materialName
  if (queryParams.value.projectName) params.projectName = queryParams.value.projectName
  if (queryParams.value.warning) params.warning = queryParams.value.warning
  exportParams.value = params
  exportVisible.value = true
}
onMounted(() => { loadData() })

// ================= 预警配置 =================
const configVisible = ref(false)
const configLoading = ref(false)
const configSaving = ref(false)
const configList = ref<any[]>([])
const configTotal = ref(0)
const configPage = ref({ page: 1, size: 10 })
const materialOptions = ref<any[]>([])
const projectOptions = ref<any[]>([])
const configForm = ref<{ materialId: number | undefined; materialName: string; projectId: number | undefined; safetyStock: number }>({
  materialId: undefined, materialName: '', projectId: undefined, safetyStock: 0
})

function openWarningConfig() {
  configVisible.value = true
  configPage.value.page = 1
  loadConfigs()
  searchMaterialDict('')
  searchProject('')
}

async function loadConfigs() {
  configLoading.value = true
  try {
    const res: any = await getStockWarningConfigPage(configPage.value)
    configList.value = res.data?.records || []
    configTotal.value = res.data?.total || 0
  } finally {
    configLoading.value = false
  }
}

async function searchMaterialDict(query: string) {
  const res: any = await getMaterialDictPage({ page: 1, size: 20, materialName: query || undefined })
  materialOptions.value = res.data?.records || []
}

async function searchProject(query: string) {
  const res: any = await getProjectList({ projectName: query })
  projectOptions.value = res.data || []
}

function handleMaterialChange(id: number) {
  const m = materialOptions.value.find(x => x.id === id)
  configForm.value.materialName = m?.materialName || ''
}

async function handleConfigSave() {
  if (!configForm.value.materialId) {
    ElMessage.warning('请选择材料')
    return
  }
  if (configForm.value.safetyStock == null || configForm.value.safetyStock < 0) {
    ElMessage.warning('安全库存必须为非负数')
    return
  }
  configSaving.value = true
  try {
    await saveStockWarningConfig({
      projectId: configForm.value.projectId ?? null,
      materialId: configForm.value.materialId,
      materialName: configForm.value.materialName,
      safetyStock: configForm.value.safetyStock,
      enabled: 1
    })
    ElMessage.success('保存成功')
    loadConfigs()
    loadData() // 刷新库存列表的最低库存列
  } finally {
    configSaving.value = false
  }
}

async function handleConfigDelete(row: any) {
  await ElMessageBox.confirm(`确定删除【${row.materialName}】的安全库存配置？`, '提示', { type: 'warning' })
  await deleteStockWarningConfig(row.id)
  ElMessage.success('删除成功')
  loadConfigs()
  loadData()
}
</script>

<style scoped>
.material-stock-container { padding: 16px; }
.pagination-wrap { margin-top: 16px; display: flex; justify-content: flex-end; }
</style>
