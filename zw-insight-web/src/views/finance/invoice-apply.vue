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
        <el-form-item label="状态">
          <el-select v-model="queryParams.status" placeholder="全部" clearable style="width: 120px">
            <el-option label="草稿" value="DRAFT" />
            <el-option label="审批中" value="APPROVING" />
            <el-option label="已通过" value="APPROVED" />
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
          <el-icon><Plus /></el-icon>新增开票申请
        </el-button>
      </div>

      <el-table :data="tableData" v-loading="loading" border>
        <el-table-column prop="projectName" label="项目名称" min-width="180" show-overflow-tooltip />
        <el-table-column prop="invoiceAmount" label="开票金额" width="130" align="right">
          <template #default="{ row }">{{ formatMoney(row.invoiceAmount) }}</template>
        </el-table-column>
        <el-table-column prop="invoiceType" label="发票类型" width="120" />
        <el-table-column prop="applyDate" label="申请日期" width="110" />
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)" size="small">
              {{ getStatusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleView(row)">查看</el-button>
            <el-button v-if="row.status === 'DRAFT' || row.status === 'REJECTED'" link type="success" @click="handleSubmitApply(row)">提交</el-button>
            <el-button v-if="row.status === 'DRAFT'" link type="danger" @click="handleDelete(row)">删除</el-button>
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

    <!-- 新增弹窗 -->
    <el-dialog v-model="dialogVisible" title="新增开票申请" width="600px" destroy-on-close>
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="100px">
        <el-form-item label="项目" prop="projectId">
          <el-select
            v-model="formData.projectId"
            placeholder="请选择项目"
            filterable
            remote
            :remote-method="searchProject"
            style="width: 100%"
          >
            <el-option v-for="item in projectList" :key="item.id" :label="item.projectName" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="关联合同" prop="contractId">
          <ContractSelector v-model="formData.contractId" :project-id="formData.projectId" />
        </el-form-item>
        <el-form-item label="开票金额" prop="invoiceAmount">
          <el-input-number v-model="formData.invoiceAmount" :min="0" :precision="2" style="width: 100%" />
        </el-form-item>
        <el-form-item label="税率">
          <TaxRateSelector v-model="formData.taxRate" />
        </el-form-item>
        <el-form-item label="发票类型">
          <el-select v-model="formData.invoiceType" style="width: 100%">
            <el-option label="增值税专用发票" value="增值税专用发票" />
            <el-option label="增值税普通发票" value="增值税普通发票" />
          </el-select>
        </el-form-item>
        <el-form-item label="申请日期" prop="applyDate">
          <el-date-picker v-model="formData.applyDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleFormSubmit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 详情抽屉（真实数据源：GET /v1/finance/invoice-apply/{id}；
         后端 getById 不回填 projectName，项目名回落取列表行同源字段） -->
    <el-drawer v-model="detailVisible" title="开票申请详情" size="480px" data-testid="invoice-detail-drawer">
      <div v-loading="detailLoading">
        <div v-if="detailError" class="detail-error" data-testid="detail-error">
          <span>加载失败：{{ detailError }}</span>
          <el-button size="small" type="primary" @click="retryDetail">重试</el-button>
        </div>
        <el-descriptions v-else-if="detailData" :column="1" border size="small">
          <el-descriptions-item label="项目名称">{{ detailData.projectName || detailRow?.projectName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="申请日期">{{ detailData.applyDate || '-' }}</el-descriptions-item>
          <el-descriptions-item label="发票类型">{{ detailData.invoiceType || '-' }}</el-descriptions-item>
          <el-descriptions-item label="开票金额">{{ formatMoney(detailData.invoiceAmount) }}</el-descriptions-item>
          <el-descriptions-item label="税率">{{ detailData.taxRate != null ? detailData.taxRate + '%' : '-' }}</el-descriptions-item>
          <el-descriptions-item label="发票抬头">{{ detailData.invoiceTitle || '-' }}</el-descriptions-item>
          <el-descriptions-item label="纳税人识别号">{{ detailData.taxpayerId || '-' }}</el-descriptions-item>
          <el-descriptions-item label="银行名称">{{ detailData.bankName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="银行账号">{{ detailData.bankAccount || '-' }}</el-descriptions-item>
          <el-descriptions-item label="合同金额快照">{{ formatMoney(detailData.contractAmountSnapshot) }}</el-descriptions-item>
          <el-descriptions-item label="结算金额快照">{{ formatMoney(detailData.settlementAmountSnapshot) }}</el-descriptions-item>
          <el-descriptions-item label="历史已开票快照">{{ formatMoney(detailData.historicalInvoicedSnapshot) }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="getStatusType(detailData.status)" size="small">{{ getStatusLabel(detailData.status) }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ detailData.createdAt || '-' }}</el-descriptions-item>
        </el-descriptions>
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance } from 'element-plus'
import { getInvoiceApplyPage, getInvoiceApplyDetail, createInvoiceApply, deleteInvoiceApply, submitInvoiceApply } from '@/api/finance'
import { getProjectList } from '@/api/project'
import TaxRateSelector from '@/components/TaxRateSelector.vue'
import ContractSelector from '@/components/ContractSelector.vue'

const formRef = ref<FormInstance>()
const loading = ref(false)
const tableData = ref<any[]>([])
const total = ref(0)
const projectList = ref<any[]>([])
const dialogVisible = ref(false)
const submitLoading = ref(false)

const queryParams = ref({
  pageNum: 1,
  pageSize: 10,
  projectId: undefined as number | undefined,
  status: ''
})

const formData = ref({
  projectId: undefined as number | undefined,
  contractId: undefined as number | undefined,
  invoiceAmount: 0,
  taxRate: undefined as number | undefined,
  invoiceType: '增值税专用发票',
  applyDate: ''
})

const formRules = {
  projectId: [{ required: true, message: '请选择项目', trigger: 'change' }],
  contractId: [{ required: true, message: '请选择关联合同', trigger: 'change' }],
  invoiceAmount: [{ required: true, message: '请输入开票金额', trigger: 'blur' }],
  applyDate: [{ required: true, message: '请选择申请日期', trigger: 'change' }]
}

function formatMoney(val: number) {
  if (!val && val !== 0) return '-'
  return val.toLocaleString('zh-CN', { minimumFractionDigits: 2 })
}

const statusMap: Record<string, { label: string; type: string }> = {
  DRAFT: { label: '草稿', type: 'info' },
  SUBMITTED: { label: '审批中', type: 'warning' },
  APPROVING: { label: '审批中', type: 'warning' },
  APPROVED: { label: '已通过', type: 'success' },
  REJECTED: { label: '已驳回', type: 'danger' }
}

function getStatusLabel(status: string) {
  return statusMap[status]?.label || status
}

function getStatusType(status: string) {
  return (statusMap[status]?.type || 'info') as any
}

async function searchProject(query: string) {
  const res: any = await getProjectList({ projectName: query })
  projectList.value = res.data || []
}

async function loadData() {
  loading.value = true
  try {
    const res: any = await getInvoiceApplyPage(queryParams.value)
    tableData.value = res.data?.records || []
    total.value = res.data?.total || 0
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  queryParams.value.pageNum = 1
  loadData()
}

function handleReset() {
  queryParams.value = { pageNum: 1, pageSize: 10, projectId: undefined, status: '' }
  loadData()
}

function handleAdd() {
  formData.value = { projectId: undefined, contractId: undefined, invoiceAmount: 0, taxRate: undefined, invoiceType: '增值税专用发票', applyDate: '' }
  dialogVisible.value = true
}

function handleView(row: any) {
  loadDetail(row)
}

// ================= 详情抽屉（真实接口 /v1/finance/invoice-apply/{id}） =================
const detailVisible = ref(false)
const detailLoading = ref(false)
const detailError = ref('')
const detailData = ref<any>(null)
const detailRow = ref<any>(null)

async function loadDetail(row: any) {
  detailRow.value = row
  detailData.value = null
  detailError.value = ''
  detailVisible.value = true
  detailLoading.value = true
  try {
    const res: any = await getInvoiceApplyDetail(row.id)
    detailData.value = res.data || null
    if (!res.data) {
      // 不静默：返回空数据显式进入错误态
      detailError.value = '接口未返回详情数据'
      ElMessage.error('加载开票申请详情失败：接口未返回数据')
    }
  } catch (e: any) {
    // 不静默：失败显式提示并在抽屉内提供重试入口
    detailError.value = e?.message || '接口异常'
    ElMessage.error('加载开票申请详情失败：' + (e?.message || '接口异常'))
  } finally {
    detailLoading.value = false
  }
}

function retryDetail() {
  if (detailRow.value) loadDetail(detailRow.value)
}

async function handleFormSubmit() {
  await formRef.value?.validate()
  submitLoading.value = true
  try {
    await createInvoiceApply({ ...formData.value, projectId: formData.value.projectId!, contractId: formData.value.contractId! })
    ElMessage.success('新增成功')
    dialogVisible.value = false
    loadData()
  } finally {
    submitLoading.value = false
  }
}

async function handleSubmitApply(row: any) {
  await ElMessageBox.confirm('确定要提交该开票申请吗？', '提示', { type: 'warning' })
  await submitInvoiceApply(row.id)
  ElMessage.success('已提交审批，审批通过后生效')
  loadData()
}

async function handleDelete(row: any) {
  await ElMessageBox.confirm('确定要删除吗？', '提示', { type: 'warning' })
  await deleteInvoiceApply(row.id)
  ElMessage.success('删除成功')
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
.table-toolbar {
  margin-bottom: 16px;
}
.pagination-wrap {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
.detail-error {
  display: flex;
  align-items: center;
  gap: 12px;
  color: var(--el-color-danger);
  padding: 8px 0;
}
</style>
