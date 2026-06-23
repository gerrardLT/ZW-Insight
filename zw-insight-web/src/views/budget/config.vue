<template>
  <div class="budget-config-container">
    <el-card shadow="never">
      <el-form :model="queryParams" inline>
        <el-form-item label="科目名称">
          <el-input v-model="queryParams.subjectName" placeholder="科目名称" clearable style="width: 200px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <div class="table-toolbar">
        <el-button type="primary" @click="handleAdd">新增预算科目配置</el-button>
      </div>

      <el-table :data="tableData" v-loading="loading" border>
        <el-table-column prop="subjectCode" label="科目编码" width="120" />
        <el-table-column prop="subjectName" label="科目名称" min-width="160" />
        <el-table-column prop="parentName" label="上级科目" width="140" />
        <el-table-column prop="controlType" label="控制方式" width="120">
          <template #default="{ row }">
            <el-tag size="small">{{ row.controlType === 'HARD' ? '强控' : '弱控' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="warningRate" label="预警比例(%)" width="120" align="center" />
        <el-table-column prop="enabled" label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.enabled ? 'success' : 'danger'" size="small">{{ row.enabled ? '启用' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
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

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑配置' : '新增预算配置'" width="500px" destroy-on-close>
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="100px">
        <el-form-item label="科目编码" prop="subjectCode">
          <el-input v-model="formData.subjectCode" placeholder="请输入科目编码" />
        </el-form-item>
        <el-form-item label="科目名称" prop="subjectName">
          <el-input v-model="formData.subjectName" placeholder="请输入科目名称" />
        </el-form-item>
        <el-form-item label="上级科目">
          <el-input v-model="formData.parentName" placeholder="请输入上级科目" />
        </el-form-item>
        <el-form-item label="控制方式" prop="controlType">
          <el-radio-group v-model="formData.controlType">
            <el-radio value="HARD">强控</el-radio>
            <el-radio value="SOFT">弱控</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="预警比例(%)">
          <el-input-number v-model="formData.warningRate" :min="0" :max="100" style="width: 100%" />
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="formData.enabled" />
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
import { getBudgetConfigList, saveBudgetConfig, updateBudgetConfig, deleteBudgetConfig } from '@/api/budget'

const formRef = ref<FormInstance>()
const loading = ref(false)
const tableData = ref<any[]>([])
const total = ref(0)
const dialogVisible = ref(false)
const submitLoading = ref(false)
const isEdit = ref(false)

const queryParams = ref({ pageNum: 1, pageSize: 10, subjectName: '' })

const formData = ref({
  id: undefined as number | undefined,
  subjectCode: '',
  subjectName: '',
  parentName: '',
  controlType: 'HARD',
  warningRate: 80,
  enabled: true
})

const formRules = {
  subjectCode: [{ required: true, message: '请输入科目编码', trigger: 'blur' }],
  subjectName: [{ required: true, message: '请输入科目名称', trigger: 'blur' }],
  controlType: [{ required: true, message: '请选择控制方式', trigger: 'change' }]
}

async function loadData() {
  loading.value = true
  try {
    const res: any = await getBudgetConfigList(queryParams.value)
    tableData.value = res.data?.records || res.data || []
    total.value = res.data?.total || tableData.value.length
  } finally {
    loading.value = false
  }
}

function handleSearch() { queryParams.value.pageNum = 1; loadData() }
function handleReset() { queryParams.value = { pageNum: 1, pageSize: 10, subjectName: '' }; loadData() }

function handleAdd() {
  isEdit.value = false
  formData.value = { id: undefined, subjectCode: '', subjectName: '', parentName: '', controlType: 'HARD', warningRate: 80, enabled: true }
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
      await updateBudgetConfig(formData.value)
    } else {
      await saveBudgetConfig(formData.value)
    }
    ElMessage.success(isEdit.value ? '更新成功' : '新增成功')
    dialogVisible.value = false
    loadData()
  } finally {
    submitLoading.value = false
  }
}

async function handleDelete(row: any) {
  await ElMessageBox.confirm('确定要删除吗？', '提示', { type: 'warning' })
  await deleteBudgetConfig(row.id)
  ElMessage.success('删除成功')
  loadData()
}

onMounted(() => { loadData() })
</script>

<style scoped>
.budget-config-container { padding: 16px; }
.table-toolbar { margin-bottom: 16px; }
.pagination-wrap { margin-top: 16px; display: flex; justify-content: flex-end; }
</style>
