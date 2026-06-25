<template>
  <div class="invoice-apply-form">
    <el-form ref="formRef" :model="form" :rules="formRules" label-width="120px">
      <el-form-item label="所属项目" prop="projectId">
        <el-select v-model="form.projectId" placeholder="请选择项目" style="width: 100%">
          <el-option v-for="p in projects" :key="p.id" :label="p.projectName" :value="p.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="开票金额" prop="amount">
        <el-input-number v-model="form.amount" :min="0" :precision="2" style="width: 100%" />
      </el-form-item>
      <el-form-item label="发票类型" prop="invoiceType">
        <el-select v-model="form.invoiceType" style="width: 100%">
          <el-option label="增值税专用发票" value="增值税专用发票" />
          <el-option label="增值税普通发票" value="增值税普通发票" />
        </el-select>
      </el-form-item>
      <el-form-item label="购方名称" prop="buyerName">
        <el-input v-model="form.buyerName" placeholder="请输入购方名称" />
      </el-form-item>
      <el-form-item label="购方税号" prop="buyerTaxNo">
        <el-input v-model="form.buyerTaxNo" placeholder="请输入税号" />
      </el-form-item>
      <el-form-item label="开票内容" prop="content">
        <el-input v-model="form.content" placeholder="如：工程款" />
      </el-form-item>
      <el-form-item label="申请日期" prop="applyDate">
        <el-date-picker v-model="form.applyDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
      </el-form-item>
      <el-form-item label="备注">
        <el-input v-model="form.remark" type="textarea" :rows="3" placeholder="请输入备注" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">提交申请</el-button>
      </el-form-item>
    </el-form>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import type { FormInstance } from 'element-plus'
import { createInvoiceApply } from '@/api/finance'

const formRef = ref<FormInstance>()
const submitting = ref(false)
const projects = ref<any[]>([])

const form = ref({
  projectId: null as number | null,
  amount: 0,
  invoiceType: '增值税专用发票',
  buyerName: '',
  buyerTaxNo: '',
  content: '',
  applyDate: '',
  remark: ''
})

const formRules = {
  projectId: [{ required: true, message: '请选择项目', trigger: 'change' }],
  amount: [{ required: true, message: '请输入开票金额', trigger: 'blur' }],
  invoiceType: [{ required: true, message: '请选择发票类型', trigger: 'change' }],
  buyerName: [{ required: true, message: '请输入购方名称', trigger: 'blur' }]
}

async function handleSubmit() {
  await formRef.value?.validate()
  submitting.value = true
  try {
    await createInvoiceApply(form.value)
  } finally {
    submitting.value = false
  }
}
</script>
