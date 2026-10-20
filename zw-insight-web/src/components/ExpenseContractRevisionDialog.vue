<template>
  <el-dialog
    :model-value="visible"
    :title="`${categoryLabel}修订申请`"
    width="780px"
    destroy-on-close
    @update:model-value="emit('update:visible', $event)"
  >
    <el-alert title="独立审批生效；审批前合同金额不变。修订后金额不得低于已发生累计值。" type="info" :closable="false" />
    <p class="revision-summary">
      基准版本 {{ baseVersion }}，当前合同金额 {{ money(beforeAmount) }}；修订差额 {{ money(Number(afterAmount) - Number(beforeAmount)) }}
    </p>
    <el-form label-width="100px">
      <el-form-item label="修订后金额">
        <el-input-number v-model="afterAmount" :min="0.01" :precision="2" :step="1000" style="width: 220px" />
      </el-form-item>
      <el-form-item label="修订原因">
        <el-input v-model="reason" type="textarea" :maxlength="1000" show-word-limit />
      </el-form-item>
      <el-button type="primary" :loading="busy" :disabled="!reason.trim() || Number(afterAmount) === Number(beforeAmount)" @click="create">保存申请</el-button>
    </el-form>
    <el-table :data="rows" v-loading="loading" size="small" class="revision-table">
      <el-table-column prop="baseVersion" label="基准版本" width="90" />
      <el-table-column label="修订前" width="120" align="right">
        <template #default="{ row }">{{ money(row.beforeAmount) }}</template>
      </el-table-column>
      <el-table-column label="修订后" width="120" align="right">
        <template #default="{ row }">{{ money(row.afterAmount) }}</template>
      </el-table-column>
      <el-table-column label="差额" width="120" align="right">
        <template #default="{ row }">{{ money(row.deltaAmount) }}</template>
      </el-table-column>
      <el-table-column prop="reason" label="原因" show-overflow-tooltip />
      <el-table-column prop="status" label="状态" width="90" />
      <el-table-column label="操作" width="160">
        <template #default="{ row }">
          <el-button v-if="row.status === 'DRAFT'" link :disabled="busy" @click="submit(row.id)">提交审批</el-button>
          <el-button v-if="row.status === 'DRAFT'" link :disabled="busy" @click="cancel(row.id)">作废</el-button>
          <el-button link @click="showSnapshot(row)">快照</el-button>
        </template>
      </el-table-column>
    </el-table>
    <section v-if="snapshot" class="revision-snapshot">
      <h4>修订前快照</h4>
      <pre>{{ snapshot.beforeSnapshot }}</pre>
      <h4>修订后快照</h4>
      <pre>{{ snapshot.afterSnapshot }}</pre>
    </section>
  </el-dialog>
</template>

<script setup lang="ts">
/**
 * ExpenseContractRevisionDialog — 支出合同族通用修订弹窗（V2026_123）。
 * 四类支出合同（采购/劳务/机械/分包）共用统一修订表与审批流程；
 * 修订后金额下限由后端各模块 handler 校验（已发生产值/结算/收票/付款），前端只做必填提示。
 */
import { computed, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getContractRevisions,
  createContractRevision,
  submitContractRevision,
  cancelContractRevision,
  type ContractRevision
} from '@/api/contractRevision'

const props = withDefaults(defineProps<{
  visible: boolean
  category: 'PURCHASE' | 'LABOR' | 'MACHINE' | 'SUBCONTRACT'
  contractId?: string | number
  baseVersion?: number
  beforeAmount?: number | string
}>(), {
  baseVersion: 0,
  beforeAmount: 0
})

const emit = defineEmits<{
  (e: 'update:visible', value: boolean): void
  (e: 'changed'): void
}>()

const CATEGORY_LABEL: Record<string, string> = {
  PURCHASE: '采购合同',
  LABOR: '劳务合同',
  MACHINE: '机械合同',
  SUBCONTRACT: '分包合同'
}

const categoryLabel = computed(() => CATEGORY_LABEL[props.category] || '合同')
const afterAmount = ref<number>(Number(props.beforeAmount) || 0)
const reason = ref('')
const rows = ref<ContractRevision[]>([])
const snapshot = ref<ContractRevision>()
const busy = ref(false)
const loading = ref(false)

function money(value: unknown) {
  const num = Number(value ?? 0)
  return num.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

function showSnapshot(row: unknown) {
  snapshot.value = row as ContractRevision
}

async function refresh() {
  if (props.contractId === undefined || props.contractId === null || props.contractId === '') {
    rows.value = []
    return
  }
  loading.value = true
  try {
    const response = await getContractRevisions(props.contractId, props.category) as unknown as { data: ContractRevision[] }
    rows.value = response.data || []
  } finally {
    loading.value = false
  }
}

watch(
  () => props.visible,
  async (visible) => {
    if (!visible) return
    afterAmount.value = Number(props.beforeAmount) || 0
    reason.value = ''
    snapshot.value = undefined
    await refresh()
  },
  { immediate: true }
)

async function create() {
  if (props.contractId === undefined || props.contractId === null || props.contractId === '') return
  busy.value = true
  try {
    await createContractRevision({
      contractId: props.contractId,
      contractCategory: props.category,
      baseVersion: props.baseVersion || 0,
      afterAmount: Number(afterAmount.value),
      reason: reason.value.trim()
    })
    ElMessage.success('修订申请已保存')
    await refresh()
  } finally {
    busy.value = false
  }
}

async function submit(id: string | number) {
  try {
    await ElMessageBox.confirm('提交后将进入合同修订审批流程，确认提交？', '提交审批', { type: 'warning' })
  } catch {
    return
  }
  busy.value = true
  try {
    await submitContractRevision(id)
    ElMessage.success('已提交审批')
    await refresh()
    emit('changed')
  } finally {
    busy.value = false
  }
}

async function cancel(id: string | number) {
  try {
    await ElMessageBox.confirm('确认作废该草稿修订申请？', '作废', { type: 'warning' })
  } catch {
    return
  }
  busy.value = true
  try {
    await cancelContractRevision(id)
    ElMessage.success('已作废')
    await refresh()
  } finally {
    busy.value = false
  }
}
</script>

<style scoped>
.revision-summary {
  margin: var(--zw-space-sm) 0;
  color: var(--el-text-color-regular);
}
.revision-table {
  margin-top: var(--zw-space-md);
}
.revision-snapshot pre {
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
</style>
