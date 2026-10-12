<template>
  <div class="settlement-create-container">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>新建机械结算单</span>
          <el-button @click="router.back()">返回</el-button>
        </div>
      </template>

      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="100px" style="max-width: 600px">
        <el-form-item label="项目" prop="projectId">
          <el-select
            v-model="formData.projectId"
            placeholder="请选择项目"
            filterable
            style="width: 100%"
            @change="handleProjectChange"
          >
            <el-option
              v-for="item in projectList"
              :key="item.id"
              :label="item.projectName"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="结算周期" prop="period">
          <el-date-picker
            v-model="formData.period"
            type="daterange"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            value-format="YYYY-MM-DD"
            style="width: 100%"
            @change="handlePeriodChange"
          />
        </el-form-item>
      </el-form>

      <!-- 机械明细预览：数据源与后端结算聚合同源（biz_machine_work_log） -->
      <div v-if="previewVisible" class="preview-section">
        <el-divider content-position="left">机械工作量明细预览</el-divider>
        <el-table :data="previewData" v-loading="previewLoading" border size="small">
          <el-table-column prop="machineName" label="机械名称" min-width="140" />
          <el-table-column prop="pricingTypeLabel" label="计价方式" width="100" align="center" />
          <el-table-column prop="shiftCount" label="台班数" width="100" align="right" />
          <el-table-column prop="workQuantity" label="工作量" width="100" align="right" />
          <el-table-column prop="unitPrice" label="单价(元)" width="120" align="right">
            <template #default="{ row }">{{ row.unitPrice?.toLocaleString() }}</template>
          </el-table-column>
          <el-table-column prop="amount" label="金额(元)" width="120" align="right">
            <template #default="{ row }">{{ row.amount?.toLocaleString() }}</template>
          </el-table-column>
        </el-table>
        <div class="preview-total">
          <span>合计金额：</span>
          <span class="total-amount">¥ {{ totalAmount.toLocaleString() }}</span>
        </div>
      </div>

      <div class="form-actions">
        <el-button @click="router.back()">取消</el-button>
        <!-- 盲点 12 守卫（2026-08-24）：预览为空/加载中禁用保存，防空结算单提交；
             后端 MachineWorkSettlementService 已有「无可结算工作量」拦截，此为前端体验对齐 -->
        <el-button type="primary" :loading="submitLoading" :disabled="!canSave" @click="handleSave">保存结算单</el-button>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { FormInstance } from 'element-plus'
import { createMachineSettlement, getMachineWorkLogPage, getMachineContractPage } from '@/api/machine'
import { getProjectList } from '@/api/project'

const router = useRouter()
const formRef = ref<FormInstance>()
const submitLoading = ref(false)
const previewLoading = ref(false)
const previewVisible = ref(false)
const previewData = ref<any[]>([])
const projectList = ref<any[]>([])

const formData = ref({
  projectId: undefined as number | undefined,
  period: null as string[] | null
})

const formRules = {
  projectId: [{ required: true, message: '请选择项目', trigger: 'change' }],
  period: [{ required: true, message: '请选择结算周期', trigger: 'change' }]
}

const totalAmount = computed(() => {
  return previewData.value.reduce((sum, item) => sum + (item.amount || 0), 0)
})

// 盲点 12 守卫：预览齐备且有明细才可保存（空预览/加载中禁用）
const canSave = computed(() => previewVisible.value && !previewLoading.value && previewData.value.length > 0)

async function loadProjects() {
  try {
    const res: any = await getProjectList()
    projectList.value = res.data || []
  } catch {
    // 加载失败不阻断
  }
}

function handleProjectChange() {
  loadPreview()
}

function handlePeriodChange() {
  loadPreview()
}

// 与后端 MachineWorkSettlementService.createSettlement 同源同口径：
// 聚合 biz_machine_work_log（原误用 usage-record），按项目 + 结算周期过滤、排除已结算（SETTLED）日志，
// 再按合同（回退机械）归集后逐组计价，使预览行数与金额与后端将要结算的内容一致。
async function loadPreview() {
  if (!formData.value.projectId || !formData.value.period || formData.value.period.length !== 2) {
    previewVisible.value = false
    previewData.value = []
    return
  }
  previewVisible.value = true
  previewLoading.value = true
  try {
    const [startDate, endDate] = formData.value.period
    const [logRes, contractRes]: any[] = await Promise.all([
      getMachineWorkLogPage({ projectId: formData.value.projectId, page: 1, size: 200 }),
      getMachineContractPage({ projectId: formData.value.projectId, page: 1, size: 200 })
    ])
    const contracts: any[] = contractRes.data?.records || []
    // 与后端一致：contractId 命中不限状态；机械名称匹配仅取生效合同
    const byId = new Map(contracts.map((c: any) => [c.id, c]))
    const byName = new Map(contracts.filter((c: any) => c.status === 'EFFECTIVE').map((c: any) => [c.machineName, c]))
    const logs = (logRes.data?.records || []).filter((log: any) =>
      log.workDate >= startDate && log.workDate <= endDate && log.settlementStatus !== 'SETTLED')
    previewData.value = groupLogs(logs, byId, byName, startDate, endDate)
  } catch {
    previewData.value = []
  } finally {
    previewLoading.value = false
  }
}

function groupLogs(logs: any[], byId: Map<any, any>, byName: Map<any, any>, periodStart: string, periodEnd: string) {
  const groups = new Map<any, { contract: any; machineName: string; logs: any[] }>()
  for (const log of logs) {
    const contract = (log.contractId != null && byId.get(log.contractId)) || byName.get(log.machineName) || null
    const key = contract?.id ?? log.machineId ?? log.id
    if (!groups.has(key)) groups.set(key, { contract, machineName: log.machineName || '', logs: [] })
    groups.get(key)!.logs.push(log)
  }
  return Array.from(groups.values()).map(group => buildPreviewRow(group, periodStart, periodEnd))
}

function buildPreviewRow(group: { contract: any; machineName: string; logs: any[] }, periodStart: string, periodEnd: string) {
  const { contract, logs } = group
  const shiftCount = logs.reduce((sum, log) => sum + Number(log.shiftCount || 0), 0)
  const workQuantity = logs.reduce((sum, log) => sum + Number(log.workQuantity || 0), 0)
  const unitPrice = Number(contract?.unitPrice ?? contract?.contractAmount ?? 0)
  const rentalType = contract?.rentalType
  let pricingType = 'SHIFT'
  let pricingTypeLabel = '台班计价'
  let quantity = shiftCount
  if (rentalType === 'VOLUME' || rentalType === '工作量') {
    pricingType = 'VOLUME'
    pricingTypeLabel = '工作量计价'
    quantity = workQuantity
  } else if (rentalType === 'MONTHLY' || rentalType === '月租' || rentalType === '包月') {
    pricingType = 'MONTHLY'
    pricingTypeLabel = '月租计价'
    quantity = monthlyBillingQuantity(contract, periodStart, periodEnd)
  }
  const amount = Number((quantity * unitPrice).toFixed(2))
  return { machineName: group.machineName, pricingType, pricingTypeLabel, shiftCount, workQuantity, unitPrice, amount }
}

// 月租/包月计价数量：合同有效期与结算周期交集逐月折算（与后端 ChronoUnit 折算口径一致）
function monthlyBillingQuantity(contract: any, periodStart: string, periodEnd: string) {
  if (!contract?.startDate || !contract?.endDate) return 1
  const start = periodStart > contract.startDate ? periodStart : contract.startDate
  const end = periodEnd < contract.endDate ? periodEnd : contract.endDate
  if (start > end) return 0
  let quantity = 0
  let day = start
  while (day <= end) {
    const monthEnd = lastDayOfMonth(day)
    const last = monthEnd < end ? monthEnd : end
    quantity += (diffDays(day, last) + 1) / daysInMonth(day)
    day = addDays(last, 1)
  }
  return Number(quantity.toFixed(4))
}

function parseDay(dateStr: string) {
  return Date.parse(`${dateStr}T00:00:00Z`)
}
function lastDayOfMonth(dateStr: string) {
  const d = new Date(parseDay(dateStr))
  return new Date(Date.UTC(d.getUTCFullYear(), d.getUTCMonth() + 1, 0)).toISOString().slice(0, 10)
}
function daysInMonth(dateStr: string) {
  return Number(lastDayOfMonth(dateStr).slice(8, 10))
}
function diffDays(a: string, b: string) {
  return Math.round((parseDay(b) - parseDay(a)) / 86400000)
}
function addDays(dateStr: string, n: number) {
  return new Date(parseDay(dateStr) + n * 86400000).toISOString().slice(0, 10)
}

async function handleSave() {
  await formRef.value?.validate()
  if (!canSave.value) {
    // 按钮已禁用，此处为编程调用/竞态兜底：空预览不发请求（后端亦会拦截「无可结算工作量」）
    ElMessage.warning('当前周期内无可结算的机械使用明细，无法保存')
    return
  }
  submitLoading.value = true
  try {
    await createMachineSettlement({
      projectId: formData.value.projectId,
      periodStart: formData.value.period![0],
      periodEnd: formData.value.period![1]
    })
    ElMessage.success('结算单创建成功')
    router.push('/machine/settlement')
  } finally {
    submitLoading.value = false
  }
}

onMounted(() => {
  loadProjects()
})
</script>

<style scoped>
.settlement-create-container {
  padding: var(--zw-space-md);
}
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.preview-section {
  margin-top: var(--zw-space-lg);
}
.preview-total {
  margin-top: var(--zw-space-sm-md);
  text-align: right;
  font-size: var(--zw-font-size-md);
}
.total-amount {
  font-weight: bold;
  color: var(--zw-info);
  font-size: var(--zw-font-size-lg);
}
.form-actions {
  margin-top: var(--zw-space-xl);
  text-align: center;
}
</style>
