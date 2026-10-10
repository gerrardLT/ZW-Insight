<template>
  <div class="supplier-evaluation-container">
    <el-card shadow="never">
      <el-form :model="queryParams" inline>
        <el-form-item label="供应商名称">
          <el-input v-model="queryParams.supplierName" placeholder="供应商名称" clearable style="width: 200px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <div class="table-toolbar">
        <el-button type="primary" @click="handleAdd">新增评价</el-button>
      </div>

      <!-- 2026-10-10：原表单绑 score/content、且无 supplierId，而实体 BizSupplierEvaluation 的字段是
           qualityScore/timelinessScore/priceScore/serviceScore/cooperationScore(均 1-5 分)+remark，
           totalScore 由 Service 按五项均分计算；biz_supplier_evaluation.supplier_id 为 NOT NULL 无默认值。
           原实现 → 新增必报 ERROR 1364，且即便插入成功总分也恒为 0。现按实体真实字段重建。 -->
      <el-table :data="tableData" v-loading="loading" border>
        <el-table-column prop="supplierName" label="供应商名称" min-width="160" show-overflow-tooltip />
        <el-table-column prop="totalScore" label="综合评分" width="90" align="center" />
        <el-table-column prop="qualityScore" label="质量" width="70" align="center" />
        <el-table-column prop="timelinessScore" label="交期" width="70" align="center" />
        <el-table-column prop="priceScore" label="价格" width="70" align="center" />
        <el-table-column prop="serviceScore" label="服务" width="70" align="center" />
        <el-table-column prop="cooperationScore" label="配合" width="70" align="center" />
        <el-table-column prop="evaluationDate" label="评价日期" width="110" />
        <el-table-column prop="remark" label="备注" min-width="160" show-overflow-tooltip />
        <el-table-column label="操作" width="80" fixed="right">
          <template #default="{ row }">
            <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrap">
        <el-pagination v-model:current-page="queryParams.pageNum" v-model:page-size="queryParams.pageSize" :page-sizes="[10, 20, 50]" :total="total" layout="total, sizes, prev, pager, next, jumper" @size-change="loadData" @current-change="loadData" />
      </div>
    </el-card>

    <el-dialog v-model="dialogVisible" title="新增评价" width="560px" destroy-on-close>
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="100px">
        <el-form-item label="供应商" prop="supplierId">
          <SupplierSelector v-model="formData.supplierId" @change="onSupplierChange" />
        </el-form-item>
        <el-form-item label="评价类型">
          <el-select v-model="formData.evaluationType" style="width: 100%">
            <el-option label="人工评价" value="MANUAL" />
            <el-option label="系统自动" value="AUTO" />
          </el-select>
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12"><el-form-item label="质量(1-5)"><el-input-number v-model="formData.qualityScore" :min="1" :max="5" style="width: 100%" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="交期(1-5)"><el-input-number v-model="formData.timelinessScore" :min="1" :max="5" style="width: 100%" /></el-form-item></el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12"><el-form-item label="价格(1-5)"><el-input-number v-model="formData.priceScore" :min="1" :max="5" style="width: 100%" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="服务(1-5)"><el-input-number v-model="formData.serviceScore" :min="1" :max="5" style="width: 100%" /></el-form-item></el-col>
        </el-row>
        <el-form-item label="配合(1-5)"><el-input-number v-model="formData.cooperationScore" :min="1" :max="5" style="width: 100%" /></el-form-item>
        <el-form-item label="评价日期"><el-date-picker v-model="formData.evaluationDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="formData.remark" type="textarea" :rows="3" placeholder="请输入评价备注" /></el-form-item>
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
import { getSupplierEvaluationPage, createSupplierEvaluation, deleteSupplierEvaluation } from '@/api/basedata'
import SupplierSelector from '@/components/SupplierSelector.vue'

const formRef = ref<FormInstance>()
const loading = ref(false)
const tableData = ref<any[]>([])
const total = ref(0)
const dialogVisible = ref(false)
const submitLoading = ref(false)

const queryParams = ref({ pageNum: 1, pageSize: 10, supplierName: '' })

function defaultForm() {
  return {
    supplierId: undefined as number | string | undefined,
    supplierName: '',
    evaluationType: 'MANUAL',
    qualityScore: 5,
    timelinessScore: 5,
    priceScore: 5,
    serviceScore: 5,
    cooperationScore: 5,
    evaluationDate: '',
    remark: ''
  }
}
const formData = ref(defaultForm())
const formRules = {
  supplierId: [{ required: true, message: '请选择供应商', trigger: 'change' }]
}

function onSupplierChange(_val: number | string | undefined, item: any) {
  formData.value.supplierName = item?.supplierName || ''
}

async function loadData() {
  loading.value = true
  try {
    const res: any = await getSupplierEvaluationPage(queryParams.value)
    tableData.value = res.data?.records || []
    total.value = res.data?.total || 0
  } finally {
    loading.value = false
  }
}

function handleSearch() { queryParams.value.pageNum = 1; loadData() }
function handleReset() { queryParams.value = { pageNum: 1, pageSize: 10, supplierName: '' }; loadData() }
function handleAdd() { formData.value = defaultForm(); dialogVisible.value = true }

async function handleFormSubmit() {
  await formRef.value?.validate()
  submitLoading.value = true
  try {
    await createSupplierEvaluation(formData.value)
    ElMessage.success('新增成功')
    dialogVisible.value = false
    loadData()
  } finally {
    submitLoading.value = false
  }
}

async function handleDelete(row: any) {
  await ElMessageBox.confirm('确定要删除该评价吗？', '提示', { type: 'warning' })
  await deleteSupplierEvaluation(row.id)
  ElMessage.success('删除成功')
  loadData()
}

onMounted(() => { loadData() })
</script>

<style scoped>
.supplier-evaluation-container { padding: var(--zw-space-md); }
.table-toolbar { margin-bottom: var(--zw-space-md); }
.pagination-wrap { margin-top: var(--zw-space-md); display: flex; justify-content: flex-end; }
</style>
