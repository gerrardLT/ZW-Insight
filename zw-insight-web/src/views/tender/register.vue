<template>
  <div class="tender-register-container">
    <el-card shadow="never">
      <el-form :model="queryParams" inline>
        <el-form-item label="项目">
          <ProjectSelector v-model="queryParams.projectId" width="200px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <div class="table-toolbar">
        <el-button type="primary" @click="handleAdd">新增投标报名</el-button>
      </div>

      <el-table :data="tableData" v-loading="loading" border>
        <el-table-column prop="ownerCompany" label="业主单位" min-width="180" show-overflow-tooltip />
        <el-table-column prop="bidMethod" label="招标方式" width="120" />
        <el-table-column prop="registerDate" label="报名日期" width="110" />
        <el-table-column prop="openDate" label="开标日期" width="110" />
        <el-table-column prop="depositAmount" label="保证金(元)" width="140" align="right">
          <template #default="{ row }">{{ row.depositAmount?.toLocaleString() }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 'WON' ? 'success' : row.status === 'LOST' ? 'danger' : row.status === 'SUBMITTED' ? 'warning' : 'info'" size="small">
              {{ statusLabelMap[row.status] || row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleEdit(row)">编辑</el-button>
            <el-button v-if="row.status === 'REGISTERED'" link type="success" @click="handleSubmitApply(row)">提交</el-button>
            <el-button v-if="row.status === 'REGISTERED'" link type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrap">
        <el-pagination v-model:current-page="queryParams.page" v-model:page-size="queryParams.size" :page-sizes="[10, 20, 50]" :total="total" layout="total, sizes, prev, pager, next, jumper" @size-change="loadData" @current-change="loadData" />
      </div>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑投标报名' : '新增投标报名'" width="600px" destroy-on-close>
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="100px">
        <el-form-item label="项目" prop="projectId"><ProjectSelector v-model="formData.projectId" /></el-form-item>
        <el-form-item label="业主单位" prop="ownerCompany"><el-input v-model="formData.ownerCompany" /></el-form-item>
        <el-form-item label="招标方式" prop="bidMethod">
          <el-select v-model="formData.bidMethod" clearable style="width: 100%">
            <el-option label="公开招标" value="PUBLIC" />
            <el-option label="邀请招标" value="INVITE" />
          </el-select>
        </el-form-item>
        <el-form-item label="报名方式" prop="registerMethod"><el-input v-model="formData.registerMethod" /></el-form-item>
        <el-form-item label="投标方式" prop="tenderMethod"><el-input v-model="formData.tenderMethod" /></el-form-item>
        <el-form-item label="报名日期" prop="registerDate"><el-date-picker v-model="formData.registerDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item>
        <el-form-item label="开标日期" prop="openDate"><el-date-picker v-model="formData.openDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item>
        <el-form-item label="保证金(元)" prop="depositAmount"><el-input-number v-model="formData.depositAmount" :min="0" :precision="2" style="width: 100%" /></el-form-item>
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
import { getTenderRegisterPage, getTenderRegisterDetail, createTenderRegister, updateTenderRegister, deleteTenderRegister, submitTenderRegister } from '@/api/tender'
import ProjectSelector from '@/components/ProjectSelector.vue'

const formRef = ref<FormInstance>()
const loading = ref(false)
const tableData = ref<any[]>([])
const statusLabelMap: Record<string, string> = { REGISTERED: '报名中', SUBMITTED: '已投标', WON: '中标', LOST: '未中标' }
const total = ref(0)
const dialogVisible = ref(false)
const submitLoading = ref(false)
const isEdit = ref(false)

const queryParams = ref({ page: 1, size: 10, projectId: undefined as number | undefined })
const defaultForm = () => ({ id: undefined as number | undefined, projectId: undefined as number | undefined, ownerCompany: '', bidMethod: '', registerMethod: '', tenderMethod: '', registerDate: '', openDate: '', depositAmount: 0 })
const formData = ref(defaultForm())
const formRules = {
  projectId: [{ required: true, message: '请选择项目', trigger: 'change' }],
  ownerCompany: [{ required: true, message: '请输入业主单位', trigger: 'blur' }]
}

async function loadData() { loading.value = true; try { const res: any = await getTenderRegisterPage(queryParams.value); tableData.value = res.data?.records || []; total.value = res.data?.total || 0 } finally { loading.value = false } }
function handleSearch() { queryParams.value.page = 1; loadData() }
function handleReset() { queryParams.value = { page: 1, size: 10, projectId: undefined }; loadData() }
function handleAdd() { isEdit.value = false; formData.value = defaultForm(); dialogVisible.value = true }
async function handleEdit(row: any) { isEdit.value = true; const res: any = await getTenderRegisterDetail(row.id); formData.value = { ...defaultForm(), ...(res.data || row) }; dialogVisible.value = true }
async function handleFormSubmit() { await formRef.value?.validate(); submitLoading.value = true; try { isEdit.value ? await updateTenderRegister(formData.value) : await createTenderRegister(formData.value); ElMessage.success(isEdit.value ? '更新成功' : '新增成功'); dialogVisible.value = false; loadData() } finally { submitLoading.value = false } }
async function handleSubmitApply(row: any) { await ElMessageBox.confirm('确定要提交该投标报名吗？', '提示', { type: 'warning' }); await submitTenderRegister(row.id); ElMessage.success('提交成功'); loadData() }
async function handleDelete(row: any) { await ElMessageBox.confirm('确定要删除吗？', '提示', { type: 'warning' }); await deleteTenderRegister(row.id); ElMessage.success('删除成功'); loadData() }
onMounted(() => { loadData() })
</script>

<style scoped>
.tender-register-container { padding: 16px; }
.table-toolbar { margin-bottom: 16px; }
.pagination-wrap { margin-top: 16px; display: flex; justify-content: flex-end; }
</style>
