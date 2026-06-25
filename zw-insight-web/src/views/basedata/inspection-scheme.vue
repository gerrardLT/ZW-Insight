<template>
  <div class="inspection-scheme-container">
    <el-card shadow="never">
      <el-form :model="queryParams" inline>
        <el-form-item label="方案名称">
          <el-input v-model="queryParams.schemeName" placeholder="方案名称" clearable style="width: 200px" />
        </el-form-item>
        <el-form-item label="方案类型">
          <el-select v-model="queryParams.schemeType" placeholder="全部" clearable style="width: 140px">
            <el-option label="质量检查" value="QUALITY" />
            <el-option label="安全检查" value="SAFETY" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <div class="table-toolbar">
        <el-button type="primary" @click="handleAdd">新增方案</el-button>
      </div>

      <el-table :data="tableData" v-loading="loading" border>
        <el-table-column prop="schemeName" label="方案名称" min-width="180" show-overflow-tooltip />
        <el-table-column prop="schemeType" label="类型" width="100" align="center">
          <template #default="{ row }">
            {{ row.schemeType === 'QUALITY' ? '质量检查' : '安全检查' }}
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ENABLED' ? 'success' : 'info'" size="small">
              {{ row.status === 'ENABLED' ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="170" />
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleEdit(row)">编辑</el-button>
            <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrap">
        <el-pagination v-model:current-page="queryParams.pageNum" v-model:page-size="queryParams.pageSize" :page-sizes="[10, 20, 50]" :total="total" layout="total, sizes, prev, pager, next, jumper" @size-change="loadData" @current-change="loadData" />
      </div>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑方案' : '新增方案'" width="500px" destroy-on-close>
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="100px">
        <el-form-item label="方案名称" prop="schemeName">
          <el-input v-model="formData.schemeName" placeholder="请输入方案名称" />
        </el-form-item>
        <el-form-item label="方案类型" prop="schemeType">
          <el-select v-model="formData.schemeType" style="width: 100%">
            <el-option label="质量检查" value="QUALITY" />
            <el-option label="安全检查" value="SAFETY" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="formData.remark" type="textarea" :rows="3" placeholder="请输入备注" />
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
import { getInspectionSchemePage, createInspectionScheme, updateInspectionScheme, deleteInspectionScheme } from '@/api/basedata'

const formRef = ref<FormInstance>()
const loading = ref(false)
const tableData = ref<any[]>([])
const total = ref(0)
const dialogVisible = ref(false)
const submitLoading = ref(false)
const isEdit = ref(false)

const queryParams = ref({ pageNum: 1, pageSize: 10, schemeName: '', schemeType: '' })
const formData = ref({ id: undefined as number | undefined, schemeName: '', schemeType: 'QUALITY', remark: '' })
const formRules = {
  schemeName: [{ required: true, message: '请输入方案名称', trigger: 'blur' }],
  schemeType: [{ required: true, message: '请选择方案类型', trigger: 'change' }]
}

async function loadData() {
  loading.value = true
  try {
    const res: any = await getInspectionSchemePage(queryParams.value)
    tableData.value = res.data?.records || []
    total.value = res.data?.total || 0
  } finally {
    loading.value = false
  }
}

function handleSearch() { queryParams.value.pageNum = 1; loadData() }
function handleReset() { queryParams.value = { pageNum: 1, pageSize: 10, schemeName: '', schemeType: '' }; loadData() }
function handleAdd() { isEdit.value = false; formData.value = { id: undefined, schemeName: '', schemeType: 'QUALITY', remark: '' }; dialogVisible.value = true }
function handleEdit(row: any) { isEdit.value = true; formData.value = { ...row }; dialogVisible.value = true }

async function handleFormSubmit() {
  await formRef.value?.validate()
  submitLoading.value = true
  try {
    isEdit.value ? await updateInspectionScheme(formData.value) : await createInspectionScheme(formData.value)
    ElMessage.success(isEdit.value ? '更新成功' : '新增成功')
    dialogVisible.value = false
    loadData()
  } finally {
    submitLoading.value = false
  }
}

async function handleDelete(row: any) {
  await ElMessageBox.confirm('确定要删除该方案吗？', '提示', { type: 'warning' })
  await deleteInspectionScheme(row.id)
  ElMessage.success('删除成功')
  loadData()
}

onMounted(() => { loadData() })
</script>

<style scoped>
.inspection-scheme-container { padding: 16px; }
.table-toolbar { margin-bottom: 16px; }
.pagination-wrap { margin-top: 16px; display: flex; justify-content: flex-end; }
</style>
