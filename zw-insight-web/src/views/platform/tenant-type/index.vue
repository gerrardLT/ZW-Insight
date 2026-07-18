<template>
  <div class="platform-container">
    <el-card shadow="never">
      <el-form :model="queryParams" inline>
        <el-form-item label="类型名称">
          <el-input v-model="queryParams.typeName" placeholder="请输入类型名称" clearable style="width: 180px" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryParams.status" placeholder="全部" clearable style="width: 120px">
            <el-option label="启用" :value="1" />
            <el-option label="停用" :value="0" />
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
          <el-icon><Plus /></el-icon>新增租户类型
        </el-button>
      </div>

      <el-table :data="tableData" v-loading="loading" border>
        <el-table-column prop="typeName" label="类型名称" min-width="180" show-overflow-tooltip />
        <el-table-column prop="durationDays" label="有效期(天)" width="120" align="right" />
        <el-table-column prop="sortOrder" label="排序" width="90" align="right" />
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
              {{ row.status === 1 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleEdit(row)">编辑</el-button>
            <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
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

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="560px" destroy-on-close>
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="100px">
        <el-form-item label="类型名称" prop="typeName">
          <el-input v-model="formData.typeName" placeholder="请输入类型名称" />
        </el-form-item>
        <el-form-item label="有效期(天)" prop="durationDays">
          <el-select v-model="formData.durationDays" placeholder="请选择有效期" style="width: 100%">
            <el-option label="30 天" :value="30" />
            <el-option label="90 天" :value="90" />
            <el-option label="180 天" :value="180" />
            <el-option label="365 天" :value="365" />
          </el-select>
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="formData.sortOrder" :min="0" style="width: 100%" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="formData.status" :active-value="1" :inactive-value="0" />
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
import {
  getTenantTypePage,
  createTenantType,
  updateTenantType,
  deleteTenantType,
  type TenantType
} from '@/api/platform'

const formRef = ref<FormInstance>()
const loading = ref(false)
const tableData = ref<any[]>([])
const total = ref(0)
const dialogVisible = ref(false)
const dialogTitle = ref('新增租户类型')
const submitLoading = ref(false)

const queryParams = ref({
  page: 1,
  size: 10,
  typeName: undefined as string | undefined,
  status: undefined as number | undefined
})

const formData = ref<TenantType>({
  id: undefined,
  typeName: '',
  durationDays: 30,
  sortOrder: 0,
  status: 1
})

const formRules = {
  typeName: [{ required: true, message: '请输入类型名称', trigger: 'blur' }],
  durationDays: [{ required: true, message: '请选择有效期', trigger: 'change' }]
}

async function loadData() {
  loading.value = true
  try {
    const res: any = await getTenantTypePage(queryParams.value)
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
  queryParams.value = { page: 1, size: 10, typeName: undefined, status: undefined }
  loadData()
}

function handleAdd() {
  dialogTitle.value = '新增租户类型'
  formData.value = { id: undefined, typeName: '', durationDays: 30, sortOrder: 0, status: 1 }
  dialogVisible.value = true
}

function handleEdit(row: any) {
  dialogTitle.value = '编辑租户类型'
  formData.value = { ...row }
  dialogVisible.value = true
}

async function handleFormSubmit() {
  await formRef.value?.validate()
  submitLoading.value = true
  try {
    if (formData.value.id) {
      await updateTenantType(formData.value.id, formData.value)
      ElMessage.success('更新成功')
    } else {
      await createTenantType(formData.value)
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    loadData()
  } finally {
    submitLoading.value = false
  }
}

async function handleDelete(row: any) {
  await ElMessageBox.confirm(`确定要删除租户类型「${row.typeName}」吗？`, '提示', { type: 'warning' })
  await deleteTenantType(row.id)
  ElMessage.success('删除成功')
  loadData()
}

onMounted(() => {
  loadData()
})
</script>

<style scoped>
.platform-container {
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
