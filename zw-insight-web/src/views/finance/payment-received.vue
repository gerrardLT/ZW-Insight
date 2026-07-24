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
          <el-icon><Plus /></el-icon>新增回款登记
        </el-button>
      </div>

      <el-table :data="tableData" v-loading="loading" border>
        <el-table-column prop="projectName" label="项目名称" min-width="180" show-overflow-tooltip />
        <el-table-column prop="receiveAmount" label="回款金额" width="130" align="right">
          <template #default="{ row }">{{ formatMoney(row.receiveAmount) }}</template>
        </el-table-column>
        <el-table-column prop="receiveDate" label="回款日期" width="110" />
        <el-table-column prop="receiveType" label="收款方式" width="120" />
        <el-table-column prop="receiver" label="收款人" width="110" show-overflow-tooltip />
        <el-table-column prop="receiveBankAccount" label="收款账户" min-width="160" show-overflow-tooltip />
        <el-table-column prop="createdAt" label="登记时间" width="170" />
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleEdit(row)">编辑</el-button>
            <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
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

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="600px" destroy-on-close>
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
        <el-form-item label="回款金额" prop="receiveAmount">
          <el-input-number v-model="formData.receiveAmount" :min="0" :precision="2" style="width: 100%" />
        </el-form-item>
        <el-form-item label="回款日期" prop="receiveDate">
          <el-date-picker v-model="formData.receiveDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="收款方式">
          <el-select v-model="formData.receiveType" style="width: 100%">
            <el-option label="银行转账" value="银行转账" />
            <el-option label="支票" value="支票" />
            <el-option label="现金" value="现金" />
            <el-option label="承兑汇票" value="承兑汇票" />
          </el-select>
        </el-form-item>
        <el-form-item label="收款人">
          <el-input v-model="formData.receiver" placeholder="请输入收款人" />
        </el-form-item>
        <el-form-item label="收款账户">
          <el-input v-model="formData.receiveBankAccount" placeholder="请输入收款银行账户" />
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
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance } from 'element-plus'
import { getPaymentReceivedPage, createPaymentReceived, updatePaymentReceived, deletePaymentReceived } from '@/api/finance'
import { getProjectList } from '@/api/project'

const formRef = ref<FormInstance>()
const loading = ref(false)
const tableData = ref<any[]>([])
const total = ref(0)
const projectList = ref<any[]>([])
const dialogVisible = ref(false)
const dialogTitle = ref('新增回款登记')
const submitLoading = ref(false)

const queryParams = ref({
  pageNum: 1,
  pageSize: 10,
  projectId: undefined as number | undefined
})

const formData = ref({
  id: undefined as number | undefined,
  projectId: undefined as number | undefined,
  receiveAmount: 0,
  receiveDate: '',
  receiveType: '银行转账',
  receiver: '',
  receiveBankAccount: ''
})

const formRules = {
  projectId: [{ required: true, message: '请选择项目', trigger: 'change' }],
  receiveAmount: [{ required: true, message: '请输入回款金额', trigger: 'blur' }],
  receiveDate: [{ required: true, message: '请选择回款日期', trigger: 'change' }]
}

function formatMoney(val: number) {
  if (!val && val !== 0) return '-'
  return val.toLocaleString('zh-CN', { minimumFractionDigits: 2 })
}

async function searchProject(query: string) {
  const res: any = await getProjectList({ projectName: query })
  projectList.value = res.data || []
}

async function loadData() {
  loading.value = true
  try {
    const res: any = await getPaymentReceivedPage(queryParams.value)
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
  queryParams.value = { pageNum: 1, pageSize: 10, projectId: undefined }
  loadData()
}

function handleAdd() {
  dialogTitle.value = '新增回款登记'
  formData.value = { id: undefined, projectId: undefined, receiveAmount: 0, receiveDate: '', receiveType: '银行转账', receiver: '', receiveBankAccount: '' }
  dialogVisible.value = true
}

function handleEdit(row: any) {
  dialogTitle.value = '编辑回款登记'
  formData.value = { ...row }
  dialogVisible.value = true
}

async function handleFormSubmit() {
  await formRef.value?.validate()
  submitLoading.value = true
  try {
    if (formData.value.id) {
      await updatePaymentReceived({ ...formData.value, id: formData.value.id! })
      ElMessage.success('更新成功')
    } else {
      await createPaymentReceived(formData.value)
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    loadData()
  } finally {
    submitLoading.value = false
  }
}

async function handleDelete(row: any) {
  await ElMessageBox.confirm('确定要删除吗？', '提示', { type: 'warning' })
  await deletePaymentReceived(row.id)
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
</style>
