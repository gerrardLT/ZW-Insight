<template>
  <div class="tax-rate-container">
    <el-card shadow="never">
      <div class="table-toolbar">
        <el-button type="primary" @click="handleAdd">新增税率</el-button>
      </div>

      <el-table :data="tableData" v-loading="loading" border>
        <el-table-column prop="name" label="税率名称" min-width="180" show-overflow-tooltip />
        <el-table-column label="税率数值" width="140" align="center">
          <template #default="{ row }">{{ formatRate(row.rateValue) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ENABLED' ? 'success' : 'info'" size="small">
              {{ row.status === 'ENABLED' ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="180" align="center" />
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleEdit(row)">编辑</el-button>
            <el-button v-if="row.status === 'ENABLED'" link type="danger" @click="handleDelete(row)">停用</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑税率' : '新增税率'" width="480px" destroy-on-close>
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="90px">
        <el-form-item label="税率名称" prop="name">
          <el-input v-model="formData.name" placeholder="请输入税率名称" maxlength="30" show-word-limit clearable />
        </el-form-item>
        <el-form-item label="税率数值" prop="rateValue">
          <el-input-number
            v-model="formData.rateValue"
            :min="0.01"
            :max="99.99"
            :precision="2"
            :step="1"
            controls-position="right"
            style="width: 100%"
            placeholder="0.01 - 99.99"
          />
          <span class="rate-unit">%</span>
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
import type { FormInstance, FormRules } from 'element-plus'
import {
  getAllTaxRates,
  createTaxRate,
  updateTaxRate,
  deleteTaxRate,
  type TaxRateDTO
} from '@/api/tax-rate'

const formRef = ref<FormInstance>()
const loading = ref(false)
const tableData = ref<TaxRateDTO[]>([])
const dialogVisible = ref(false)
const submitLoading = ref(false)
const isEdit = ref(false)

interface TaxRateForm {
  id: number | undefined
  name: string
  rateValue: number | undefined
}

const formData = ref<TaxRateForm>({ id: undefined, name: '', rateValue: undefined })

const validateRateValue = (_rule: any, value: number | undefined, callback: (error?: Error) => void) => {
  if (value === undefined || value === null) {
    callback(new Error('请输入税率数值'))
    return
  }
  if (value < 0.01 || value > 99.99) {
    callback(new Error('税率数值需在 0.01 - 99.99 之间'))
    return
  }
  if (!/^\d+(\.\d{1,2})?$/.test(String(value))) {
    callback(new Error('税率数值最多保留 2 位小数'))
    return
  }
  callback()
}

const formRules: FormRules = {
  name: [
    { required: true, message: '请输入税率名称', trigger: 'blur' },
    { max: 30, message: '税率名称不能超过 30 个字符', trigger: 'blur' }
  ],
  rateValue: [{ required: true, validator: validateRateValue, trigger: 'blur' }]
}

function formatRate(value: number) {
  if (value === undefined || value === null) return '-'
  return `${value}%`
}

async function loadData() {
  loading.value = true
  try {
    const res: any = await getAllTaxRates()
    tableData.value = res.data || []
  } finally {
    loading.value = false
  }
}

function handleAdd() {
  isEdit.value = false
  formData.value = { id: undefined, name: '', rateValue: undefined }
  dialogVisible.value = true
}

function handleEdit(row: any) {
  isEdit.value = true
  formData.value = { id: row.id, name: row.name, rateValue: row.rateValue }
  dialogVisible.value = true
}

async function handleFormSubmit() {
  await formRef.value?.validate()
  submitLoading.value = true
  try {
    const payload = { name: formData.value.name, rateValue: formData.value.rateValue as number }
    if (isEdit.value && formData.value.id !== undefined) {
      await updateTaxRate(formData.value.id, payload)
      ElMessage.success('更新成功')
    } else {
      await createTaxRate(payload)
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    loadData()
  } finally {
    submitLoading.value = false
  }
}

async function handleDelete(row: any) {
  await ElMessageBox.confirm(`确定要停用税率「${row.name}」吗？停用后将不可用于新单据。`, '停用确认', {
    type: 'warning',
    confirmButtonText: '确定停用',
    cancelButtonText: '取消'
  })
  await deleteTaxRate(row.id)
  ElMessage.success('已停用')
  loadData()
}

onMounted(() => {
  loadData()
})
</script>

<style scoped>
.tax-rate-container {
  padding: 16px;
}
.table-toolbar {
  margin-bottom: 16px;
}
.rate-unit {
  margin-left: 8px;
  color: #909399;
}
</style>
