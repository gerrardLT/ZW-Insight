<template>
  <el-dialog v-model="visible" title="合同修订申请" width="780px">
    <el-alert title="独立审批生效；不修改签证。审批前合同与项目金额不变。" type="info" :closable="false" />
    <p>基准版本 {{ baseVersion }}，当前合同金额 {{ beforeAmount }}；修订差额 {{ (afterAmount - beforeAmount).toFixed(2) }}</p>
    <el-form label-width="100px">
      <el-form-item label="修订后金额"><el-input-number v-model="afterAmount" :min="0.01" :precision="2" /></el-form-item>
      <el-form-item label="修订原因"><el-input v-model="reason" type="textarea" :maxlength="1000" /></el-form-item>
      <el-button type="primary" :loading="busy" :disabled="!reason.trim() || afterAmount === beforeAmount" @click="create">保存申请</el-button>
    </el-form>
    <el-table :data="rows">
      <el-table-column prop="baseVersion" label="基准版本" />
      <el-table-column prop="beforeAmount" label="修订前" />
      <el-table-column prop="afterAmount" label="修订后" />
      <el-table-column prop="deltaAmount" label="差额" />
      <el-table-column prop="reason" label="原因" show-overflow-tooltip />
      <el-table-column prop="status" label="状态" />
      <el-table-column label="操作"><template #default="{ row }">
        <el-button v-if="row.status === 'DRAFT'" link :disabled="busy" @click="submit(row.id)">提交审批</el-button>
        <el-button v-if="row.status === 'DRAFT'" link :disabled="busy" @click="cancel(row.id)">作废</el-button>
        <el-button link @click="showSnapshot(row)">快照</el-button>
      </template></el-table-column>
    </el-table>
    <section v-if="snapshot"><h4>修订前快照</h4><pre>{{ snapshot.beforeSnapshot }}</pre><h4>修订后快照</h4><pre>{{ snapshot.afterSnapshot }}</pre></section>
  </el-dialog>
</template>
<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getContractDetail } from '@/api/contract'
import { getContractRevisions, createContractRevision, submitContractRevision, cancelContractRevision, type ContractRevision } from '@/api/contractRevision'
const emit = defineEmits<{ changed: [] }>()
const visible = ref(false), busy = ref(false), reason = ref('')
const contractId = ref<string | number>(''), baseVersion = ref(0), beforeAmount = ref(0), afterAmount = ref(0)
const rows = ref<ContractRevision[]>([]), snapshot = ref<ContractRevision>()
function showSnapshot(row: unknown) { snapshot.value = row as ContractRevision }
async function refresh() {
  const response = await getContractRevisions(contractId.value) as unknown as { data: ContractRevision[] }
  rows.value = response.data
}
async function open(id: string | number) {
  const { data } = await getContractDetail(id) as unknown as { data: { version: number; contractAmount: number } }
  contractId.value = id
  baseVersion.value = (data as typeof data & { version: number }).version
  beforeAmount.value = Number(data.contractAmount); afterAmount.value = beforeAmount.value
  reason.value = ''; snapshot.value = undefined
  await refresh(); visible.value = true
}
async function create() {
  busy.value = true
  try {
    await createContractRevision({ contractId: contractId.value, baseVersion: baseVersion.value, afterAmount: afterAmount.value, reason: reason.value.trim() })
    ElMessage.success('修订申请已保存'); await refresh()
  } finally { busy.value = false }
}
async function submit(id: string | number) {
  busy.value = true
  try { await submitContractRevision(id); ElMessage.success('已提交审批'); await refresh(); emit('changed') }
  finally { busy.value = false }
}
async function cancel(id: string | number) {
  busy.value = true
  try { await cancelContractRevision(id); await refresh() }
  finally { busy.value = false }
}
defineExpose({ open })
</script>
<style scoped>pre { white-space: pre-wrap; overflow-wrap: anywhere; }</style>
