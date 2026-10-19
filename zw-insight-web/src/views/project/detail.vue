<template>
  <div class="project-detail-container page-container">
    <!-- 工程铭牌头部 -->
    <div class="plate-header">
      <div class="plate-main">
        <div class="plate-title-wrap">
          <div class="plate-eyebrow">Project Master Dossier // 01-PROJ</div>
          <div class="plate-title">
            <span>{{ projectInfo.projectName || '项目详情' }}</span>
            <el-tag :type="getStatusType(projectInfo.status)" size="small">
              {{ getStatusLabel(projectInfo.status) }}
            </el-tag>
          </div>
          <div class="plate-meta">
            <span>CODE: {{ projectInfo.projectCode || '-' }}</span>
            <span>CREATED: {{ projectInfo.createdAt || '-' }}</span>
          </div>
        </div>
        <div class="plate-actions">
          <!-- 动作按钮区（按状态机动态启用，P1-M1） -->
          <el-button v-if="projectInfo.status === 'DRAFT'" type="primary" @click="handleSubmit">
            提交立项
          </el-button>
          <el-button v-if="projectInfo.status === 'FILED'" type="warning" plain @click="handleWithdraw">
            撤回立项
          </el-button>
          <el-button v-if="projectInfo.status === 'TENDERING'" type="info" plain @click="handleLoseBid">
            落标归档
          </el-button>
          <el-button v-if="projectInfo.status === 'CONSTRUCTION'" type="warning" @click="handlePause">
            暂停施工
          </el-button>
          <el-button v-if="projectInfo.status === 'PAUSED'" type="success" @click="handleResume">
            恢复施工
          </el-button>
          <el-button
            v-if="canTerminate"
            type="danger"
            plain
            @click="handleTerminate"
          >
            项目终止
          </el-button>
          <el-button @click="handleBack">
            <el-icon><ArrowLeft /></el-icon>返回列表
          </el-button>
        </div>
      </div>
    </div>

    <!-- 首屏老板看板：8大核心工程经营指标（大号字、红绿分明） -->
    <div class="boss-kpi-grid" v-loading="kpiLoading">
      <div class="kpi-card" @click="activeTab = 'contract'">
        <div class="kpi-header">
          <span class="kpi-label">签约合同价</span>
          <el-tag size="small" type="primary">收入总源</el-tag>
        </div>
        <div class="kpi-value text-brand">{{ formatKpiMoney(kpiData.contractAmount) }}</div>
        <div class="kpi-tip">包含施工总承包合同及已生效签证</div>
      </div>

      <div class="kpi-card" @click="activeTab = 'contract'">
        <div class="kpi-header">
          <span class="kpi-label">完成产值</span>
          <el-tag size="small" type="info">已干产值</el-tag>
        </div>
        <div class="kpi-value">{{ formatKpiMoney(kpiData.outputAmount) }}</div>
        <div class="kpi-tip">累计监理已审核认价产值</div>
      </div>

      <div class="kpi-card" @click="activeTab = 'funds'">
        <div class="kpi-header">
          <span class="kpi-label">已收工程款</span>
          <el-tag size="small" type="success">真金白银</el-tag>
        </div>
        <div class="kpi-value text-success">{{ formatKpiMoney(kpiData.receivedAmount) }}</div>
        <div class="kpi-tip">累计已确认回款登记总额</div>
      </div>

      <div class="kpi-card" @click="activeTab = 'funds'">
        <div class="kpi-header">
          <span class="kpi-label">工程款未收</span>
          <el-tag size="small" :type="kpiData.unreceived > 0 ? 'warning' : 'info'">甲方欠款</el-tag>
        </div>
        <div class="kpi-value text-warning">{{ formatKpiMoney(kpiData.unreceived) }}</div>
        <div class="kpi-tip">合同总造价 − 累计已回收款</div>
      </div>

      <div class="kpi-card" @click="activeTab = 'funds'">
        <div class="kpi-header">
          <span class="kpi-label">累计已付款</span>
          <el-tag size="small" type="info">支出执行</el-tag>
        </div>
        <div class="kpi-value">{{ formatKpiMoney(kpiData.totalPaid) }}</div>
        <div class="kpi-tip">五类支出合同+零星费用已批已付</div>
      </div>

      <div class="kpi-card" @click="activeTab = 'funds'">
        <div class="kpi-header">
          <span class="kpi-label">应付未付款</span>
          <el-tag size="small" :type="kpiData.payableOutstanding > 0 ? 'danger' : 'info'">应付欠款</el-tag>
        </div>
        <div class="kpi-value text-danger">{{ formatKpiMoney(kpiData.payableOutstanding) }}</div>
        <div class="kpi-tip">已结算但尚未付清款项</div>
      </div>

      <div class="kpi-card" @click="activeTab = 'operation'">
        <div class="kpi-header">
          <span class="kpi-label">实际净现金流</span>
          <el-tag size="small" :type="kpiData.pocketProfit >= 0 ? 'success' : 'danger'">
            {{ kpiData.pocketProfit >= 0 ? '资金造血' : '垫资失血' }}
          </el-tag>
        </div>
        <div class="kpi-value" :class="kpiData.pocketProfit >= 0 ? 'text-success' : 'text-danger'">
          {{ formatKpiMoney(kpiData.pocketProfit) }}
        </div>
        <div class="kpi-tip">累计已收工程款 − 实际总计支出</div>
      </div>

      <div class="kpi-card" @click="activeTab = 'operation'">
        <div class="kpi-header">
          <span class="kpi-label">预计结算利润</span>
          <el-tag size="small" type="primary">最终盈利</el-tag>
        </div>
        <div class="kpi-value text-brand">{{ formatKpiMoney(kpiData.forecastProfit) }}</div>
        <div class="kpi-tip">签约总价 − 预计最终总成本(EAC)</div>
      </div>
    </div>

    <!-- 主体内容卡片（蓝图角标） -->
    <el-card shadow="never" class="card-corner-marked detail-main-card">
      <template #header>
        <div class="card-header">
          <span>项目详情：{{ projectInfo.projectName }}</span>
          <div class="header-nav-actions">
            <el-button size="small" type="primary" plain @click="router.push(`/cockpit/project-operation?projectId=${projectId}`)">
              项目经营驾驶舱
            </el-button>
            <el-button size="small" @click="handleBack">返回</el-button>
          </div>
        </div>
      </template>

      <el-tabs v-model="activeTab" class="custom-tabs">
        <!-- 1. 基本信息与概况档案 -->
        <el-tab-pane label="基本信息" name="info">
          <div class="section-title">工程基本概况</div>
          <el-descriptions :column="2" border>
            <el-descriptions-item label="项目编号">
              <span class="stat-number">{{ projectInfo.projectCode }}</span>
            </el-descriptions-item>
            <el-descriptions-item label="项目名称">{{ projectInfo.projectName }}</el-descriptions-item>
            <el-descriptions-item label="项目性质">{{ projectInfo.projectNature }}</el-descriptions-item>
            <el-descriptions-item label="项目类型">{{ projectInfo.projectType }}</el-descriptions-item>
            <el-descriptions-item label="建设单位（发包人）">{{ projectInfo.ownerCompanyName }}</el-descriptions-item>
            <el-descriptions-item label="施工单位（承包人）">{{ projectInfo.signingCompanyName }}</el-descriptions-item>
            <el-descriptions-item label="工程地点">{{ projectInfo.projectAddress }}</el-descriptions-item>
            <el-descriptions-item label="联系人">{{ projectInfo.contactName }}</el-descriptions-item>
            <el-descriptions-item label="联系电话">{{ projectInfo.contactPhone }}</el-descriptions-item>
            <el-descriptions-item label="项目预算（目标成本）">
              <span class="stat-number text-brand font-semibold">{{ projectInfo.budgetAmount != null ? (projectInfo.budgetAmount === 0 ? '0' : '¥ ' + projectInfo.budgetAmount) : '-' }}</span>
            </el-descriptions-item>
            <el-descriptions-item label="状态">
              <el-tag :type="getStatusType(projectInfo.status)" size="small">
                {{ getStatusLabel(projectInfo.status) }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="创建时间">
              <span class="stat-number">{{ projectInfo.createdAt }}</span>
            </el-descriptions-item>

            <!-- 周期与时间要素（P1-M1 A2） -->
            <el-descriptions-item label="计划开工日期">
              {{ projectInfo.plannedStartDate || '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="计划竣工日期">
              {{ projectInfo.plannedEndDate || '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="实际开工日期">
              {{ projectInfo.actualStartDate || '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="实际竣工日期">
              {{ projectInfo.actualEndDate || '-' }}
            </el-descriptions-item>

            <!-- 异常分支原因（按需展示） -->
            <el-descriptions-item v-if="projectInfo.pauseReason" label="暂停原因" :span="2">
              <span class="text-danger">{{ projectInfo.pauseReason }}</span>
            </el-descriptions-item>
            <el-descriptions-item v-if="projectInfo.lostReason" label="落标原因" :span="2">
              <span class="text-danger">{{ projectInfo.lostReason }}</span>
            </el-descriptions-item>
            <el-descriptions-item v-if="projectInfo.terminateReason" label="终止原因" :span="2">
              <span class="text-danger">{{ projectInfo.terminateReason }}</span>
            </el-descriptions-item>

            <el-descriptions-item label="工程概况" :span="2">{{ projectInfo.projectOverview || '暂无描述' }}</el-descriptions-item>
          </el-descriptions>
        </el-tab-pane>

        <!-- 2. 项目综合经营一本账 -->
        <el-tab-pane label="项目经营" name="operation">
          <div class="operation-tab">
            <div class="operation-toolbar">
              <el-button link type="primary" @click="router.push('/cockpit/project-operation?projectId=' + projectId)">
                在经营驾驶舱中查看完整项目经营（含健康度/成本执行率/穿透）→
              </el-button>
            </div>
            <ProjectOnebookPanel :project-id="projectId" />
            <el-divider />
            <ProjectBusinessSummary :project-id="projectId" />
          </div>
        </el-tab-pane>

        <!-- 3. 总包与工程产值 -->
        <el-tab-pane label="总包与产值" name="contract">
          <div class="sub-portal-pane">
            <div class="sub-portal-intro">
              <span class="portal-badge">收入主链</span>
              <span>施工总承包合同、工程变更签证单与月度产值认价。点击卡片直达对应台账（已自动锁定当前项目）：</span>
            </div>
            <div class="portal-cards">
              <div class="portal-card" @click="router.push(`/contract/list?projectId=${projectId}`)">
                <div class="portal-card-title">施工总承包合同</div>
                <div class="portal-card-desc">签约合同清单、甲供材约定、收付款节点条款。</div>
                <div class="portal-card-action">查看关联合同列表 →</div>
              </div>
              <div class="portal-card" @click="router.push(`/contract/change-event?projectId=${projectId}`)">
                <div class="portal-card-title">工程变更与签证</div>
                <div class="portal-card-desc">现场签证单、设计变更通知、单项签证造价增减及审批。</div>
                <div class="portal-card-action">查看变更签证单据 →</div>
              </div>
              <div class="portal-card" @click="router.push(`/contract/output-report?projectId=${projectId}`)">
                <div class="portal-card-title">月度产值申报</div>
                <div class="portal-card-desc">每月形象进度产值上报、监理审核认价与累计产值台账。</div>
                <div class="portal-card-action">查看产值申报台账 →</div>
              </div>
            </div>
          </div>
        </el-tab-pane>

        <!-- 4. 工程资金与往来 -->
        <el-tab-pane label="工程资金往来" name="funds">
          <div class="sub-portal-pane">
            <div class="sub-portal-intro">
              <span class="portal-badge">资金钱链</span>
              <span>工程款申请、应付账款台账、工程款回款登记与应收账款台账穿透通道：</span>
            </div>
            <div class="portal-cards">
              <div class="portal-card" @click="router.push(`/finance/payment-apply?projectId=${projectId}`)">
                <div class="portal-card-title">工程付款申请</div>
                <div class="portal-card-desc">供应商与分包付款申请审批、在途冻结金额与支付执行。</div>
                <div class="portal-card-action">查看付款申请台账 →</div>
              </div>
              <div class="portal-card" @click="router.push(`/finance/payable?projectId=${projectId}`)">
                <div class="portal-card-title">应付账款台账</div>
                <div class="portal-card-desc">过程与竣工结算应付款、已核销金额与未结欠款账龄。</div>
                <div class="portal-card-action">查看应付欠款台账 →</div>
              </div>
              <div class="portal-card" @click="router.push(`/finance/payment-received?projectId=${projectId}`)">
                <div class="portal-card-title">工程回款登记</div>
                <div class="portal-card-desc">甲方银行来款认领、回款比例统计与资金入账勾稽。</div>
                <div class="portal-card-action">查看工程回款记录 →</div>
              </div>
              <div class="portal-card" @click="router.push(`/finance/receivable?projectId=${projectId}`)">
                <div class="portal-card-title">应收工程账款台账</div>
                <div class="portal-card-desc">结算应收款催收、质保金返还到期提醒与逾期账龄分析。</div>
                <div class="portal-card-action">查看应收账款台账 →</div>
              </div>
            </div>
          </div>
        </el-tab-pane>

        <!-- 5. 施工任务与现场日志 -->
        <el-tab-pane label="现场施工与安全" name="site">
          <div class="sub-portal-pane">
            <div class="sub-portal-intro">
              <span class="portal-badge">施工履约</span>
              <span>工程施工进度甘特图、日常施工日志、现场质量安全检查与隐患整改闭环：</span>
            </div>
            <div class="portal-cards">
              <div class="portal-card" @click="router.push(`/site/schedule?projectId=${projectId}`)">
                <div class="portal-card-title">施工进度计划</div>
                <div class="portal-card-desc">里程碑节点、甘特图关键线路与现场进度滞后预警。</div>
                <div class="portal-card-action">查看进度计划甘特图 →</div>
              </div>
              <div class="portal-card" @click="router.push(`/site/log?projectId=${projectId}`)">
                <div class="portal-card-title">日常施工日志</div>
                <div class="portal-card-desc">每日施工部位、班组出勤人数、机械运转与施工备忘。</div>
                <div class="portal-card-action">查看施工日志记录 →</div>
              </div>
              <div class="portal-card" @click="router.push(`/site/inspection?projectId=${projectId}`)">
                <div class="portal-card-title">质量与安全检查</div>
                <div class="portal-card-desc">现场巡检、隐患下发整改通知单与复查闭合验收。</div>
                <div class="portal-card-action">查看检查整改台账 →</div>
              </div>
            </div>
          </div>
        </el-tab-pane>

        <!-- 6. 项目团队（保留测试兼容） -->
        <el-tab-pane label="项目团队" name="team">
          <ProjectMember :project-id="projectId" />
        </el-tab-pane>

        <!-- 7. 流转大事记时间线与审计台账 -->
        <el-tab-pane label="流转大事记" name="timeline">
          <div v-loading="timelineLoading" class="timeline-pane">
            <el-timeline v-if="statusLogs.length">
              <el-timeline-item
                v-for="log in statusLogs"
                :key="log.id"
                :timestamp="log.createdAt"
                placement="top"
                type="primary"
              >
                <el-card shadow="never" class="timeline-card">
                  <div class="timeline-title">
                    <el-tag size="small" type="info">{{ getStatusLabel(log.fromStatus || '') || '起点' }}</el-tag>
                    <span class="timeline-arrow">➔</span>
                    <el-tag size="small" :type="getStatusType(log.toStatus)">{{ getStatusLabel(log.toStatus) }}</el-tag>
                    <span class="timeline-event">事件：{{ getEventLabel(log.event) }}</span>
                  </div>
                  <div v-if="log.remark" class="timeline-remark">
                    备注：{{ log.remark }}
                  </div>
                </el-card>
              </el-timeline-item>
            </el-timeline>
            <ZwEmptyState v-else description="暂无状态流转记录" />
          </div>
        </el-tab-pane>

        <!-- 关键字段变更台账 -->
        <el-tab-pane label="变更台账" name="changeLogs">
          <div v-loading="changeLogsLoading" class="changelog-pane">
            <el-table v-if="changeLogs.length" :data="changeLogs" border stripe>
              <el-table-column prop="createdAt" label="变更时间" width="180" />
              <el-table-column prop="fieldName" label="变更字段" width="160">
                <template #default="{ row }">
                  <span>{{ fieldNameMap[row.fieldName] || row.fieldName }}</span>
                </template>
              </el-table-column>
              <el-table-column prop="oldValue" label="变更前" min-width="180">
                <template #default="{ row }">
                  <span class="changelog-old">{{ row.oldValue || '(空)' }}</span>
                </template>
              </el-table-column>
              <el-table-column prop="newValue" label="变更后" min-width="180">
                <template #default="{ row }">
                  <span class="changelog-new">{{ row.newValue || '(空)' }}</span>
                </template>
              </el-table-column>
            </el-table>
            <ZwEmptyState v-else description="暂无关键字段变更记录" />
          </div>
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getProjectDetail,
  submitProject,
  withdrawProject,
  loseBidProject,
  pauseProject,
  resumeProject,
  terminateProject,
  getProjectStatusLog,
  getProjectChangeLog,
  getProjectOnebook
} from '@/api/project'
import type { ProjectStatusLog, ProjectChangeLog } from '@/types/project'
import ProjectMember from './components/ProjectMember.vue'
import ProjectBusinessSummary from './components/ProjectBusinessSummary.vue'
import ProjectOnebookPanel from './components/ProjectOnebookPanel.vue'
import ZwEmptyState from '@/components/ZwEmptyState.vue'
import * as bizLabels from '@/constants/biz-labels'

const route = useRoute()
const router = useRouter()

const projectId = computed(() => route.params.id as string)
const projectInfo = ref<any>({})
const activeTab = ref('info')

// 首屏 8 大核心指标数据
const kpiLoading = ref(false)
const kpiData = ref({
  contractAmount: 0,
  outputAmount: 0,
  receivedAmount: 0,
  unreceived: 0,
  totalPaid: 0,
  payableOutstanding: 0,
  pocketProfit: 0,
  forecastProfit: null as number | null
})

function formatKpiMoney(v: unknown): string {
  if (v == null || v === '') return '—'
  const n = Number(v)
  if (!Number.isFinite(n)) return '—'
  return '¥ ' + n.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

async function loadKpis() {
  if (!projectId.value) return
  kpiLoading.value = true
  try {
    const res: any = await getProjectOnebook(projectId.value)
    if (res?.data) {
      const b = res.data
      kpiData.value = {
        contractAmount: b.income?.contractAmount ?? 0,
        outputAmount: b.income?.outputAmount ?? 0,
        receivedAmount: b.income?.receivedAmount ?? 0,
        unreceived: b.income?.unreceived ?? 0,
        totalPaid: b.expense?.totalPaid ?? 0,
        payableOutstanding: b.expense?.totalOutstanding ?? b.fund?.payableOpen ?? 0,
        pocketProfit: b.profit?.pocketProfit ?? 0,
        forecastProfit: b.profit?.forecastProfit ?? null
      }
    }
  } catch {
    // 一本账加载失败不阻断详情主表显示
  } finally {
    kpiLoading.value = false
  }
}

const statusLogs = ref<ProjectStatusLog[]>([])
const timelineLoading = ref(false)

const changeLogs = ref<ProjectChangeLog[]>([])
const changeLogsLoading = ref(false)

const fieldNameMap: Record<string, string> = {
  projectName: '项目名称',
  ownerCompanyId: '建设单位ID',
  ownerCompanyName: '建设单位名称',
  signingCompanyId: '施工单位ID',
  signingCompanyName: '施工单位名称',
  budgetAmount: '项目预算（目标成本）',
  contractAmount: '签约合同价',
  plannedStartDate: '计划开工日期',
  plannedEndDate: '计划竣工日期',
  needTender: '是否需要招标'
}

// 状态显示名与徽章色唯一源：@/constants/biz-labels（术语表 §四）
const { PROJECT_STATUS, PROJECT_EVENT, labelOf, typeOf } = bizLabels

function getEventLabel(event: string) {
  return PROJECT_EVENT[event] ?? event
}

const canTerminate = computed(() => {
  const s = projectInfo.value.status
  return s && !['CLOSED', 'LOST', 'TERMINATED', 'CLOSING', 'TERMINATING'].includes(s)
})

function getStatusLabel(status: string) {
  return labelOf(PROJECT_STATUS, status) || status || ''
}

function getStatusType(status: string) {
  return typeOf(PROJECT_STATUS, status) as any
}

async function loadDetail() {
  if (!projectId.value) return
  const res: any = await getProjectDetail(projectId.value)
  projectInfo.value = res.data || {}
}

async function loadTimeline() {
  if (!projectId.value) return
  timelineLoading.value = true
  try {
    const res: any = await getProjectStatusLog(projectId.value)
    statusLogs.value = res.data || []
  } finally {
    timelineLoading.value = false
  }
}

async function loadChangeLogs() {
  if (!projectId.value) return
  changeLogsLoading.value = true
  try {
    const res: any = await getProjectChangeLog(projectId.value)
    changeLogs.value = res.data || []
  } finally {
    changeLogsLoading.value = false
  }
}

// ======================== 操作处理 ========================

async function handleSubmit() {
  await ElMessageBox.confirm('确定提交立项吗？', '提交立项', { type: 'info' })
  await submitProject(projectId.value)
  ElMessage.success('提交立项成功')
  await loadDetail()
}

async function handleWithdraw() {
  await ElMessageBox.confirm('确定撤回立项吗？项目将回退为草稿状态供编辑。', '撤回立项', { type: 'warning' })
  await withdrawProject(projectId.value)
  ElMessage.success('撤回成功，项目已回退草稿')
  await loadDetail()
}

async function handleLoseBid() {
  const { value } = await ElMessageBox.prompt('请输入落标归档原因：', '落标归档', {
    inputPattern: /.+/,
    inputErrorMessage: '落标原因不能为空'
  })
  await loseBidProject(projectId.value, value)
  ElMessage.success('落标归档完成')
  await loadDetail()
}

async function handlePause() {
  const { value } = await ElMessageBox.prompt('请输入暂停施工原因：', '暂停施工', {
    inputPattern: /.+/,
    inputErrorMessage: '暂停原因不能为空'
  })
  await pauseProject(projectId.value, value)
  ElMessage.success('已标记暂停施工')
  await loadDetail()
}

async function handleResume() {
  await ElMessageBox.confirm('确定恢复施工吗？', '恢复施工', { type: 'info' })
  await resumeProject(projectId.value)
  ElMessage.success('已恢复施工')
  await loadDetail()
}

async function handleTerminate() {
  const { value } = await ElMessageBox.prompt(
    '发起项目终止将进入终止审批流程。在途财务单据需已结清。请输入终止原因：',
    '发起项目终止',
    {
      inputPattern: /.+/,
      inputErrorMessage: '终止原因不能为空'
    }
  )
  await terminateProject(projectId.value, value)
  ElMessage.success('终止审批流程已发起')
  await loadDetail()
}

function handleBack() {
  router.push('/project/list')
}

watch(activeTab, (tab) => {
  if (tab === 'timeline') loadTimeline()
  if (tab === 'changeLogs') loadChangeLogs()
})

onMounted(() => {
  loadDetail()
  loadKpis()
  if (route.query.tab) {
    activeTab.value = route.query.tab as string
  }
})
</script>

<style scoped>
.project-detail-container {
  padding: var(--zw-space-md);
}

.plate-header {
  margin-bottom: var(--zw-space-md);
}

.plate-main {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.plate-title {
  display: flex;
  align-items: center;
  gap: var(--zw-space-sm);
  font-size: var(--zw-font-size-xl);
  font-weight: bold;
}

.plate-eyebrow {
  font-size: var(--zw-font-size-xs);
  color: var(--zw-text-quaternary);
  font-family: var(--zw-font-mono);
}

.plate-meta {
  display: flex;
  gap: var(--zw-space-md);
  font-size: var(--zw-font-size-xs);
  color: var(--zw-text-secondary);
  margin-top: var(--zw-space-xs);
}

/* 老板首屏 8 大核心指标栅格卡片 */
.boss-kpi-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: var(--zw-space-sm);
  margin-bottom: var(--zw-space-md);
}

.kpi-card {
  background: var(--el-bg-color-overlay);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: var(--zw-radius-sm);
  padding: var(--zw-space-sm) var(--zw-space-md);
  cursor: pointer;
  transition: all 0.2s ease-in-out;
}

.kpi-card:hover {
  border-color: var(--zw-brand);
  box-shadow: var(--zw-shadow-sm);
  transform: translateY(-1px);
}

.kpi-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--zw-space-2xs);
}

.kpi-label {
  font-size: var(--zw-font-size-sm);
  color: var(--zw-text-secondary);
  font-weight: 500;
}

.kpi-value {
  font-size: var(--zw-font-size-xl);
  font-weight: 700;
  font-family: var(--zw-font-mono);
  margin-bottom: var(--zw-space-2xs);
}

.kpi-tip {
  font-size: var(--zw-font-size-xs);
  color: var(--zw-text-quaternary);
}

.text-brand { color: var(--zw-brand); }
.text-success { color: var(--el-color-success); }
.text-warning { color: var(--el-color-warning); }
.text-danger { color: var(--el-color-danger); }

.detail-main-card {
  margin-top: var(--zw-space-xs);
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.header-nav-actions {
  display: flex;
  align-items: center;
  gap: var(--zw-space-sm);
}

/* 全景业务穿透通道 */
.sub-portal-pane {
  padding: var(--zw-space-sm) 0;
}

.sub-portal-intro {
  display: flex;
  align-items: center;
  gap: var(--zw-space-sm);
  font-size: var(--zw-font-size-sm);
  color: var(--zw-text-secondary);
  margin-bottom: var(--zw-space-md);
}

.portal-badge {
  background: var(--zw-brand);
  color: var(--el-color-white);
  padding: var(--zw-space-2xs) var(--zw-space-xs);
  border-radius: var(--zw-radius-xs);
  font-size: var(--zw-font-size-xs);
  font-weight: 600;
}

.portal-cards {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: var(--zw-space-md);
}

.portal-card {
  background: var(--el-bg-color-overlay);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: var(--zw-radius-sm);
  padding: var(--zw-space-md);
  cursor: pointer;
  transition: all 0.2s ease-in-out;
}

.portal-card:hover {
  border-color: var(--zw-brand);
  box-shadow: var(--zw-shadow-md);
  transform: translateY(-2px);
}

.portal-card-title {
  font-size: var(--zw-font-size-md);
  font-weight: 600;
  color: var(--zw-text-primary);
  margin-bottom: var(--zw-space-xs);
}

.portal-card-desc {
  font-size: var(--zw-font-size-xs);
  color: var(--zw-text-secondary);
  min-height: 36px;
  line-height: 1.5;
  margin-bottom: var(--zw-space-sm);
}

.portal-card-action {
  font-size: var(--zw-font-size-xs);
  font-weight: 600;
  color: var(--zw-brand);
}

.operation-tab { margin-top: var(--zw-space-sm); }
.operation-toolbar { margin-bottom: var(--zw-space-sm); }

.timeline-pane {
  padding: var(--zw-space-md) var(--zw-space-sm);
}
.timeline-card {
  margin-bottom: var(--zw-space-sm);
}
.timeline-title {
  font-weight: bold;
  margin-bottom: var(--zw-space-xs);
}
.timeline-arrow {
  margin: 0 var(--zw-space-sm);
}
.timeline-event {
  margin-left: var(--zw-space-sm);
  font-weight: normal;
  color: var(--zw-text-secondary);
}
.timeline-remark {
  color: var(--zw-text-regular);
  font-size: var(--zw-font-size-sm);
}

.changelog-pane {
  padding: var(--zw-space-sm);
}
.changelog-old {
  color: var(--zw-text-tertiary);
}
.changelog-new {
  font-weight: 500;
  color: var(--zw-brand);
}
</style>
