<template>
  <div class="tender-register-container">
    <el-card shadow="never">
      <!-- 顶部搜索栏：支持按项目筛选及落标原因分类筛选（B4） -->
      <el-form :model="queryParams" inline>
        <el-form-item label="项目">
          <ProjectSelector v-model="queryParams.projectId" width="200px" @change="handleSearch" />
        </el-form-item>
        <el-form-item label="落标原因">
          <el-select
            v-model="queryParams.lostReasonCategory"
            placeholder="全部"
            clearable
            style="width: 160px"
          >
            <el-option
              v-for="item in lostReasonFilterOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <div class="table-toolbar">
        <el-button type="primary" @click="handleAdd">新增投标报名</el-button>
      </div>

      <el-table :data="tableData" v-loading="loading" border>
        <el-table-column prop="projectName" label="关联项目" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="font-medium">{{ row.projectName || row.ownerCompany || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="ownerCompany" label="业主单位" min-width="160" show-overflow-tooltip />
        <el-table-column prop="bidMethod" label="招标方式" width="110">
          <template #default="{ row }">
            {{ row.bidMethod === 'PUBLIC' ? '公开招标' : row.bidMethod === 'INVITE' ? '邀请招标' : (row.bidMethod || '-') }}
          </template>
        </el-table-column>
        <el-table-column prop="registerDate" label="报名日期" width="110" />
        <el-table-column prop="openDate" label="开标日期" width="110" />
        <el-table-column prop="depositAmount" label="保证金(元)" width="130" align="right">
          <template #default="{ row }">
            {{ row.depositAmount != null ? '¥ ' + Number(row.depositAmount).toLocaleString() : '-' }}
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" size="small">
              {{ statusLabelMap[row.status] || row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="lostReasonCategory" label="落标原因" width="110" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.lostReasonCategory" type="danger" size="small">
              {{ lostReasonLabelMap[row.lostReasonCategory] || row.lostReasonCategory }}
            </el-tag>
            <span v-else class="text-secondary">-</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="{ row }">
            <!-- 查看全景综合抽屉 (A3) -->
            <el-button link type="primary" @click="handleViewDetail(row)">详情</el-button>

            <!-- 报名中：可编辑、提交、删除 -->
            <el-button v-if="row.status === 'REGISTERED'" link type="primary" @click="handleEdit(row)">编辑</el-button>
            <el-button v-if="row.status === 'REGISTERED'" link type="success" @click="handleSubmitApply(row)">提交</el-button>
            <el-button v-if="row.status === 'REGISTERED'" link type="danger" @click="handleDelete(row)">删除</el-button>

            <!-- 已投标或已开标：支持申请保证金、录入开标结果 -->
            <el-button
              v-if="row.status === 'SUBMITTED' || row.status === 'REGISTERED'"
              link
              type="warning"
              @click="handleApplyDeposit(row)"
            >
              保证金
            </el-button>

            <!-- 开标录入动作（A1） -->
            <el-button
              v-if="row.status === 'SUBMITTED'"
              link
              type="success"
              @click="handleOpenBid(row)"
            >
              开标登记
            </el-button>
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

    <!-- 新增 / 编辑报名弹窗 -->
    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑投标报名' : '新增投标报名'" width="600px" destroy-on-close>
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="100px">
        <el-form-item label="项目" prop="projectId">
          <ProjectSelector v-model="formData.projectId" @change="handleProjectChange" />
        </el-form-item>
        <el-form-item label="业主单位" prop="ownerCompany">
          <el-input v-model="formData.ownerCompany" />
        </el-form-item>
        <el-form-item label="招标方式" prop="bidMethod">
          <el-select v-model="formData.bidMethod" clearable style="width: 100%">
            <el-option label="公开招标" value="PUBLIC" />
            <el-option label="邀请招标" value="INVITE" />
          </el-select>
        </el-form-item>
        <el-form-item label="报名方式" prop="registerMethod">
          <el-input v-model="formData.registerMethod" />
        </el-form-item>
        <el-form-item label="投标方式" prop="tenderMethod">
          <el-input v-model="formData.tenderMethod" />
        </el-form-item>
        <el-form-item label="报名日期" prop="registerDate">
          <el-date-picker v-model="formData.registerDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="开标日期" prop="openDate">
          <el-date-picker
            v-model="formData.openDate"
            type="date"
            value-format="YYYY-MM-DD"
            :disabled-date="disableBeforeRegisterDate"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="保证金(元)" prop="depositAmount">
          <el-input-number v-model="formData.depositAmount" :min="0" :precision="2" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleFormSubmit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 弹窗 1：开标结果录入弹窗 (A1/B4) -->
    <OpenBidDialog
      v-model="openBidDialogVisible"
      :register="currentRow"
      @success="loadData"
    />

    <!-- 弹窗 2：保证金申请 / 退还弹窗 (A2) -->
    <DepositDialog
      v-model="depositDialogVisible"
      :mode="depositDialogMode"
      :register="currentRow"
      :deposit-apply-id="currentDepositApplyId"
      @success="loadData"
    />

    <!-- 抽屉：投标详情全景综合抽屉 (A3/B1/B2/B3) -->
    <TenderDetailDrawer
      v-model="drawerVisible"
      :register="currentRow"
      @apply-deposit="openApplyDepositFromDrawer"
      @refund-deposit="openRefundDepositFromDrawer"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance } from 'element-plus'
import {
  getTenderRegisterPage,
  getTenderRegisterDetail,
  createTenderRegister,
  updateTenderRegister,
  deleteTenderRegister,
  submitTenderRegister
} from '@/api/tender'
import { getProjectList } from '@/api/project'
import ProjectSelector from '@/components/ProjectSelector.vue'
import OpenBidDialog from './components/OpenBidDialog.vue'
import DepositDialog from './components/DepositDialog.vue'
import TenderDetailDrawer from './components/TenderDetailDrawer.vue'

const formRef = ref<FormInstance>()
const loading = ref(false)
const tableData = ref<any[]>([])
const projectMap = ref<Record<string, string>>({})
const statusLabelMap: Record<string, string> = {
  REGISTERED: '报名中',
  SUBMITTED: '已投标',
  WON: '中标',
  LOST: '未中标'
}

const lostReasonLabelMap: Record<string, string> = {
  PRICE_OVER: '报价偏高',
  TECH_WEAK: '技术标失分',
  BIZ_DEVIATION: '商务偏离',
  CREDIT_LACK: '资信不足',
  OTHER: '其他原因'
}

const lostReasonFilterOptions = [
  { value: 'PRICE_OVER', label: '报价偏高' },
  { value: 'TECH_WEAK', label: '技术标失分' },
  { value: 'BIZ_DEVIATION', label: '商务偏离' },
  { value: 'CREDIT_LACK', label: '资信不足' },
  { value: 'OTHER', label: '其他原因' }
]

function statusTagType(status?: string) {
  if (status === 'WON') return 'success'
  if (status === 'LOST') return 'danger'
  if (status === 'SUBMITTED') return 'warning'
  return 'info'
}

const total = ref(0)
const dialogVisible = ref(false)
const submitLoading = ref(false)
const isEdit = ref(false)

// 详情抽屉与业务弹窗控制
const currentRow = ref<any>(null)
const currentDepositApplyId = ref<number | string | undefined>(undefined)
const drawerVisible = ref(false)
const openBidDialogVisible = ref(false)
const depositDialogVisible = ref(false)
const depositDialogMode = ref<'apply' | 'refund'>('apply')

const queryParams = ref({
  page: 1,
  size: 10,
  projectId: undefined as number | undefined,
  lostReasonCategory: undefined as string | undefined
})

const defaultForm = () => ({
  id: undefined as number | undefined,
  projectId: undefined as number | undefined,
  ownerCompany: '',
  bidMethod: '',
  registerMethod: '',
  tenderMethod: '',
  registerDate: '',
  openDate: '',
  depositAmount: 0
})

const formData = ref(defaultForm())

const formRules = {
  projectId: [{ required: true, message: '请选择项目', trigger: 'change' }],
  ownerCompany: [{ required: true, message: '请输入业主单位', trigger: 'blur' }],
  openDate: [
    {
      validator: (_rule: any, value: string, callback: any) => {
        if (value && formData.value.registerDate && value < formData.value.registerDate) {
          callback(new Error('开标日期不能早于报名日期'))
        } else {
          callback()
        }
      },
      trigger: 'change'
    }
  ]
}

function disableBeforeRegisterDate(date: Date): boolean {
  if (!formData.value.registerDate) return false
  const y = date.getFullYear()
  const m = String(date.getMonth() + 1).padStart(2, '0')
  const d = String(date.getDate()).padStart(2, '0')
  return `${y}-${m}-${d}` < formData.value.registerDate
}

async function loadProjects() {
  try {
    const res: any = await getProjectList()
    const map: Record<string, string> = {}
    if (Array.isArray(res.data)) {
      res.data.forEach((p: any) => {
        map[String(p.id)] = p.projectName
      })
    }
    projectMap.value = map
  } catch {
    // 忽略加载错误
  }
}

async function loadData() {
  loading.value = true
  try {
    const res: any = await getTenderRegisterPage(queryParams.value)
    const list = res.data?.records || []
    // 挂载 projectName 便于列表展示
    tableData.value = list.map((item: any) => ({
      ...item,
      projectName: item.projectName || (item.projectId ? projectMap.value[String(item.projectId)] : '')
    }))
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
  queryParams.value = {
    page: 1,
    size: 10,
    projectId: undefined,
    lostReasonCategory: undefined
  }
  loadData()
}

function handleAdd() {
  isEdit.value = false
  formData.value = defaultForm()
  dialogVisible.value = true
}

function handleProjectChange(_val: any, item?: any) {
  if (item && item.ownerCompanyName && !formData.value.ownerCompany) {
    formData.value.ownerCompany = item.ownerCompanyName
  }
}

async function handleEdit(row: any) {
  isEdit.value = true
  const res: any = await getTenderRegisterDetail(row.id)
  formData.value = { ...defaultForm(), ...(res.data || row) }
  dialogVisible.value = true
}

async function handleFormSubmit() {
  await formRef.value?.validate()
  submitLoading.value = true
  try {
    isEdit.value
      ? await updateTenderRegister(formData.value)
      : await createTenderRegister(formData.value)
    ElMessage.success(isEdit.value ? '更新成功' : '新增成功')
    dialogVisible.value = false
    loadData()
  } finally {
    submitLoading.value = false
  }
}

async function handleSubmitApply(row: any) {
  await ElMessageBox.confirm('确定要提交该投标报名吗？', '提示', { type: 'warning' })
  await submitTenderRegister(row.id)
  ElMessage.success('提交成功')
  loadData()
}

async function handleDelete(row: any) {
  await ElMessageBox.confirm('确定要删除吗？', '提示', { type: 'warning' })
  await deleteTenderRegister(row.id)
  ElMessage.success('删除成功')
  loadData()
}

// 综合抽屉
function handleViewDetail(row: any) {
  currentRow.value = row
  drawerVisible.value = true
}

// 开标登记
function handleOpenBid(row: any) {
  currentRow.value = row
  openBidDialogVisible.value = true
}

// 保证金申请
function handleApplyDeposit(row: any) {
  currentRow.value = row
  depositDialogMode.value = 'apply'
  depositDialogVisible.value = true
}

function openApplyDepositFromDrawer(reg: any) {
  currentRow.value = reg
  depositDialogMode.value = 'apply'
  depositDialogVisible.value = true
}

function openRefundDepositFromDrawer(reg: any, applyId: number | string) {
  currentRow.value = reg
  currentDepositApplyId.value = applyId
  depositDialogMode.value = 'refund'
  depositDialogVisible.value = true
}

onMounted(async () => {
  await loadProjects()
  await loadData()
})
</script>

<style scoped>
.tender-register-container {
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

.font-medium {
  font-weight: 500;
}

.text-secondary {
  color: var(--zw-color-text-secondary);
}
</style>
