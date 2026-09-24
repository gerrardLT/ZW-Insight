<template>
  <!-- 单据穿透链（UI §13/§16，驾驶舱 P2-4）：
       经营数字 → 成本分类 → 供应商（或成本流水）→ 合同 → 原始单据 → 审批轨迹。
       口径纪律：每级的 basis/note 以 alert 如实展示；原始附件环节系统未支持，
       固定展示 attachmentNote，不伪造附件跳转。 -->
  <el-drawer :model-value="modelValue" title="单据穿透" size="72%" direction="rtl"
    @update:model-value="emit('update:modelValue', $event)">
    <div class="dd-container">
      <el-breadcrumb separator="/" class="dd-breadcrumb">
        <el-breadcrumb-item v-for="(step, i) in steps" :key="step.key">
          <span :class="i === steps.length - 1 ? 'dd-crumb-current' : 'dd-crumb-link'"
            @click="i < steps.length - 1 && backTo(i)">
            {{ step.label }}
          </span>
        </el-breadcrumb-item>
      </el-breadcrumb>

      <!-- 当前级口径/边界说明（basis/note 不得静默） -->
      <el-alert v-if="current?.note" type="info" :closable="false" show-icon
        :title="current.note" class="dd-note" />
      <el-alert v-if="current?.basis" type="info" :closable="false" show-icon
        :title="current.basis" class="dd-note" />

      <div v-loading="current?.loading" class="dd-body">
        <!-- L1 项目构成（静态行，数据源为驾驶舱 project-health，不重复请求） -->
        <el-table v-if="current?.level === 'projects'" :data="current.rows" size="small" border
          highlight-current-row class="dd-clickable" @row-click="onProjectRow">
          <el-table-column prop="projectName" label="项目" min-width="180" />
          <el-table-column label="数值" align="right" min-width="130">
            <template #default="{ row }">
              {{ row.value == null ? '—' : `${formatWan(row.value)} 万` }}
            </template>
          </el-table-column>
          <el-table-column label="操作" width="110" align="center">
            <template #default>
              <el-button link type="primary" size="small">成本构成 →</el-button>
            </template>
          </el-table-column>
        </el-table>

        <!-- L2 成本分类（CBS 侧与合同侧并列对照，不强行轧平） -->
        <el-table v-else-if="current?.level === 'costCategories'" :data="current.rows"
          size="small" border>
          <el-table-column label="成本分类" min-width="220">
            <template #default="{ row }">
              <div>{{ row.categoryName }}</div>
              <!-- 口径直陈（不藏 hover）：老板要知道两个数字为什么不相等 -->
              <div class="dd-basis">{{ row.basis }}</div>
            </template>
          </el-table-column>
          <el-table-column label="CBS 实际" align="right" min-width="110">
            <template #default="{ row }">{{ formatWan(row.actual) }}</template>
          </el-table-column>
          <el-table-column label="CBS 预测" align="right" min-width="110">
            <template #default="{ row }">{{ formatWan(row.forecast) }}</template>
          </el-table-column>
          <el-table-column label="合同累计结算" align="right" min-width="120">
            <template #default="{ row }">{{ formatWan(row.contractSettlement) }}</template>
          </el-table-column>
          <el-table-column label="合同累计付款" align="right" min-width="120">
            <template #default="{ row }">{{ formatWan(row.contractPaid) }}</template>
          </el-table-column>
          <el-table-column prop="accountCount" label="账户数" width="80" align="center" />
          <el-table-column label="构成去向" min-width="200">
            <template #default="{ row }">
              <el-button link type="primary" size="small"
                @click="drillSuppliers(row)">供应商构成 →</el-button>
              <el-button link type="primary" size="small"
                @click="drillTxn(row)">成本流水 →</el-button>
            </template>
          </el-table-column>
          <template #empty>
            <el-empty :description="current.emptyText || '无成本分类数据'" :image-size="50" />
          </template>
        </el-table>

        <!-- L3a 供应商构成 -->
        <el-table v-else-if="current?.level === 'suppliers'" :data="current.rows"
          size="small" border highlight-current-row class="dd-clickable" @row-click="onSupplierRow">
          <el-table-column prop="supplierName" label="供应商（乙方）" min-width="170"
            show-overflow-tooltip />
          <el-table-column prop="contractCount" label="合同数" width="90" align="center" />
          <el-table-column label="合同金额" align="right" min-width="110">
            <template #default="{ row }">{{ formatWan(row.contractAmount) }}</template>
          </el-table-column>
          <el-table-column label="累计结算" align="right" min-width="110">
            <template #default="{ row }">{{ formatWan(row.settlementTotal) }}</template>
          </el-table-column>
          <el-table-column label="累计付款" align="right" min-width="110">
            <template #default="{ row }">{{ formatWan(row.paidTotal) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="110" align="center">
            <template #default>
              <el-button link type="primary" size="small">合同清单 →</el-button>
            </template>
          </el-table-column>
          <template #empty>
            <el-empty description="该类别下无合同（间接费/其他类的报销支出请走「成本流水」查看）"
              :image-size="50" />
          </template>
        </el-table>

        <!-- L3b 成本账户实际流水（这个数字怎么来的） -->
        <el-table v-else-if="current?.level === 'txn'" :data="current.rows" size="small" border>
          <el-table-column label="成本账户" min-width="150">
            <template #default="{ row }">{{ row.accountCode }} {{ row.accountName }}</template>
          </el-table-column>
          <el-table-column label="变动金额" align="right" min-width="110">
            <template #default="{ row }">
              <b :class="Number(row.deltaAmount) < 0 ? 'dd-danger' : ''">
                {{ formatDelta(row.deltaAmount) }}
              </b>
            </template>
          </el-table-column>
          <el-table-column label="变动后余额" align="right" min-width="110">
            <template #default="{ row }">{{ formatWan(row.balanceAfter) }}</template>
          </el-table-column>
          <el-table-column label="来源类型" width="110">
            <template #default="{ row }">{{ sourceTypeLabel(row.sourceType) }}</template>
          </el-table-column>
          <el-table-column label="来源单据" min-width="140">
            <template #default="{ row }">
              <el-tooltip v-if="isRollup(row.sourceType)"
                content="归集任务按单据汇总对账产生的差额（幂等键 ROLLUP:{账户}:{维度}:{from}→{to}），源头为该账户对应类别的结算/出库单据"
                placement="top">
                <span>{{ row.sourceNumber || row.sourceId }}</span>
              </el-tooltip>
              <span v-else>{{ row.sourceNumber || row.sourceId || '—' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="发生时间" min-width="150">
            <template #default="{ row }">{{ formatDateTime(row.occurredAt) }}</template>
          </el-table-column>
          <el-table-column prop="remark" label="备注" min-width="140" show-overflow-tooltip />
          <template #empty>
            <el-empty description="该类别账户无实际成本流水（账户余额未经记账变动）" :image-size="50" />
          </template>
        </el-table>

        <!-- L4 合同清单 -->
        <el-table v-else-if="current?.level === 'contracts'" :data="current.rows"
          size="small" border>
          <el-table-column label="合同" min-width="220">
            <template #default="{ row }">
              <span class="dd-mono">{{ row.contractCode || '（无编号）' }}</span>
              {{ row.contractName }}
            </template>
          </el-table-column>
          <el-table-column prop="supplierName" label="乙方" min-width="130" show-overflow-tooltip />
          <el-table-column label="金额" align="right" min-width="100">
            <template #default="{ row }">{{ formatWan(row.contractAmount) }}</template>
          </el-table-column>
          <el-table-column label="累计结算" align="right" min-width="100">
            <template #default="{ row }">{{ formatWan(row.cumulativeSettlement) }}</template>
          </el-table-column>
          <el-table-column label="累计付款" align="right" min-width="100">
            <template #default="{ row }">{{ formatWan(row.cumulativePaid) }}</template>
          </el-table-column>
          <el-table-column label="状态" width="90">
            <template #default="{ row }">{{ statusLabel(row.status) }}</template>
          </el-table-column>
          <el-table-column label="操作" min-width="180">
            <template #default="{ row }">
              <el-button link type="primary" size="small" @click="drillDocs(row)">构成单据 →</el-button>
              <el-button v-if="row.workflowInstanceId" link type="primary" size="small"
                @click="openTrace(row.workflowInstanceId, row.contractCode || row.contractName)">
                审批轨迹
              </el-button>
            </template>
          </el-table-column>
          <template #empty>
            <el-empty description="该供应商在此类别下无合同" :image-size="50" />
          </template>
        </el-table>

        <!-- L5 原始单据（结算/付款/收票/入库） -->
        <el-table v-else-if="current?.level === 'docs'" :data="current.rows" size="small" border>
          <el-table-column label="单据类型" width="100">
            <template #default="{ row }">
              <el-tag size="small" :type="docTagType(row.docType)">{{ row.docTypeName }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="单号" min-width="150">
            <template #default="{ row }">
              <span class="dd-mono">{{ row.docNo || `单据#${row.docId}` }}</span>
              <!-- 表无单号列时以主键呈现（如实，不合成假编号） -->
            </template>
          </el-table-column>
          <el-table-column label="日期" min-width="120">
            <template #header>
              <span>日期</span>
              <el-tooltip content="无业务日期列的单据如实取创建时间（列头随行走，此为整表说明）" placement="top">
                <el-icon class="dd-help"><QuestionFilled /></el-icon>
              </el-tooltip>
            </template>
            <template #default="{ row }">{{ formatDate(row.docDate) }}</template>
          </el-table-column>
          <el-table-column label="金额" align="right" min-width="110">
            <template #default="{ row }">{{ formatWan(row.amount) }}</template>
          </el-table-column>
          <el-table-column label="状态" width="100">
            <template #default="{ row }">{{ statusLabel(row.status) }}</template>
          </el-table-column>
          <el-table-column label="支付状态" width="100">
            <template #default="{ row }">
              <span v-if="row.payStatus" :class="row.payStatus === 'PAID' ? 'dd-success' : row.payStatus === 'PARTIAL_PAID' ? 'dd-warning' : 'dd-danger'">
                {{ payStatusLabel(row.payStatus) }}
              </span>
              <span v-else class="dd-muted">—</span>
            </template>
          </el-table-column>
          <el-table-column label="关联" min-width="130">
            <template #default="{ row }">
              <span v-if="row.refInboundCode" class="dd-mono">{{ row.refInboundCode }}</span>
              <span v-else-if="row.supplierName" class="dd-muted">{{ row.supplierName }}</span>
              <span v-else class="dd-muted">—</span>
            </template>
          </el-table-column>
          <el-table-column label="审批轨迹" width="100" align="center">
            <template #default="{ row }">
              <el-button v-if="row.workflowInstanceId" link type="primary" size="small"
                @click="openTrace(row.workflowInstanceId, `${row.docTypeName} ${row.docNo || '#' + row.docId}`)">
                查看
              </el-button>
              <el-tooltip v-else placement="top"
                :content="row.docType === 'INVOICE' ? '收票登记无审批流程（登记即入账）' : '该单据无流程实例（草稿或表无审批列）'">
                <span class="dd-muted">无</span>
              </el-tooltip>
            </template>
          </el-table-column>
          <template #empty>
            <el-empty description="该合同暂无构成单据（未发生结算/付款/收票）" :image-size="50" />
          </template>
        </el-table>
      </div>

      <!-- 附件环节现状（实证：五类支出单据均无附件上传功能，如实止步，不伪造） -->
      <div class="dd-footer">
        <el-icon><InfoFilled /></el-icon>
        <span>{{ attachmentNote }}</span>
      </div>
    </div>

    <!-- 审批轨迹（§13 末环：审批流程） -->
    <el-dialog v-model="traceVisible" :title="`审批轨迹 · ${traceTitle}`" width="560px"
      append-to-body>
      <div v-loading="traceLoading" class="trace-body">
        <template v-if="trace">
          <div class="trace-head">
            <span>流程：{{ trace.processName || '（实例不存在或已清理）' }}</span>
            <el-tag size="small" :type="trace.status === 'COMPLETED' ? 'success' : trace.status === 'RUNNING' ? 'warning' : 'info'">
              {{ traceStatusLabel(trace.status) }}
            </el-tag>
          </div>
          <div v-if="trace.startUserName || trace.startTime" class="trace-sub">
            发起：{{ trace.startUserName || '—' }} · {{ formatDateTime(trace.startTime) }}
          </div>
          <el-alert v-if="trace.note" type="warning" :closable="false" show-icon
            :title="trace.note" class="trace-note" />
          <el-timeline v-if="trace.approvalRecords?.length">
            <el-timeline-item v-for="(r, i) in trace.approvalRecords" :key="i"
              :timestamp="formatDateTime(r.endTime)" placement="top"
              :type="r.result === 'approved' ? 'success' : r.result === 'rejected' ? 'danger' : 'info'">
              <div class="trace-node">
                <b>{{ r.taskName || '流程节点' }}</b>
                <span class="trace-who">{{ r.assigneeName }}</span>
                <el-tag size="small"
                  :type="r.result === 'approved' ? 'success' : r.result === 'rejected' ? 'danger' : 'info'">
                  {{ r.resultText }}
                </el-tag>
              </div>
              <div v-if="r.comment" class="trace-comment">{{ r.comment }}</div>
            </el-timeline-item>
          </el-timeline>
          <el-empty v-else-if="!trace.approvalRecords?.length && !trace.note"
            description="无审批操作记录" :image-size="50" />
        </template>
      </div>
    </el-dialog>
  </el-drawer>
</template>

<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { InfoFilled, QuestionFilled } from '@element-plus/icons-vue'
import {
  getDrillCostCategories, getDrillSuppliers, getDrillAccountTxn,
  getDrillContracts, getDrillContractDocs,
  type DrillContractCategory, type DrillCostCategoryRow, type DrillSupplierRow,
  type DrillContractRow
} from '@/api/cockpit'
import { getApprovalTrace } from '@/api/workflow'
import { formatWan } from '@/utils/chart-format'

/**
 * 穿透链入口定义。
 * - projectRows：从驾驶舱数字卡进入时传 L1 项目构成（数据已在手，不重复请求）；
 * - contractCategory + categoryLabel：从成本中心/项目经营分类行进入（L1 直接给到类别级）。
 */
export interface DrillEntry {
  projectId: number
  projectName: string
  rootLabel?: string
  contractCategory?: DrillContractCategory
  categoryLabel?: string
  projectRows?: { projectId: number; projectName: string; value: number | null }[]
}

const props = defineProps<{
  modelValue: boolean
  entry: DrillEntry | null
}>()
const emit = defineEmits<{ (e: 'update:modelValue', v: boolean): void }>()

type Level = 'projects' | 'costCategories' | 'suppliers' | 'txn' | 'contracts' | 'docs'

interface Step {
  key: string
  label: string
  level: Level
  projectId?: number
  projectName?: string
  contractCategory?: DrillContractCategory
  supplierName?: string | null
  contractId?: number
  loading: boolean
  rows: any[]
  note?: string
  basis?: string
  emptyText?: string
  /**
   * 进入该级时的取数动作（静态行如 L1 项目构成无 fetch）。
   * 必须经 self（reactive 代理）写入 rows 等字段：闭包持有的原始对象赋值
   * 不触发依赖更新（2026-09-24 测试实证的响应性陷阱）。
   */
  fetch?: (self: Step) => Promise<void>
}

const steps = reactive<Step[]>([])
// 附件环节说明：取首个下发 attachmentNote 的级（后端固定文案），兜底与后端一致的实证结论
const attachmentNote = ref(
  '原始附件环节当前系统未支持：付款/结算/收票/合同单据均无附件上传功能，不伪造跳转')

const current = ref<Step | null>(null)

function syncCurrent() {
  current.value = steps[steps.length - 1] || null
}

/** 每次进入新级：拉取数据并同步面包屑（取数经代理对象写入） */
async function pushStep(step: Step) {
  steps.push(step)
  syncCurrent()
  // 从 reactive 数组重新取回代理引用，后续状态变更才能触发渲染
  await runFetch(steps[steps.length - 1])
}

async function runFetch(live: Step) {
  if (!live.fetch) return
  live.loading = true
  try {
    await live.fetch(live)
  } catch (e: any) {
    live.rows = []
    // 失败显式提示不静默（穿透链任何一级取不到数都不能装作「无数据」）
    ElMessage.error('穿透取数失败：' + (e?.message || '接口异常'))
  } finally {
    live.loading = false
  }
}

function backTo(index: number) {
  steps.splice(index + 1)
  syncCurrent()
}

// ==================== 各级入口 ====================

watch(() => [props.modelValue, props.entry], () => {
  if (props.modelValue && props.entry) {
    openEntry(props.entry)
  }
}, { immediate: true })

function openEntry(entry: DrillEntry) {
  steps.splice(0)
  if (entry.projectRows?.length) {
    // L1：经营数字 → 项目构成（静态，来自 project-health）
    steps.push({
      key: 'projects', level: 'projects', loading: false, rows: entry.projectRows,
      label: entry.rootLabel || '项目构成'
    })
    syncCurrent()
    // 预选单项目时直接深入一级（成本中心/项目经营入口都带明确项目）
    return
  }
  if (entry.contractCategory) {
    // 从分类行进入：面包屑 = 项目（成本分类，回看可见）→ 类别 → 供应商构成
    pushCostCategories(entry.projectId, entry.rootLabel || entry.projectName)
    pushCategoryStep(entry.projectId, entry.projectName,
      entry.contractCategory, entry.categoryLabel || entry.contractCategory)
    return
  }
  // 项目级进入：直接拉成本分类
  pushCostCategories(entry.projectId, entry.rootLabel || entry.projectName)
}

function pushCostCategories(projectId: number, projectLabel: string) {
  const step: Step = {
    key: `cc-${projectId}`, level: 'costCategories', loading: false, rows: [],
    projectId, label: projectLabel,
    async fetch(self) {
      const res: any = await getDrillCostCategories({ projectId })
      const data = res?.data || {}
      self.rows = data.rows || []
      self.note = data.note
      self.emptyText = data.hasCbs === false ? '该项目未建 CBS 成本账户（如实为空）' : undefined
      if (data.attachmentNote) attachmentNote.value = data.attachmentNote
    }
  }
  pushStep(step)
}

function onProjectRow(row: any) {
  pushCostCategories(Number(row.projectId), row.projectName)
}

function pushCategoryStep(projectId: number, projectLabel: string,
  contractCategory: DrillContractCategory, categoryLabel: string) {
  const step: Step = {
    key: `sup-${projectId}-${contractCategory}`, level: 'suppliers', loading: false, rows: [],
    projectId, contractCategory, label: categoryLabel,
    async fetch(self) {
      const res: any = await getDrillSuppliers({ projectId, contractCategory })
      const data = res?.data || {}
      self.rows = data.rows || []
      self.note = data.note
      self.basis = `类别：${data.categoryName || contractCategory}（五类支出合同按乙方聚合）`
      if (data.attachmentNote) attachmentNote.value = data.attachmentNote
    }
  }
  pushStep(step)
}

function drillSuppliers(row: DrillCostCategoryRow) {
  pushCategoryStep(current.value!.projectId!, current.value!.label,
    row.contractCategory, row.categoryName)
}

function drillTxn(row: DrillCostCategoryRow) {
  const step: Step = {
    key: `txn-${current.value!.projectId}-${row.contractCategory}`,
    level: 'txn', loading: false, rows: [],
    projectId: current.value!.projectId, contractCategory: row.contractCategory,
    label: `${row.categoryName} · 成本流水`,
    async fetch(self) {
      const res: any = await getDrillAccountTxn({
        projectId: current.value!.projectId!, contractCategory: row.contractCategory
      })
      const data = res?.data || {}
      self.rows = data.rows || []
      self.basis = data.basis
      self.note = data.note
      if (data.attachmentNote) attachmentNote.value = data.attachmentNote
    }
  }
  pushStep(step)
}

function onSupplierRow(row: DrillSupplierRow) {
  drillContracts(row.supplierName)
}

function drillContracts(supplierName: string | null) {
  const parent = current.value!
  const step: Step = {
    key: `ct-${parent.projectId}-${parent.contractCategory}-${supplierName ?? 'ALL'}`,
    level: 'contracts', loading: false, rows: [],
    projectId: parent.projectId, contractCategory: parent.contractCategory, supplierName,
    label: supplierName || '全部合同',
    async fetch(self) {
      const res: any = await getDrillContracts({
        projectId: parent.projectId!,
        contractCategory: parent.contractCategory!,
        supplierName: supplierName || undefined
      })
      const data = res?.data || {}
      self.rows = data.rows || []
      self.note = data.note
      if (data.attachmentNote) attachmentNote.value = data.attachmentNote
    }
  }
  pushStep(step)
}

function drillDocs(row: DrillContractRow) {
  const parent = current.value!
  const step: Step = {
    key: `doc-${parent.contractCategory}-${row.id}`,
    level: 'docs', loading: false, rows: [],
    projectId: parent.projectId, contractCategory: parent.contractCategory, contractId: row.id,
    label: row.contractCode || row.contractName,
    async fetch(self) {
      const res: any = await getDrillContractDocs({
        contractCategory: parent.contractCategory!, contractId: row.id
      })
      const data = res?.data || {}
      self.rows = data.rows || []
      self.note = data.note
      self.basis = `合同 ${row.contractCode || '#' + row.id} 的构成单据（结算/付款/收票${parent.contractCategory === 'PURCHASE' ? '/入库' : ''}）`
      if (data.attachmentNote) attachmentNote.value = data.attachmentNote
    }
  }
  pushStep(step)
}

// ==================== 审批轨迹（末环） ====================
const traceVisible = ref(false)
const traceLoading = ref(false)
const traceTitle = ref('')
const trace = ref<any>(null)

async function openTrace(processInstanceId: string, title: string) {
  traceTitle.value = title
  traceVisible.value = true
  traceLoading.value = true
  trace.value = null
  try {
    const res: any = await getApprovalTrace(processInstanceId)
    trace.value = res?.data || null
  } catch (e: any) {
    // 轨迹取不到如实告知（不伪造审批链）
    ElMessage.error('加载审批轨迹失败：' + (e?.message || '接口异常'))
  } finally {
    traceLoading.value = false
  }
}

// ==================== 展示辅助 ====================

function formatDelta(value: unknown): string {
  if (value == null) return '—'
  const n = Number(value)
  if (Number.isNaN(n)) return '—'
  return `${n > 0 ? '+' : ''}${formatWan(n)}`
}

function formatDate(value: unknown): string {
  if (!value) return '—'
  return String(value).slice(0, 10)
}

function formatDateTime(value: unknown): string {
  if (!value) return '—'
  return String(value).replace('T', ' ').slice(0, 19)
}

const STATUS_LABELS: Record<string, string> = {
  DRAFT: '草稿', SUBMITTED: '审批中', APPROVED: '已审批', REJECTED: '已驳回',
  EFFECTIVE: '生效', SETTLED: '已结算', CLOSED: '已关闭', TERMINATED: '已终止',
  RUNNING: '进行中', COMPLETED: '已完成'
}

function statusLabel(status: string | null | undefined): string {
  if (!status) return '—'
  return STATUS_LABELS[status] || status
}

function traceStatusLabel(status: string): string {
  return status === 'COMPLETED' ? '已完成' : status === 'RUNNING' ? '进行中' : '未知'
}

const PAY_STATUS_LABELS: Record<string, string> = {
  UNPAID: '未支付', PARTIAL_PAID: '部分支付', PAID: '已支付'
}

function payStatusLabel(v: string): string {
  return PAY_STATUS_LABELS[v] || v
}

const SOURCE_TYPE_LABELS: Record<string, string> = {
  ROLLUP: '归集对账', SEED: '演示种子', MANUAL: '人工调整', CONTRACT: '合同',
  SETTLEMENT: '结算', PURCHASE: '采购', MATERIAL: '出库', CHANGE_EVENT: '变更事件',
  PAYMENT: '付款'
}

function sourceTypeLabel(v: string): string {
  return SOURCE_TYPE_LABELS[v] || v
}

function isRollup(v: string): boolean {
  return v === 'ROLLUP'
}

function docTagType(docType: string): 'primary' | 'success' | 'warning' | 'info' {
  if (docType === 'PAYMENT') return 'warning'
  if (docType === 'SETTLEMENT') return 'primary'
  if (docType === 'INVOICE') return 'success'
  return 'info'
}
</script>

<style scoped>
.dd-container {
  display: flex;
  flex-direction: column;
  gap: var(--zw-space-sm);
  height: 100%;
}
.dd-breadcrumb {
  font-size: var(--zw-font-size-sm);
}
.dd-crumb-link {
  color: var(--el-color-primary);
  cursor: pointer;
}
.dd-crumb-current {
  color: var(--el-text-color-primary);
  font-weight: var(--zw-font-weight-semibold);
}
.dd-note {
  margin-bottom: 0;
}
.dd-basis {
  font-size: var(--zw-font-size-xs);
  color: var(--el-text-color-secondary);
  line-height: 1.4;
}
.dd-body { flex: 1; min-height: 200px; }
.dd-clickable :deep(.el-table__row) { cursor: pointer; }
.dd-mono {
  font-family: var(--zw-font-family-mono, monospace);
  font-size: var(--zw-font-size-xs);
  color: var(--el-text-color-secondary);
  margin-right: var(--zw-space-xs);
}
.dd-help {
  margin-left: var(--zw-space-xs);
  cursor: help;
  color: var(--el-text-color-placeholder);
}
.dd-danger { color: var(--el-color-danger); }
.dd-warning { color: var(--el-color-warning); }
.dd-success { color: var(--el-color-success); }
.dd-muted { color: var(--el-text-color-placeholder); }
.dd-footer {
  display: flex;
  align-items: center;
  gap: var(--zw-space-xs);
  font-size: var(--zw-font-size-xs);
  color: var(--el-text-color-secondary);
  padding: var(--zw-space-xs) 0;
  border-top: 1px solid var(--el-border-color-lighter);
}
.trace-body { min-height: 100px; }
.trace-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--zw-space-sm);
  font-size: var(--zw-font-size-sm);
  margin-bottom: var(--zw-space-xs);
}
.trace-sub {
  font-size: var(--zw-font-size-xs);
  color: var(--el-text-color-secondary);
  margin-bottom: var(--zw-space-sm);
}
.trace-note { margin-bottom: var(--zw-space-sm); }
.trace-node {
  display: flex;
  align-items: center;
  gap: var(--zw-space-sm);
  font-size: var(--zw-font-size-sm);
}
.trace-who { color: var(--el-text-color-secondary); }
.trace-comment {
  margin-top: var(--zw-space-xs);
  font-size: var(--zw-font-size-xs);
  color: var(--el-text-color-secondary);
}
</style>
