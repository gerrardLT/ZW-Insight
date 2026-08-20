<template>
  <div class="output-report-container">
    <!-- 查询区 -->
    <el-card shadow="never" class="filter-card">
      <el-form :inline="true" :model="queryParams">
        <el-form-item label="项目">
          <ProjectSelector v-model="queryParams.projectId" style="width: 220px" @change="handleContractReload" />
        </el-form-item>
        <el-form-item label="合同">
          <el-select v-model="queryParams.contractId" placeholder="全部" clearable filterable style="width: 220px">
            <el-option v-for="c in contractOptions" :key="c.id" :label="c.contractName || c.contractCode" :value="c.id as number" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
          <el-button type="success" @click="openCreateDialog">新增产值上报</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 列表 -->
    <el-card shadow="never">
      <el-table :data="tableData" v-loading="loading" border>
        <el-table-column prop="reportPeriod" label="报告期间" width="120" />
        <el-table-column prop="currentOutput" label="本期产值" width="140" align="right">
          <template #default="{ row }">{{ formatMoney(row.currentOutput) }}</template>
        </el-table-column>
        <el-table-column prop="cumulativeOutput" label="累计产值" width="140" align="right">
          <template #default="{ row }">{{ formatMoney(row.cumulativeOutput) }}</template>
        </el-table-column>
        <el-table-column prop="confirmDate" label="确认日期" width="120" />
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)" size="small">{{ getStatusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 'DRAFT' || row.status === 'REJECTED'" link type="success" @click="handleSubmit(row)">提交</el-button>
            <el-button v-if="row.status === 'DRAFT' || row.status === 'REJECTED'" link type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="queryParams.pageNum"
          v-model:page-size="queryParams.pageSize"
          :page-sizes="[10, 20, 50]"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="loadData"
          @current-change="loadData"
        />
      </div>
    </el-card>

    <!-- 新增产值上报对话框 -->
    <el-dialog v-model="createDialogVisible" title="新增产值上报" width="820px" destroy-on-close>
      <el-form ref="createFormRef" :model="createForm" :rules="createRules" label-width="100px">
        <el-form-item label="项目" prop="projectId">
          <ProjectSelector v-model="createForm.projectId" style="width: 260px" @change="handleFormProjectChange" />
        </el-form-item>
        <el-form-item label="施工合同" prop="contractId">
          <el-select v-model="createForm.contractId" placeholder="请选择合同" filterable style="width: 260px" @change="handleFormContractChange">
            <el-option v-for="c in formContractOptions" :key="c.id" :label="c.contractName || c.contractCode" :value="c.id as number" />
          </el-select>
        </el-form-item>
        <el-form-item label="报告期间" prop="reportPeriod">
          <el-date-picker v-model="createForm.reportPeriod" type="month" value-format="YYYY-MM" placeholder="选择月份" style="width: 260px" />
        </el-form-item>
        <el-form-item label="确认日期" prop="confirmDate">
          <el-date-picker v-model="createForm.confirmDate" type="date" value-format="YYYY-MM-DD" placeholder="选择日期" style="width: 260px" />
        </el-form-item>

        <!-- 填报方式：清单行 or 纯金额 -->
        <el-form-item label="填报方式">
          <el-radio-group v-model="fillMode" @change="handleFillModeChange">
            <el-radio-button value="boq" :disabled="boqItems.length === 0">按清单行</el-radio-button>
            <el-radio-button value="amount">纯金额</el-radio-button>
          </el-radio-group>
          <span v-if="boqItems.length === 0 && createForm.contractId" class="form-tip" style="margin-left: 12px">该合同暂无工程量清单，仅可按纯金额填报</span>
        </el-form-item>

        <!-- 清单行填报 -->
        <el-table v-if="fillMode === 'boq'" :data="boqItems" border max-height="320" size="small">
          <el-table-column prop="itemCode" label="项目编码" width="110" show-overflow-tooltip />
          <el-table-column prop="itemName" label="项目名称" min-width="160" show-overflow-tooltip />
          <el-table-column prop="unit" label="单位" width="70" align="center" />
          <el-table-column prop="unitPrice" label="综合单价" width="110" align="right">
            <template #default="{ row }">{{ formatMoney(row.unitPrice) }}</template>
          </el-table-column>
          <el-table-column prop="quantity" label="清单量" width="90" align="right" />
          <el-table-column label="本期完成量" width="130" align="center">
            <template #default="scope">
              <el-input-number v-model="(scope.row as BoqRow).reportQuantity" :min="0" :precision="2" :controls="false" size="small" style="width: 110px" @change="recalcFromBoq" />
            </template>
          </el-table-column>
          <el-table-column label="本期金额" width="120" align="right">
            <template #default="scope">{{ formatMoney(lineAmount(scope.row as BoqRow)) }}</template>
          </el-table-column>
        </el-table>

        <!-- 纯金额填报 -->
        <el-form-item v-else label="本期产值" prop="currentOutput">
          <el-input-number v-model="createForm.currentOutput" :min="0" :precision="2" :controls="false" style="width: 260px" />
        </el-form-item>

        <div v-if="fillMode === 'boq'" class="total-line">
          本期产值合计：<span class="amount">{{ formatMoney(createForm.currentOutput) }}</span>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="createDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="createLoading" @click="handleCreateSubmit">保存草稿</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import ProjectSelector from '@/components/ProjectSelector.vue'
import { getOutputReportPage, createOutputReport, submitOutputReport, deleteOutputReport, getContractPage } from '@/api/contract'
import { getBoqFlat } from '@/api/boq'
import type { OutputReportDetail } from '@/types/contract'

interface BoqRow {
  id: number
  itemCode?: string
  itemName?: string
  unit?: string
  unitPrice?: number
  quantity?: number
  reportQuantity?: number
}

const loading = ref(false)
const total = ref(0)
const tableData = ref<any[]>([])
const contractOptions = ref<any[]>([])

const queryParams = reactive({
  pageNum: 1,
  pageSize: 10,
  projectId: undefined as number | undefined,
  contractId: undefined as number | undefined
})

// ================= 列表 =================
async function loadData() {
  loading.value = true
  try {
    const res: any = await getOutputReportPage({
      // 后端 Controller 契约为 page/size（@RequestParam 硬编码），直传避免口径失配
      page: queryParams.pageNum,
      size: queryParams.pageSize,
      projectId: queryParams.projectId,
      contractId: queryParams.contractId
    })
    tableData.value = res.data?.records || []
    total.value = res.data?.total || 0
  } finally {
    loading.value = false
  }
}

async function loadContractOptions(projectId?: number, target: 'query' | 'form' = 'query') {
  if (!projectId) {
    if (target === 'query') contractOptions.value = []
    else formContractOptions.value = []
    return
  }
  const res: any = await getContractPage({ pageNum: 1, pageSize: 100, projectId, status: 'EFFECTIVE' })
  const list = res.data?.records || []
  if (target === 'query') contractOptions.value = list
  else formContractOptions.value = list
}

function handleContractReload() {
  queryParams.contractId = undefined
  loadContractOptions(queryParams.projectId, 'query')
}

function handleSearch() {
  queryParams.pageNum = 1
  loadData()
}

function handleReset() {
  queryParams.projectId = undefined
  queryParams.contractId = undefined
  queryParams.pageNum = 1
  contractOptions.value = []
  loadData()
}

async function handleSubmit(row: any) {
  await ElMessageBox.confirm('确定要提交该产值上报吗？', '提示', { type: 'warning' })
  await submitOutputReport(row.id)
  ElMessage.success('已提交审批，审批通过后生效')
  loadData()
}

async function handleDelete(row: any) {
  await ElMessageBox.confirm('确定要删除该产值报告吗？删除后不可恢复', '提示', { type: 'warning' })
  await deleteOutputReport(row.id)
  ElMessage.success('删除成功')
  loadData()
}

// ================= 新增对话框 =================
const createDialogVisible = ref(false)
const createLoading = ref(false)
const createFormRef = ref<FormInstance>()
const formContractOptions = ref<any[]>([])
const boqItems = ref<BoqRow[]>([])
const fillMode = ref<'boq' | 'amount'>('amount')

const createForm = reactive({
  projectId: undefined as number | undefined,
  contractId: undefined as number | undefined,
  reportPeriod: '',
  confirmDate: '',
  currentOutput: 0
})

const createRules: FormRules = {
  projectId: [{ required: true, message: '请选择项目', trigger: 'change' }],
  contractId: [{ required: true, message: '请选择施工合同', trigger: 'change' }],
  reportPeriod: [{ required: true, message: '请选择报告期间', trigger: 'change' }],
  currentOutput: [{ type: 'number', required: true, min: 0.01, message: '本期产值须大于0', trigger: 'blur' }]
}

function openCreateDialog() {
  createForm.projectId = queryParams.projectId
  createForm.contractId = undefined
  createForm.reportPeriod = ''
  createForm.confirmDate = ''
  createForm.currentOutput = 0
  boqItems.value = []
  fillMode.value = 'amount'
  formContractOptions.value = []
  if (createForm.projectId) loadContractOptions(createForm.projectId, 'form')
  createDialogVisible.value = true
}

function handleFormProjectChange() {
  createForm.contractId = undefined
  boqItems.value = []
  fillMode.value = 'amount'
  loadContractOptions(createForm.projectId, 'form')
}

async function handleFormContractChange() {
  boqItems.value = []
  fillMode.value = 'amount'
  createForm.currentOutput = 0
  if (!createForm.contractId) return
  const res: any = await getBoqFlat(createForm.contractId)
  const items: BoqRow[] = (res.data || []).map((it: any) => ({
    id: it.id,
    itemCode: it.itemCode,
    itemName: it.itemName,
    unit: it.unit,
    unitPrice: it.unitPrice,
    quantity: it.quantity,
    reportQuantity: 0
  }))
  boqItems.value = items
  if (items.length > 0) fillMode.value = 'boq'
}

function handleFillModeChange() {
  createForm.currentOutput = 0
  if (fillMode.value === 'boq') recalcFromBoq()
}

function lineAmount(row: BoqRow): number {
  const q = row.reportQuantity || 0
  const p = row.unitPrice || 0
  return Number((q * p).toFixed(2))
}

function recalcFromBoq() {
  const sum = boqItems.value.reduce((acc, row) => acc + lineAmount(row), 0)
  createForm.currentOutput = Number(sum.toFixed(2))
}

async function handleCreateSubmit() {
  if (!createFormRef.value) return
  await createFormRef.value.validate()

  let details: OutputReportDetail[] | undefined
  if (fillMode.value === 'boq') {
    details = boqItems.value
      .filter(row => (row.reportQuantity || 0) > 0)
      .map(row => ({
        boqItemId: row.id,
        quantity: row.reportQuantity as number,
        amount: lineAmount(row)
      }))
    if (details.length === 0) {
      ElMessage.warning('请至少填写一条清单行的本期完成量')
      return
    }
  }

  createLoading.value = true
  try {
    await createOutputReport({
      projectId: createForm.projectId,
      contractId: createForm.contractId,
      reportPeriod: createForm.reportPeriod,
      confirmDate: createForm.confirmDate,
      currentOutput: createForm.currentOutput,
      details
    })
    ElMessage.success('保存成功')
    createDialogVisible.value = false
    loadData()
  } finally {
    createLoading.value = false
  }
}

// ================= 公共 =================
function formatMoney(val: number) {
  if (val === null || val === undefined) return '-'
  return val.toLocaleString('zh-CN', { minimumFractionDigits: 2 })
}

const statusMap: Record<string, { label: string; type: 'success' | 'warning' | 'info' | 'danger' }> = {
  DRAFT: { label: '草稿', type: 'info' },
  SUBMITTED: { label: '审批中', type: 'warning' },
  APPROVED: { label: '已通过', type: 'success' },
  REJECTED: { label: '已驳回', type: 'danger' }
}
function getStatusLabel(status: string) {
  return statusMap[status]?.label || status
}
function getStatusType(status: string) {
  return statusMap[status]?.type || 'info'
}

loadData()
</script>

<style scoped>
.output-report-container {
  padding: 16px;
}
.filter-card {
  margin-bottom: 16px;
}
.pagination-wrap {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
.form-tip {
  font-size: 12px;
  color: #909399;
}
.total-line {
  margin-top: 12px;
  text-align: right;
  font-size: 14px;
}
.total-line .amount {
  font-size: 16px;
  font-weight: 600;
  color: #67c23a;
}
</style>
