<template>
  <div class="finance-container">
    <el-card shadow="never">
      <el-form :model="queryParams" inline>
        <el-form-item label="项目">
          <el-select
            v-model="queryParams.projectId"
            placeholder="请选择项目"
            filterable
            remote
            :remote-method="searchProject"
            clearable
            style="width: 220px"
          >
            <el-option v-for="item in projectList" :key="item.id" :label="item.projectName" :value="item.id" />
          </el-select>
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

      <div class="table-toolbar">
        <el-button type="primary" @click="handleAdd">
          <el-icon><Plus /></el-icon>新增收票登记
        </el-button>
      </div>

      <el-table :data="tableData" v-loading="loading" border>
        <el-table-column prop="projectName" label="项目名称" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ row.projectName || '-' }}</template>
        </el-table-column>
        <el-table-column prop="contractCategory" label="合同类型" width="120">
          <template #default="{ row }">{{ getCategoryLabel(row.contractCategory) }}</template>
        </el-table-column>
        <el-table-column prop="contractId" label="关联合同" width="130">
          <template #default="{ row }">{{ row.contractId ? `合同#${row.contractId}` : '-' }}</template>
        </el-table-column>
        <el-table-column prop="supplierName" label="供应商" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">{{ row.supplierName || '-' }}</template>
        </el-table-column>
        <el-table-column prop="invoiceAmount" label="发票金额" width="130" align="right">
          <template #default="{ row }">{{ formatMoney(row.invoiceAmount) }}</template>
        </el-table-column>
        <el-table-column prop="invoiceNumber" label="发票号码" min-width="180" />
        <el-table-column prop="invoiceCode" label="发票代码" min-width="130" />
        <el-table-column prop="amountWithoutTax" label="不含税金额" width="140" />
        <el-table-column prop="taxAmount" label="税额" width="120" />
        <el-table-column prop="taxRate" label="税率" width="90" align="right">
          <template #default="{ row }">{{ formatTaxRate(row.taxRate) }}</template>
        </el-table-column>
        <el-table-column prop="invoiceDate" label="收票日期" width="120" />
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 'APPROVED' ? 'success' : 'info'" size="small">
              {{ row.status === 'APPROVED' ? '已通过' : (row.status || '-') }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="查验" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.verifyStatus === 'VERIFIED' ? 'success' : row.verifyStatus === 'MISMATCH' ? 'danger' : 'info'" size="small">
              {{ row.verifyStatus === 'VERIFIED' ? '已查验' : row.verifyStatus === 'MISMATCH' ? '查验不符' : '未查验' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="入账" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.bookkeepStatus === 'BOOKED' ? 'success' : 'info'" size="small">
              {{ row.bookkeepStatus === 'BOOKED' ? '已入账' : '未入账' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.verifyStatus !== 'VERIFIED'" size="small" type="success" link @click="markVerify(row, 'VERIFIED')">查验通过</el-button>
            <el-button v-if="row.verifyStatus !== 'MISMATCH'" size="small" type="danger" link @click="markVerify(row, 'MISMATCH')">查验不符</el-button>
            <el-button size="small" link @click="toggleBookkeep(row)">
              {{ row.bookkeepStatus === 'BOOKED' ? '取消入账' : '标记入账' }}
            </el-button>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="登记时间" width="170" />
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

    <!-- 新增弹窗 -->
    <el-dialog v-model="dialogVisible" title="新增收票登记" width="600px" destroy-on-close>
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="100px">
        <el-form-item label="项目" prop="projectId">
          <el-select
            v-model="formData.projectId"
            placeholder="请选择项目"
            filterable
            remote
            :remote-method="searchProject"
            style="width: 100%"
            @change="handleProjectOrCategoryChange"
          >
            <el-option v-for="item in projectList" :key="item.id" :label="item.projectName" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="合同类型" prop="contractCategory">
          <el-select
            v-model="formData.contractCategory"
            placeholder="请选择支出合同类型（可选）"
            clearable
            style="width: 100%"
            @change="handleProjectOrCategoryChange"
          >
            <el-option label="采购合同" value="PURCHASE" />
            <el-option label="劳务合同" value="LABOR" />
            <el-option label="机械合同" value="MACHINE" />
            <el-option label="分包合同" value="SUBCONTRACT" />
            <el-option label="其他支出合同" value="OTHER_EXPENSE" />
          </el-select>
        </el-form-item>
        <el-form-item label="关联合同" prop="contractId">
          <el-select
            v-model="formData.contractId"
            placeholder="选择关联合同（回写合同累计收票额）"
            clearable
            filterable
            style="width: 100%"
            @change="handleContractChange"
          >
            <el-option
              v-for="c in contractOptions"
              :key="c.id"
              :label="c.contractName || c.contractCode || ('合同#' + c.id)"
              :value="c.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="供应商" prop="supplierName">
          <el-input v-model="formData.supplierName" placeholder="请输入供应商名称" />
        </el-form-item>
        <el-form-item label="发票号码"><el-input v-model="formData.invoiceNumber" maxlength="20" placeholder="8位或20位数字" /></el-form-item>
        <el-form-item label="发票代码"><el-input v-model="formData.invoiceCode" maxlength="12" placeholder="数电票可空" /></el-form-item>
        <el-form-item label="发票金额" prop="invoiceAmount">
          <el-input-number v-model="formData.invoiceAmount" :min="0" :precision="2" style="width: 100%" />
        </el-form-item>
        <el-form-item label="税率">
          <TaxRateSelector v-model="formData.taxRate" />
        </el-form-item>
        <el-form-item label="收票日期" prop="invoiceDate">
          <el-date-picker v-model="formData.invoiceDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
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
import { ElMessage } from 'element-plus'
import type { FormInstance } from 'element-plus'
import { getInvoiceReceivedPage, createInvoiceReceived, verifyInvoiceReceived, markInvoiceReceivedBookkeep } from '@/api/finance'
import { getProjectList } from '@/api/project'
import { getOtherContractPage } from '@/api/contract'
import { getPurchaseContractPage } from '@/api/purchase'
import { getLaborContractPage } from '@/api/labor'
import { getMachineContractPage } from '@/api/machine'
import { getSubcontractPage } from '@/api/subcontract'
import TaxRateSelector from '@/components/TaxRateSelector.vue'

const formRef = ref<FormInstance>()
const loading = ref(false)
const tableData = ref<any[]>([])
const total = ref(0)
const projectList = ref<any[]>([])
const contractOptions = ref<any[]>([])
const dialogVisible = ref(false)
const submitLoading = ref(false)

const categoryMap: Record<string, string> = {
  PURCHASE: '采购合同',
  LABOR: '劳务合同',
  MACHINE: '机械合同',
  SUBCONTRACT: '分包合同',
  OTHER_EXPENSE: '其他支出合同',
  OTHER: '其他合同'
}

function getCategoryLabel(category: string) {
  return categoryMap[category] || category || '-'
}

const queryParams = ref({
  pageNum: 1,
  pageSize: 10,
  projectId: undefined as number | undefined
})

const formData = ref({
  projectId: undefined as number | undefined,
  contractCategory: '',
  contractId: undefined as number | undefined,
  supplierName: '',
  invoiceAmount: 0,
  invoiceNumber: '',
  invoiceCode: '',
  taxRate: undefined as number | undefined,
  invoiceDate: ''
})

const formRules = {
  projectId: [{ required: true, message: '请选择项目', trigger: 'change' }],
  invoiceAmount: [{ required: true, message: '请输入发票金额', trigger: 'blur' }],
  invoiceDate: [{ required: true, message: '请选择收票日期', trigger: 'change' }]
}

function formatMoney(val: number) {
  if (!val && val !== 0) return '-'
  return val.toLocaleString('zh-CN', { minimumFractionDigits: 2 })
}

function formatTaxRate(val: number) {
  if (val === undefined || val === null) return '-'
  return `${val}%`
}

async function searchProject(query: string) {
  const res: any = await getProjectList({ projectName: query })
  projectList.value = res.data || []
}

async function handleProjectOrCategoryChange() {
  contractOptions.value = []
  formData.value.contractId = undefined
  const pid = formData.value.projectId
  if (!pid) return
  const params: any = { projectId: pid, page: 1, size: 100, pageNum: 1, pageSize: 100 }
  let res: any
  try {
    switch (formData.value.contractCategory) {
      case 'PURCHASE': res = await getPurchaseContractPage(params); break
      case 'LABOR': res = await getLaborContractPage(params); break
      case 'MACHINE': res = await getMachineContractPage(params); break
      case 'SUBCONTRACT': res = await getSubcontractPage(params); break
      case 'OTHER_EXPENSE': res = await getOtherContractPage({ ...params, contractCategory: 'OTHER_EXPENSE' }); break
      default: res = await getPurchaseContractPage(params)
    }
    contractOptions.value = res?.data?.records || res?.data || []
  } catch (e) {
    contractOptions.value = []
  }
}

function handleContractChange(val: any) {
  if (!val) return
  const selected = contractOptions.value.find((c: any) => c.id === val)
  if (selected) {
    formData.value.supplierName = selected.partyBName || selected.supplierName || selected.subcontractor || selected.teamName || ''
  }
}

async function loadData() {
  loading.value = true
  try {
    const res: any = await getInvoiceReceivedPage(queryParams.value)
    tableData.value = res.data?.records || []
    total.value = res.data?.total || 0
  } finally {
    loading.value = false
  }
}

// V2026_110：查验/入账状态流转（人工核对税务平台后如实标记）
async function markVerify(row: any, verifyStatus: 'VERIFIED' | 'MISMATCH') {
  await verifyInvoiceReceived(Number(row.id), verifyStatus)
  ElMessage.success(verifyStatus === 'VERIFIED' ? '已标记查验通过' : '已标记查验不符')
  await loadData()
}

async function toggleBookkeep(row: any) {
  await markInvoiceReceivedBookkeep(Number(row.id), row.bookkeepStatus !== 'BOOKED')
  await loadData()
}

function handleSearch() {
  queryParams.value.pageNum = 1
  loadData()
}

function handleReset() {
  queryParams.value = { pageNum: 1, pageSize: 10, projectId: undefined as number | undefined }
  loadData()
}

function handleAdd() {
  contractOptions.value = []
  formData.value = {
    projectId: undefined,
    contractCategory: '',
    contractId: undefined,
    supplierName: '',
    invoiceAmount: 0,
  invoiceNumber: '',
  invoiceCode: '',
    taxRate: undefined,
    invoiceDate: ''
  }
  dialogVisible.value = true
}

async function handleFormSubmit() {
  await formRef.value?.validate()
  submitLoading.value = true
  try {
    await createInvoiceReceived(formData.value)
    ElMessage.success('新增成功')
    dialogVisible.value = false
    loadData()
  } finally {
    submitLoading.value = false
  }
}

onMounted(() => {
  loadData()
  searchProject('')
})
</script>

<style scoped>
.finance-container {
  padding: var(--zw-space-md);
}
.table-toolbar {
  margin-bottom: var(--zw-space-md);
}
.pagination-wrap {
  margin-top: var(--zw-space-md);
  display: flex;
  justify-content: flex-end;
}
</style>
