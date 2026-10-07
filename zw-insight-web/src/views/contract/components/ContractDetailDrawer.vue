<template>
  <el-drawer
    v-model="visible"
    :title="`合同履约全息视窗：${contract?.contractCode || ''}`"
    size="840px"
    destroy-on-close
  >
    <div v-loading="loading" class="contract-drawer-content">
      <!-- 顶部工程铭牌与四率看板 -->
      <div class="summary-card mb-4">
        <div class="top-row">
          <div class="title-block">
            <span class="main-title font-semibold">{{ contract?.partyAName ? `${contract.partyAName} - 施工合同` : '施工合同' }}</span>
            <span class="sub-code font-mono ml-2">{{ contract?.contractCode }}</span>
            <el-tag :type="statusTagType(contract?.status)" size="small" class="ml-2">
              {{ statusLabelMap[contract?.status] || contract?.status }}
            </el-tag>
          </div>
          <div class="meta-block text-xs text-secondary">
            <span>项目：{{ contract?.projectName || '-' }}</span>
            <span class="ml-3 font-mono">签订：{{ contract?.signingDate || '-' }}</span>
          </div>
        </div>

        <el-divider class="my-2" />

        <!-- 四大核心金额指标 -->
        <div class="kpi-grid">
          <div class="kpi-item">
            <span class="kpi-label">合同总金额</span>
            <span class="kpi-value text-brand font-semibold">
              ¥ {{ Number(contract?.contractAmount || 0).toLocaleString() }}
            </span>
          </div>
          <div class="kpi-item">
            <span class="kpi-label">不含税金额 / 税率</span>
            <span class="kpi-value">
              ¥ {{ Number(contract?.amountWithoutTax || 0).toLocaleString() }}
              <span class="text-xs text-secondary">({{ contract?.taxRate || 0 }}%)</span>
            </span>
          </div>
          <div class="kpi-item">
            <span class="kpi-label">累计变更金额</span>
            <span class="kpi-value" :class="contract?.cumulativeChangeAmount > 0 ? 'text-warning font-medium' : ''">
              ¥ {{ Number(contract?.cumulativeChangeAmount || 0).toLocaleString() }}
            </span>
          </div>
          <div class="kpi-item">
            <span class="kpi-label">调整后总额</span>
            <span class="kpi-value font-semibold">
              ¥ {{ (Number(contract?.contractAmount || 0) + Number(contract?.cumulativeChangeAmount || 0)).toLocaleString() }}
            </span>
          </div>
        </div>

        <!-- 履约四率进度条 (产值率 / 开票率 / 回款率) -->
        <div class="progress-section mt-3">
          <div class="progress-row">
            <span class="progress-label">产值率 (¥ {{ Number(contract?.cumulativeOutput || 0).toLocaleString() }})</span>
            <el-progress
              :percentage="calcRate(contract?.cumulativeOutput, contract?.contractAmount)"
              status="success"
              class="flex-1 ml-2"
            />
          </div>
          <div class="progress-row mt-2">
            <span class="progress-label">开票率 (¥ {{ Number(contract?.cumulativeInvoiceAmount || 0).toLocaleString() }})</span>
            <el-progress
              :percentage="calcRate(contract?.cumulativeInvoiceAmount, contract?.contractAmount)"
              class="flex-1 ml-2"
            />
          </div>
          <div class="progress-row mt-2">
            <span class="progress-label">回款率 (¥ {{ Number(contract?.cumulativeReceivedAmount || 0).toLocaleString() }})</span>
            <el-progress
              :percentage="calcRate(contract?.cumulativeReceivedAmount, contract?.contractAmount)"
              status="warning"
              class="flex-1 ml-2"
            />
          </div>
        </div>
      </div>

      <!-- Tab 页签 -->
      <el-tabs v-model="activeTab" class="contract-tabs">
        <!-- Tab 1: 基本条款与清单明细 -->
        <el-tab-pane label="基本要素与清单明细" name="details">
          <el-descriptions :column="2" border class="mb-3">
            <el-descriptions-item label="甲方单位">{{ contract?.partyAName || '-' }}</el-descriptions-item>
            <el-descriptions-item label="合同性质">{{ contract?.contractType === 'REGISTER' ? '登记合同' : contract?.contractType || '-' }}</el-descriptions-item>
            <el-descriptions-item label="计划工期">
              {{ contract?.startDate || '-' }} 至 {{ contract?.endDate || '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="税额 (¥)">
              ¥ {{ Number(contract?.taxAmount || 0).toLocaleString() }}
            </el-descriptions-item>
          </el-descriptions>

          <div class="section-header mb-2 font-medium">合同清单明细列表 (共 {{ details.length }} 项)</div>
          <el-table :data="details" border size="small">
            <el-table-column type="index" label="#" width="50" align="center" />
            <el-table-column prop="itemName" label="清单项目名称" min-width="150" show-overflow-tooltip />
            <el-table-column prop="specification" label="规格型号" width="120" show-overflow-tooltip />
            <el-table-column prop="unit" label="单位" width="70" align="center" />
            <el-table-column prop="quantity" label="工程量" width="100" align="right">
              <template #default="{ row }">{{ Number(row.quantity || 0).toLocaleString() }}</template>
            </el-table-column>
            <el-table-column prop="unitPrice" label="综合单价(元)" width="110" align="right">
              <template #default="{ row }">¥ {{ Number(row.unitPrice || 0).toLocaleString() }}</template>
            </el-table-column>
            <el-table-column prop="totalPrice" label="合价(元)" width="120" align="right">
              <template #default="{ row }">¥ {{ Number(row.totalPrice || 0).toLocaleString() }}</template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <!-- Tab 2: 变更历程 (变更签证 + 变更事件) -->
        <el-tab-pane label="变更历程与签证" name="changes">
          <div class="tab-toolbar mb-3">
            <span class="font-medium">变更签证与项目变更记录</span>
            <el-button
              v-if="contract?.status === 'EFFECTIVE'"
              type="primary"
              size="small"
              @click="handleAddVisa"
            >
              登记变更签证
            </el-button>
          </div>

          <el-table :data="changeVisas" border size="small" class="mb-4">
            <el-table-column prop="changeType" label="签证类型" width="120">
              <template #default="{ row }">
                {{ row.changeType === 'DESIGN_CHANGE' ? '设计变更' : row.changeType === 'SITE_VISA' ? '现场签证' : row.changeType }}
              </template>
            </el-table-column>
            <el-table-column prop="changeAmount" label="增减金额(元)" width="130" align="right">
              <template #default="{ row }">
                <span :class="Number(row.changeAmount) >= 0 ? 'text-success' : 'text-danger'">
                  {{ Number(row.changeAmount) >= 0 ? '+' : '' }}¥ {{ Number(row.changeAmount || 0).toLocaleString() }}
                </span>
              </template>
            </el-table-column>
            <el-table-column prop="changeReason" label="变更原因" min-width="150" show-overflow-tooltip />
            <el-table-column prop="changeContent" label="变更内容" min-width="180" show-overflow-tooltip />
            <el-table-column prop="status" label="状态" width="90" align="center">
              <template #default="{ row }">
                <el-tag :type="row.status === 'APPROVED' ? 'success' : 'info'" size="small">
                  {{ row.status === 'APPROVED' ? '已批准' : '编制中' }}
                </el-tag>
              </template>
            </el-table-column>
          </el-table>

          <ZwEmptyState v-if="!changeVisas.length" description="暂无变更签证记录" />
        </el-tab-pane>

        <!-- Tab 3: 产值执行台账 -->
        <el-tab-pane label="产值上报记录" name="outputs">
          <div class="section-header mb-2 font-medium">累计产值上报明细</div>
          <el-table :data="outputReports" border size="small">
            <el-table-column prop="reportPeriod" label="报告期间" width="110" />
            <el-table-column prop="currentOutput" label="本期完成产值(元)" width="140" align="right">
              <template #default="{ row }">¥ {{ Number(row.currentOutput || 0).toLocaleString() }}</template>
            </el-table-column>
            <el-table-column prop="cumulativeOutput" label="累计产值(元)" width="140" align="right">
              <template #default="{ row }">¥ {{ Number(row.cumulativeOutput || 0).toLocaleString() }}</template>
            </el-table-column>
            <el-table-column prop="status" label="状态" width="100" align="center">
              <template #default="{ row }">
                <el-tag :type="row.status === 'APPROVED' ? 'success' : row.status === 'SUBMITTED' ? 'warning' : 'info'" size="small">
                  {{ row.status === 'APPROVED' ? '已确认' : row.status === 'SUBMITTED' ? '审批中' : '草稿' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="remark" label="说明" min-width="150" show-overflow-tooltip />
          </el-table>
          <ZwEmptyState v-if="!outputReports.length" description="暂无产值上报记录" />
        </el-tab-pane>

        <!-- Tab 4: 竣工结算单 -->
        <el-tab-pane label="竣工结算" name="settlement">
          <div v-if="finalSettlement" class="settlement-info">
            <el-descriptions :column="2" border>
              <el-descriptions-item label="最终审定结算价">
                <span class="text-brand font-semibold">¥ {{ Number(finalSettlement.settlementAmount || 0).toLocaleString() }}</span>
              </el-descriptions-item>
              <el-descriptions-item label="审减/增额">
                ¥ {{ Number(finalSettlement.auditDifference || 0).toLocaleString() }}
              </el-descriptions-item>
              <el-descriptions-item label="结算状态">
                <el-tag type="success">已生效结算</el-tag>
              </el-descriptions-item>
              <el-descriptions-item label="结算说明" :span="2">
                {{ finalSettlement.remark || '无' }}
              </el-descriptions-item>
            </el-descriptions>
          </div>
          <ZwEmptyState v-else description="尚未发起竣工结算" />
        </el-tab-pane>
      </el-tabs>
    </div>

    <!-- 弹窗：登记变更签证 (B1) -->
    <el-dialog v-model="visaDialogVisible" title="登记变更签证" width="500px" append-to-body>
      <el-form ref="visaFormRef" :model="visaForm" :rules="visaRules" label-width="90px">
        <el-form-item label="变更类型" prop="changeType">
          <el-select v-model="visaForm.changeType" placeholder="选择类型" style="width: 100%">
            <el-option label="设计变更" value="DESIGN_CHANGE" />
            <el-option label="现场签证" value="SITE_VISA" />
            <el-option label="技术核定" value="TECH_CONFIRM" />
          </el-select>
        </el-form-item>
        <el-form-item label="变更金额" prop="changeAmount">
          <el-input-number
            v-model="visaForm.changeAmount"
            :precision="2"
            :step="1000"
            controls-position="right"
            style="width: 100%"
            placeholder="正数调增，负数调减"
          />
        </el-form-item>
        <el-form-item label="变更原因" prop="changeReason">
          <el-input v-model="visaForm.changeReason" placeholder="如：业主设计调整、地质变动" />
        </el-form-item>
        <el-form-item label="变更内容" prop="changeContent">
          <el-input v-model="visaForm.changeContent" type="textarea" :rows="3" placeholder="详细变更部位与技术核定说明" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="visaDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="visaSubmitting" @click="submitVisa">确认提交</el-button>
      </template>
    </el-dialog>
  </el-drawer>
</template>

<script setup lang="ts">
import { ref, watch, computed } from 'vue'
import { ElMessage, type FormInstance } from 'element-plus'
import ZwEmptyState from '@/components/ZwEmptyState.vue'
import {
  getContractDetails,
  getChangeVisaPage,
  createChangeVisa,
  getOutputReportPage,
  getFinalSettlementPage
} from '@/api/contract'

const props = defineProps<{
  modelValue: boolean
  contract?: any
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'refresh'): void
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

const loading = ref(false)
const activeTab = ref('details')

const statusLabelMap: Record<string, string> = {
  DRAFT: '草稿',
  SUBMITTED: '审批中',
  EFFECTIVE: '执行中',
  SETTLED: '已结算',
  CLOSED: '已关闭'
}

function statusTagType(status?: string) {
  if (status === 'EFFECTIVE') return 'success'
  if (status === 'SUBMITTED') return 'warning'
  if (status === 'SETTLED') return 'primary'
  if (status === 'CLOSED') return 'info'
  return 'info'
}

function calcRate(amount?: number, total?: number): number {
  if (!amount || !total || total <= 0) return 0
  const rate = Math.round((Number(amount) / Number(total)) * 100)
  return Math.min(rate, 100)
}

const details = ref<any[]>([])
const changeVisas = ref<any[]>([])
const outputReports = ref<any[]>([])
const finalSettlement = ref<any>(null)

// 变更签证弹窗
const visaDialogVisible = ref(false)
const visaSubmitting = ref(false)
const visaFormRef = ref<FormInstance>()
const visaForm = ref({
  changeType: 'DESIGN_CHANGE',
  changeAmount: undefined as number | undefined,
  changeReason: '',
  changeContent: ''
})
const visaRules = {
  changeType: [{ required: true, message: '请选择变更类型', trigger: 'change' }],
  changeAmount: [{ required: true, message: '请输入变更金额', trigger: 'blur' }],
  changeReason: [{ required: true, message: '请输入变更原因', trigger: 'blur' }]
}

watch(
  () => [props.modelValue, props.contract],
  async ([val]) => {
    if (val && props.contract?.id) {
      activeTab.value = 'details'
      await loadAllData()
    }
  },
  { immediate: true }
)

async function loadAllData() {
  if (!props.contract?.id) return
  loading.value = true
  const cid = props.contract.id
  try {
    // 1. 合同明细
    try {
      const dRes: any = await getContractDetails(cid)
      details.value = dRes.data || []
    } catch {
      details.value = []
    }

    // 2. 变更签证
    try {
      const vRes: any = await getChangeVisaPage({ contractId: cid, pageNum: 1, pageSize: 50 })
      changeVisas.value = vRes.data?.records || []
    } catch {
      changeVisas.value = []
    }

    // 3. 产值记录
    try {
      const oRes: any = await getOutputReportPage({ contractId: cid, pageNum: 1, pageSize: 50 })
      outputReports.value = oRes.data?.records || []
    } catch {
      outputReports.value = []
    }

    // 4. 竣工结算
    try {
      const sRes: any = await getFinalSettlementPage({ contractId: cid, pageNum: 1, pageSize: 1 })
      finalSettlement.value = sRes.data?.records?.[0] || null
    } catch {
      finalSettlement.value = null
    }
  } finally {
    loading.value = false
  }
}

function handleAddVisa() {
  visaForm.value = {
    changeType: 'DESIGN_CHANGE',
    changeAmount: undefined,
    changeReason: '',
    changeContent: ''
  }
  visaDialogVisible.value = true
}

async function submitVisa() {
  await visaFormRef.value?.validate()
  visaSubmitting.value = true
  try {
    await createChangeVisa({
      projectId: props.contract.projectId,
      contractId: props.contract.id,
      changeType: visaForm.value.changeType,
      changeAmount: visaForm.value.changeAmount,
      changeReason: visaForm.value.changeReason,
      changeContent: visaForm.value.changeContent,
      status: 'APPROVED'
    })
    ElMessage.success('变更签证登记成功')
    visaDialogVisible.value = false
    const vRes: any = await getChangeVisaPage({ contractId: props.contract.id, pageNum: 1, pageSize: 50 })
    changeVisas.value = vRes.data?.records || []
    emit('refresh')
  } finally {
    visaSubmitting.value = false
  }
}
</script>

<style scoped>
.contract-drawer-content {
  padding: 0 var(--zw-space-sm);
}

.summary-card {
  padding: var(--zw-space-md);
  background-color: var(--zw-color-bg-base);
  border: 1px solid var(--zw-color-border-light);
  border-radius: var(--zw-border-radius-base);
}

.top-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.title-block {
  display: flex;
  align-items: center;
}

.main-title {
  font-size: var(--zw-font-size-md);
  color: var(--zw-color-text-primary);
}

.sub-code {
  color: var(--zw-color-text-secondary);
  font-size: var(--zw-font-size-sm);
}

.kpi-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: var(--zw-space-md);
  margin-top: var(--zw-space-sm);
}

.kpi-item {
  display: flex;
  flex-direction: column;
  gap: var(--zw-space-xs);
}

.kpi-label {
  font-size: var(--zw-font-size-xs);
  color: var(--zw-color-text-secondary);
}

.kpi-value {
  font-size: var(--zw-font-size-md);
  color: var(--zw-color-text-primary);
}

.progress-section {
  background: var(--zw-color-bg-subtle, var(--zw-color-bg-base));
  padding: var(--zw-space-sm) var(--zw-space-md);
  border-radius: var(--zw-border-radius-sm);
}

.progress-row {
  display: flex;
  align-items: center;
}

.progress-label {
  width: 220px;
  font-size: var(--zw-font-size-xs);
  color: var(--zw-color-text-secondary);
}

.tab-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.mb-2 {
  margin-bottom: var(--zw-space-xs);
}

.mb-3 {
  margin-bottom: var(--zw-space-sm);
}

.mb-4 {
  margin-bottom: var(--zw-space-md);
}

.mt-2 {
  margin-top: var(--zw-space-xs);
}

.mt-3 {
  margin-top: var(--zw-space-sm);
}

.ml-2 {
  margin-left: var(--zw-space-xs);
}

.ml-3 {
  margin-left: var(--zw-space-sm);
}

.my-2 {
  margin: var(--zw-space-xs) 0;
}

.text-xs {
  font-size: var(--zw-font-size-xs);
}

.text-secondary {
  color: var(--zw-color-text-secondary);
}

.font-medium {
  font-weight: 500;
}

.font-semibold {
  font-weight: 600;
}

.text-brand {
  color: var(--zw-color-primary);
}

.text-warning {
  color: var(--zw-color-warning);
}

.text-success {
  color: var(--zw-color-success);
}

.text-danger {
  color: var(--zw-color-danger);
}

.flex-1 {
  flex: 1;
}
</style>
