<template>
  <div class="labor-team-container">
    <el-card shadow="never">
      <el-form :model="queryParams" inline>
        <el-form-item label="班组名称">
          <el-input v-model="queryParams.teamName" placeholder="班组名称" clearable style="width: 200px" />
        </el-form-item>
        <el-form-item label="工种">
          <el-input v-model="queryParams.workType" placeholder="工种" clearable style="width: 140px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <div class="table-toolbar">
        <el-button type="primary" @click="handleAdd">新增班组</el-button>
      </div>

      <el-table :data="tableData" v-loading="loading" border>
        <el-table-column prop="teamName" label="班组名称" min-width="150" />
        <el-table-column prop="leaderName" label="班组长" width="100" />
        <el-table-column prop="leaderPhone" label="联系电话" width="130" />
        <el-table-column prop="workType" label="工种" width="100" />
        <el-table-column prop="memberCount" label="人数" width="80" align="center" />
        <el-table-column prop="projectName" label="所属项目" min-width="160" show-overflow-tooltip />
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

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑班组' : '新增班组'" width="500px" destroy-on-close>
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="90px">
        <el-form-item label="班组名称" prop="teamName"><el-input v-model="formData.teamName" /></el-form-item>
        <el-form-item label="班组长" prop="leaderName"><el-input v-model="formData.leaderName" /></el-form-item>
        <el-form-item label="联系电话"><el-input v-model="formData.leaderPhone" /></el-form-item>
        <el-form-item label="工种" prop="workType"><el-input v-model="formData.workType" /></el-form-item>
        <el-form-item label="人数"><el-input-number v-model="formData.memberCount" :min="1" style="width: 100%" /></el-form-item>
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
import { getLaborTeamPage, createLaborTeam, updateLaborTeam, deleteLaborTeam } from '@/api/labor'

const formRef = ref<FormInstance>()
const loading = ref(false)
const tableData = ref<any[]>([])
const total = ref(0)
const dialogVisible = ref(false)
const submitLoading = ref(false)
const isEdit = ref(false)

const queryParams = ref({ pageNum: 1, pageSize: 10, teamName: '', workType: '' })
const formData = ref({ id: undefined as number | undefined, teamName: '', leaderName: '', leaderPhone: '', workType: '', memberCount: 1 })
const formRules = { teamName: [{ required: true, message: '请输入班组名称', trigger: 'blur' }], leaderName: [{ required: true, message: '请输入班组长', trigger: 'blur' }], workType: [{ required: true, message: '请输入工种', trigger: 'blur' }] }

async function loadData() { loading.value = true; try { const res: any = await getLaborTeamPage(queryParams.value); tableData.value = res.data?.records || []; total.value = res.data?.total || 0 } finally { loading.value = false } }
function handleSearch() { queryParams.value.pageNum = 1; loadData() }
function handleReset() { queryParams.value = { pageNum: 1, pageSize: 10, teamName: '', workType: '' }; loadData() }
function handleAdd() { isEdit.value = false; formData.value = { id: undefined, teamName: '', leaderName: '', leaderPhone: '', workType: '', memberCount: 1 }; dialogVisible.value = true }
function handleEdit(row: any) { isEdit.value = true; formData.value = { ...row }; dialogVisible.value = true }
async function handleFormSubmit() { await formRef.value?.validate(); submitLoading.value = true; try { isEdit.value ? await updateLaborTeam(formData.value) : await createLaborTeam(formData.value); ElMessage.success(isEdit.value ? '更新成功' : '新增成功'); dialogVisible.value = false; loadData() } finally { submitLoading.value = false } }
async function handleDelete(row: any) { await ElMessageBox.confirm('确定要删除吗？', '提示', { type: 'warning' }); await deleteLaborTeam(row.id); ElMessage.success('删除成功'); loadData() }
onMounted(() => { loadData() })
</script>

<style scoped>
.labor-team-container { padding: 16px; }
.table-toolbar { margin-bottom: 16px; }
.pagination-wrap { margin-top: 16px; display: flex; justify-content: flex-end; }
</style>
