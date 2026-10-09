<template>
  <div class="labor-settlement-container">
    <el-card shadow="never">
      <el-form :model="queryParams" inline>
        <el-form-item label="项目">
          <ProjectSelector v-model="queryParams.projectId" width="180px" />
        </el-form-item>
        <el-form-item label="关联合同">
          <el-select v-model="queryParams.contractId" placeholder="全部合同" clearable filterable style="width: 220px" @change="handleSearch">
            <el-option v-for="c in contractOptions" :key="c.id" :label="c.contractName || c.contractCode" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <div class="table-toolbar">
        <el-button type="primary" @click="handleAdd">新增劳务结算</el-button>
      </div>

      <el-table :data="tableData" v-loading="loading" border>
        <el-table-column prop="contractName" label="关联合同" min-width="160" show-overflow-tooltip />
        <el-table-column prop="settlementAmount" label="本次结算(元)" width="140" align="right">
          <template #default="{ row }">{{ formatAmount(row.settlementAmount) }}</template>
        </el-table-column>
        <el-table-column prop="cumulativeSettlement" label="累计结算(元)" width="140" align="right">
          <template #default="{ row }">{{ formatAmount(row.cumulativeSettlement) }}</template>
        </el-table-column>
        <el-table-column prop="createdAt" label="申请时间" width="170" />
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 'APPROVED' ? 'success' : row.status === 'SUBMITTED' ? 'warning' : 'info'" size="small">
              {{ row.status === 'APPROVED' ? '已审批' : row.status === 'SUBMITTED' ? '审批中' : '草稿' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <template v-if="row.status === 'DRAFT'">
              <el-button link type="primary" @click="handleEdit(row)">编辑</el-button>
              <el-button link type="success" @click="handleSubmit(row)">提交</el-button>
              <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
            </template>
            <span v-else style="color: var(--zw-text-tertiary)">{{ row.status === 'APPROVED' ? '已审批' : '审批中' }}</span>
            <el-button v-if="row.status === 'APPROVED'" link type="primary" class="ml-2" @click="handleApplyPayment(row)">发起付款</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrap">
        <el-pagination v-model:current-page="queryParams.page" v-model:page-size="queryParams.size" :page-sizes="[10, 20, 50]" :total="total" layout="total, sizes, prev, pager, next, jumper" @size-change="loadData" @current-change="loadData" />
      </div>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑结算单' : '新增劳务结算'" width="550px" destroy-on-close>
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="110px">
        <el-form-item label="所属项目" prop="projectId">
          <ProjectSelector v-model="formData.projectId" style="width: 100%" @change="onProjectChange" />
        </el-form-item>
        <el-form-item label="关联合同" prop="contractId">
          <el-select v-model="formData.contractId" placeholder="请选择劳务合同" filterable style="width: 100%">
            <el-option v-for="c in contractOptions" :key="c.id" :label="c.contractName || c.contractCode" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="结算金额(元)" prop="settlementAmount">
          <el-input-number v-model="formData.settlementAmount" :min="0.01" :precision="2" style="width: 100%" />
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
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance } from 'element-plus'
import {
  getLaborSettlementPage,
  createLaborSettlement,
  updateLaborSettlement,
  deleteLaborSettlement,
  submitLaborSettlement,
  getLaborContractPage
} from '@/api/labor'
import ProjectSelector from '@/components/ProjectSelector.vue'

const router = useRouter()
const formRef = ref<FormInstance>()
const loading = ref(false)
const tableData = ref<any[]>([])
const total = ref(0)
const dialogVisible = ref(false)
const submitLoading = ref(false)
const isEdit = ref(false)
const contractOptions = ref<any[]>([])
let contractSeq = 0

const queryParams = ref({
  page: 1,
  size: 10,
  projectId: undefined as number | undefined,
  contractId: undefined as number | undefined
})

const defaultForm = () => ({
  id: undefined as number | undefined,
  projectId: undefined as number | undefined,
  contractId: undefined as number | undefined,
  settlementAmount: undefined as number | undefined
})

const formData = ref(defaultForm())

const formRules = {
  projectId: [{ required: true, message: '请选择项目', trigger: 'change' }],
  contractId: [{ required: true, message: '请选择劳务合同', trigger: 'change' }],
  settlementAmount: [{ required: true, message: '请输入结算金额', trigger: 'blur' }]
}

function formatAmount(val: any) {
  if (val == null) return '0.00'
  return Number(val).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

async function loadContracts(projectId?: number) {
  const seq = ++contractSeq
  try {
    const res: any = await getLaborContractPage({ page: 1, size: 100, projectId })
    if (seq === contractSeq) {
      contractOptions.value = res.data?.records || []
    }
  } catch {
    if (seq === contractSeq) {
      contractOptions.value = []
    }
  }
}

function onProjectChange(val: any) {
  formData.value.contractId = undefined
  loadContracts(val)
}

async function loadData() {
  loading.value = true
  try {
    const res: any = await getLaborSettlementPage(queryParams.value)
    tableData.value = res.data?.records || []
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
  queryParams.value = { page: 1, size: 10, projectId: undefined, contractId: undefined }
  loadData()
}

function handleAdd() {
  isEdit.value = false
  formData.value = defaultForm()
  dialogVisible.value = true
}

function handleEdit(row: any) {
  isEdit.value = true
  formData.value = { ...row }
  loadContracts(row.projectId)
  dialogVisible.value = true
}

async function handleFormSubmit() {
  if (!formRef.value) return
  await formRef.value.validate()
  submitLoading.value = true
  try {
    if (isEdit.value) {
      await updateLaborSettlement(formData.value)
      ElMessage.success('更新成功')
    } else {
      await createLaborSettlement(formData.value)
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    loadData()
  } finally {
    submitLoading.value = false
  }
}

async function handleSubmit(row: any) {
  try {
    await ElMessageBox.confirm('确定提交该结算单进入审批流程吗？', '提示', { type: 'warning' })
    await submitLaborSettlement(row.id)
    ElMessage.success('提交成功')
    loadData()
  } catch {}
}

async function handleDelete(row: any) {
  try {
    await ElMessageBox.confirm('确定删除该结算记录吗？', '警告', { type: 'danger' })
    await deleteLaborSettlement(row.id)
    ElMessage.success('删除成功')
    loadData()
  } catch {}
}

function handleApplyPayment(row: any) {
  router.push({
    path: '/finance/payment-apply',
    query: {
      projectId: row.projectId,
      contractId: row.contractId,
      contractCategory: 'LABOR',
      amount: row.settlementAmount
    }
  })
}

onMounted(() => {
  loadContracts()
  loadData()
})
</script>

<style scoped>
.labor-settlement-container {
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
.ml-2 {
  margin-left: var(--zw-space-sm);
}
</style>
