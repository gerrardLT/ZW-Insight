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

      <ZwListToolbar
        :columns="invoiceColumns"
        :visible="columnVisible"
        @update:visible="setVisible"
        @reset="resetColumns"
        @refresh="loadData"
      >
        <el-button type="primary" @click="handleAdd">
          <el-icon><Plus /></el-icon>新增收票登记
        </el-button>
        <el-button @click="ocrDialogVisible = true">
          <el-icon><DocumentChecked /></el-icon>发票识别
        </el-button>
        <el-button @click="openClaimDialog">
          <el-icon><FolderOpened /></el-icon>从发票库关联合同
        </el-button>
      </ZwListToolbar>

      <el-table :data="tableData" v-loading="loading" border show-summary :summary-method="pageSummary">
        <el-table-column type="index" label="#" width="50" />
        <el-table-column v-if="columnVisible[0]" prop="projectName" label="项目名称" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ row.projectName || '-' }}</template>
        </el-table-column>
        <el-table-column v-if="columnVisible[1]" prop="contractCategory" label="合同类型" width="120">
          <template #default="{ row }">{{ getCategoryLabel(row.contractCategory) }}</template>
        </el-table-column>
        <el-table-column v-if="columnVisible[2]" prop="contractId" label="关联合同" width="130">
          <template #default="{ row }">{{ row.contractId ? `合同#${row.contractId}` : '-' }}</template>
        </el-table-column>
        <el-table-column v-if="columnVisible[3]" prop="supplierName" label="供应商" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">{{ row.supplierName || '-' }}</template>
        </el-table-column>
        <el-table-column v-if="columnVisible[4]" prop="invoiceAmount" label="发票金额" width="130" align="right">
          <template #default="{ row }">{{ formatMoney(row.invoiceAmount) }}</template>
        </el-table-column>
        <el-table-column v-if="columnVisible[5]" prop="invoiceNumber" label="发票号码" min-width="180" />
        <el-table-column v-if="columnVisible[6]" prop="invoiceCode" label="发票代码" min-width="130" />
        <el-table-column v-if="columnVisible[7]" prop="amountWithoutTax" label="不含税金额" width="140" align="right" />
        <el-table-column v-if="columnVisible[8]" prop="taxAmount" label="税额" width="120" align="right" />
        <el-table-column v-if="columnVisible[9]" prop="taxRate" label="税率" width="90" align="right">
          <template #default="{ row }">{{ formatTaxRate(row.taxRate) }}</template>
        </el-table-column>
        <el-table-column v-if="columnVisible[10]" prop="invoiceDate" label="收票日期" width="120" />
        <el-table-column v-if="columnVisible[11]" label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 'APPROVED' ? 'success' : 'info'" size="small">
              {{ row.status === 'APPROVED' ? '已通过' : (row.status || '-') }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column v-if="columnVisible[12]" label="查验" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.verifyStatus === 'VERIFIED' ? 'success' : row.verifyStatus === 'MISMATCH' ? 'danger' : 'info'" size="small">
              {{ row.verifyStatus === 'VERIFIED' ? '已查验' : row.verifyStatus === 'MISMATCH' ? '查验不符' : '未查验' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column v-if="columnVisible[13]" label="入账" width="80" align="center">
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
        <el-table-column v-if="columnVisible[14]" prop="createdAt" label="登记时间" width="170" />
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
    <!-- 发票 OCR 识别弹窗（V2026_117：tesseract.js 本地识别，人工确认后填入） -->
    <el-dialog v-model="ocrDialogVisible" title="发票 OCR 识别" width="500px" destroy-on-close>
      <div class="ocr-upload-area">
        <input ref="ocrFileInput" type="file" accept="image/*" style="display:none" @change="handleOcrFile" />
        <el-button type="primary" @click="($refs.ocrFileInput as HTMLInputElement).click()">
          选择发票图片
        </el-button>
        <p v-if="ocrProgress" class="ocr-progress">识别中… {{ ocrProgress }}%</p>
        <p v-if="ocrError" class="ocr-error" role="alert">{{ ocrError }}</p>
      </div>
      <template v-if="ocrResult">
        <el-divider content-position="left">识别结果（请人工核对后填入）</el-divider>
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item v-for="(v, k) in ocrFields" :key="k" :label="k">{{ v || '—' }}</el-descriptions-item>
        </el-descriptions>
        <el-button type="primary" style="margin-top: var(--zw-space-sm)" @click="applyOcrToForm">填入表单</el-button>
      </template>
    </el-dialog>

    <!-- 发票库（V2026_123）：列已登记但未关联支出合同的发票，选中后补关联并回写合同累计收票，
         不复制新建（发票号唯一，重复登记会被后端 409 拒绝） -->
    <el-dialog v-model="claimDialogVisible" title="从发票库关联合同" width="900px" destroy-on-close>
      <el-form inline>
        <el-form-item label="关键字">
          <el-input v-model="claimKeyword" placeholder="发票号码 / 供应商" clearable style="width: 220px" @keyup.enter="handleClaimSearch" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleClaimSearch">搜索</el-button>
        </el-form-item>
      </el-form>
      <el-table :data="claimRows" v-loading="claimLoading" border size="small" highlight-current-row @current-change="handleClaimSelect">
        <el-table-column prop="invoiceNumber" label="发票号码" min-width="170" />
        <el-table-column prop="invoiceCode" label="发票代码" width="120">
          <template #default="{ row }">{{ row.invoiceCode || '—' }}</template>
        </el-table-column>
        <el-table-column prop="projectName" label="项目" min-width="150" show-overflow-tooltip />
        <el-table-column prop="supplierName" label="供应商" min-width="150" show-overflow-tooltip />
        <el-table-column prop="invoiceAmount" label="发票金额" width="130" align="right">
          <template #default="{ row }">{{ formatMoney(row.invoiceAmount) }}</template>
        </el-table-column>
        <el-table-column prop="invoiceDate" label="收票日期" width="110" />
      </el-table>
      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="claimPage.page"
          v-model:page-size="claimPage.size"
          :page-sizes="[10, 20, 50]"
          :total="claimTotal"
          layout="total, sizes, prev, pager, next"
          @size-change="loadClaimRows"
          @current-change="loadClaimRows"
        />
      </div>
      <el-divider content-position="left">关联合同</el-divider>
      <el-form label-width="100px">
        <el-form-item label="发票">
          <span v-if="claimTarget">{{ claimTarget.invoiceNumber || '—' }} / {{ formatMoney(claimTarget.invoiceAmount) }}</span>
          <span v-else class="claim-hint">请在上表选择一张发票</span>
        </el-form-item>
        <el-form-item label="合同类别">
          <el-select v-model="claimCategory" :disabled="!claimTarget" style="width: 220px" @change="handleClaimCategoryChange">
            <el-option label="采购合同" value="PURCHASE" />
            <el-option label="劳务合同" value="LABOR" />
            <el-option label="机械合同" value="MACHINE" />
            <el-option label="分包合同" value="SUBCONTRACT" />
            <el-option label="其他支出合同" value="OTHER_EXPENSE" />
          </el-select>
        </el-form-item>
        <el-form-item label="支出合同">
          <el-select v-model="claimContractId" :disabled="!claimCategory" filterable style="width: 320px">
            <el-option v-for="c in claimContracts" :key="c.id" :label="contractLabel(c)" :value="c.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="claimDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="claimSubmitting" :disabled="!claimTarget || !claimContractId" @click="confirmClaim">确认关联</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance } from 'element-plus'
import { getInvoiceReceivedPage, createInvoiceReceived, verifyInvoiceReceived, markInvoiceReceivedBookkeep, claimInvoiceReceived } from '@/api/finance'
import { getProjectList } from '@/api/project'
import { getOtherContractPage } from '@/api/contract'
import { getPurchaseContractPage } from '@/api/purchase'
import { getLaborContractPage } from '@/api/labor'
import { getMachineContractPage } from '@/api/machine'
import { getSubcontractPage } from '@/api/subcontract'
import TaxRateSelector from '@/components/TaxRateSelector.vue'
import { ocrInvoiceImage, type OcrInvoiceResult } from '@/utils/invoice-ocr'
import ZwListToolbar from '@/components/ZwListToolbar.vue'
import { useColumnSetting } from '@/composables/useColumnSetting'

// 列显隐配置：按 invoice-received-table 持久化 localStorage
const invoiceColumns = [
  { key: 'projectName', label: '项目名称' },
  { key: 'contractCategory', label: '合同类型' },
  { key: 'contractId', label: '关联合同' },
  { key: 'supplierName', label: '供应商' },
  { key: 'invoiceAmount', label: '发票金额' },
  { key: 'invoiceNumber', label: '发票号码' },
  { key: 'invoiceCode', label: '发票代码' },
  { key: 'amountWithoutTax', label: '不含税金额' },
  { key: 'taxAmount', label: '税额' },
  { key: 'taxRate', label: '税率' },
  { key: 'invoiceDate', label: '收票日期' },
  { key: 'status', label: '状态' },
  { key: 'verifyStatus', label: '查验' },
  { key: 'bookkeepStatus', label: '入账' },
  { key: 'createdAt', label: '登记时间' },
]
const { visible: columnVisible, setVisible, reset: resetColumns } = useColumnSetting('invoice-received-table', invoiceColumns)

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

// 本页合计（非全量）：发票金额/不含税/税额三列（进项台账化 V2026_115 配套）
function pageSummary({ columns, data }: { columns: Array<{ property?: string }>; data: any[] }) {
  const keys = ['invoiceAmount', 'amountWithoutTax', 'taxAmount']
  return columns.map((col, i) => {
    if (i === 0) return '本页合计'
    if (col.property && keys.includes(col.property)) {
      const v = data.reduce((acc, r) => acc + Number(r[col.property!] || 0), 0)
      return v.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
    }
    return ''
  })
}

// ==================== 发票库关联合同（V2026_123） ====================
// 发票号码唯一，复制新建会被后端 409 拒绝；「从发票库选择」实现为认领：
// 只列未关联支出合同的发票，选中后补关联并回写合同累计收票。
const claimDialogVisible = ref(false)
const claimKeyword = ref('')
const claimRows = ref<any[]>([])
const claimTotal = ref(0)
const claimPage = ref({ page: 1, size: 10 })
const claimLoading = ref(false)
const claimTarget = ref<any>(null)
const claimCategory = ref('')
const claimContractId = ref<number | undefined>(undefined)
const claimContracts = ref<any[]>([])
const claimSubmitting = ref(false)

function openClaimDialog() {
  claimKeyword.value = ''
  claimPage.value.page = 1
  claimTarget.value = null
  claimCategory.value = ''
  claimContractId.value = undefined
  claimContracts.value = []
  claimDialogVisible.value = true
  loadClaimRows()
}

async function loadClaimRows() {
  claimLoading.value = true
  try {
    const res: any = await getInvoiceReceivedPage({
      page: claimPage.value.page,
      size: claimPage.value.size,
      unlinkedContract: true,
      keyword: claimKeyword.value || undefined
    })
    claimRows.value = res.data?.records || []
    claimTotal.value = Number(res.data?.total || 0)
  } finally {
    claimLoading.value = false
  }
}

function handleClaimSearch() {
  claimPage.value.page = 1
  loadClaimRows()
}

function handleClaimSelect(row: any) {
  claimTarget.value = row
  claimCategory.value = ''
  claimContractId.value = undefined
  claimContracts.value = []
}

function contractLabel(c: any) {
  const name = c.contractName || c.subcontractor || c.teamName || ''
  const code = c.contractCode || c.subcontractCode || ''
  return [code, name].filter(Boolean).join(' ') || `合同#${c.id}`
}

async function handleClaimCategoryChange() {
  claimContractId.value = undefined
  claimContracts.value = []
  if (!claimCategory.value || !claimTarget.value?.projectId) return
  const params: any = { projectId: claimTarget.value.projectId, page: 1, size: 100, pageNum: 1, pageSize: 100 }
  let res: any
  switch (claimCategory.value) {
    case 'PURCHASE': res = await getPurchaseContractPage(params); break
    case 'LABOR': res = await getLaborContractPage(params); break
    case 'MACHINE': res = await getMachineContractPage(params); break
    case 'SUBCONTRACT': res = await getSubcontractPage(params); break
    case 'OTHER_EXPENSE': res = await getOtherContractPage({ ...params, contractCategory: 'OTHER_EXPENSE' }); break
    default: res = null
  }
  claimContracts.value = res?.data?.records || res?.data || []
}

async function confirmClaim() {
  if (!claimTarget.value || !claimCategory.value || !claimContractId.value) {
    ElMessage.warning('请选择发票、合同类别与关联合同')
    return
  }
  claimSubmitting.value = true
  try {
    await claimInvoiceReceived(claimTarget.value.id, {
      contractCategory: claimCategory.value,
      contractId: claimContractId.value,
      supplierName: claimTarget.value.supplierName
    })
    ElMessage.success('已关联支出合同并回写累计收票')
    claimDialogVisible.value = false
    loadData()
  } finally {
    claimSubmitting.value = false
  }
}

// ==================== 发票 OCR（V2026_117） ====================
const ocrDialogVisible = ref(false)
const ocrFileInput = ref<HTMLInputElement>()
const ocrProgress = ref('')
const ocrError = ref('')
const ocrResult = ref<OcrInvoiceResult | null>(null)

const ocrFields = computed(() => {
  if (!ocrResult.value) return {}
  const r = ocrResult.value
  return {
    '发票号码': r.invoiceNumber,
    '发票代码': r.invoiceCode,
    '开票日期': r.invoiceDate,
    '含税金额': r.amount != null ? `¥${r.amount.toFixed(2)}` : '',
    '不含税金额': r.amountWithoutTax != null ? `¥${r.amountWithoutTax.toFixed(2)}` : '',
    '税额': r.taxAmount != null ? `¥${r.taxAmount.toFixed(2)}` : '',
    '税率': r.taxRate != null ? `${r.taxRate}%` : '',
    '供应方': r.supplierName
  }
})

async function handleOcrFile(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  ocrProgress.value = '0'
  ocrError.value = ''
  ocrResult.value = null
  try {
    ocrProgress.value = '50'
    const result = await ocrInvoiceImage(file, (p) => { ocrProgress.value = `${p}` })
    ocrResult.value = result
    if (!result.invoiceNumber && !result.amount) {
      ocrError.value = 'OCR 未能提取到有效发票字段，请人工录入或更换图片'
    }
  } catch (err: any) {
    ocrError.value = 'OCR 识别失败：' + (err?.message || '未知错误')
  } finally {
    ocrProgress.value = ''
    input.value = ''
  }
}

function applyOcrToForm() {
  const r = ocrResult.value
  if (!r) return
  const fd = formData.value as any
  if (r.invoiceNumber) fd.invoiceNumber = r.invoiceNumber
  if (r.invoiceCode) fd.invoiceCode = r.invoiceCode
  if (r.invoiceDate) fd.invoiceDate = r.invoiceDate
  if (r.amount != null) fd.invoiceAmount = r.amount
  if (r.amountWithoutTax != null) fd.amountWithoutTax = r.amountWithoutTax
  if (r.taxAmount != null) fd.taxAmount = r.taxAmount
  if (r.taxRate != null) fd.taxRate = r.taxRate
  if (r.supplierName) fd.supplierName = r.supplierName
  ocrDialogVisible.value = false
  ElMessage.success('OCR 结果已填入表单，请核对后提交')
}
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
.claim-hint {
  color: var(--el-text-color-secondary);
}
</style>
