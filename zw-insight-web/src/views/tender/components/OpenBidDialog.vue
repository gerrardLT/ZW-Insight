<template>
  <el-dialog
    v-model="visible"
    title="录入开标结果"
    width="540px"
    destroy-on-close
    @closed="handleClosed"
  >
    <el-alert
      type="info"
      :closable="false"
      show-icon
      class="mb-4"
    >
      <template #title>
        开标登记为投标收束节点，录入后将自动驱动项目状态机（中标流转为 WON，未中标流转为 LOST），并联动释放押证人员。
      </template>
    </el-alert>

    <el-form
      ref="formRef"
      :model="formData"
      :rules="formRules"
      label-width="110px"
    >
      <el-form-item label="开标结果" prop="isWon">
        <el-radio-group v-model="formData.isWon">
          <el-radio :value="1">
            <el-tag type="success" effect="dark" size="small">中标 (WON)</el-tag>
          </el-radio>
          <el-radio :value="0">
            <el-tag type="danger" effect="dark" size="small">未中标 (LOST)</el-tag>
          </el-radio>
        </el-radio-group>
      </el-form-item>

      <el-form-item label="中标/报价金额" prop="bidAmount">
        <el-input-number
          v-model="formData.bidAmount"
          :min="0"
          :precision="2"
          :step="1000"
          controls-position="right"
          style="width: 100%"
          placeholder="请输入中标金额或我方最终报价（元）"
        />
      </el-form-item>

      <!-- 落标原因分类（B4：未中标时必选） -->
      <el-form-item
        v-if="formData.isWon === 0"
        label="落标原因分类"
        prop="lostReasonCategory"
      >
        <el-select
          v-model="formData.lostReasonCategory"
          placeholder="请选择落标主因"
          style="width: 100%"
        >
          <el-option
            v-for="item in lostReasonOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
      </el-form-item>

      <el-form-item label="开标说明" prop="winInfo">
        <el-input
          v-model="formData.winInfo"
          type="textarea"
          :rows="3"
          :placeholder="formData.isWon === 1 ? '请填写中标通知书编号、候选人排序及关键说明' : '请填写实际中标单位、中标价或落标复盘分析'"
        />
      </el-form-item>
    </el-form>

    <template #footer>
      <div class="dialog-footer">
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">
          确认录入
        </el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, watch, computed } from 'vue'
import { ElMessage, type FormInstance } from 'element-plus'
import { createTenderOpen } from '@/api/tender'

const props = defineProps<{
  modelValue: boolean
  register?: any
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

const lostReasonOptions = [
  { value: 'PRICE_OVER', label: '报价偏高（高于基准或竞争对手）' },
  { value: 'TECH_WEAK', label: '技术标失分（施工方案/业绩/技术偏差）' },
  { value: 'BIZ_DEVIATION', label: '商务偏离（付款方式/工期承诺/响应度）' },
  { value: 'CREDIT_LACK', label: '资信不足（企业资质/财务指标/同类经验）' },
  { value: 'OTHER', label: '其他原因' }
]

const formData = ref({
  isWon: 1,
  bidAmount: undefined as number | undefined,
  winInfo: '',
  lostReasonCategory: ''
})

const formRules = {
  isWon: [{ required: true, message: '请选择开标结果', trigger: 'change' }],
  bidAmount: [{ required: true, message: '请输入开标金额', trigger: 'blur' }],
  lostReasonCategory: [
    {
      validator: (_rule: any, value: string, callback: any) => {
        if (formData.value.isWon === 0 && !value) {
          callback(new Error('未中标时必须选择落标原因分类'))
        } else {
          callback()
        }
      },
      trigger: 'change'
    }
  ]
}

watch(
  () => props.modelValue,
  (val) => {
    if (val) {
      formData.value = {
        isWon: 1,
        bidAmount: undefined,
        winInfo: '',
        lostReasonCategory: ''
      }
    }
  }
)

function handleClosed() {
  formRef.value?.resetFields()
}

async function handleSubmit() {
  if (!props.register?.id) {
    ElMessage.error('缺少投标报名信息')
    return
  }
  await formRef.value?.validate()
  submitLoading.value = true
  try {
    const payload: any = {
      registerId: props.register.id,
      projectId: props.register.projectId,
      isWon: formData.value.isWon,
      bidAmount: formData.value.bidAmount,
      winInfo: formData.value.winInfo,
      status: formData.value.isWon === 1 ? 'WON' : 'LOST'
    }
    if (formData.value.isWon === 0) {
      payload.lostReasonCategory = formData.value.lostReasonCategory
    }
    await createTenderOpen(payload)
    ElMessage.success('开标结果录入成功')
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
