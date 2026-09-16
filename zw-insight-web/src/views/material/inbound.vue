<template>
  <div class="material-inbound-container">
    <el-card shadow="never">
      <el-form :model="queryParams" inline>
        <el-form-item label="项目">
          <ProjectSelector v-model="queryParams.projectId" width="200px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <div class="table-toolbar">
        <el-button v-if="selectedRows.length > 0" type="danger" @click="handleBatchDelete" :disabled="selectedRows.length === 0">
          <el-icon><Delete /></el-icon>批量删除 (已选 {{ selectedRows.length }})
        </el-button>
        <el-button type="primary" @click="handleAdd">新增入库单</el-button>
        <ColumnSettingPopover
          :columns="inboundColumns"
          :visible="columnVisible"
          @update:visible="setVisible"
          @reset="resetColumns"
        />
      </div>

      <!-- 首屏骨架（感知性能 S1.4）：无数据且加载中显示骨架；翻页保留 v-loading 遮罩 -->
      <el-skeleton v-if="loading && !tableData.length" :rows="5" animated />
      <el-table 
        v-else
        :data="tableData" 
        v-loading="loading" 
        border 
        @selection-change="handleSelectionChange"
        @cell-contextmenu="showContextMenu"
      >
        <el-table-column type="selection" width="55" align="center" />
        <el-table-column v-if="columnVisible[0]" prop="inboundCode" label="入库单号" width="170" />
        <el-table-column v-if="columnVisible[1]" prop="inboundDate" label="入库日期" width="120" />
        <el-table-column v-if="columnVisible[2]" prop="totalAmount" label="入库总金额(元)" width="150" align="right">
          <template #default="{ row }">{{ Number(row.totalAmount || 0).toLocaleString() }}</template>
        </el-table-column>
        <el-table-column v-if="columnVisible[3]" prop="directOutbound" label="直接出库" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.directOutbound === 1 ? 'warning' : 'info'" size="small">{{ row.directOutbound === 1 ? '是' : '否' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column v-if="columnVisible[4]" prop="status" label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 'APPROVED' ? 'success' : 'info'" size="small">{{ row.status === 'APPROVED' ? '已审批' : '草稿' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleEdit(row)">编辑</el-button>
            <el-button v-if="row.status === 'DRAFT'" link type="success" @click="handleSubmit(row)">提交</el-button>
            <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- P1 Context Menu -->
      <el-dropdown 
        v-if="contextMenuTarget && contextMenuVisible"
        :show-timeout="100"
        trigger="manual"
        :style="{ left: contextMenuPosition.x + 'px', top: contextMenuPosition.y + 'px' }"
        @command="handleRowAction"
        @visible-change="hideContextMenu"
      >
        <span style="display: none"></span>
        <el-dropdown-menu>
          <el-dropdown-item command="edit">📝 编辑</el-dropdown-item>
          <el-dropdown-item command="submit" v-if="contextMenuTarget.status === 'DRAFT'">✅ 提交</el-dropdown-item>
          <el-dropdown-item command="duplicate">📄 复制</el-dropdown-item>
          <el-dropdown-item command="delete" divided type="danger">🗑️ 删除</el-dropdown-item>
        </el-dropdown-menu>
      </el-dropdown>

      <div class="pagination-wrap">
        <el-pagination v-model:current-page="queryParams.page" v-model:page-size="queryParams.size" :page-sizes="[10, 20, 50]" :total="total" layout="total, sizes, prev, pager, next, jumper" @size-change="loadData" @current-change="loadData" />
      </div>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑入库单' : '新增入库单'" width="820px" destroy-on-close>
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="90px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="项目" prop="projectId">
              <ProjectSelector v-model="formData.projectId" @change="handleProjectChange" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="采购合同">
              <PurchaseContractSelector v-model="formData.contractId" :project-id="formData.projectId" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="入库日期" prop="inboundDate">
              <el-date-picker v-model="formData.inboundDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="直接出库">
              <el-switch v-model="formData.directOutbound" :active-value="1" :inactive-value="0" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-divider content-position="left">入库明细</el-divider>
        <div class="detail-toolbar">
          <el-button type="primary" size="small" @click="handleAddDetail">添加明细</el-button>
        </div>
        <el-table :data="formData.details" border style="width: 100%; margin-top: var(--zw-space-sm-md)">
          <el-table-column label="材料名称" min-width="160">
            <template #default="{ row }"><el-input v-model="row.materialName" placeholder="材料名称" /></template>
          </el-table-column>
          <el-table-column label="规格" width="120">
            <template #default="{ row }"><el-input v-model="row.specification" placeholder="规格" /></template>
          </el-table-column>
          <el-table-column label="单位" width="80">
            <template #default="{ row }"><el-input v-model="row.unit" placeholder="单位" /></template>
          </el-table-column>
          <el-table-column label="数量" width="120">
            <template #default="{ row }"><el-input-number v-model="row.quantity" :min="0" :precision="2" controls-position="right" style="width: 100%" /></template>
          </el-table-column>
          <el-table-column label="单价" width="120">
            <template #default="{ row }"><el-input-number v-model="row.unitPrice" :min="0" :precision="2" controls-position="right" style="width: 100%" /></template>
          </el-table-column>
          <el-table-column label="金额" width="110" align="right">
            <template #default="{ row }">{{ ((row.quantity || 0) * (row.unitPrice || 0)).toFixed(2) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="70" align="center">
            <template #default="{ $index }"><el-button link type="danger" @click="handleDeleteDetail($index)">删除</el-button></template>
          </el-table-column>
        </el-table>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleFormSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, nextTick } from 'vue'
import { ElMessage, ElMessageBox, ElPopover } from 'element-plus'
import type { FormInstance } from 'element-plus'
import { getMaterialInboundPage, getMaterialInboundDetail, createMaterialInbound, updateMaterialInbound, deleteMaterialInbound, submitMaterialInbound } from '@/api/material'
import ProjectSelector from '@/components/ProjectSelector.vue'
import PurchaseContractSelector from '@/components/PurchaseContractSelector.vue'
import ColumnSettingPopover from '@/components/ColumnSettingPopover.vue'
import { useColumnSetting } from '@/composables/useColumnSetting'

// 列显隐配置（S2.1）：按 material-inbound-table 持久化 localStorage
const inboundColumns = [
  { key: 'inboundCode', label: '入库单号' },
  { key: 'inboundDate', label: '入库日期' },
  { key: 'totalAmount', label: '入库总金额' },
  { key: 'directOutbound', label: '直接出库' },
  { key: 'status', label: '状态' },
]
const { visible: columnVisible, setVisible, reset: resetColumns } = useColumnSetting('material-inbound-table', inboundColumns)

interface InboundDetail {
  materialName: string
  specification: string
  unit: string
  quantity: number
  unitPrice: number
}

const formRef = ref<FormInstance>()
const loading = ref(false)
const tableData = ref<any[]>([])
const total = ref(0)
const dialogVisible = ref(false)
const submitLoading = ref(false)
const isEdit = ref(false)
const selectedRows = ref<any[]>([]) // P1: 选中行
const contextMenuVisible = ref(false) // P1: 显示上下文菜单
const contextMenuTarget = ref<any>(null) // P1: 目标行
const contextMenuPosition = ref({ x: 0, y: 0 }) // P1: 菜单位置

const queryParams = ref({ page: 1, size: 10, projectId: undefined as number | undefined })
const formData = ref({
  id: undefined as number | undefined,
  projectId: undefined as number | undefined,
  contractId: undefined as number | undefined,
  inboundDate: '',
  directOutbound: 0,
  details: [] as InboundDetail[]
})
const formRules = {
  projectId: [{ required: true, message: '请选择项目', trigger: 'change' }],
  inboundDate: [{ required: true, message: '请选择入库日期', trigger: 'change' }]
}

// P1 Keyboard Shortcuts for Table Navigation
function handleGlobalKeydown(event: KeyboardEvent) {
  if (dialogVisible.value) return
  
  if ((event.ctrlKey || event.metaKey) && event.key === 'Enter') {
    event.preventDefault()
    handleAdd()
  } else if (event.key === 'Escape') {
    dialogVisible.value = false
    selectedRows.value = []
  }
}

async function loadData() {
  loading.value = true
  try {
    const res: any = await getMaterialInboundPage(queryParams.value)
    tableData.value = res.data?.records || []
    total.value = res.data?.total || 0
  } finally {
    loading.value = false
  }
}
function handleSearch() { queryParams.value.page = 1; loadData() }
function handleReset() { queryParams.value = { page: 1, size: 10, projectId: undefined }; loadData() }
function newDetail(): InboundDetail { return { materialName: '', specification: '', unit: '', quantity: 0, unitPrice: 0 } }
function handleAdd() {
  isEdit.value = false
  formData.value = { id: undefined, projectId: undefined, contractId: undefined, inboundDate: '', directOutbound: 0, details: [newDetail()] }
  dialogVisible.value = true
}
async function handleEdit(row: any) {
  isEdit.value = true
  formData.value = { id: row.id, projectId: row.projectId, contractId: row.contractId, inboundDate: row.inboundDate || '', directOutbound: row.directOutbound ?? 0, details: [] }
  dialogVisible.value = true
  // 回显明细
  const res: any = await getMaterialInboundDetail(row.id)
  const detail = res.data || {}
  formData.value.details = (detail.details || []).map((d: any) => ({
    materialName: d.materialName || '',
    specification: d.specification || '',
    unit: d.unit || '',
    quantity: Number(d.quantity ?? 0),
    unitPrice: Number(d.unitPrice ?? 0)
  }))
}
function handleProjectChange() { formData.value.contractId = undefined }
function handleAddDetail() { formData.value.details.push(newDetail()) }
function handleDeleteDetail(index: number) { formData.value.details.splice(index, 1) }
async function handleFormSubmit() {
  await formRef.value?.validate()
  // 2026-08-14 P0 修复盲点 10：原守卫仅新增时生效（!isEdit 前缀），编辑态可删光明细保存
  if (formData.value.details.filter(d => d.materialName).length === 0) {
    ElMessage.warning('请至少填写一条入库明细')
    return
  }
  submitLoading.value = true
  try {
    const payload = { ...formData.value, details: formData.value.details.filter(d => d.materialName) }
    isEdit.value ? await updateMaterialInbound(payload) : await createMaterialInbound(payload)
    ElMessage.success(isEdit.value ? '更新成功' : '新增成功')
    dialogVisible.value = false
    loadData()
  } finally {
    submitLoading.value = false
  }
}
async function handleSubmit(row: any) {
  await ElMessageBox.confirm('提交后将更新库存与合同累计入库，确定提交？', '提示', { type: 'warning' })
  await submitMaterialInbound(row.id)
  ElMessage.success('提交成功')
  loadData()
}
async function handleDelete(row: any) {
  await ElMessageBox.confirm('确定要删除吗？', '提示', { type: 'warning' })
  await deleteMaterialInbound(row.id)
  ElMessage.success('删除成功')
  loadData()
}

// P1 Batch Operations Functions
function handleSelectionChange(selection: any[]) {
  selectedRows.value = selection
}

async function handleBatchDelete() {
  if (selectedRows.value.length === 0) return
  
  await ElMessageBox.confirm(`确定要批量删除选中的 ${selectedRows.value.length} 条记录吗？`, '提示', { type: 'warning' })
  
  const taskIds = selectedRows.value.map(row => row.id)
  for (const id of taskIds) {
    try {
      await deleteMaterialInbound(id)
    } catch (e) {
      ElMessage.error(`删除 ${id} 失败`)
    }
  }
  
  ElMessage.success('批量删除成功')
  selectedRows.value = []
  loadData()
}

// P1 Context Menu Functions
function showContextMenu(event: MouseEvent, row: any) {
  event.preventDefault()
  contextMenuTarget.value = row
  contextMenuPosition.value = { x: event.clientX, y: event.clientY }
  contextMenuVisible.value = true
}

function hideContextMenu() {
  contextMenuVisible.value = false
}

async function handleRowAction(action: string) {
  if (!contextMenuTarget.value) return
  
  switch (action) {
    case 'edit':
      hideContextMenu()
      await handleEdit(contextMenuTarget.value)
      break
    case 'delete':
      hideContextMenu()
      await handleDelete(contextMenuTarget.value)
      break
    case 'submit':
      hideContextMenu()
      await handleSubmit(contextMenuTarget.value)
      break
    case 'duplicate':
      hideContextMenu()
      // 复制逻辑略，需要调用复制 API
      ElMessage.info('复制功能开发中')
      break
  }
}

onMounted(() => { 
  loadData()
  document.addEventListener('keydown', handleGlobalKeydown)
})

// onUnmounted(() => {
//   document.removeEventListener('keydown', handleGlobalKeydown)
// })
</script>

<style scoped>
.material-inbound-container { padding: var(--zw-space-md); }
.table-toolbar { margin-bottom: var(--zw-space-md); display: flex; gap: var(--zw-space-sm-md); }
.pagination-wrap { margin-top: var(--zw-space-md); display: flex; justify-content: flex-end; }
.detail-toolbar { display: flex; align-items: center; gap: var(--zw-space-sm-md); }

/* P1 Context Menu Styles */
.el-dropdown { cursor: pointer; }

/* P1 Keyboard Navigation Styles */
.el-table__row.keyboard-focused {
  background-color: var(--zw-info-light) !important;
  outline: 2px solid var(--el-color-primary) !important;
  outline-offset: -2px;
}
</style>
