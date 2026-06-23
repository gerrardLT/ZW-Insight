<template>
  <div class="tender-register-container">
    <el-card shadow="never">
      <el-form :model="queryParams" inline>
        <el-form-item label="项目名称">
          <el-input v-model="queryParams.projectName" placeholder="项目名称" clearable style="width: 200px" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryParams.status" placeholder="全部" clearable style="width: 120px">
            <el-option label="报名中" value="REGISTERING" />
            <el-option label="已投标" value="SUBMITTED" />
            <el-option label="中标" value="WON" />
            <el-option label="未中标" value="LOST" />
          </el-select>
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
        <el-table-column prop="projectName" label="项目名称" min-width="200" show-overflow-tooltip />
        <el-table-column prop="tenderUnit" label="招标单位" width="150" show-overflow-tooltip />
        <el-table-column prop="registrationDeadline" label="报名截止" width="110" />
        <el-table-column prop="bidOpenDate" label="开标日期" width="110" />
        <el-table-column prop="estimatedAmount" label="预估金额(万元)" width="140" align="right">
          <template #default="{ row }">{{ row.estimatedAmount?.toLocaleString() }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 'WON' ? 'success' : row.status === 'LOST' ? 'danger' : row.status === 'SUBMITTED' ? 'warning' : 'info'" size="small">
              {{ { REGISTERING: '报名中', SUBMITTED: '已投标', WON: '中标', LOST: '未中标' }[row.status] || row.status }}
            </el-tag>
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
        <el-pagination v-model:current-page="queryParams.pageNum" v-model:page-size="queryParams.pageSize" :page-sizes="[10, 20, 50]" :total="total" layout="total, sizes, prev, pager, next, jumper" @size-change="loadData" @current-change="loadData" />
      </div>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑投标报名' : '新增投标报名'" width="600px" destroy-on-close>
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="100px">
        <el-form-item label="项目名称" prop="projectName"><el-input v-model="formData.projectName" /></el-form-item>
        <el-form-item label="招标单位" prop="tenderUnit"><el-input v-model="formData.tenderUnit" /></el-form-item>
        <el-form-item label="报名截止"><el-date-picker v-model="formData.registrationDeadline" type="date" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item>
        <el-form-item label="开标日期"><el-date-picker v-model="formData.bidOpenDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item>
        <el-form-item label="预估金额(万)"><el-input-number v-model="formData.estimatedAmount" :min="0" :precision="2" style="width: 100%" /></el-form-item>
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
import { getTenderRegisterPage, createTenderRegister, updateTenderRegister, deleteTenderRegister } from '@/api/tender'

const formRef = ref<FormInstance>()
const loading = ref(false)
const tableData = ref<any[]>([])
const total = ref(0)
const dialogVisible = ref(false)
const submitLoading = ref(false)
const isEdit = ref(false)

const queryParams = ref({ pageNum: 1, pageSize: 10, projectName: '', status: '' })
const formData = ref({ id: undefined as number | undefined, projectName: '', tenderUnit: '', registrationDeadline: '', bidOpenDate: '', estimatedAmount: 0, remark: '' })
const formRules = { projectName: [{ required: true, message: '请输入项目名称', trigger: 'blur' }], tenderUnit: [{ required: true, message: '请输入招标单位', trigger: 'blur' }] }

async function loadData() { loading.value = true; try { const res: any = await getTenderRegisterPage(queryParams.value); tableData.value = res.data?.records || []; total.value = res.data?.total || 0 } finally { loading.value = false } }
function handleSearch() { queryParams.value.pageNum = 1; loadData() }
function handleReset() { queryParams.value = { pageNum: 1, pageSize: 10, projectName: '', status: '' }; loadData() }
function handleAdd() { isEdit.value = false; formData.value = { id: undefined, projectName: '', tenderUnit: '', registrationDeadline: '', bidOpenDate: '', estimatedAmount: 0, remark: '' }; dialogVisible.value = true }
function handleEdit(row: any) { isEdit.value = true; formData.value = { ...row }; dialogVisible.value = true }
async function handleFormSubmit() { await formRef.value?.validate(); submitLoading.value = true; try { isEdit.value ? await updateTenderRegister(formData.value) : await createTenderRegister(formData.value); ElMessage.success(isEdit.value ? '更新成功' : '新增成功'); dialogVisible.value = false; loadData() } finally { submitLoading.value = false } }
async function handleDelete(row: any) { await ElMessageBox.confirm('确定要删除吗？', '提示', { type: 'warning' }); await deleteTenderRegister(row.id); ElMessage.success('删除成功'); loadData() }
onMounted(() => { loadData() })
</script>

<style scoped>
.tender-register-container { padding: 16px; }
.table-toolbar { margin-bottom: 16px; }
.pagination-wrap { margin-top: 16px; display: flex; justify-content: flex-end; }
</style>
