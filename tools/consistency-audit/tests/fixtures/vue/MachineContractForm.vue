<template>
  <div class="machine-contract-form">
    <!-- 搜索表单 -->
    <el-form :model="queryParams" inline>
      <el-form-item label="合同名称">
        <el-input v-model="queryParams.contractName" placeholder="合同名称" clearable style="width: 200px" />
      </el-form-item>
      <el-form-item label="设备供应商">
        <el-input v-model="queryParams.supplierName" placeholder="设备供应商" clearable style="width: 160px" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="handleSearch">搜索</el-button>
        <el-button @click="handleReset">重置</el-button>
      </el-form-item>
    </el-form>

    <!-- 编辑表单弹窗 -->
    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑机械合同' : '新增机械合同'" width="600px">
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="100px">
        <el-form-item label="合同名称" prop="contractName">
          <el-input v-model="formData.contractName" />
        </el-form-item>
        <el-form-item label="设备供应商" prop="supplierName">
          <el-input v-model="formData.supplierName" />
        </el-form-item>
        <el-form-item label="设备名称" prop="machineName">
          <el-input v-model="formData.machineName" />
        </el-form-item>
        <el-form-item label="合同金额" prop="contractAmount">
          <el-input-number v-model="formData.contractAmount" :min="0" :precision="2" style="width: 100%" />
        </el-form-item>
        <el-form-item label="租赁方式">
          <el-select v-model="formData.rentalType" style="width: 100%">
            <el-option label="月租" value="月租" />
            <el-option label="台班" value="台班" />
            <el-option label="包月" value="包月" />
          </el-select>
        </el-form-item>
        <el-form-item label="开始日期">
          <el-date-picker v-model="formData.startDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="结束日期">
          <el-date-picker v-model="formData.endDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
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
import { ref } from 'vue'
import type { FormInstance } from 'element-plus'

const formRef = ref<FormInstance>()
const dialogVisible = ref(false)
const submitLoading = ref(false)
const isEdit = ref(false)

const queryParams = ref({
  pageNum: 1,
  pageSize: 10,
  contractName: '',
  supplierName: ''
})

const formData = ref({
  id: undefined as number | undefined,
  contractName: '',
  supplierName: '',
  machineName: '',
  contractAmount: 0,
  rentalType: '月租',
  startDate: '',
  endDate: ''
})

const formRules = {
  contractName: [{ required: true, message: '请输入合同名称', trigger: 'blur' }],
  supplierName: [{ required: true, message: '请输入供应商', trigger: 'blur' }],
  machineName: [{ required: true, message: '请输入设备名称', trigger: 'blur' }]
}

function handleSearch() { /* ... */ }
function handleReset() { queryParams.value = { pageNum: 1, pageSize: 10, contractName: '', supplierName: '' } }
async function handleFormSubmit() { /* ... */ }
</script>
