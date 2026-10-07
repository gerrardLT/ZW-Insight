<template>
  <el-dialog
    v-model="visible"
    :title="mode === 'apply' ? '申请投标保证金' : '退还投标保证金'"
    width="500px"
    destroy-on-close
    @closed="handleClosed"
  >
    <el-alert
      v-if="mode === 'apply'"
      type="warning"
      :closable="false"
      show-icon
      class="mb-4"
    >
      <template #title>
        法律合规提醒（TI-1）：投标保证金金额依法不得超过项目概算/预算总额的 2%，申请提交后将发起线上审批流。
      </template>
    </el-alert>

    <el-alert
      v-else
      type="success"
      :closable="false"
      show-icon
      class="mb-4"
    >
      <template #title>
        开标完成后（中标或落标），已缴纳的投标保证金必须启动退还追踪（TI-4），结清资金往来。
      </template>
    </el-alert>

    <el-form
      ref="formRef"
      :model="formData"
      :rules="formRules"
      label-width="110px"
    >
      <!-- 申请模式 -->
      <template v-if="mode === 'apply'">
        <el-form-item label="申请金额(元)" prop="depositAmount">
          <el-input-number
            v-model="formData.depositAmount"
            :min="0"
            :precision="2"
            :step="1000"
            controls-position="right"
            style="width: 100%"
            placeholder="请输入保证金金额"
          />
        </el-form-item>
        <el-form-item label="拟支付日期" prop="paymentDate">
          <el-date-picker
            v-model="formData.paymentDate"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="选择支付日期"
            style="width: 100%"
          />
        </el-form-item>
      </template>

      <!-- 退还模式 -->
      <template v-else>
        <el-form-item label="退还金额(元)" prop="returnAmount">
          <el-input-number
            v-model="formData.returnAmount"
            :min="0"
            :precision="2"
            :step="1000"
            controls-position="right"
            style="width: 100%"
            placeholder="请输入退还金额"
          />
        </el-form-item>
        <el-form-item label="退还日期" prop="returnDate">
          <el-date-picker
            v-model="formData.returnDate"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="选择退还日期"
            style="width: 100%"
          />
        </el-form-item>
      </template>
    </el-form>

    <template #footer>
      <div class="dialog-footer">
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">
          {{ mode === 'apply' ? '确认申请' : '确认退还' }}
        </el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, watch, computed } from 'vue'
import { ElMessage, type FormInstance } from 'element-plus'
import { createTenderDeposit, createTenderRefund } from '@/api/tender'

const props = defineProps<{
  modelValue: boolean
  mode: 'apply' | 'refund'
  register?: any
  depositApplyId?: number | string
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'success'): void
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

const formRef = ref<FormInstance>()
const submitLoading = ref(false)

const formData = ref({
  depositAmount: undefined as number | undefined,
  paymentDate: '',
  returnAmount: undefined as number | undefined,
  returnDate: ''
})

const formRules = {
  depositAmount: [{ required: true, message: '请输入保证金金额', trigger: 'blur' }],
  paymentDate: [{ required: true, message: '请选择拟支付日期', trigger: 'change' }],
  returnAmount: [{ required: true, message: '请输入退还金额', trigger: 'blur' }],
  returnDate: [{ required: true, message: '请选择退还日期', trigger: 'change' }]
}

watch(
  () => props.modelValue,
  (val) => {
    if (val) {
      const today = new Date().toISOString().slice(0, 10)
      formData.value = {
        depositAmount: props.register?.depositAmount || undefined,
        paymentDate: today,
        returnAmount: props.register?.depositAmount || undefined,
        returnDate: today
      }
    }
  }
)

function handleClosed() {
  formRef.value?.resetFields()
}

async function handleSubmit() {
  await formRef.value?.validate()
  submitLoading.value = true
  try {
    if (props.mode === 'apply') {
      if (!props.register?.id) {
        ElMessage.error('缺少投标报名信息')
        return
      }
      await createTenderDeposit({
        registerId: props.register.id,
        projectId: props.register.projectId,
        depositAmount: formData.value.depositAmount,
        paymentDate: formData.value.paymentDate,
        status: 'DRAFT'
      })
      ElMessage.success('保证金申请已提交')
    } else {
      if (!props.depositApplyId) {
        ElMessage.error('缺少保证金申请记录 ID')
        return
      }
      await createTenderRefund({
        depositApplyId: props.depositApplyId,
        returnAmount: formData.value.returnAmount,
        returnDate: formData.value.returnDate
      })
      ElMessage.success('保证金退还记录已登记')
    }
    visible.value = false
    emit('success')
  } finally {
    submitLoading.value = false
  }
}
</script>

<style scoped>
.mb-4 {
  margin-bottom: var(--zw-space-md);
}
</style>
