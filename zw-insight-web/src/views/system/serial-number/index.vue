<template>
  <div class="serial-number-container">
    <el-card shadow="never">
      <!-- 操作栏 -->
      <div class="table-toolbar">
        <el-button type="primary" @click="handleAdd">
          <el-icon><Plus /></el-icon>新增
        </el-button>
      </div>

      <!-- 表格 -->
      <el-table :data="tableData" v-loading="loading" border>
        <el-table-column prop="businessType" label="业务类型" min-width="120" />
        <el-table-column prop="rulePrefix" label="规则前缀" width="100" />
        <el-table-column prop="dateFormat" label="日期格式" width="120" />
        <el-table-column prop="seqLength" label="序号长度" width="90" align="center" />
        <el-table-column prop="resetPeriod" label="重置周期" width="100">
          <template #default="{ row }">
            {{ row.resetPeriod === 'MONTH' ? '按月' : '按年' }}
          </template>
        </el-table-column>
        <el-table-column prop="description" label="描述" min-width="150" show-overflow-tooltip />
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="handlePreview(row as SerialNumberRule)">预览</el-button>
            <el-button link type="primary" size="small" @click="handleEdit(row as SerialNumberRule)">编辑</el-button>
            <el-button link type="danger" size="small" @click="handleDelete(row as SerialNumberRule)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 新增/编辑对话框 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="500px" destroy-on-close>
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="100px">
        <el-form-item label="业务类型" prop="businessType">
          <el-input v-model="formData.businessType" placeholder="请输入业务类型（字母/数字/下划线）" maxlength="50" />
        </el-form-item>
        <el-form-item label="规则前缀" prop="rulePrefix">
          <el-input v-model="formData.rulePrefix" placeholder="请输入规则前缀" maxlength="20" />
        </el-form-item>
        <el-form-item label="日期格式" prop="dateFormat">
          <el-select v-model="formData.dateFormat" placeholder="请选择日期格式" style="width: 100%">
            <el-option label="yyyyMMdd" value="yyyyMMdd" />
            <el-option label="yyyyMM" value="yyyyMM" />
            <el-option label="yyyy" value="yyyy" />
          </el-select>
        </el-form-item>
        <el-form-item label="序号长度" prop="seqLength">
          <el-input-number v-model="formData.seqLength" :min="1" :max="10" :step="1" :precision="0" style="width: 100%" />
        </el-form-item>
        <el-form-item label="重置周期" prop="resetPeriod">
          <el-select v-model="formData.resetPeriod" placeholder="请选择重置周期" style="width: 100%">
            <el-option label="按月 (MONTH)" value="MONTH" />
            <el-option label="按年 (YEAR)" value="YEAR" />
          </el-select>
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="formData.description" type="textarea" placeholder="请输入描述" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance } from 'element-plus'
import {
  getSerialNumberList,
  createSerialNumber,
  updateSerialNumber,
  deleteSerialNumber,
  generateSerialNumber
} from '@/api/file'
import type { SerialNumberRule } from '@/api/file'
import { validateBusinessType, validateSeqLength } from '@/utils/serial-number-validation'

const formRef = ref<FormInstance>()
const loading = ref(false)
const tableData = ref<SerialNumberRule[]>([])
const dialogVisible = ref(false)
const dialogTitle = ref('')
const submitLoading = ref(false)

const formData = ref<SerialNumberRule>({
  id: undefined,
  businessType: '',
  rulePrefix: '',
  dateFormat: '',
  seqLength: 4,
  resetPeriod: '',
  description: ''
})

const formRules = {
  businessType: [
    { required: true, message: '请输入业务类型', trigger: 'blur' },
    {
      validator: (_rule: any, value: string, callback: (error?: Error) => void) => {
        if (!value) return callback()
        if (validateBusinessType(value)) {
          callback()
        } else {
          callback(new Error('仅允许字母、数字和下划线，长度不超过50'))
        }
      },
      trigger: 'blur'
    }
  ],
  rulePrefix: [
    { required: true, message: '请输入规则前缀', trigger: 'blur' },
    { max: 20, message: '长度不超过20字符', trigger: 'blur' }
  ],
  dateFormat: [
    { required: true, message: '请选择日期格式', trigger: 'change' }
  ],
  seqLength: [
    { required: true, message: '请输入序号长度', trigger: 'blur' },
    {
      validator: (_rule: any, value: number, callback: (error?: Error) => void) => {
        if (value === undefined || value === null) return callback()
        if (validateSeqLength(value)) {
          callback()
        } else {
          callback(new Error('序号长度为1-10的整数'))
        }
      },
      trigger: 'blur'
    }
  ],
  resetPeriod: [
    { required: true, message: '请选择重置周期', trigger: 'change' }
  ]
}

async function loadData() {
  loading.value = true
  try {
    const res: any = await getSerialNumberList()
    tableData.value = res.data || []
  } finally {
    loading.value = false
  }
}

function handleAdd() {
  dialogTitle.value = '新增编号规则'
  formData.value = {
    id: undefined,
    businessType: '',
    rulePrefix: '',
    dateFormat: '',
    seqLength: 4,
    resetPeriod: '',
    description: ''
  }
  dialogVisible.value = true
}

function handleEdit(row: SerialNumberRule) {
  dialogTitle.value = '编辑编号规则'
  formData.value = { ...row }
  dialogVisible.value = true
}

async function handleSubmit() {
  await formRef.value?.validate()
  submitLoading.value = true
  try {
    if (formData.value.id) {
      await updateSerialNumber(formData.value.id, formData.value)
      ElMessage.success('更新成功')
    } else {
      await createSerialNumber(formData.value)
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    loadData()
  } catch (error: any) {
    ElMessage.error(error?.message || '操作失败')
  } finally {
    submitLoading.value = false
  }
}

async function handleDelete(row: SerialNumberRule) {
  await ElMessageBox.confirm('确定要删除该编号规则吗？', '提示', { type: 'warning' })
  try {
    await deleteSerialNumber(row.id!)
    ElMessage.success('删除成功')
    loadData()
  } catch (error: any) {
    ElMessage.error(error?.message || '删除失败')
  }
}

async function handlePreview(row: SerialNumberRule) {
  try {
    const res: any = await generateSerialNumber(row.businessType)
    ElMessage.success(`生成编号：${res.data || res}`)
  } catch (error: any) {
    ElMessage.error(error?.message || '预览生成失败')
  }
}

onMounted(() => {
  loadData()
})
</script>

<style scoped>
.serial-number-container {
  padding: 16px;
}
.table-toolbar {
  margin-bottom: 16px;
}
</style>
