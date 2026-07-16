<template>
  <div class="hr-entry-container">
    <el-card shadow="never">
      <el-form :model="queryParams" inline>
        <el-form-item label="姓名">
          <el-input v-model="queryParams.realName" placeholder="真实姓名" clearable style="width: 140px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <div class="table-toolbar">
        <el-button type="primary" @click="handleAdd">新增入职申请</el-button>
      </div>

      <el-table :data="tableData" v-loading="loading" border>
        <el-table-column prop="realName" label="姓名" width="100" />
        <el-table-column prop="username" label="登录账号" width="130" />
        <el-table-column prop="phone" label="手机号" width="130" />
        <el-table-column prop="entryDate" label="入职日期" width="120" />
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 'APPROVED' ? 'success' : 'info'" size="small">
              {{ row.status === 'APPROVED' ? '已通过' : '草稿' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleEdit(row)">编辑</el-button>
            <el-button v-if="row.status === 'DRAFT'" link type="success" @click="handleSubmitApply(row)">提交</el-button>
            <el-button v-if="row.status === 'DRAFT'" link type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrap">
        <el-pagination v-model:current-page="queryParams.page" v-model:page-size="queryParams.size" :page-sizes="[10, 20, 50]" :total="total" layout="total, sizes, prev, pager, next, jumper" @size-change="loadData" @current-change="loadData" />
      </div>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑入职申请' : '新增入职申请'" width="600px" destroy-on-close>
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="90px">
        <el-form-item label="真实姓名" prop="realName"><el-input v-model="formData.realName" /></el-form-item>
        <el-form-item label="登录账号" prop="username"><el-input v-model="formData.username" placeholder="审批通过后据此创建系统账号" /></el-form-item>
        <el-form-item label="手机号" prop="phone"><el-input v-model="formData.phone" /></el-form-item>
        <el-form-item label="性别" prop="gender">
          <el-select v-model="formData.gender" style="width: 100%">
            <el-option label="男" value="M" />
            <el-option label="女" value="F" />
          </el-select>
        </el-form-item>
        <el-form-item label="部门" prop="orgId"><OrgSelector v-model="formData.orgId" /></el-form-item>
        <el-form-item label="岗位" prop="postId"><PostSelector v-model="formData.postId" /></el-form-item>
        <el-form-item label="入职日期" prop="entryDate"><el-date-picker v-model="formData.entryDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item>
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
import { getHrEntryPage, getHrEntryDetail, createHrEntry, updateHrEntry, deleteHrEntry, submitHrEntry } from '@/api/hr'
import OrgSelector from '@/components/OrgSelector.vue'
import PostSelector from '@/components/PostSelector.vue'

const formRef = ref<FormInstance>()
const loading = ref(false)
const tableData = ref<any[]>([])
const total = ref(0)
const dialogVisible = ref(false)
const submitLoading = ref(false)
const isEdit = ref(false)

const queryParams = ref({ page: 1, size: 10, realName: '' })
const defaultForm = () => ({ id: undefined as number | undefined, realName: '', username: '', phone: '', gender: 'M', orgId: undefined as number | undefined, postId: undefined as number | undefined, entryDate: '' })
const formData = ref(defaultForm())
const formRules = {
  realName: [{ required: true, message: '请输入真实姓名', trigger: 'blur' }],
  username: [{ required: true, message: '请输入登录账号', trigger: 'blur' }],
  phone: [{ required: true, message: '请输入手机号', trigger: 'blur' }],
  orgId: [{ required: true, message: '请选择部门', trigger: 'change' }],
  postId: [{ required: true, message: '请选择岗位', trigger: 'change' }]
}

async function loadData() { loading.value = true; try { const res: any = await getHrEntryPage(queryParams.value); tableData.value = res.data?.records || []; total.value = res.data?.total || 0 } finally { loading.value = false } }
function handleSearch() { queryParams.value.page = 1; loadData() }
function handleReset() { queryParams.value = { page: 1, size: 10, realName: '' }; loadData() }
function handleAdd() { isEdit.value = false; formData.value = defaultForm(); dialogVisible.value = true }
async function handleEdit(row: any) { isEdit.value = true; const res: any = await getHrEntryDetail(row.id); formData.value = { ...defaultForm(), ...(res.data || row) }; dialogVisible.value = true }
async function handleFormSubmit() { await formRef.value?.validate(); submitLoading.value = true; try { isEdit.value ? await updateHrEntry(formData.value) : await createHrEntry(formData.value); ElMessage.success(isEdit.value ? '更新成功' : '新增成功'); dialogVisible.value = false; loadData() } finally { submitLoading.value = false } }
async function handleSubmitApply(row: any) { await ElMessageBox.confirm('提交后将发起审批，通过后自动创建系统账号，确定提交？', '提示', { type: 'warning' }); await submitHrEntry(row.id); ElMessage.success('提交成功'); loadData() }
async function handleDelete(row: any) { await ElMessageBox.confirm('确定要删除吗？', '提示', { type: 'warning' }); await deleteHrEntry(row.id); ElMessage.success('删除成功'); loadData() }
onMounted(() => { loadData() })
</script>

<style scoped>
.hr-entry-container { padding: 16px; }
.table-toolbar { margin-bottom: 16px; }
.pagination-wrap { margin-top: 16px; display: flex; justify-content: flex-end; }
</style>
