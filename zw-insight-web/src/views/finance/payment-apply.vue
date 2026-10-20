<template>
  <div class="finance-container">
    <el-card shadow="never">
      <el-form :model="queryParams" inline>
        <el-form-item label="项目">
          <el-select
            v-model="queryParams.projectId"
            placeholder="请选择项目"
            filterable
            remote
            :remote-method="searchProject"
            clearable
            style="width: 220px"
          >
            <el-option v-for="item in projectList" :key="item.id" :label="item.projectName" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <!-- 筛选值域与下方 statusMap 同源：后端 PaymentApplyService 产生
               DRAFT/SUBMITTED/APPROVED/REJECTED；支付态另走 payStatus 筛选（V2026_56） -->
          <el-select v-model="queryParams.status" placeholder="全部" clearable style="width: 120px">
            <el-option label="草稿" value="DRAFT" />
            <el-option label="审批中" value="SUBMITTED" />
            <el-option label="已通过" value="APPROVED" />
            <el-option label="已驳回" value="REJECTED" />
          </el-select>
        </el-form-item>
        <el-form-item label="支付状态">
          <el-select v-model="queryParams.payStatus" placeholder="全部" clearable style="width: 120px">
            <el-option label="未支付" value="UNPAID" />
            <el-option label="部分支付" value="PARTIAL_PAID" />
            <el-option label="已支付" value="PAID" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">
            <el-icon><Search /></el-icon>搜索
          </el-button>
          <el-button @click="handleReset">
            <el-icon><Refresh /></el-icon>重置
          </el-button>
        </el-form-item>
      </el-form>

      <ZwListToolbar
        :columns="paymentColumns"
        :visible="columnVisible"
        @update:visible="setVisible"
        @reset="resetColumns"
        @refresh="loadData"
      >
<el-button v-if="canCreate" type="primary" @click="handleAdd">
          <el-icon><Plus /></el-icon>新增付款申请
        </el-button>
        <!-- 批量操作（Phase 1.2）：勾选态驱动；后端整体单事务，任一失败全部回滚 -->
        <el-button
          v-if="canPay"
          type="danger"
          plain
          :disabled="!selectedRows.length"
          :loading="batchLoading"
          @click="handleBatchDelete"
        >批量删除（{{ selectedRows.length }}）</el-button>
        <el-button
          v-if="canPay"
          type="success"
          plain
          :disabled="!selectedRows.length"
          :loading="batchLoading"
          @click="handleBatchSubmit"
        >批量提交（{{ selectedRows.length }}）</el-button>
      </ZwListToolbar>

            <!-- 首屏骨架（感知性能 S1.4）：无数据且加载中显示骨架；翻页保留 v-loading 遮罩 -->
            <el-skeleton v-if="loading && !tableData.length" :rows="5" animated />
            <el-table v-else :data="tableData" v-loading="loading" border @selection-change="handleSelectionChange">
        <el-table-column type="selection" width="44" />
        <el-table-column v-if="columnVisible[0]" prop="projectName" label="项目名称" min-width="180" show-overflow-tooltip />
        <el-table-column v-if="columnVisible[1]" prop="supplierName" label="收款单位" width="150" show-overflow-tooltip />
        <el-table-column v-if="columnVisible[2]" prop="paymentAmount" label="付款金额" width="130" align="right">
          <template #default="{ row }">{{ formatMoney(row.paymentAmount) }}</template>
        </el-table-column>
        <!-- 在途占用（V2026_115）：同合同审批中申请合计（含本单），生效前冻结额度 -->
        <el-table-column v-if="columnVisible[3]" label="申请中(在途)" width="130" align="right">
          <template #default="{ row }">
            <span v-if="row.applyingAmount != null" :class="{ 'in-flight-amount': Number(row.applyingAmount) > 0 }">{{ formatMoney(row.applyingAmount) }}</span>
            <span v-else>—</span>
          </template>
        </el-table-column>
        <el-table-column v-if="columnVisible[4]" label="可申请余额" width="130" align="right">
          <template #default="{ row }">
            <span v-if="row.availableAmount != null">{{ formatMoney(row.availableAmount) }}</span>
            <span v-else>—</span>
          </template>
        </el-table-column>
        <el-table-column v-if="columnVisible[5]" prop="paymentDate" label="付款日期" width="110" />
        <el-table-column v-if="columnVisible[6]" label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)" size="small">
              {{ getStatusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column v-if="columnVisible[7]" label="支付状态" width="110" align="center">
          <template #default="{ row }">
            <!-- 支付态仅对已审批单据有意义（V2026_56：审批≠已付，现金口径）；
                 用原生 title 提示而非 el-tooltip 组件实例，避免每行额外组件开销 -->
            <el-tag v-if="row.status === 'APPROVED' && row.payStatus === 'PAID'" type="success" size="small"
              effect="dark" :title="row.payDate ? `支付日期 ${row.payDate}` : ''">已支付</el-tag>
            <el-tag v-else-if="row.status === 'APPROVED'" type="warning" size="small">未支付</el-tag>
            <span v-else class="pay-status-na">—</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="230" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleView(row)">查看</el-button>
            <el-button v-if="(row.status === 'DRAFT' || row.status === 'REJECTED') && canOperateRow(row)" link type="success" @click="handleSubmitApply(row)">提交</el-button>
            <el-button v-if="row.status === 'DRAFT' && canOperateRow(row)" link type="danger" @click="handleDelete(row)">删除</el-button>
            <el-button v-if="canPay && row.status === 'APPROVED' && row.payStatus !== 'PAID'" link type="warning" @click="handleMarkPaid(row)">标记支付</el-button>
            <el-button v-if="canPay && row.status === 'APPROVED' && row.payStatus === 'PAID'" link type="info" @click="handleRevokePaid(row)">撤销标记</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="queryParams.pageNum"
          v-model:page-size="queryParams.pageSize"
          :page-sizes="[10, 20, 50]"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="loadData"
          @current-change="loadData"
        />
      </div>
    </el-card>

    <stat-chart-panel
      v-if="canViewFundPlan"
      ref="planPanelRef"
      class="stat-panel"
      title="资金计划（按月应付预测）"
      :fetch-data="fetchFundPlan"
      :build-option="buildFundPlanOption"
      empty-text="暂无已审批的付款申请"
    />

    <!-- 新增弹窗 -->
    <el-dialog v-model="dialogVisible" title="新增付款申请" width="600px" destroy-on-close @keydown.enter.prevent>
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="100px">
        <ZwFormPane description="项目与合同确定后可付上限；提交时校验累计结算与在途占用。">
        <div class="zw-form-pane__section-title">基本信息</div>
        <el-form-item label="项目" prop="projectId">
          <el-select
            v-model="formData.projectId"
            placeholder="请选择项目"
            filterable
            remote
            :remote-method="searchProject"
            style="width: 100%"
            @change="loadContracts"
            data-keyboard-field
          >
            <el-option v-for="item in projectList" :key="item.id" :label="item.projectName" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="合同类型" prop="contractCategory">
          <el-select v-model="formData.contractCategory" placeholder="请选择合同类型" style="width: 100%" @change="loadContracts" data-keyboard-field>
            <el-option label="其他支出合同" value="OTHER_EXPENSE" />
            <el-option label="采购合同" value="PURCHASE" />
            <el-option label="劳务合同" value="LABOR" />
            <el-option label="机械合同" value="MACHINE" />
            <el-option label="分包合同" value="SUBCONTRACT" />
          </el-select>
        </el-form-item>
        <el-form-item label="关联合同" prop="contractId">
          <el-select
            v-model="formData.contractId"
            filterable
            placeholder="请先选择项目与合同类型"
            style="width: 100%"
            data-keyboard-field
          >
            <el-option
              v-for="c in contractOptions"
              :key="c.id"
              :label="c.contractName || c.contractCode || ('合同#' + c.id)"
              :value="c.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="收款单位" prop="supplierId">
          <SupplierSelector v-model="formData.supplierId" @change="handleSupplierChange" />
        </el-form-item>
        <div class="zw-form-pane__section-title">金额与日期</div>
        <el-form-item prop="paymentAmount">
          <template #label>
            <FieldHelpLabel
              label="付款金额"
              tooltip="可付上限 = 累计结算 + 净奖惩 − 累计已付，提交时校验，超出将被拒绝并提示最大可付金额。详见帮助中心业务术语。"
            />
          </template>
          <el-input-number v-model="formData.paymentAmount" :min="0" :precision="2" style="width: 100%" data-keyboard-field />
        </el-form-item>
        <el-form-item label="付款日期" prop="paymentDate">
          <el-date-picker v-model="formData.paymentDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" data-keyboard-field />
        </el-form-item>
        <div class="keyboard-hint" data-keyboard-field style="margin-top: var(--zw-space-sm); color: var(--el-text-color-secondary); font-size: var(--zw-font-size-xs);">
          💡 提示：按 Ctrl+Enter 快速提交表单 | 按 Tab 切换字段
        </div>
        </ZwFormPane>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleFormSubmit">确定 (Ctrl+Enter)</el-button>
      </template>
    </el-dialog>

    <!-- 提交审批确认弹窗（抄送人可选，V2026_123） -->
    <ApprovalSubmitDialog
      v-model:visible="submitDialogVisible"
      v-model:cc-user-ids="submitCcUserIds"
      :message="`确定提交付款申请${submitTarget?.projectName ? `（${submitTarget.projectName}）` : ''}进入审批？可选择抄送人。`"
      :submitting="submitSubmitting"
      @confirm="confirmSubmitApply"
    />

    <!-- 详情抽屉（真实数据源：GET /v1/finance/payment-apply/{id}；
         后端 getById 不回填 projectName，项目名回落取列表行同源字段） -->
    <el-drawer v-model="detailVisible" title="付款申请详情" size="480px" data-testid="payment-detail-drawer">
      <div v-loading="detailLoading">
        <div v-if="detailError" class="detail-error" data-testid="detail-error">
          <span>加载失败：{{ detailError }}</span>
          <el-button size="small" type="primary" @click="retryDetail">重试</el-button>
        </div>
        <el-descriptions v-else-if="detailData" :column="1" border size="small">
          <el-descriptions-item label="项目名称">{{ detailData.projectName || detailRow?.projectName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="合同类型">{{ getCategoryLabel(detailData.contractCategory) }}</el-descriptions-item>
          <el-descriptions-item label="收款单位">{{ detailData.supplierName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="付款金额">{{ formatMoney(detailData.paymentAmount) }}</el-descriptions-item>
          <el-descriptions-item label="付款日期">{{ detailData.paymentDate || '-' }}</el-descriptions-item>
          <el-descriptions-item>
            <template #label>
              <FieldHelpLabel
                label="累计结算快照"
                tooltip="提交付款申请时点的合同累计结算金额，保存供后续追溯，不随后续结算变化。"
              />
            </template>
            {{ formatMoney(detailData.cumulativeSettlementSnapshot) }}
          </el-descriptions-item>
          <el-descriptions-item>
            <template #label>
              <FieldHelpLabel
                label="未付金额快照"
                tooltip="提交时点的可付余额（累计结算 + 净奖惩 − 累计已付，与可付上限校验同口径），提交后不再变化。"
              />
            </template>
            {{ formatMoney(detailData.unpaidAmountSnapshot) }}
          </el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="getStatusType(detailData.status)" size="small">{{ getStatusLabel(detailData.status) }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="支付状态">
            <template v-if="detailData.status === 'APPROVED'">
              <el-tag :type="detailData.payStatus === 'PAID' ? 'success' : 'warning'" size="small">
                {{ detailData.payStatus === 'PAID' ? '已支付' : '未支付' }}
              </el-tag>
              <span v-if="detailData.payDate" class="pay-date-text">支付日期：{{ detailData.payDate }}</span>
            </template>
            <span v-else>—</span>
          </el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ detailData.createdAt || '-' }}</el-descriptions-item>
        </el-descriptions>
      </div>
    </el-drawer>

    <!-- 手工标记支付（V2026_56；已勾稽银行流水的单据后端会拒绝，支付态以流水为准）
         v-if 懒挂载：未打开时不创建 date-picker/input-number 组件实例，降低首屏挂载成本 -->
    <el-dialog v-if="canPay && payDialogVisible" v-model="payDialogVisible" title="标记已支付" width="440px" destroy-on-close>
      <el-alert type="info" :closable="false" show-icon style="margin-bottom: var(--zw-space-sm-md)"
        title="支付标记为现金口径，不改变项目支出（审批口径）；若已导入网银流水，请优先在银行流水页勾稽自动产生支付态" />
      <el-form label-width="100px">
        <el-form-item label="付款金额">
          <span>{{ formatMoney(payRow?.paymentAmount) }}</span>
        </el-form-item>
        <el-form-item label="支付日期" required>
          <el-date-picker v-model="payForm.payDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="支付账户ID">
          <el-input-number v-model="payForm.payAccountId" :min="1" controls-position="right" style="width: 100%" placeholder="选填，银行账户ID" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="payDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="paySubmitLoading" @click="submitMarkPaid">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance } from 'element-plus'
import { ZW_SHORTCUT_EVENTS } from '@/composables/useShortcuts'
import ColumnSettingPopover from '@/components/ColumnSettingPopover.vue'
import ApprovalSubmitDialog from '@/components/ApprovalSubmitDialog.vue'
import ZwFormPane from '@/components/ZwFormPane.vue'
import { useColumnSetting } from '@/composables/useColumnSetting'
import { useRowNavigation } from '@/composables/useRowNavigation'
import { usePermission } from '@/composables/usePermission'
import { useUserStore } from '@/stores/user'
import { getPaymentApplyPage, getPaymentApplyDetail, createPaymentApply, deletePaymentApply, submitPaymentApply, batchPaymentApply, getFundPlan, markPaymentApplyPaid, revokePaymentApplyPaid } from '@/api/finance'

// 列显隐配置（S2.1）：按 payment-apply-table 持久化 localStorage
const paymentColumns = [
  { key: 'projectName', label: '项目名称' },
  { key: 'supplierName', label: '收款单位' },
  { key: 'paymentAmount', label: '付款金额' },
  { key: 'applyingAmount', label: '申请中(在途)' },
  { key: 'availableAmount', label: '可申请余额' },
  { key: 'paymentDate', label: '付款日期' },
  { key: 'status', label: '状态' },
  { key: 'payStatus', label: '支付状态' },
]
const { visible: columnVisible, setVisible, reset: resetColumns } = useColumnSetting('payment-apply-table', paymentColumns)
import { getProjectList } from '@/api/project'
import { getOtherContractPage } from '@/api/contract'
import { getPurchaseContractPage } from '@/api/purchase'
import { getLaborContractPage } from '@/api/labor'
import { getMachineContractPage } from '@/api/machine'
import { getSubcontractPage } from '@/api/subcontract'
import SupplierSelector from '@/components/SupplierSelector.vue'
import StatChartPanel from '@/components/StatChartPanel.vue'
import FieldHelpLabel from '@/components/FieldHelpLabel.vue'
import { positiveAmount } from '@/utils/form-rules'
import { toWan } from '@/utils/chart-format'

const route = useRoute()
// V2026_124：财务现金动作（标记支付/撤销/批量）与资金计划面板仅财务可见；
// 项目经理通过项目付款入口复用本页，但只能查看/新增/编辑/提交本人草稿。
const { hasPermission } = usePermission()
const userStore = useUserStore()
const canPay = computed(() => hasPermission('finance:payment:submit'))
const canViewFundPlan = computed(() => hasPermission('finance:view'))
/** 有财务视图码或细粒度新增码才显示新增入口（与后端 save 的 OR 语义一致） */
const canCreate = computed(() => hasPermission('finance:view') || hasPermission('finance:paymentapply:add'))
const currentUserId = computed(() => userStore.userInfo?.userId)
/**
 * 行内写操作归属：财务不受限；非财务仅允许本人创建的单据。
 * createdBy 缺失的历史数据保持可见，由后端归属守卫兜底。
 */
function canOperateRow(row: any): boolean {
  if (canPay.value) return true
  if (row?.createdBy == null) return true
  return String(row.createdBy) === String(currentUserId.value)
}
const formRef = ref<FormInstance>()
const loading = ref(false)
const tableData = ref<any[]>([])
const total = ref(0)
const projectList = ref<any[]>([])
const contractOptions = ref<any[]>([])
const dialogVisible = ref(false)
const submitLoading = ref(false)
// 提交审批弹窗（抄送人 V2026_123）
const submitDialogVisible = ref(false)
const submitTarget = ref<any>(null)
const submitCcUserIds = ref<string[]>([])
const submitSubmitting = ref(false)

const queryParams = ref({
  pageNum: 1,
  pageSize: 10,
  // 雪花项目ID以字符串承接（Number() 精度丢位，A2-07 纪律）
  projectId: undefined as number | string | undefined,
  status: '',
  payStatus: ''
})

const formData = ref({
  projectId: undefined as number | string | undefined,
  // 审计缺陷 D7 修复：不再默认 OTHER_EXPENSE（该分支累计结算无回写路径，付款永不可提交，
  // 已由 21-finance-chain.spec.ts 钉住为产品缺口）；空值强制用户显式选择，required 规则兜底
  contractCategory: '',
  contractId: undefined as number | string | undefined,
  supplierId: undefined as number | string | undefined,
  supplierName: '',
  paymentAmount: 0,
  paymentDate: ''
})

const formRules = {
  projectId: [{ required: true, message: '请选择项目', trigger: 'change' }],
  contractCategory: [{ required: true, message: '请选择合同类型', trigger: 'change' }],
  contractId: [{ required: true, message: '请选择关联合同', trigger: 'change' }],
  supplierId: [{ required: true, message: '请选择收款单位', trigger: 'change' }],
  paymentAmount: [{ required: true, message: '请输入付款金额', trigger: 'blur' }, ...positiveAmount('付款金额必须大于 0')],
  paymentDate: [{ required: true, message: '请选择付款日期', trigger: 'change' }]
}

/**
 * 按合同类型 + 项目加载可选合同（与后端 PaymentApplyService 按 contractCategory 路由一致）。
 * 项目或合同类型变化时重新加载并清空已选合同。
 */
async function loadContracts() {
  formData.value.contractId = undefined
  contractOptions.value = []
  const pid = formData.value.projectId
  if (!pid) return
  const params: any = { projectId: pid, page: 1, size: 100, pageNum: 1, pageSize: 100 }
  let res: any
  switch (formData.value.contractCategory) {
    case 'PURCHASE': res = await getPurchaseContractPage(params); break
    case 'LABOR': res = await getLaborContractPage(params); break
    case 'MACHINE': res = await getMachineContractPage(params); break
    case 'SUBCONTRACT': res = await getSubcontractPage(params); break
    default: res = await getOtherContractPage({ ...params, contractCategory: 'OTHER_EXPENSE' })
  }
  contractOptions.value = res.data?.records || res.data || []
}

function handleSupplierChange(_val: number | string | undefined, item: any) {
  formData.value.supplierName = item?.supplierName || ''
}

const statusMap: Record<string, { label: string; type: string }> = {
  DRAFT: { label: '草稿', type: 'info' },
  SUBMITTED: { label: '审批中', type: 'warning' },
  APPROVING: { label: '审批中', type: 'warning' },
  APPROVED: { label: '已通过', type: 'success' },
  REJECTED: { label: '已驳回', type: 'danger' },
  PAID: { label: '已付款', type: 'primary' }
}

function getStatusLabel(status: string) {
  return statusMap[status]?.label || status
}

function getStatusType(status: string) {
  return (statusMap[status]?.type || 'info') as any
}

function formatMoney(val: number) {
  if (!val && val !== 0) return '-'
  return val.toLocaleString('zh-CN', { minimumFractionDigits: 2 })
}

async function searchProject(query: string) {
  const res: any = await getProjectList({ projectName: query })
  projectList.value = res.data || []
}

async function loadData() {
  loading.value = true
  try {
    const res: any = await getPaymentApplyPage(queryParams.value)
    tableData.value = res.data?.records || []
    total.value = res.data?.total || 0
    rowNav.reset() // 数据刷新时重置行导航状态
  } finally {
    loading.value = false
  }
}

// 行级键盘导航：composable 化（3b 去重；Enter=查看详情）
const rowNav = useRowNavigation(tableData, (row) => handleView(row))

function handleSearch() {
  queryParams.value.pageNum = 1
  loadData()
  planPanelRef.value?.reload()
}

function handleReset() {
  queryParams.value = { pageNum: 1, pageSize: 10, projectId: undefined as number | undefined, status: '', payStatus: '' }
  loadData()
  planPanelRef.value?.reload()
}

function handleAdd() {
  formData.value = { projectId: undefined, contractCategory: '', contractId: undefined, supplierId: undefined, supplierName: '', paymentAmount: 0, paymentDate: '' }
  contractOptions.value = []
  dialogVisible.value = true
}

function handleView(row: any) {
  loadDetail(row)
}

// ================= 详情抽屉（真实接口 /v1/finance/payment-apply/{id}） =================
const detailVisible = ref(false)
const detailLoading = ref(false)
const detailError = ref('')
const detailData = ref<any>(null)
const detailRow = ref<any>(null)

// 合同类型枚举与新增表单选项同源（后端 PaymentApplyService 按 contractCategory 路由）
const categoryMap: Record<string, string> = {
  OTHER_EXPENSE: '其他支出合同',
  PURCHASE: '采购合同',
  LABOR: '劳务合同',
  MACHINE: '机械合同',
  SUBCONTRACT: '分包合同'
}

function getCategoryLabel(category: string) {
  return categoryMap[category] || category || '-'
}

async function loadDetail(row: any) {
  detailRow.value = row
  detailData.value = null
  detailError.value = ''
  detailVisible.value = true
  detailLoading.value = true
  try {
    const res: any = await getPaymentApplyDetail(row.id)
    detailData.value = res.data || null
    if (!res.data) {
      // 不静默：返回空数据显式进入错误态
      detailError.value = '接口未返回详情数据'
      ElMessage.error('加载付款申请详情失败：接口未返回数据')
    }
  } catch (e: any) {
    // 不静默：失败显式提示并在抽屉内提供重试入口
    detailError.value = e?.message || '接口异常'
    ElMessage.error('加载付款申请详情失败：' + (e?.message || '接口异常'))
  } finally {
    detailLoading.value = false
  }
}

function retryDetail() {
  if (detailRow.value) loadDetail(detailRow.value)
}

async function handleFormSubmit() {
  await formRef.value?.validate()
  submitLoading.value = true
  try {
    await createPaymentApply({ ...formData.value, projectId: formData.value.projectId! })
    ElMessage.success('新增成功')
    dialogVisible.value = false
    loadData()
  } finally {
    submitLoading.value = false
  }
}

function handleSubmitApply(row: any) {
  submitTarget.value = row
  submitCcUserIds.value = []
  submitDialogVisible.value = true
}

async function confirmSubmitApply() {
  if (!submitTarget.value) return
  submitSubmitting.value = true
  try {
    await submitPaymentApply(submitTarget.value.id, submitCcUserIds.value)
    ElMessage.success('提交成功')
    submitDialogVisible.value = false
    submitTarget.value = null
    loadData()
  } finally {
    submitSubmitting.value = false
  }
}

async function handleDelete(row: any) {
  await ElMessageBox.confirm('确定要删除吗？', '提示', { type: 'warning' })
  await deletePaymentApply(row.id)
  ElMessage.success('删除成功')
  loadData()
}

// ================= 支付执行态（V2026_56） =================
// 支付态优先由银行流水勾稽自动产生；无网银流水导入场景用手工标记备选。
// 口径不变量：标记支付不回写项目 total_expense（审批口径），仅变更现金口径 pay_status。
const payDialogVisible = ref(false)
const paySubmitLoading = ref(false)
const payRow = ref<any>(null)
const payForm = ref({ payDate: '', payAccountId: undefined as number | undefined })

function handleMarkPaid(row: any) {
  payRow.value = row
  payForm.value = { payDate: new Date().toISOString().slice(0, 10), payAccountId: undefined }
  payDialogVisible.value = true
}

async function submitMarkPaid() {
  if (!payForm.value.payDate) {
    ElMessage.warning('请选择支付日期')
    return
  }
  paySubmitLoading.value = true
  try {
    await markPaymentApplyPaid(payRow.value.id, payForm.value.payDate, payForm.value.payAccountId)
    ElMessage.success('已标记支付')
    payDialogVisible.value = false
    loadData()
  } finally {
    paySubmitLoading.value = false
  }
}

async function handleRevokePaid(row: any) {
  await ElMessageBox.confirm(
    '确定撤销该付款申请的支付标记吗？若支付态由银行流水勾稽产生，需到银行流水页取消勾稽。',
    '提示', { type: 'warning' })
  await revokePaymentApplyPaid(row.id)
  ElMessage.success('已撤销支付标记')
  loadData()
}

// ================= 批量操作（Phase 1.2） =================
const selectedRows = ref<any[]>([])
const batchLoading = ref(false)

function handleSelectionChange(rows: any[]) {
  selectedRows.value = rows
}

/** 批量提交：仅 DRAFT/REJECTED 可提交；含非法状态项时后端整体回滚，前端预检并定位首个问题项 */
async function handleBatchSubmit() {
  const invalid = selectedRows.value.find((r) => r.status !== 'DRAFT' && r.status !== 'REJECTED')
  if (invalid) {
    ElMessage.warning(`勾选项含不可提交状态（${getStatusLabel(invalid.status)}），仅草稿/已驳回可提交`)
    return
  }
  await ElMessageBox.confirm(`确定批量提交选中的 ${selectedRows.value.length} 条付款申请吗？`, '提示', { type: 'warning' })
  await runBatch('submit')
}

/** 批量删除：仅 DRAFT 可删；后端 @SecondaryConfirm 449 时拦截器自动弹密码框重发 */
async function handleBatchDelete() {
  const invalid = selectedRows.value.find((r) => r.status !== 'DRAFT')
  if (invalid) {
    ElMessage.warning(`勾选项含不可删除状态（${getStatusLabel(invalid.status)}），仅草稿可删除`)
    return
  }
  await ElMessageBox.confirm(`确定批量删除选中的 ${selectedRows.value.length} 条付款申请吗？删除后不可恢复。`, '提示', { type: 'warning' })
  await runBatch('delete')
}

async function runBatch(action: 'delete' | 'submit') {
  batchLoading.value = true
  try {
    const res: any = await batchPaymentApply(action, selectedRows.value.map((r) => r.id))
    ElMessage.success(`批量${action === 'delete' ? '删除' : '提交'}成功 ${res?.data ?? selectedRows.value.length} 条`)
    loadData()
  } catch {
    // 失败（含二次确认取消）已由拦截器/弹框提示；整体事务回滚，无需局部刷新
  } finally {
    batchLoading.value = false
  }
}

// 全局快捷键 Ctrl+S / Ctrl+Enter 由 useShortcuts 经事件总线派发；
// 本组件仅在新增/编辑弹窗打开时消费，触发表单提交（替代原从未注册的半成品 handleGlobalKeydown）。
function handleSaveShortcut() {
  if (dialogVisible.value) handleFormSubmit()
}

onMounted(async () => {
  const routeQuery = route?.query || {}
  const qPid = routeQuery.projectId != null ? String(routeQuery.projectId).trim() : ''
  if (qPid && qPid !== '0') {
    queryParams.value.projectId = qPid
  }
  loadData()
  await searchProject('')
  // P3-M5 B3：从采购结算页跳转时预填项目、采购合同与结算金额，并打开新增弹窗
  const pid = routeQuery.projectId != null ? String(routeQuery.projectId).trim() : ''
  const contractId = routeQuery.contractId != null ? String(routeQuery.contractId).trim() : ''
  const amount = Number(routeQuery.applyAmount)
  const category = String(routeQuery.contractCategory || '').trim()
  if (pid && contractId && category && pid !== '0' && contractId !== '0') {
    formData.value.projectId = pid
    formData.value.contractCategory = category
    await loadContracts()
    formData.value.contractId = contractId
    formData.value.paymentAmount = Number.isFinite(amount) && amount > 0 ? amount : 0
    const selected = contractOptions.value.find((item: any) => String(item.id) === contractId)
    formData.value.supplierId = selected?.partyBId || selected?.supplierId
    formData.value.supplierName = selected?.partyBName || selected?.supplierName || ''
    dialogVisible.value = true
  }
  window.addEventListener(ZW_SHORTCUT_EVENTS.save, handleSaveShortcut)
  rowNav.install()
})

onBeforeUnmount(() => {
  window.removeEventListener(ZW_SHORTCUT_EVENTS.save, handleSaveShortcut)
  rowNav.uninstall()
})

// ================= 资金计划面板 =================
const planPanelRef = ref<InstanceType<typeof StatChartPanel>>()

async function fetchFundPlan() {
  // 未选项目时后端跨项目汇总全部（与列表同口径），不再要求先选项目
  const res: any = await getFundPlan(queryParams.value.projectId || undefined, 6)
  return res.data
}

function buildFundPlanOption(list: any[]) {
  if (!list?.length) return null
  return {
    tooltip: { trigger: 'axis' },
    legend: { bottom: 0 },
    grid: { left: '3%', right: '4%', bottom: '14%', containLabel: true },
    xAxis: { type: 'category', data: list.map(i => i.month) },
    yAxis: [
      { type: 'value', name: '万元', axisLabel: { formatter: '{value} 万' } },
      { type: 'value', name: '申请笔数', minInterval: 1 }
    ],
    series: [
      { name: '计划应付', type: 'bar', barMaxWidth: 36, data: list.map(i => toWan(Number(i.plannedAmount) || 0)) },
      { name: '申请笔数', type: 'line', yAxisIndex: 1, data: list.map(i => Number(i.applyCount) || 0) }
    ]
  }
}
</script>

<style scoped>
.finance-container {
  padding: var(--zw-space-md);
}
.stat-panel {
  margin-bottom: var(--zw-space-md);
}
.table-toolbar {
  margin-bottom: var(--zw-space-md);
}
.pagination-wrap {
  margin-top: var(--zw-space-md);
  display: flex;
  justify-content: flex-end;
}
.detail-error {
  display: flex;
  align-items: center;
  gap: var(--zw-space-sm-md);
  color: var(--el-color-danger);
  padding: var(--zw-space-sm) 0;
}

/* P1 Keyboard Navigation Styles */
.el-table__row.keyboard-focused {
  background-color: var(--zw-info-light) !important;
  outline: 2px solid var(--el-color-primary) !important;
  outline-offset: -2px;
}

.keyboard-hint {
  border-left: 3px solid var(--el-color-warning);
  padding-left: var(--zw-space-sm);
}
.in-flight-amount { color: var(--el-color-warning); font-weight: 600; }

.pay-status-na {
  color: var(--el-text-color-placeholder);
}
.pay-date-text {
  margin-left: var(--zw-space-sm);
  font-size: var(--zw-font-size-xs);
  color: var(--el-text-color-secondary);
}
</style>
