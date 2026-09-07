<template>
  <div class="cost-account-container">
    <el-card shadow="never">
      <!-- 筛选区 -->
      <el-form :model="queryParams" inline>
        <el-form-item label="项目">
          <el-select
            v-model="queryParams.projectId"
            placeholder="请选择项目"
            filterable
            remote
            :remote-method="searchProject"
            clearable
            style="width: 240px"
            @change="handleProjectChange"
          >
            <el-option v-for="item in projectList" :key="item.id" :label="item.projectName" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="费用类别">
          <el-select v-model="queryParams.costCategory" placeholder="全部" clearable style="width: 140px">
            <el-option v-for="(label, value) in CATEGORY_LABELS" :key="value" :label="label" :value="value" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryParams.status" placeholder="全部" clearable style="width: 120px">
            <el-option label="活跃" value="ACTIVE" />
            <el-option label="锁定" value="LOCKED" />
            <el-option label="已关闭" value="CLOSED" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch"><el-icon><Search /></el-icon>查询</el-button>
          <el-button @click="handleReset"><el-icon><Refresh /></el-icon>重置</el-button>
        </el-form-item>
      </el-form>

      <!-- 汇总条：成本主线六维一眼可见 -->
      <div v-if="queryParams.projectId && rows.length" class="summary-strip">
        <div class="sum-item">
          <span class="sum-label">目标成本</span>
          <span class="sum-value">{{ toWan(totals.baseline) }}</span>
        </div>
        <div class="sum-item">
          <span class="sum-label">当前预算</span>
          <span class="sum-value">{{ toWan(totals.current) }}</span>
        </div>
        <div class="sum-item">
          <span class="sum-label">已承诺</span>
          <span class="sum-value">{{ toWan(totals.commitment) }}</span>
        </div>
        <div class="sum-item">
          <span class="sum-label">实际成本</span>
          <span class="sum-value">{{ toWan(totals.actual) }}</span>
        </div>
        <div class="sum-item">
          <span class="sum-label">完工预测</span>
          <span class="sum-value">{{ toWan(totals.forecast) }}</span>
        </div>
        <div class="sum-item">
          <span class="sum-label">偏差</span>
          <span class="sum-value" :class="totals.variance < 0 ? 'is-danger' : 'is-success'">
            {{ toWan(totals.variance) }}
          </span>
        </div>
      </div>

      <div class="table-toolbar">
        <el-button type="primary" :disabled="!queryParams.projectId" @click="handleAdd(null)">
          <el-icon><Plus /></el-icon>新增账户
        </el-button>
        <el-button :disabled="!selection.length" @click="handleBatchDelete">
          批量删除（{{ selection.length }}）
        </el-button>
        <el-button
          type="warning"
          :disabled="!queryParams.projectId"
          :loading="rollupLoading"
          @click="triggerRollup"
        >
          <el-icon><Refresh /></el-icon>立即归集
        </el-button>
        <span class="toolbar-hint">
          金额单位：万元。承诺=有效合同；实际=已审批结算+材料出库（非付款）。归集每日 02:30 自动对账。
        </span>
      </div>

      <!-- 归集报告：把「哪些单据没归集、为什么」透明地交给业务人员 -->
      <el-alert
        v-if="rollupReport && rollupReport.needsAttention"
        :type="rollupReport.failed?.length ? 'error' : 'warning'"
        show-icon
        :closable="true"
        class="rollup-alert"
        @close="rollupReport = null"
      >
        <template #title>
          {{ rollupReport.message }}
          <el-button type="primary" link size="small" @click="rollupDialogVisible = true">查看详情</el-button>
        </template>
      </el-alert>

      <!-- 账户表格 -->
      <el-table
        v-loading="loading"
        :data="pagedRows"
        border
        stripe
        row-key="id"
        :tree-props="{ children: 'children' }"
        default-expand-all
        @selection-change="(v: CostAccount[]) => (selection = v)"
      >
        <el-table-column type="selection" width="46" :selectable="isSelectable" />
        <el-table-column prop="accountCode" label="编码" width="140" fixed="left" />
        <el-table-column prop="accountName" label="账户名称" min-width="180" show-overflow-tooltip />
        <el-table-column prop="costCategory" label="费用类别" width="110" align="center">
          <template #default="{ row }">
            <el-tag size="small" effect="plain">{{ CATEGORY_LABELS[row.costCategory] || row.costCategory }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="目标成本" width="120" align="right">
          <template #default="{ row }">{{ toWan(row.baselineAmount) }}</template>
        </el-table-column>
        <el-table-column label="当前预算" width="120" align="right">
          <template #default="{ row }">
            <span :class="{ 'is-changed': hasChanged(row) }">{{ toWan(row.currentAmount) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="已承诺" width="120" align="right">
          <template #default="{ row }">{{ toWan(row.commitmentAmount) }}</template>
        </el-table-column>
        <el-table-column label="实际成本" width="120" align="right">
          <template #default="{ row }">
            <span :class="{ 'is-danger': isOverrun(row) }">{{ toWan(row.actualAmount) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="完工预测" width="120" align="right">
          <template #default="{ row }">{{ toWan(row.forecastAmount) }}</template>
        </el-table-column>
        <el-table-column label="偏差" width="110" align="right">
          <template #default="{ row }">
            <span :class="varianceOf(row) < 0 ? 'is-danger' : 'is-success'">{{ toWan(varianceOf(row)) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="使用率" width="130" align="center">
          <template #default="{ row }">
            <el-progress
              :percentage="clampPercent(usageRateOf(row))"
              :stroke-width="10"
              :color="usageColor(usageRateOf(row))"
              :format="(p: number) => `${p.toFixed(1)}%`"
            />
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="statusTagType(row.status)">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="210" fixed="right" align="center">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="openLedger(row)">流水</el-button>
            <el-button type="primary" link size="small" @click="handleAdd(row)">加子账户</el-button>
            <el-button type="primary" link size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button
              v-if="row.status === 'ACTIVE'"
              type="warning" link size="small" @click="handleLock(row)"
            >锁定</el-button>
            <el-button type="danger" link size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="pagination.page"
          v-model:page-size="pagination.size"
          :total="pagination.total"
          :page-sizes="[20, 50, 100]"
          layout="total, sizes, prev, pager, next"
          @size-change="loadPage"
          @current-change="loadPage"
        />
      </div>
    </el-card>

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="640px" :close-on-click-modal="false">
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="110px">
        <el-form-item v-if="parentLabel" label="父账户">
          <el-input :model-value="parentLabel" disabled />
        </el-form-item>
        <el-form-item label="账户编码" prop="accountCode">
          <el-input v-model="formData.accountCode" placeholder="如 01 / 01.02 / 01.02.03" maxlength="50" />
        </el-form-item>
        <el-form-item label="账户名称" prop="accountName">
          <el-input v-model="formData.accountName" placeholder="如 主体结构-混凝土" maxlength="200" />
        </el-form-item>
        <el-form-item label="费用类别" prop="costCategory">
          <el-select v-model="formData.costCategory" placeholder="请选择" style="width: 100%">
            <el-option v-for="(label, value) in CATEGORY_LABELS" :key="value" :label="label" :value="value" />
          </el-select>
        </el-form-item>
        <el-form-item label="费用子类">
          <el-input v-model="formData.costSubcategory" placeholder="可空，对应费用子类字典" maxlength="100" />
        </el-form-item>
        <el-form-item label="关联 WBS">
          <el-select
            v-model="formData.wbsNodeId"
            placeholder="可空，挂到工作包后成本与范围/进度同口径"
            clearable
            filterable
            style="width: 100%"
          >
            <el-option
              v-for="n in wbsOptions"
              :key="n.id"
              :label="`${n.nodeCode} ${n.nodeName}`"
              :value="n.id!"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="目标成本" prop="baselineAmount">
          <el-input-number v-model="formData.baselineAmount" :min="0" :precision="2" :step="10000" style="width: 100%" />
          <div class="field-hint">首次批准预算，作为考核基准，后续只能通过变更事件调整当前预算。</div>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="formData.remark" type="textarea" :rows="2" maxlength="500" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 成本流水抽屉：这个数字是怎么来的 -->
    <el-drawer v-model="ledgerVisible" :title="`成本流水 - ${ledgerAccount?.accountName || ''}`" size="55%">
      <div v-loading="ledgerLoading">
        <el-alert
          v-if="!ledgerLoading && !ledgerRows.length"
          title="该账户暂无金额变动流水"
          type="info"
          :closable="false"
          show-icon
        />
        <el-table v-else :data="ledgerRows" border size="small">
          <el-table-column prop="occurredAt" label="发生时间" width="160" />
          <el-table-column prop="amountType" label="维度" width="100" align="center">
            <template #default="{ row }">
              <el-tag size="small" effect="plain">{{ AMOUNT_TYPE_LABELS[row.amountType] || row.amountType }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="变动" width="130" align="right">
            <template #default="{ row }">
              <span :class="Number(row.deltaAmount) < 0 ? 'is-danger' : 'is-success'">
                {{ Number(row.deltaAmount) > 0 ? '+' : '' }}{{ toWan(row.deltaAmount) }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="变动后余额" width="130" align="right">
            <template #default="{ row }">{{ toWan(row.balanceAfter) }}</template>
          </el-table-column>
          <el-table-column prop="sourceType" label="来源" width="120" align="center">
            <template #default="{ row }">{{ SOURCE_LABELS[row.sourceType] || row.sourceType }}</template>
          </el-table-column>
          <el-table-column prop="sourceNumber" label="来源单据" min-width="140" show-overflow-tooltip />
          <el-table-column prop="remark" label="备注" min-width="160" show-overflow-tooltip />
        </el-table>
      </div>
    </el-drawer>

    <!-- 归集报告 -->
    <el-dialog v-model="rollupDialogVisible" title="成本归集报告" width="720px" top="5vh">
      <template #header>
        <span>归集结果</span>
        <el-button type="primary" link style="margin-left: auto" @click="triggerRollup" :disabled="rollupLoading">立即重新归集</el-button>
      </template>
      <el-table :data="rollupReport?.unmapped || []" border size="small" height="350">
        <el-table-column prop="category" label="科目" width="120" align="center">
          <template #default="{ row }">{{ CATEGORY_LABELS[row.category] || row.category }}</template>
        </el-table-column>
        <el-table-column prop="sourceType" label="来源类型" width="140" align="center">
          <template #default="{ row }">{{ sourceTypeText(row.sourceType) }}</template>
        </el-table-column>
        <el-table-column prop="sourceNumber" label="单据编号" min-width="160" show-overflow-tooltip />
        <el-table-column prop="amount" label="金额" width="120" align="right">{{ formatMoney(row.amount) }}</el-table-column>
        <el-table-column label="原因" min-width="220" show-overflow-tooltip>
          <template #default="{ row }"><el-tag effect="plain" size="small">{{ row.reason }}</el-tag></template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right" align="center">
          <template #default="{ row }">
            <el-button type="primary" size="small" link @click="openBindDialog(row)">绑定账户</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div v-if="!rollupReport?.unmapped?.length && !rollupReport?.failed?.length" style="text-align:center;padding:80px;color:#999;font-size:14px">
        所有源单据均已自动归集或已明确指定归属账户，无待处理事项。
      </div>
      <div v-if="rollupReport?.failed?.length" class="failed-section">
        <span class="failed-label">记账失败（账户已锁定/关闭）：</span>
        <el-tag
          v-for="(f, i) in rollupReport.failed"
          :key="i"
          type="danger"
          effect="plain"
          class="failed-tag"
        >
          {{ f.accountCode }} · {{ AMOUNT_TYPE_LABELS[f.amountType] || f.amountType }} · {{ f.reason }}
        </el-tag>
      </div>
      <template #footer>
        <el-button @click="rollupDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 绑定弹窗：把一张未归集单据指定到同科目的具体账户 -->
    <el-dialog v-model="bindDialogVisible" title="绑定单据到成本账户" width="520px" top="8vh">
      <el-alert
        :title="`单据「${currentUnmappedDoc?.sourceNumber || currentUnmappedDoc?.sourceId}」未能自动归集`"
        :description="currentUnmappedDoc?.reason"
        type="warning"
        :closable="false"
        show-icon
        style="margin-bottom: 16px"
      />
      <el-form label-width="90px">
        <el-form-item label="金额">
          <span class="bind-amount">{{ formatMoney(currentUnmappedDoc?.amount) }} 元</span>
        </el-form-item>
        <el-form-item label="目标账户">
          <el-select
            v-model="bindingAccountId"
            filterable
            placeholder="仅列出同科目且未关闭的账户"
            style="width: 100%"
          >
            <el-option
              v-for="acc in bindCandidates"
              :key="acc.id"
              :label="`${acc.accountCode} · ${acc.accountName}`"
              :value="acc.id!"
            />
          </el-select>
          <div v-if="!bindCandidates.length" class="field-hint">
            本项目下无「{{ CATEGORY_LABELS[currentUnmappedDoc?.category || ''] || currentUnmappedDoc?.category }}」科目的可用账户，
            请先新增该科目的成本账户。
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="bindDialogVisible = false">取消</el-button>
        <el-button
          type="primary"
          :loading="bindingSaving"
          :disabled="!bindingAccountId"
          @click="saveBinding"
        >确认绑定并重新归集</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { Search, Refresh, Plus } from '@element-plus/icons-vue'
import {
  getCostAccountPage, createCostAccount, updateCostAccount,
  deleteCostAccount, lockCostAccount, getCostAccountLedger,
  costRollUp, bindCostAccountLink,
  type CostAccount, type CostAccountTxn, type RollupReport, type UnmappedDoc
} from '@/api/cost-account'
import { getWbsSelectList, type WbsNode } from '@/api/wbs'
import { getProjectList } from '@/api/project'
import { toWan, clampPercent } from '@/utils/chart-format'

/** 费用类别：与后端 biz_cost_account.cost_category 枚举严格一致 */
const CATEGORY_LABELS: Record<string, string> = {
  MATERIAL: '材料费',
  LABOR: '人工费',
  MACHINE: '机械费',
  SUBCONTRACT: '分包费',
  INDIRECT: '间接费',
  OTHER: '其他费用'
}

/** 金额维度标签 */
const AMOUNT_TYPE_LABELS: Record<string, string> = {
  BASELINE: '目标成本',
  CURRENT: '当前预算',
  COMMITMENT: '已承诺',
  ACTUAL: '实际成本',
  FORECAST: '完工预测'
}

/** 流水来源标签 */
const SOURCE_LABELS: Record<string, string> = {
  CHANGE_EVENT: '变更事件',
  CONTRACT: '合同',
  PURCHASE: '采购',
  SETTLEMENT: '结算',
  PAYMENT: '付款',
  MATERIAL: '材料',
  MANUAL: '手工调整',
  // 归集对账与手工调整必须区分：审计要能分辨「系统算的」与「人改的」
  ROLLUP: '自动归集'
}

/** 归集源单据类型标签 */
const SOURCE_TYPE_LABELS: Record<string, string> = {
  PURCHASE_CONTRACT: '采购合同',
  LABOR_CONTRACT: '劳务合同',
  MACHINE_CONTRACT: '机械合同',
  SUBCONTRACT_CONTRACT: '分包合同',
  PURCHASE_SETTLEMENT: '采购结算',
  LABOR_SETTLEMENT: '劳务结算',
  MACHINE_WORK_SETTLEMENT: '机械结算',
  SUBCONTRACT_SETTLEMENT: '分包结算',
  MATERIAL_OUTBOUND: '材料出库'
}

const loading = ref(false)
const submitting = ref(false)
const dialogVisible = ref(false)
const ledgerVisible = ref(false)
const ledgerLoading = ref(false)
const formRef = ref<FormInstance>()
const rows = ref<CostAccount[]>([])
const selection = ref<CostAccount[]>([])
const projectList = ref<any[]>([])
const wbsOptions = ref<WbsNode[]>([])
const ledgerRows = ref<CostAccountTxn[]>([])
const ledgerAccount = ref<CostAccount | null>(null)
const editingId = ref<number | null>(null)
const parentLabel = ref('')

// ---- 成本归集 ----
const rollupLoading = ref(false)
const rollupDialogVisible = ref(false)
const rollupReport = ref<RollupReport | null>(null)

// ---- 单据绑定（消除科目多账户歧义）----
const bindDialogVisible = ref(false)
const bindingSaving = ref(false)
const currentUnmappedDoc = ref<UnmappedDoc | null>(null)
const bindingAccountId = ref<number | null>(null)

const queryParams = reactive({
  projectId: null as number | null,
  costCategory: '',
  status: ''
})

const pagination = reactive({ page: 1, size: 50, total: 0 })

const formData = reactive({
  accountCode: '',
  accountName: '',
  costCategory: 'MATERIAL',
  costSubcategory: '',
  wbsNodeId: null as number | null,
  baselineAmount: 0,
  remark: '',
  parentId: null as number | null
})

const formRules: FormRules = {
  accountCode: [{ required: true, message: '请输入账户编码', trigger: 'blur' }],
  accountName: [{ required: true, message: '请输入账户名称', trigger: 'blur' }],
  costCategory: [{ required: true, message: '请选择费用类别', trigger: 'change' }]
}

const dialogTitle = computed(() => (editingId.value ? '编辑成本账户' : '新增成本账户'))

/** 树形分页：后端分页返回扁平数据，前端按 parentId 装配（账户量级可控） */
const pagedRows = computed(() => assembleTree(rows.value))

const totals = computed(() => {
  const t = { baseline: 0, current: 0, commitment: 0, actual: 0, forecast: 0, variance: 0 }
  for (const r of rows.value) {
    t.baseline += num(r.baselineAmount)
    t.current += num(r.currentAmount)
    t.commitment += num(r.commitmentAmount)
    t.actual += num(r.actualAmount)
    t.forecast += num(r.forecastAmount)
  }
  t.variance = t.current - t.forecast
  return t
})

function assembleTree(list: CostAccount[]): CostAccount[] {
  const byId = new Map<number, CostAccount>()
  list.forEach(a => byId.set(a.id!, { ...a, children: [] }))
  const roots: CostAccount[] = []
  list.forEach(a => {
    const node = byId.get(a.id!)!
    const parent = a.parentId ? byId.get(a.parentId) : null
    if (parent && parent.id !== node.id) {
      parent.children = [...(parent.children || []), node]
    } else {
      roots.push(node)
    }
  })
  return roots
}

function num(v: unknown): number {
  const n = typeof v === 'string' ? parseFloat(v) : Number(v)
  return Number.isFinite(n) ? n : 0
}

function varianceOf(row: CostAccount): number {
  return num(row.currentAmount) - num(row.forecastAmount)
}

function usageRateOf(row: CostAccount): number {
  const current = num(row.currentAmount)
  if (!current) return 0
  return (num(row.actualAmount) / current) * 100
}

function isOverrun(row: CostAccount): boolean {
  return num(row.actualAmount) > num(row.currentAmount)
}

/** 当前预算偏离目标成本 = 发生过变更，高亮提示以便追溯 */
function hasChanged(row: CostAccount): boolean {
  return Math.abs(num(row.currentAmount) - num(row.baselineAmount)) > 0.005
}

function usageColor(rate: number): string {
  if (rate >= 100) return 'var(--zw-danger)'
  if (rate >= 90) return 'var(--zw-warning)'
  if (rate >= 70) return 'var(--zw-info)'
  return 'var(--zw-success)'
}

function statusLabel(status?: string) {
  return ({ ACTIVE: '活跃', LOCKED: '锁定', CLOSED: '已关闭' } as Record<string, string>)[status || ''] || status || '-'
}

function statusTagType(status?: string) {
  return ({ ACTIVE: 'success', LOCKED: 'warning', CLOSED: 'info' } as Record<string, string>)[status || ''] || 'info'
}

/** 已关闭账户不可勾选删除：结案后的账必须留痕 */
function isSelectable(row: CostAccount): boolean {
  return row.status !== 'CLOSED'
}

async function searchProject(keyword: string) {
  try {
    const res: any = await getProjectList({ projectName: keyword })
    projectList.value = res.data || []
  } catch {
    projectList.value = []
  }
}

async function loadWbsOptions() {
  if (!queryParams.projectId) {
    wbsOptions.value = []
    return
  }
  try {
    const res: any = await getWbsSelectList(queryParams.projectId)
    wbsOptions.value = res.data || []
  } catch {
    wbsOptions.value = []
  }
}

async function loadPage() {
  if (!queryParams.projectId) {
    rows.value = []
    pagination.total = 0
    return
  }
  loading.value = true
  try {
    const res: any = await getCostAccountPage({
      page: pagination.page,
      size: pagination.size,
      projectId: queryParams.projectId,
      costCategory: queryParams.costCategory || undefined,
      status: queryParams.status || undefined
    })
    rows.value = res.data?.records || []
    pagination.total = res.data?.total || 0
  } catch (e: any) {
    rows.value = []
    ElMessage.error(e?.message || '加载成本账户失败')
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  pagination.page = 1
  loadPage()
}

function handleReset() {
  queryParams.costCategory = ''
  queryParams.status = ''
  pagination.page = 1
  loadPage()
}

async function handleProjectChange() {
  pagination.page = 1
  await Promise.all([loadPage(), loadWbsOptions()])
}

function resetForm() {
  editingId.value = null
  parentLabel.value = ''
  Object.assign(formData, {
    accountCode: '', accountName: '', costCategory: 'MATERIAL',
    costSubcategory: '', wbsNodeId: null, baselineAmount: 0, remark: '', parentId: null
  })
  formRef.value?.clearValidate()
}

function handleAdd(parent: CostAccount | null) {
  if (!queryParams.projectId) {
    ElMessage.warning('请先选择项目')
    return
  }
  resetForm()
  if (parent) {
    formData.parentId = parent.id ?? null
    parentLabel.value = `${parent.accountCode} ${parent.accountName}`
    // 子账户编码默认延续父级前缀，减少手工输入错误
    formData.accountCode = `${parent.accountCode}.`
  }
  dialogVisible.value = true
}

function handleEdit(row: CostAccount) {
  resetForm()
  editingId.value = row.id ?? null
  formData.accountCode = row.accountCode
  formData.accountName = row.accountName
  formData.costCategory = row.costCategory
  formData.costSubcategory = row.costSubcategory || ''
  formData.wbsNodeId = row.wbsNodeId ?? null
  formData.baselineAmount = num(row.baselineAmount)
  formData.remark = row.remark || ''
  formData.parentId = row.parentId ?? null
  dialogVisible.value = true
}

async function handleSubmit() {
  if (!formRef.value || !queryParams.projectId) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  const payload: Partial<CostAccount> = {
    projectId: queryParams.projectId,
    parentId: formData.parentId,
    accountCode: formData.accountCode.trim(),
    accountName: formData.accountName.trim(),
    costCategory: formData.costCategory,
    costSubcategory: formData.costSubcategory || undefined,
    wbsNodeId: formData.wbsNodeId,
    baselineAmount: formData.baselineAmount,
    remark: formData.remark || undefined
  }

  submitting.value = true
  try {
    if (editingId.value) {
      await updateCostAccount(editingId.value, payload)
      ElMessage.success('更新成功')
    } else {
      await createCostAccount(payload)
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    await loadPage()
  } catch (e: any) {
    ElMessage.error(e?.message || '保存失败')
  } finally {
    submitting.value = false
  }
}

async function handleLock(row: CostAccount) {
  try {
    await ElMessageBox.confirm(
      `锁定后账户「${row.accountName}」的预算口径将冻结（实际成本仍可回写）。确认锁定？`,
      '锁定确认',
      { type: 'warning' }
    )
  } catch {
    return
  }
  try {
    await lockCostAccount(row.id!)
    ElMessage.success('已锁定')
    await loadPage()
  } catch (e: any) {
    ElMessage.error(e?.message || '锁定失败')
  }
}

async function handleDelete(row: CostAccount) {
  try {
    await ElMessageBox.confirm(`确认删除账户「${row.accountCode} ${row.accountName}」？`, '删除确认', { type: 'warning' })
  } catch {
    return
  }
  try {
    await deleteCostAccount(row.id!)
    ElMessage.success('删除成功')
    await loadPage()
  } catch (e: any) {
    ElMessage.error(e?.message || '删除失败')
  }
}

async function handleBatchDelete() {
  if (!selection.value.length) return
  try {
    await ElMessageBox.confirm(`确认删除选中的 ${selection.value.length} 个账户？`, '批量删除', { type: 'warning' })
  } catch {
    return
  }
  let ok = 0
  const failures: string[] = []
  for (const row of selection.value) {
    try {
      await deleteCostAccount(row.id!)
      ok++
    } catch (e: any) {
      failures.push(`${row.accountCode}: ${e?.message || '失败'}`)
    }
  }
  if (failures.length) {
    // 部分失败必须明确告知，不能只报成功数
    ElMessage.warning(`成功 ${ok} 个，失败 ${failures.length} 个：${failures.slice(0, 3).join('；')}`)
  } else {
    ElMessage.success(`已删除 ${ok} 个账户`)
  }
  selection.value = []
  await loadPage()
}

async function openLedger(row: CostAccount) {
  ledgerAccount.value = row
  ledgerVisible.value = true
  ledgerLoading.value = true
  ledgerRows.value = []
  try {
    const res: any = await getCostAccountLedger(row.id!)
    ledgerRows.value = res.data || []
  } catch (e: any) {
    ElMessage.error(e?.message || '加载流水失败')
  } finally {
    ledgerLoading.value = false
  }
}

// ==================== 成本归集 ====================

/**
 * 立即归集：从合同/结算/材料出库自动汇总承诺额与实际成本。
 * <p>
 * 可重复点击：幂等键编码「状态跃迁 from→to」，无变化时 delta=0 不写流水。
 * 归集后刷新列表，让新的 commitment/actual 立即反映到六维汇总条。
 * </p>
 */
async function triggerRollup() {
  if (!queryParams.projectId) {
    ElMessage.warning('请先选择项目')
    return
  }
  rollupLoading.value = true
  try {
    const res: any = await costRollUp(queryParams.projectId)
    rollupReport.value = res.data || null
    const report = rollupReport.value

    if (!report) {
      ElMessage.warning('归集未返回报告')
      return
    }

    // 有待处理项时直接展开报告；全部干净则只给轻提示，不打断操作
    if (report.unmapped?.length || report.failed?.length) {
      rollupDialogVisible.value = true
      ElMessage.warning(report.message)
    } else {
      ElMessage.success(report.message || '归集完成')
    }

    // 归集会改金额，必须重拉列表，否则汇总条与表格显示旧值
    await loadPage()
  } catch (e: any) {
    ElMessage.error(e?.message || '归集失败')
  } finally {
    rollupLoading.value = false
  }
}

/**
 * 打开绑定弹窗：把一张未归集单据指定到具体成本账户。
 * <p>
 * 只列出<b>同科目</b>的账户——把材料合同绑到劳务账户是明显错误，
 * 在 UI 层就不给这个机会，比事后校验更省心。
 * </p>
 */
function openBindDialog(doc: UnmappedDoc) {
  currentUnmappedDoc.value = doc
  bindingAccountId.value = null
  bindDialogVisible.value = true
}

/** 同科目的候选账户（绑定弹窗下拉数据源） */
const bindCandidates = computed(() => {
  const cat = currentUnmappedDoc.value?.category
  if (!cat) return []
  return rows.value.filter(a => a.costCategory === cat && a.status !== 'CLOSED')
})

async function saveBinding() {
  const doc = currentUnmappedDoc.value
  if (!doc) return
  if (!bindingAccountId.value) {
    ElMessage.warning('请选择要绑定的成本账户')
    return
  }
  bindingSaving.value = true
  try {
    await bindCostAccountLink({
      projectId: queryParams.projectId!,
      accountId: bindingAccountId.value,
      sourceType: doc.sourceType,
      sourceId: doc.sourceId,
      remark: `归集报告人工绑定：${doc.sourceNumber || doc.sourceId}`
    })
    ElMessage.success('绑定成功，正在重新归集')
    bindDialogVisible.value = false
    // 绑定只是声明意图，金额由归集统一计算——立即重跑让绑定生效
    await triggerRollup()
  } catch (e: any) {
    ElMessage.error(e?.message || '绑定失败')
  } finally {
    bindingSaving.value = false
  }
}

function sourceTypeText(type?: string) {
  return SOURCE_TYPE_LABELS[type || ''] || type || '-'
}

/** 原始金额展示（流水/报告里需要看到元级精度，不能一律折万） */
function formatMoney(value: unknown): string {
  if (value == null || value === '') return '-'
  const n = typeof value === 'string' ? parseFloat(value) : Number(value)
  if (!Number.isFinite(n)) return '-'
  return n.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

onMounted(async () => {
  await searchProject('')
})
</script>

<style scoped lang="scss">
.cost-account-container {
  padding: var(--zw-space-lg);
}

.summary-strip {
  display: flex;
  flex-wrap: wrap;
  gap: var(--zw-space-md);
  padding: var(--zw-space-sm-md) var(--zw-space-md);
  margin-bottom: var(--zw-space-sm-md);
  background: var(--zw-bg-hover);
  border-left: 3px solid var(--zw-brand);

  .sum-item {
    display: flex;
    flex-direction: column;
    gap: 2px;
    min-width: 120px;

    .sum-label {
      font-size: var(--zw-font-size-xs);
      color: var(--zw-text-tertiary);
    }

    .sum-value {
      font-family: var(--zw-font-mono);
      font-size: var(--zw-font-size-md);
      font-weight: var(--zw-font-weight-semibold);
      color: var(--zw-text-primary);
    }
  }
}

.table-toolbar {
  display: flex;
  align-items: center;
  gap: var(--zw-space-sm);
  margin-bottom: var(--zw-space-sm-md);

  .toolbar-hint {
    margin-left: auto;
    font-size: var(--zw-font-size-xs);
    color: var(--zw-text-tertiary);
  }
}

.pagination-wrap {
  margin-top: var(--zw-space-md);
  display: flex;
  justify-content: flex-end;
}

.field-hint {
  font-size: var(--zw-font-size-xs);
  color: var(--zw-text-tertiary);
  line-height: 1.5;
  margin-top: 2px;
}

.is-danger { color: var(--zw-danger); font-weight: var(--zw-font-weight-medium); }
.is-success { color: var(--zw-success); }
.is-changed {
  color: var(--zw-brand);
  font-weight: var(--zw-font-weight-medium);
}

.rollup-alert {
  margin-bottom: var(--zw-space-sm-md);
}

.failed-section {
  margin-top: var(--zw-space-md);
  padding-top: var(--zw-space-sm-md);
  border-top: 1px solid var(--zw-border-light);

  .failed-label {
    display: block;
    font-size: var(--zw-font-size-sm);
    color: var(--zw-text-secondary);
    margin-bottom: var(--zw-space-sm);
  }

  .failed-tag {
    margin: 0 var(--zw-space-sm) var(--zw-space-sm) 0;
  }
}

.bind-amount {
  font-family: var(--zw-font-mono);
  font-size: var(--zw-font-size-md);
  font-weight: var(--zw-font-weight-semibold);
  color: var(--zw-text-primary);
}
</style>
