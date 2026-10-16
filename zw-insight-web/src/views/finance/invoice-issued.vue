<template>
  <div class="finance-container">
    <el-card shadow="never">
      <el-alert type="info" :closable="false" class="mb-2"
        title="销项发票台账登记实际开具的发票（真实票据事实），与开票申请（审批口径累计开票）并列对照，不互相改写。" />

      <el-form :model="queryParams" inline>
        <el-form-item label="方向">
          <el-select v-model="queryParams.direction" clearable placeholder="全部" style="width: 140px">
            <el-option label="蓝字" value="BLUE" />
            <el-option label="红字" value="RED" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch"><el-icon><Search /></el-icon>搜索</el-button>
          <el-button @click="handleReset"><el-icon><Refresh /></el-icon>重置</el-button>
        </el-form-item>
      </el-form>

      <div class="table-toolbar">
        <el-button type="primary" @click="openDialog()"><el-icon><Plus /></el-icon>登记发票</el-button>
      </div>

      <el-table :data="tableData" v-loading="loading" border>
        <el-table-column prop="issueDate" label="开票日期" width="110" />
        <el-table-column label="方向" width="70" align="center">
          <template #default="{ row }">
            <el-tag :type="row.direction === 'RED' ? 'danger' : 'primary'" size="small">
              {{ row.direction === 'RED' ? '红字' : '蓝字' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="invoiceNumber" label="发票号码" min-width="170" />
        <el-table-column prop="invoiceCode" label="发票代码" width="120">
          <template #default="{ row }">{{ row.invoiceCode || '—' }}</template>
        </el-table-column>
        <el-table-column prop="amount" label="价税合计" width="130" align="right">
          <template #default="{ row }">{{ Number(row.amount || 0).toLocaleString() }}</template>
        </el-table-column>
        <el-table-column prop="taxRate" label="税率" width="80" align="right">
          <template #default="{ row }">{{ row.taxRate != null ? row.taxRate + '%' : '—' }}</template>
        </el-table-column>
        <el-table-column prop="taxAmount" label="税额" width="110" align="right">
          <template #default="{ row }">{{ row.taxAmount != null ? Number(row.taxAmount).toLocaleString() : '—' }}</template>
        </el-table-column>
        <el-table-column prop="contractId" label="关联合同" width="120">
          <template #default="{ row }">{{ row.contractId ? `合同#${row.contractId}` : '—' }}</template>
        </el-table-column>
        <el-table-column prop="applyId" label="来源申请" width="110">
          <template #default="{ row }">{{ row.applyId ? `申请#${row.applyId}` : '手工' }}</template>
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
        <el-table-column label="操作" width="230" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.verifyStatus !== 'VERIFIED'" size="small" type="success" link @click="verify(row, 'VERIFIED')">查验通过</el-button>
            <el-button v-if="row.verifyStatus !== 'MISMATCH'" size="small" type="danger" link @click="verify(row, 'MISMATCH')">查验不符</el-button>
            <el-button size="small" link @click="toggleBookkeep(row)">
              {{ row.bookkeepStatus === 'BOOKED' ? '取消入账' : '标记入账' }}
            </el-button>
            <el-button size="small" type="danger" link @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
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

    <!-- 登记弹窗 -->
    <el-dialog v-model="dialogVisible" title="登记销项发票" width="620px" destroy-on-close>
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="110px">
        <el-form-item label="方向">
          <el-radio-group v-model="formData.direction">
            <el-radio value="BLUE">蓝字（正数发票）</el-radio>
            <el-radio value="RED">红字（冲红）</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="formData.direction === 'RED'" label="原蓝字发票" prop="originalId">
          <el-select v-model="formData.originalId" filterable placeholder="选择被冲红的蓝字发票" style="width: 100%">
            <el-option v-for="b in blueOptions" :key="b.id" :label="`${b.invoiceNumber}（${Number(b.amount).toLocaleString()}）`" :value="Number(b.id)" />
          </el-select>
        </el-form-item>
        <el-form-item label="发票号码" prop="invoiceNumber">
          <el-input v-model="formData.invoiceNumber" placeholder="数电20位；传统8位须填代码" />
        </el-form-item>
        <el-form-item label="发票代码">
          <el-input v-model="formData.invoiceCode" placeholder="传统票10/12位；数电票留空" />
        </el-form-item>
        <el-form-item label="开票日期" prop="issueDate">
          <el-date-picker v-model="formData.issueDate" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="价税合计" prop="amount">
          <el-input-number v-model="formData.amount" :precision="2" style="width: 100%"
            :placeholder="formData.direction === 'RED' ? '红字填负数' : ''" />
        </el-form-item>
        <el-form-item label="税率(%)">
          <el-input-number v-model="formData.taxRate" :min="0" :max="100" :precision="2" style="width: 100%" placeholder="未知留空" />
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="项目ID"><el-input-number v-model="formData.projectId" :precision="0" style="width: 100%" controls-position="right" /></el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="合同ID"><el-input-number v-model="formData.contractId" :precision="0" style="width: 100%" controls-position="right" /></el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="来源申请ID">
          <el-input-number v-model="formData.applyId" :precision="0" style="width: 100%" controls-position="right" placeholder="由已批开票申请带入，可空" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">登记</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance } from 'element-plus'
import { Search, Refresh, Plus } from '@element-plus/icons-vue'
import {
  getInvoiceIssuedPage,
  createInvoiceIssued,
  verifyInvoiceIssued,
  markInvoiceIssuedBookkeep,
  deleteInvoiceIssued
} from '@/api/finance'

defineOptions({ name: 'FinanceInvoiceIssued' })

const loading = ref(false)
const tableData = ref<any[]>([])
const total = ref(0)
const queryParams = ref({ page: 1, size: 10, direction: '' as string })
const dialogVisible = ref(false)
const submitting = ref(false)
const formRef = ref<FormInstance>()
const blueOptions = ref<any[]>([])
const formData = ref<any>({})
const formRules = {
  invoiceNumber: [{ required: true, message: '请输入发票号码', trigger: 'blur' }],
  issueDate: [{ required: true, message: '请选择开票日期', trigger: 'change' }],
  amount: [{ required: true, message: '请输入价税合计', trigger: 'blur' }]
}

async function loadData() {
  loading.value = true
  try {
    const res: any = await getInvoiceIssuedPage({
      page: queryParams.value.page,
      size: queryParams.value.size,
      direction: queryParams.value.direction || undefined
    })
    tableData.value = res.data?.records || []
    total.value = Number(res.data?.total || 0)
  } finally {
    loading.value = false
  }
}

function handleSearch() { queryParams.value.page = 1; loadData() }
function handleReset() { queryParams.value = { page: 1, size: 10, direction: '' }; loadData() }

async function openDialog() {
  formData.value = {
    direction: 'BLUE', invoiceNumber: '', invoiceCode: '', issueDate: '',
    amount: undefined, taxRate: undefined, projectId: undefined, contractId: undefined,
    applyId: undefined, originalId: undefined
  }
  dialogVisible.value = true
  if (!blueOptions.value.length) {
    try {
      const res: any = await getInvoiceIssuedPage({ page: 1, size: 200, direction: 'BLUE' })
      blueOptions.value = res.data?.records || []
    } catch { blueOptions.value = [] }
  }
}

async function submit() {
  await formRef.value?.validate()
  submitting.value = true
  try {
    await createInvoiceIssued({
      direction: formData.value.direction,
      invoiceNumber: formData.value.invoiceNumber.trim(),
      invoiceCode: formData.value.invoiceCode?.trim() || undefined,
      issueDate: formData.value.issueDate,
      amount: formData.value.amount,
      taxRate: formData.value.taxRate,
      projectId: formData.value.projectId ?? undefined,
      contractId: formData.value.contractId ?? undefined,
      applyId: formData.value.applyId ?? undefined,
      originalId: formData.value.direction === 'RED' ? formData.value.originalId : undefined
    })
    ElMessage.success('发票已登记')
    dialogVisible.value = false
    await loadData()
  } finally { submitting.value = false }
}

async function verify(row: any, verifyStatus: 'VERIFIED' | 'MISMATCH') {
  await verifyInvoiceIssued(Number(row.id), verifyStatus)
  ElMessage.success(verifyStatus === 'VERIFIED' ? '已标记查验通过（人工核对税务平台）' : '已标记查验不符')
  await loadData()
}

async function toggleBookkeep(row: any) {
  await markInvoiceIssuedBookkeep(Number(row.id), row.bookkeepStatus !== 'BOOKED')
  await loadData()
}

async function remove(row: any) {
  try {
    await ElMessageBox.confirm('确定删除该发票记录？（存在关联红字时会被拒绝）', '提示', { type: 'warning' })
  } catch { return }
  await deleteInvoiceIssued(Number(row.id))
  ElMessage.success('已删除')
  await loadData()
}

onMounted(loadData)
</script>

<style scoped>
.finance-container { padding: 12px; }
.table-toolbar { margin-bottom: 8px; }
.pagination-wrap { display: flex; justify-content: flex-end; margin-top: 10px; }
</style>
