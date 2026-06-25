<template>
  <div class="material-inbound-form">
    <el-form ref="formRef" :model="form" :rules="formRules" label-width="120px">
      <el-form-item label="所属项目" prop="projectId">
        <el-select v-model="form.projectId" placeholder="请选择项目" style="width: 100%">
          <el-option v-for="p in projects" :key="p.id" :label="p.projectName" :value="p.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="材料名称" prop="materialName">
        <el-input v-model="form.materialName" placeholder="请输入材料名称" />
      </el-form-item>
      <el-form-item label="规格型号" prop="specification">
        <el-input v-model="form.specification" placeholder="请输入规格型号" />
      </el-form-item>
      <el-form-item label="单位" prop="unit">
        <el-input v-model="form.unit" placeholder="如：吨、m³、根" />
      </el-form-item>
      <el-form-item label="入库数量" prop="quantity">
        <el-input-number v-model="form.quantity" :min="0" :precision="2" style="width: 100%" />
      </el-form-item>
      <el-form-item label="单价(元)" prop="unitPrice">
        <el-input-number v-model="form.unitPrice" :min="0" :precision="2" style="width: 100%" />
      </el-form-item>
      <el-form-item label="供应商" prop="supplierName">
        <el-input v-model="form.supplierName" placeholder="请输入供应商名称" />
      </el-form-item>
      <el-form-item label="入库日期" prop="inboundDate">
        <el-date-picker v-model="form.inboundDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
      </el-form-item>
      <el-form-item label="仓库位置">
        <el-input v-model="form.warehouseLocation" placeholder="请输入仓库位置" />
      </el-form-item>
      <el-form-item label="备注">
        <el-input v-model="form.remark" type="textarea" :rows="3" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">保存入库</el-button>
      </el-form-item>
    </el-form>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import type { FormInstance } from 'element-plus'
import { createInbound } from '@/api/material'

const formRef = ref<FormInstance>()
const submitting = ref(false)
const projects = ref<any[]>([])

const form = ref({
  projectId: null as number | null,
  materialName: '',
  specification: '',
  unit: '',
  quantity: 0,
  unitPrice: 0,
  supplierName: '',
  inboundDate: '',
  warehouseLocation: '',
  remark: ''
})

const formRules = {
  projectId: [{ required: true, message: '请选择项目', trigger: 'change' }],
  materialName: [{ required: true, message: '请输入材料名称', trigger: 'blur' }],
  quantity: [{ required: true, message: '请输入入库数量', trigger: 'blur' }],
  unitPrice: [{ required: true, message: '请输入单价', trigger: 'blur' }],
  inboundDate: [{ required: true, message: '请选择入库日期', trigger: 'change' }]
}

async function handleSubmit() {
  await formRef.value?.validate()
  submitting.value = true
  try {
    await createInbound(form.value)
  } finally {
    submitting.value = false
  }
}
</script>
