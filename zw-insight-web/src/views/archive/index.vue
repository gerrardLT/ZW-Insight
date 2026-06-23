<template>
  <div class="archive-container">
    <el-card shadow="never">
      <el-tabs v-model="activeTab" @tab-change="handleTabChange">
        <el-tab-pane label="合同档案" name="CONTRACT" />
        <el-tab-pane label="项目档案" name="PROJECT" />
        <el-tab-pane label="财务档案" name="FINANCE" />
        <el-tab-pane label="技术档案" name="TECHNICAL" />
        <el-tab-pane label="人事档案" name="HR" />
      </el-tabs>

      <el-form :model="queryParams" inline>
        <el-form-item label="档案名称">
          <el-input v-model="queryParams.archiveName" placeholder="档案名称" clearable style="width: 200px" />
        </el-form-item>
        <el-form-item label="归档日期">
          <el-date-picker v-model="queryParams.archiveDate" type="date" value-format="YYYY-MM-DD" style="width: 150px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <div class="table-toolbar">
        <el-button type="primary" @click="handleAdd">新增档案</el-button>
      </div>

      <el-table :data="tableData" v-loading="loading" border>
        <el-table-column prop="archiveNo" label="档案编号" width="150" />
        <el-table-column prop="archiveName" label="档案名称" min-width="200" show-overflow-tooltip />
        <el-table-column prop="categoryName" label="所属分类" width="120" />
        <el-table-column prop="projectName" label="关联项目" min-width="160" show-overflow-tooltip />
        <el-table-column prop="archiveDate" label="归档日期" width="110" />
        <el-table-column prop="keeper" label="保管人" width="90" />
        <el-table-column prop="location" label="存放位置" width="120" />
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

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑档案' : '新增档案'" width="600px" destroy-on-close>
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="90px">
        <el-form-item label="档案名称" prop="archiveName"><el-input v-model="formData.archiveName" /></el-form-item>
        <el-form-item label="档案分类"><el-select v-model="formData.category" style="width: 100%"><el-option label="合同档案" value="CONTRACT" /><el-option label="项目档案" value="PROJECT" /><el-option label="财务档案" value="FINANCE" /><el-option label="技术档案" value="TECHNICAL" /><el-option label="人事档案" value="HR" /></el-select></el-form-item>
        <el-form-item label="关联项目"><el-input v-model="formData.projectName" /></el-form-item>
        <el-form-item label="归档日期"><el-date-picker v-model="formData.archiveDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item>
        <el-form-item label="保管人"><el-input v-model="formData.keeper" /></el-form-item>
        <el-form-item label="存放位置"><el-input v-model="formData.location" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="formData.remark" type="textarea" :rows="2" /></el-form-item>
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
import { getArchivePage, createArchive, updateArchive, deleteArchive } from '@/api/archive'

const formRef = ref<FormInstance>()
const loading = ref(false)
const tableData = ref<any[]>([])
const total = ref(0)
const dialogVisible = ref(false)
const submitLoading = ref(false)
const isEdit = ref(false)
const activeTab = ref('CONTRACT')

const queryParams = ref({ pageNum: 1, pageSize: 10, archiveName: '', archiveDate: '', category: 'CONTRACT' })
const formData = ref({ id: undefined as number | undefined, archiveName: '', category: 'CONTRACT', projectName: '', archiveDate: '', keeper: '', location: '', remark: '' })
const formRules = { archiveName: [{ required: true, message: '请输入档案名称', trigger: 'blur' }] }

function handleTabChange(tab: string) { queryParams.value.category = tab; queryParams.value.pageNum = 1; loadData() }
async function loadData() { loading.value = true; try { const res: any = await getArchivePage(queryParams.value); tableData.value = res.data?.records || []; total.value = res.data?.total || 0 } finally { loading.value = false } }
function handleSearch() { queryParams.value.pageNum = 1; loadData() }
function handleReset() { queryParams.value = { pageNum: 1, pageSize: 10, archiveName: '', archiveDate: '', category: activeTab.value }; loadData() }
function handleAdd() { isEdit.value = false; formData.value = { id: undefined, archiveName: '', category: activeTab.value, projectName: '', archiveDate: '', keeper: '', location: '', remark: '' }; dialogVisible.value = true }
function handleEdit(row: any) { isEdit.value = true; formData.value = { ...row }; dialogVisible.value = true }
async function handleFormSubmit() { await formRef.value?.validate(); submitLoading.value = true; try { isEdit.value ? await updateArchive(formData.value) : await createArchive(formData.value); ElMessage.success(isEdit.value ? '更新成功' : '新增成功'); dialogVisible.value = false; loadData() } finally { submitLoading.value = false } }
async function handleDelete(row: any) { await ElMessageBox.confirm('确定要删除吗？', '提示', { type: 'warning' }); await deleteArchive(row.id); ElMessage.success('删除成功'); loadData() }
onMounted(() => { loadData() })
</script>

<style scoped>
.archive-container { padding: 16px; }
.table-toolbar { margin-bottom: 16px; }
.pagination-wrap { margin-top: 16px; display: flex; justify-content: flex-end; }
</style>
