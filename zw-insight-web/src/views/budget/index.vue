<template>
  <div class="budget-container">
    <el-card shadow="never">
      <el-form :model="queryParams" inline>
        <el-form-item label="项目">
          <el-input v-model="queryParams.projectName" placeholder="项目名称" clearable style="width: 200px" />
        </el-form-item>
        <el-form-item label="年度">
          <el-date-picker v-model="queryParams.budgetYear" type="year" value-format="YYYY" placeholder="选择年度" style="width: 120px" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryParams.status" placeholder="全部" clearable style="width: 120px">
            <el-option label="草稿" value="DRAFT" />
            <el-option label="审批中" value="APPROVING" />
            <el-option label="已批准" value="APPROVED" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <div class="table-toolbar">
        <el-button type="primary" @click="handleAdd">新增预算编制</el-button>
      </div>

      <el-table :data="tableData" v-loading="loading" border>
        <el-table-column prop="projectName" label="项目名称" min-width="180" show-overflow-tooltip />
        <el-table-column prop="budgetYear" label="预算年度" width="100" align="center" />
        <el-table-column prop="totalAmount" label="预算总额(万元)" width="140" align="right">
          <template #default="{ row }">{{ row.totalAmount?.toLocaleString() }}</template>
        </el-table-column>
        <el-table-column prop="usedAmount" label="已用金额(万元)" width="140" align="right">
          <template #default="{ row }">{{ row.usedAmount?.toLocaleString() }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 'APPROVED' ? 'success' : row.status === 'APPROVING' ? 'warning' : 'info'" size="small">
              {{ row.status === 'APPROVED' ? '已批准' : row.status === 'APPROVING' ? '审批中' : '草稿' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="160" />
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleEdit(row)">编辑</el-button>
            <el-button v-if="row.status === 'DRAFT'" link type="success" @click="handleSubmit(row)">提交</el-button>
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

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑预算' : '新增预算编制'" width="600px" destroy-on-close>
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="100px">
        <el-form-item label="项目名称" prop="projectId">
          <el-input v-model="formData.projectName" placeholder="请输入项目名称" />
        </el-form-item>
        <el-form-item label="预算年度" prop="budgetYear">
          <el-date-picker v-model="formData.budgetYear" type="year" value-format="YYYY" placeholder="选择年度" style="width: 100%" />
        </el-form-item>
        <el-form-item label="预算总额" prop="totalAmount">
          <el-input-number v-model="formData.totalAmount" :min="0" :precision="2" style="width: 100%" />
        </el-form-item>
        <el-form-item label="科目明细">
          <el-input v-model="formData.subjectDetail" type="textarea" :rows="3" placeholder="科目及预算分配说明" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="formData.remark" type="textarea" :rows="2" />
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
import { getBudgetPage, createBudget, updateBudget, deleteBudget, submitBudget } from '@/api/budget'

const formRef = ref<FormInstance>()
const loading = ref(false)
const tableData = ref<any[]>([])
const total = ref(0)
const dialogVisible = ref(false)
const submitLoading = ref(false)
const isEdit = ref(false)

const queryParams = ref({
  pageNum: 1,
  pageSize: 10,
  projectName: '',
  budgetYear: '',
  status: ''
})

const formData = ref({
  id: undefined as number | undefined,
  projectId: undefined as number | undefined,
  projectName: '',
  budgetYear: '',
  totalAmount: 0,
  subjectDetail: '',
  remark: ''
})

const formRules = {
  projectId: [{ required: true, message: '请选择项目', trigger: 'change' }],
  budgetYear: [{ required: true, message: '请选择预算年度', trigger: 'change' }],
  totalAmount: [{ required: true, message: '请输入预算总额', trigger: 'blur' }]
}

async function loadData() {
  loading.value = true
  try {
    const res: any = await getBudgetPage(queryParams.value)
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
  queryParams.value = { pageNum: 1, pageSize: 10, projectName: '', budgetYear: '', status: '' }
  loadData()
}

function handleAdd() {
  isEdit.value = false
  formData.value = { id: undefined, projectId: undefined, projectName: '', budgetYear: '', totalAmount: 0, subjectDetail: '', remark: '' }
  dialogVisible.value = true
}

function handleEdit(row: any) {
  isEdit.value = true
  formData.value = { ...row }
  dialogVisible.value = true
}

async function handleFormSubmit() {
  await formRef.value?.validate()
  submitLoading.value = true
  try {
    if (isEdit.value) {
      await updateBudget(formData.value)
      ElMessage.success('更新成功')
    } else {
      await createBudget(formData.value)
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    loadData()
  } finally {
    submitLoading.value = false
  }
}

async function handleSubmit(row: any) {
  await ElMessageBox.confirm('确定要提交该预算编制吗？', '提示', { type: 'warning' })
  await submitBudget(row.id)
  ElMessage.success('提交成功')
  loadData()
}

async function handleDelete(row: any) {
  await ElMessageBox.confirm('确定要删除吗？', '提示', { type: 'warning' })
  await deleteBudget(row.id)
  ElMessage.success('删除成功')
  loadData()
}

onMounted(() => {
  loadData()
})
</script>

<style scoped>
.budget-container { padding: 16px; }
.table-toolbar { margin-bottom: 16px; }
.pagination-wrap { margin-top: 16px; display: flex; justify-content: flex-end; }
</style>
