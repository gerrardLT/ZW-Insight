<template>
  <div class="hr-container">
    <el-card shadow="never">
      <div class="table-toolbar">
        <el-button type="primary" @click="handleAdd">
          <el-icon><Plus /></el-icon>新增离职申请
        </el-button>
      </div>

      <el-table :data="tableData" v-loading="loading" border>
        <el-table-column prop="userName" label="姓名" width="150" show-overflow-tooltip />
        <el-table-column prop="resignDate" label="离职日期" width="130" />
        <el-table-column prop="handoverPerson" label="交接人" width="150" show-overflow-tooltip />
        <el-table-column label="是否交接" width="100" align="center">
          <template #default="{ row }">{{ row.isHandover === 1 ? '是' : '否' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)" size="small">{{ getStatusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 'DRAFT'" link type="success" @click="handleSubmitRow(row)">提交</el-button>
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

    <el-dialog v-model="dialogVisible" title="新增离职申请" width="600px" destroy-on-close>
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="100px">
        <el-form-item label="用户ID" prop="userId">
          <el-input-number v-model="formData.userId" :min="1" style="width: 100%" />
        </el-form-item>
        <el-form-item label="姓名" prop="userName">
          <el-input v-model="formData.userName" placeholder="请输入姓名" />
        </el-form-item>
        <el-form-item label="离职日期" prop="resignDate">
          <el-date-picker v-model="formData.resignDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="交接人">
          <el-input v-model="formData.handoverPerson" placeholder="选填" />
        </el-form-item>
        <el-form-item label="是否已交接">
          <el-switch v-model="formData.isHandover" :active-value="1" :inactive-value="0" />
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
import { getResignApplyPage, createResignApply, submitResignApply } from '@/api/hr'

const formRef = ref<FormInstance>()
const loading = ref(false)
const tableData = ref<any[]>([])
const total = ref(0)
const dialogVisible = ref(false)
const submitLoading = ref(false)

const queryParams = ref({
  page: 1,
  size: 10
})

const formData = ref({
  userId: undefined as number | undefined,
  userName: '',
  resignDate: '',
  handoverPerson: '',
  isHandover: 0
})

const formRules = {
  userId: [{ required: true, message: '请输入用户ID', trigger: 'blur' }],
  userName: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  resignDate: [{ required: true, message: '请选择离职日期', trigger: 'change' }]
}

const statusMap: Record<string, { label: string; type: string }> = {
  DRAFT: { label: '草稿', type: 'info' },
  APPROVING: { label: '审批中', type: 'warning' },
  APPROVED: { label: '已通过', type: 'success' }
}

function getStatusLabel(status: string) {
  return statusMap[status]?.label || status
}
function getStatusType(status: string) {
  return (statusMap[status]?.type || 'info') as any
}

async function loadData() {
  loading.value = true
  try {
    const res: any = await getResignApplyPage(queryParams.value)
    tableData.value = res.data?.records || []
    total.value = res.data?.total || 0
  } finally {
    loading.value = false
  }
}

function handleAdd() {
  formData.value = { userId: undefined, userName: '', resignDate: '', handoverPerson: '', isHandover: 0 }
  dialogVisible.value = true
}

async function handleFormSubmit() {
  await formRef.value?.validate()
  submitLoading.value = true
  try {
    await createResignApply(formData.value)
    ElMessage.success('新增成功')
    dialogVisible.value = false
    loadData()
  } finally {
    submitLoading.value = false
  }
}

async function handleSubmitRow(row: any) {
  await ElMessageBox.confirm('确定要提交该离职申请吗？', '提示', { type: 'warning' })
  await submitResignApply(row.id)
  ElMessage.success('提交成功')
  loadData()
}

onMounted(() => {
  loadData()
})
</script>

<style scoped>
.hr-container {
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
