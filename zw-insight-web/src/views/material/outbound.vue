<template>
  <div class="material-outbound-container">
    <el-card shadow="never">
      <el-form :model="queryParams" inline>
        <el-form-item label="项目">
          <el-select v-model="queryParams.projectId" placeholder="全部项目" filterable clearable style="width: 200px">
            <el-option v-for="p in projectOptions" :key="p.id" :label="p.projectName" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="queryParams.outboundType" placeholder="全部" clearable style="width: 120px">
            <el-option label="领料" value="PICK" />
            <el-option label="退货" value="RETURN" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <div class="table-toolbar">
        <el-button type="primary" @click="handleAdd">新增出库单</el-button>
      </div>

      <el-table :data="tableData" v-loading="loading" border>
        <el-table-column type="expand">
          <template #default="{ row }">
            <el-table :data="row.details || []" size="small" border style="margin: 8px 24px">
              <el-table-column prop="materialName" label="材料名称" min-width="140" />
              <el-table-column prop="specification" label="规格型号" width="120" />
              <el-table-column prop="unit" label="单位" width="70" align="center" />
              <el-table-column prop="quantity" label="数量" width="100" align="right" />
              <el-table-column prop="unitPrice" label="单价(元)" width="110" align="right" />
            </el-table>
          </template>
        </el-table-column>
        <el-table-column prop="projectName" label="项目" min-width="160" show-overflow-tooltip />
        <el-table-column prop="outboundType" label="出库类型" width="100" align="center">
          <template #default="{ row }">{{ row.outboundType === 'RETURN' ? '退货' : '领料' }}</template>
        </el-table-column>
        <el-table-column prop="operatorName" label="操作人" width="110" />
        <el-table-column prop="outboundDate" label="出库日期" width="120" />
        <el-table-column label="状态" width="90" align="center">
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

      <div class="pagination-wrap">
        <el-pagination v-model:current-page="queryParams.pageNum" v-model:page-size="queryParams.pageSize" :page-sizes="[10, 20, 50]" :total="total" layout="total, sizes, prev, pager, next, jumper" @size-change="loadData" @current-change="loadData" />
      </div>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑出库单' : '新增出库单'" width="820px" destroy-on-close>
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="90px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="项目" prop="projectId">
              <el-select v-model="formData.projectId" placeholder="请选择项目" filterable style="width: 100%">
                <el-option v-for="p in projectOptions" :key="p.id" :label="p.projectName" :value="p.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="出库类型" prop="outboundType">
              <el-select v-model="formData.outboundType" style="width: 100%">
                <el-option label="领料" value="PICK" />
                <el-option label="退货" value="RETURN" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="操作人"><el-input v-model="formData.operatorName" /></el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="出库日期"><el-date-picker v-model="formData.outboundDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item>
          </el-col>
        </el-row>

        <el-divider content-position="left">出库明细</el-divider>
        <el-button type="primary" plain size="small" @click="handleAddDetail" style="margin-bottom: 8px">添加明细</el-button>
        <el-table :data="formData.details" size="small" border>
          <el-table-column label="材料名称" min-width="150">
            <template #default="{ row }"><el-input v-model="row.materialName" placeholder="材料名称" /></template>
          </el-table-column>
          <el-table-column label="规格型号" width="130">
            <template #default="{ row }"><el-input v-model="row.specification" /></template>
          </el-table-column>
          <el-table-column label="单位" width="80">
            <template #default="{ row }"><el-input v-model="row.unit" /></template>
          </el-table-column>
          <el-table-column label="数量" width="120">
            <template #default="{ row }"><el-input-number v-model="row.quantity" :min="0" :precision="2" controls-position="right" style="width: 100%" /></template>
          </el-table-column>
          <el-table-column label="单价(元)" width="130">
            <template #default="{ row }"><el-input-number v-model="row.unitPrice" :min="0" :precision="2" controls-position="right" style="width: 100%" /></template>
          </el-table-column>
          <el-table-column label="操作" width="70" align="center">
            <template #default="{ $index }"><el-button link type="danger" @click="handleRemoveDetail($index)">删除</el-button></template>
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
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance } from 'element-plus'
import { getMaterialOutboundPage, getMaterialOutboundDetail, createMaterialOutbound, updateMaterialOutbound, deleteMaterialOutbound, submitMaterialOutbound } from '@/api/material'
import { getProjectPage } from '@/api/project'

const formRef = ref<FormInstance>()
const loading = ref(false)
const tableData = ref<any[]>([])
const total = ref(0)
const dialogVisible = ref(false)
const submitLoading = ref(false)
const isEdit = ref(false)
const projectOptions = ref<any[]>([])

const queryParams = ref({ pageNum: 1, pageSize: 10, projectId: undefined as number | undefined, outboundType: '' })
const defaultForm = () => ({ id: undefined as number | undefined, projectId: undefined as number | undefined, outboundType: 'PICK', operatorName: '', outboundDate: '', details: [] as any[] })
const formData = ref(defaultForm())
const formRules = {
  projectId: [{ required: true, message: '请选择项目', trigger: 'change' }],
  outboundType: [{ required: true, message: '请选择出库类型', trigger: 'change' }]
}

async function loadData() {
  loading.value = true
  try {
    const res: any = await getMaterialOutboundPage(queryParams.value)
    tableData.value = res.data?.records || []
    total.value = res.data?.total || 0
  } finally {
    loading.value = false
  }
}
async function loadProjectOptions() {
  const res: any = await getProjectPage({ pageNum: 1, pageSize: 200 } as any)
  projectOptions.value = res.data?.records || []
}
function handleSearch() { queryParams.value.pageNum = 1; loadData() }
function handleReset() { queryParams.value = { pageNum: 1, pageSize: 10, projectId: undefined, outboundType: '' }; loadData() }
function handleAdd() { isEdit.value = false; formData.value = defaultForm(); dialogVisible.value = true }
async function handleEdit(row: any) {
  isEdit.value = true
  const res: any = await getMaterialOutboundDetail(row.id)
  const data = res.data || {}
  formData.value = {
    id: data.id,
    projectId: data.projectId,
    outboundType: data.outboundType || 'PICK',
    operatorName: data.operatorName || '',
    outboundDate: data.outboundDate || '',
    details: data.details || []
  }
  dialogVisible.value = true
}
function handleAddDetail() { formData.value.details.push({ materialName: '', specification: '', unit: '', quantity: 0, unitPrice: 0 }) }
function handleRemoveDetail(index: number) { formData.value.details.splice(index, 1) }
async function handleFormSubmit() {
  await formRef.value?.validate()
  if (!formData.value.details.length) { ElMessage.warning('请至少添加一条出库明细'); return }
  submitLoading.value = true
  try {
    isEdit.value ? await updateMaterialOutbound(formData.value) : await createMaterialOutbound(formData.value)
    ElMessage.success(isEdit.value ? '更新成功' : '新增成功')
    dialogVisible.value = false
    loadData()
  } finally {
    submitLoading.value = false
  }
}
async function handleSubmit(row: any) {
  await ElMessageBox.confirm('确定要提交此出库单吗？', '提示', { type: 'warning' })
  await submitMaterialOutbound(row.id)
  ElMessage.success('提交成功')
  loadData()
}
async function handleDelete(row: any) {
  await ElMessageBox.confirm('确定要删除吗？', '提示', { type: 'warning' })
  await deleteMaterialOutbound(row.id)
  ElMessage.success('删除成功')
  loadData()
}
onMounted(() => { loadData(); loadProjectOptions() })
</script>

<style scoped>
.material-outbound-container { padding: 16px; }
.table-toolbar { margin-bottom: 16px; }
.pagination-wrap { margin-top: 16px; display: flex; justify-content: flex-end; }
</style>
