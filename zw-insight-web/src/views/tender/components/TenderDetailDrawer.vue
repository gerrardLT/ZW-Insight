<template>
  <el-drawer
    v-model="visible"
    :title="`投标档案：${register?.projectName || register?.ownerCompany || ''}`"
    size="780px"
    destroy-on-close
  >
    <div v-loading="loading" class="drawer-content">
      <!-- 顶部信息摘要条 -->
      <div class="summary-banner mb-4">
        <div class="summary-item">
          <span class="label">项目名称</span>
          <span class="value font-medium">{{ register?.projectName || '-' }}</span>
        </div>
        <div class="summary-item">
          <span class="label">招标方式</span>
          <span class="value">{{ register?.bidMethod === 'PUBLIC' ? '公开招标' : register?.bidMethod === 'INVITE' ? '邀请招标' : (register?.bidMethod || '-') }}</span>
        </div>
        <div class="summary-item">
          <span class="label">开标日期</span>
          <span class="value font-mono">{{ register?.openDate || '-' }}</span>
        </div>
        <div class="summary-item">
          <span class="label">投标状态</span>
          <el-tag :type="statusTagType(register?.status)" size="small">
            {{ statusLabelMap[register?.status] || register?.status }}
          </el-tag>
        </div>
      </div>

      <!-- Tab 分页签 -->
      <el-tabs v-model="activeTab" class="tender-tabs">
        <!-- Tab 1: 开标结果与复盘 (A1/B4) -->
        <el-tab-pane label="开标结果与复盘" name="openBid">
          <div v-if="openBidRecord" class="open-bid-panel">
            <el-descriptions :column="2" border>
              <el-descriptions-item label="开标结果">
                <el-tag :type="openBidRecord.isWon === 1 ? 'success' : 'danger'" effect="dark">
                  {{ openBidRecord.isWon === 1 ? '中标 (WON)' : '未中标 (LOST)' }}
                </el-tag>
              </el-descriptions-item>
              <el-descriptions-item label="开标/报价金额">
                <span class="text-brand font-semibold">
                  {{ openBidRecord.bidAmount != null ? '¥ ' + Number(openBidRecord.bidAmount).toLocaleString() : '-' }}
                </span>
              </el-descriptions-item>
              <el-descriptions-item v-if="openBidRecord.isWon === 0" label="落标原因分类">
                <el-tag type="danger" size="small">
                  {{ lostReasonMap[openBidRecord.lostReasonCategory] || openBidRecord.lostReasonCategory || '未分类' }}
                </el-tag>
              </el-descriptions-item>
              <el-descriptions-item label="登记时间">
                {{ openBidRecord.createdAt || '-' }}
              </el-descriptions-item>
              <el-descriptions-item label="说明与复盘" :span="2">
                {{ openBidRecord.winInfo || '无' }}
              </el-descriptions-item>
            </el-descriptions>
          </div>
          <ZwEmptyState
            v-else
            description="尚未录入开标结果"
          />
        </el-tab-pane>

        <!-- Tab 2: 保证金台账 (A2/TI-1/TI-4) -->
        <el-tab-pane label="保证金申请与退还" name="deposit">
          <div class="tab-header mb-3">
            <span class="font-medium">保证金申请及退还跟踪</span>
            <el-button
              v-if="!depositApplies.length"
              type="primary"
              size="small"
              @click="emit('apply-deposit', register)"
            >
              申请保证金
            </el-button>
          </div>
          <el-table :data="depositApplies" border size="small">
            <el-table-column prop="depositAmount" label="申请金额(元)" align="right">
              <template #default="{ row }">¥ {{ Number(row.depositAmount || 0).toLocaleString() }}</template>
            </el-table-column>
            <el-table-column prop="paymentDate" label="拟付日期" width="110" />
            <el-table-column prop="status" label="状态" width="100" align="center">
              <template #default="{ row }">
                <el-tag :type="row.status === 'PAID' ? 'success' : row.status === 'SUBMITTED' ? 'warning' : 'info'" size="small">
                  {{ row.status === 'PAID' ? '已支付' : row.status === 'SUBMITTED' ? '审批中' : '草稿' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="退还状态" min-width="140">
              <template #default="{ row }">
                <span v-if="depositRefundMap[row.id]">
                  已退 ¥ {{ Number(depositRefundMap[row.id]?.returnAmount || 0).toLocaleString() }}
                  ({{ depositRefundMap[row.id]?.returnDate }})
                </span>
                <span v-else class="text-secondary">未退还</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="160" align="center">
              <template #default="{ row }">
                <el-button
                  v-if="row.status === 'DRAFT'"
                  link
                  type="primary"
                  size="small"
                  @click="handleSubmitDepositApproval(row)"
                >
                  提交审批
                </el-button>
                <el-button
                  v-if="row.status === 'PAID' && !depositRefundMap[row.id]"
                  link
                  type="success"
                  size="small"
                  @click="emit('refund-deposit', register, row.id)"
                >
                  登记退还
                </el-button>
                <span v-else-if="depositRefundMap[row.id]" class="text-secondary text-xs">已结清</span>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <!-- Tab 3: 人员押证绑定 (B1/TI-2) -->
        <el-tab-pane label="拟派押证人员" name="personBinding">
          <div class="tab-header mb-3">
            <div class="tip-wrap">
              <span class="font-medium">拟派押证人员清单</span>
              <span class="text-xs text-secondary ml-2">开标前排他锁定，防止一证多投；开标后自动释放</span>
            </div>
            <el-button
              v-if="register?.status === 'REGISTERED' || register?.status === 'SUBMITTED'"
              type="primary"
              size="small"
              @click="handleAddBinding"
            >
              绑定押证人员
            </el-button>
          </div>
          <el-table :data="personBindings" border size="small">
            <el-table-column prop="personName" label="姓名" width="100" />
            <el-table-column prop="certificateType" label="证件类型" min-width="140" />
            <el-table-column prop="bindingRole" label="拟任岗位" width="120" />
            <el-table-column prop="status" label="状态" width="90" align="center">
              <template #default="{ row }">
                <el-tag :type="row.status === 'LOCKED' ? 'danger' : 'success'" size="small">
                  {{ row.status === 'LOCKED' ? '锁定中' : '已解锁' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="releasedReason" label="解锁说明" min-width="130" show-overflow-tooltip />
          </el-table>
        </el-tab-pane>

        <!-- Tab 4: 投标费用归集 (B2) -->
        <el-tab-pane label="投标费用" name="fee">
          <div class="tab-header mb-3">
            <span class="font-medium">前期投标费用归集</span>
            <el-button type="primary" size="small" @click="handleAddFee">
              新增费用
            </el-button>
          </div>
          <el-table :data="tenderFees" border size="small">
            <el-table-column prop="feeType" label="费用类型" width="120">
              <template #default="{ row }">{{ feeTypeMap[row.feeType] || row.feeType }}</template>
            </el-table-column>
            <el-table-column prop="feeAmount" label="金额(元)" width="120" align="right">
              <template #default="{ row }">¥ {{ Number(row.feeAmount || 0).toLocaleString() }}</template>
            </el-table-column>
            <el-table-column prop="paymentDate" label="支付日期" width="110" />
            <el-table-column prop="status" label="状态" width="90" align="center">
              <template #default="{ row }">
                <el-tag :type="row.status === 'PAID' ? 'success' : 'warning'" size="small">
                  {{ row.status === 'PAID' ? '已支付' : '未支付' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="120" align="center">
              <template #default="{ row }">
                <el-button
                  v-if="row.status !== 'PAID'"
                  link
                  type="success"
                  size="small"
                  @click="handlePayFee(row)"
                >
                  确认支付
                </el-button>
                <el-button link type="danger" size="small" @click="handleDeleteFee(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <!-- Tab 5: 编标任务分工 (B3) -->
        <el-tab-pane label="编标任务" name="task">
          <div class="tab-header mb-3">
            <span class="font-medium">标书制作任务协同</span>
            <el-button type="primary" size="small" @click="handleAddTask">
              分派任务
            </el-button>
          </div>
          <el-table :data="tenderTasks" border size="small">
            <el-table-column prop="taskType" label="任务类型" width="130">
              <template #default="{ row }">{{ taskTypeMap[row.taskType] || row.taskType }}</template>
            </el-table-column>
            <el-table-column prop="responsiblePerson" label="负责人" width="110" />
            <el-table-column prop="deadline" label="截止日期" width="110" />
            <el-table-column prop="status" label="状态" width="100" align="center">
              <template #default="{ row }">
                <el-tag :type="row.status === 'COMPLETED' ? 'success' : 'info'" size="small">
                  {{ row.status === 'COMPLETED' ? '已完成' : '编制中' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="120" align="center">
              <template #default="{ row }">
                <el-button
                  v-if="row.status !== 'COMPLETED'"
                  link
                  type="primary"
                  size="small"
                  @click="handleCompleteTask(row)"
                >
                  标记完成
                </el-button>
                <el-button link type="danger" size="small" @click="handleDeleteTask(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </div>

    <!-- 弹窗 1：新增人员押证绑定 -->
    <el-dialog v-model="bindingDialogVisible" title="绑定押证人员" width="480px" append-to-body>
      <el-form ref="bindingFormRef" :model="bindingForm" :rules="bindingRules" label-width="90px">
        <el-form-item label="选择证书" prop="personCertificateId">
          <el-select
            v-model="bindingForm.personCertificateId"
            placeholder="搜索/选择持证人员"
            filterable
            style="width: 100%"
            @change="handleCertSelect"
          >
            <el-option
              v-for="cert in certOptions"
              :key="cert.id"
              :label="`${cert.personName} - ${cert.certificateType} (${cert.certificateNo || '无编号'})`"
              :value="cert.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="人员姓名">
          <el-input v-model="bindingForm.personName" disabled />
        </el-form-item>
        <el-form-item label="证书类型">
          <el-input v-model="bindingForm.certificateType" disabled />
        </el-form-item>
        <el-form-item label="拟任岗位" prop="bindingRole">
          <el-select v-model="bindingForm.bindingRole" placeholder="请选择拟任岗位" style="width: 100%">
            <el-option label="项目经理 (建造师)" value="项目经理" />
            <el-option label="技术负责人" value="技术负责人" />
            <el-option label="专职安全员" value="专职安全员" />
            <el-option label="施工员" value="施工员" />
            <el-option label="质量员" value="质量员" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="bindingDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="bindingSubmitting" @click="submitBinding">确定锁定</el-button>
      </template>
    </el-dialog>

    <!-- 弹窗 2：新增费用 -->
    <el-dialog v-model="feeDialogVisible" title="新增投标费用" width="450px" append-to-body>
      <el-form ref="feeFormRef" :model="feeForm" :rules="feeRules" label-width="90px">
        <el-form-item label="费用类型" prop="feeType">
          <el-select v-model="feeForm.feeType" placeholder="选择费用类型" style="width: 100%">
            <el-option label="招标文件费" value="BID_DOC" />
            <el-option label="图纸押金" value="DRAWING" />
            <el-option label="公证费" value="NOTARY" />
            <el-option label="专家评审费" value="EXPERT" />
            <el-option label="差旅踏勘费" value="TRAVEL" />
            <el-option label="其他费用" value="OTHER" />
          </el-select>
        </el-form-item>
        <el-form-item label="金额(元)" prop="feeAmount">
          <el-input-number v-model="feeForm.feeAmount" :min="0" :precision="2" controls-position="right" style="width: 100%" />
        </el-form-item>
        <el-form-item label="付款日期" prop="paymentDate">
          <el-date-picker v-model="feeForm.paymentDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="feeDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="feeSubmitting" @click="submitFee">确定</el-button>
      </template>
    </el-dialog>

    <!-- 弹窗 3：新增任务 -->
    <el-dialog v-model="taskDialogVisible" title="分派编标任务" width="450px" append-to-body>
      <el-form ref="taskFormRef" :model="taskForm" :rules="taskRules" label-width="90px">
        <el-form-item label="任务类型" prop="taskType">
          <el-select v-model="taskForm.taskType" placeholder="选择任务类型" style="width: 100%">
            <el-option label="商务标编制" value="COMMERCIAL" />
            <el-option label="技术标方案" value="TECHNICAL" />
            <el-option label="经济标报价" value="ECONOMIC" />
            <el-option label="封标检查" value="SEAL" />
          </el-select>
        </el-form-item>
        <el-form-item label="负责人" prop="responsiblePerson">
          <el-input v-model="taskForm.responsiblePerson" placeholder="请输入负责人姓名" />
        </el-form-item>
        <el-form-item label="截止日期" prop="deadline">
          <el-date-picker v-model="taskForm.deadline" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="taskDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="taskSubmitting" @click="submitTask">确定</el-button>
      </template>
    </el-dialog>
  </el-drawer>
</template>

<script setup lang="ts">
import { ref, watch, computed } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance } from 'element-plus'
import ZwEmptyState from '@/components/ZwEmptyState.vue'
import {
  getOpenBidByRegister,
  getTenderDepositPage,
  getTenderRefundPage,
  submitTenderDeposit,
  getPersonBindings,
  bindPerson,
  getCertificatePage,
  getTenderFeePage,
  createTenderFee,
  confirmTenderFeePayment,
  deleteTenderFee,
  getTenderTaskList,
  createTenderTask,
  completeTenderTask,
  deleteTenderTask
} from '@/api/tender'

const props = defineProps<{
  modelValue: boolean
  register?: any
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'apply-deposit', register: any): void
  (e: 'refund-deposit', register: any, applyId: number | string): void
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

const loading = ref(false)
const activeTab = ref('openBid')

const statusLabelMap: Record<string, string> = {
  REGISTERED: '报名中',
  SUBMITTED: '已投标',
  WON: '中标',
  LOST: '未中标'
}

const lostReasonMap: Record<string, string> = {
  PRICE_OVER: '报价偏高',
  TECH_WEAK: '技术标失分',
  BIZ_DEVIATION: '商务偏离',
  CREDIT_LACK: '资信不足',
  OTHER: '其他原因'
}

const feeTypeMap: Record<string, string> = {
  BID_DOC: '招标文件费',
  DRAWING: '图纸押金',
  NOTARY: '公证费',
  EXPERT: '专家评审费',
  TRAVEL: '差旅踏勘费',
  OTHER: '其他费用'
}

const taskTypeMap: Record<string, string> = {
  COMMERCIAL: '商务标编制',
  TECHNICAL: '技术标方案',
  ECONOMIC: '经济标报价',
  SEAL: '封标检查'
}

function statusTagType(status?: string) {
  if (status === 'WON') return 'success'
  if (status === 'LOST') return 'danger'
  if (status === 'SUBMITTED') return 'warning'
  return 'info'
}

// 详情各数据集
const openBidRecord = ref<any>(null)
const depositApplies = ref<any[]>([])
const depositRefundMap = ref<Record<string, any>>({})
const personBindings = ref<any[]>([])
const tenderFees = ref<any[]>([])
const tenderTasks = ref<any[]>([])

// 弹窗状态
const bindingDialogVisible = ref(false)
const bindingSubmitting = ref(false)
const bindingFormRef = ref<FormInstance>()
const certOptions = ref<any[]>([])
const bindingForm = ref({
  personCertificateId: undefined as number | undefined,
  personName: '',
  certificateType: '',
  bindingRole: ''
})
const bindingRules = {
  personCertificateId: [{ required: true, message: '请选择持证人员', trigger: 'change' }],
  bindingRole: [{ required: true, message: '请选择拟任岗位', trigger: 'change' }]
}

const feeDialogVisible = ref(false)
const feeSubmitting = ref(false)
const feeFormRef = ref<FormInstance>()
const feeForm = ref({
  feeType: 'BID_DOC',
  feeAmount: undefined as number | undefined,
  paymentDate: new Date().toISOString().slice(0, 10)
})
const feeRules = {
  feeType: [{ required: true, message: '请选择费用类型', trigger: 'change' }],
  feeAmount: [{ required: true, message: '请输入费用金额', trigger: 'blur' }]
}

const taskDialogVisible = ref(false)
const taskSubmitting = ref(false)
const taskFormRef = ref<FormInstance>()
const taskForm = ref({
  taskType: 'COMMERCIAL',
  responsiblePerson: '',
  deadline: ''
})
const taskRules = {
  taskType: [{ required: true, message: '请选择任务类型', trigger: 'change' }],
  responsiblePerson: [{ required: true, message: '请输入负责人', trigger: 'blur' }],
  deadline: [{ required: true, message: '请选择截止日期', trigger: 'change' }]
}

watch(
  () => [props.modelValue, props.register],
  async ([val]) => {
    if (val && props.register?.id) {
      activeTab.value = 'openBid'
      await loadAllDetail()
    }
  },
  { immediate: true }
)

async function loadAllDetail() {
  if (!props.register?.id) return
  loading.value = true
  const regId = props.register.id
  try {
    // 1. 开标记录
    try {
      const obRes: any = await getOpenBidByRegister(regId)
      openBidRecord.value = obRes.data || null
    } catch {
      openBidRecord.value = null
    }

    // 2. 保证金申请
    try {
      const depRes: any = await getTenderDepositPage({ registerId: regId, page: 1, size: 50 })
      depositApplies.value = depRes.data?.records || []
      // 保证金退还映射
      depositRefundMap.value = {}
      for (const apply of depositApplies.value) {
        try {
          const refRes: any = await getTenderRefundPage({ depositApplyId: apply.id, page: 1, size: 10 })
          if (refRes.data?.records?.length) {
            depositRefundMap.value[apply.id] = refRes.data.records[0]
          }
        } catch {
          // ignore
        }
      }
    } catch {
      depositApplies.value = []
    }

    // 3. 押证绑定 (B1)
    try {
      const pbRes: any = await getPersonBindings(regId)
      personBindings.value = pbRes.data || []
    } catch {
      personBindings.value = []
    }

    // 4. 投标费用 (B2)
    try {
      const feeRes: any = await getTenderFeePage({ registerId: regId, page: 1, size: 50 })
      tenderFees.value = feeRes.data?.records || []
    } catch {
      tenderFees.value = []
    }

    // 5. 编标任务 (B3)
    try {
      const taskRes: any = await getTenderTaskList(regId)
      tenderTasks.value = taskRes.data || []
    } catch {
      tenderTasks.value = []
    }
  } finally {
    loading.value = false
  }
}

// 保证金操作
async function handleSubmitDepositApproval(row: any) {
  await ElMessageBox.confirm('确定要提交该保证金申请至审批流吗？', '提示', { type: 'warning' })
  await submitTenderDeposit(row.id)
  ElMessage.success('保证金申请已提交审批')
  await loadAllDetail()
}

// 押证操作
async function handleAddBinding() {
  bindingForm.value = {
    personCertificateId: undefined,
    personName: '',
    certificateType: '',
    bindingRole: '项目经理'
  }
  bindingDialogVisible.value = true
  try {
    const certRes: any = await getCertificatePage({ type: 'person', page: 1, size: 100 })
    certOptions.value = certRes.data?.records || []
  } catch {
    certOptions.value = []
  }
}

function handleCertSelect(certId: number) {
  const found = certOptions.value.find((c) => c.id === certId)
  if (found) {
    bindingForm.value.personName = found.personName
    bindingForm.value.certificateType = found.certificateType
  }
}

async function submitBinding() {
  await bindingFormRef.value?.validate()
  bindingSubmitting.value = true
  try {
    await bindPerson(props.register.id, {
      projectId: props.register.projectId,
      personCertificateId: bindingForm.value.personCertificateId,
      personName: bindingForm.value.personName,
      certificateType: bindingForm.value.certificateType,
      bindingRole: bindingForm.value.bindingRole
    })
    ElMessage.success('人员押证锁定成功')
    bindingDialogVisible.value = false
    const pbRes: any = await getPersonBindings(props.register.id)
    personBindings.value = pbRes.data || []
  } finally {
    bindingSubmitting.value = false
  }
}

// 费用操作
function handleAddFee() {
  feeForm.value = {
    feeType: 'BID_DOC',
    feeAmount: undefined,
    paymentDate: new Date().toISOString().slice(0, 10)
  }
  feeDialogVisible.value = true
}

async function submitFee() {
  await feeFormRef.value?.validate()
  feeSubmitting.value = true
  try {
    await createTenderFee({
      registerId: props.register.id,
      projectId: props.register.projectId,
      feeType: feeForm.value.feeType,
      feeAmount: feeForm.value.feeAmount,
      paymentDate: feeForm.value.paymentDate,
      status: 'DRAFT'
    })
    ElMessage.success('投标费用登记成功')
    feeDialogVisible.value = false
    const feeRes: any = await getTenderFeePage({ registerId: props.register.id, page: 1, size: 50 })
    tenderFees.value = feeRes.data?.records || []
  } finally {
    feeSubmitting.value = false
  }
}

async function handlePayFee(row: any) {
  await ElMessageBox.confirm('确认已支付该笔投标费用吗？', '提示', { type: 'warning' })
  await confirmTenderFeePayment(row.id)
  ElMessage.success('费用支付已确认')
  const feeRes: any = await getTenderFeePage({ registerId: props.register.id, page: 1, size: 50 })
  tenderFees.value = feeRes.data?.records || []
}

async function handleDeleteFee(row: any) {
  await ElMessageBox.confirm('确定要删除该笔费用记录吗？', '提示', { type: 'warning' })
  await deleteTenderFee(row.id)
  ElMessage.success('删除成功')
  const feeRes: any = await getTenderFeePage({ registerId: props.register.id, page: 1, size: 50 })
  tenderFees.value = feeRes.data?.records || []
}

// 任务操作
function handleAddTask() {
  taskForm.value = {
    taskType: 'COMMERCIAL',
    responsiblePerson: '',
    deadline: props.register.openDate || ''
  }
  taskDialogVisible.value = true
}

async function submitTask() {
  await taskFormRef.value?.validate()
  taskSubmitting.value = true
  try {
    await createTenderTask({
      registerId: props.register.id,
      taskType: taskForm.value.taskType,
      responsiblePerson: taskForm.value.responsiblePerson,
      deadline: taskForm.value.deadline,
      status: 'PENDING'
    })
    ElMessage.success('编标任务分派成功')
    taskDialogVisible.value = false
    const taskRes: any = await getTenderTaskList(props.register.id)
    tenderTasks.value = taskRes.data || []
  } finally {
    taskSubmitting.value = false
  }
}

async function handleCompleteTask(row: any) {
  await completeTenderTask(row.id)
  ElMessage.success('任务已标记完成')
  const taskRes: any = await getTenderTaskList(props.register.id)
  tenderTasks.value = taskRes.data || []
}

async function handleDeleteTask(row: any) {
  await ElMessageBox.confirm('确定要删除该编标任务吗？', '提示', { type: 'warning' })
  await deleteTenderTask(row.id)
  ElMessage.success('删除成功')
  const taskRes: any = await getTenderTaskList(props.register.id)
  tenderTasks.value = taskRes.data || []
}
</script>

<style scoped>
.drawer-content {
  padding: 0 var(--zw-space-sm);
}

.summary-banner {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: var(--zw-space-md);
  padding: var(--zw-space-md);
  background-color: var(--zw-color-bg-base);
  border: 1px solid var(--zw-color-border-light);
  border-radius: var(--zw-border-radius-base);
}

.summary-item {
  display: flex;
  flex-direction: column;
  gap: var(--zw-space-xs);
}

.summary-item .label {
  font-size: var(--zw-font-size-xs);
  color: var(--zw-color-text-secondary);
}

.summary-item .value {
  font-size: var(--zw-font-size-sm);
  color: var(--zw-color-text-primary);
}

.tab-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.mb-3 {
  margin-bottom: var(--zw-space-sm);
}

.mb-4 {
  margin-bottom: var(--zw-space-md);
}

.ml-2 {
  margin-left: var(--zw-space-sm);
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
</style>
