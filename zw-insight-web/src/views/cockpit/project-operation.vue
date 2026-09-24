<template>
  <div class="po-container">
    <!-- 项目列表（§12 项目经营）：老板先看全局排序，再点进单项目详情（§6） -->
    <el-card shadow="never" class="list-card">
      <template #header>
        <div class="card-header">
          <span class="card-title">项目经营</span>
          <div class="header-actions">
            <el-radio-group v-model="quickFilter" size="small" @change="loadList">
              <el-radio-button v-for="q in quickFilters" :key="q.code" :label="q.code">
                {{ q.label }}
              </el-radio-button>
            </el-radio-group>
            <el-button :loading="loading" :icon="Refresh" @click="loadList">刷新</el-button>
          </div>
        </div>
      </template>
      <el-table :data="projects" size="small" border v-loading="loading" highlight-current-row
        @row-click="selectProject">
        <el-table-column label="健康度" width="90" align="center">
          <template #default="{ row }">
            <span class="health-dot" :class="`dot-${String(row.health).toLowerCase()}`"></span>
            <span class="health-text">{{ healthText(row.health) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="projectName" label="项目" min-width="180" show-overflow-tooltip />
        <el-table-column label="合同收入" align="right" min-width="110">
          <template #default="{ row }">{{ formatWan(row.contractIncome) }} 万</template>
        </el-table-column>
        <el-table-column label="预计成本" align="right" min-width="110">
          <template #default="{ row }">{{ formatWan(row.forecastTotalCost) }} 万</template>
        </el-table-column>
        <el-table-column label="预计利润" align="right" min-width="110">
          <template #default="{ row }">
            <b :class="Number(row.forecastProfit) < 0 ? 'is-danger' : ''">
              {{ formatWan(row.forecastProfit) }} 万
            </b>
          </template>
        </el-table-column>
        <el-table-column label="利润率" align="right" width="90">
          <template #default="{ row }">{{ formatRate(row.profitRate) }}</template>
        </el-table-column>
        <el-table-column align="right" width="90">
          <template #header>
            <span>回款率</span>
            <el-tooltip content="累计回款 ÷ 合同收入" placement="top">
              <el-icon class="col-help"><QuestionFilled /></el-icon>
            </el-tooltip>
          </template>
          <template #default="{ row }">{{ formatRate(ratio(row.cumulativeReceived, row.contractIncome)) }}</template>
        </el-table-column>
        <el-table-column align="right" width="90">
          <template #header>
            <span>支付率</span>
            <el-tooltip content="累计支付 ÷ 预计成本（审批口径，非现金口径）" placement="top">
              <el-icon class="col-help"><QuestionFilled /></el-icon>
            </el-tooltip>
          </template>
          <template #default="{ row }">{{ formatRate(ratio(row.cumulativePaid, row.forecastTotalCost)) }}</template>
        </el-table-column>
        <el-table-column label="资金缺口" align="right" width="110">
          <template #default="{ row }">
            <span :class="Number(row.fundGapAmount) > 0 ? 'is-danger' : 'is-muted'">
              {{ Number(row.fundGapAmount) > 0 ? `${formatWan(row.fundGapAmount)} 万` : '—' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="风险" width="90" align="center">
          <template #default="{ row }">
            <span v-if="row.redCount || row.yellowCount" class="is-danger">
              🔴{{ row.redCount }} 🟡{{ row.yellowCount }}
            </span>
            <span v-else class="is-muted">无</span>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty :image-size="60"
            :description="quickFilter === 'ALL' ? '暂无在算项目' : `无符合「${quickFilterLabel}」的项目`" />
        </template>
      </el-table>
      <!-- 口径如实告知：无 CBS 账户的项目「预计成本」退化为已实现支出，不具备预测意义 -->
      <div v-if="fallbackProjects.length" class="basis-warn">
        ⚠ {{ fallbackProjects.length }} 个项目未建 CBS 成本账户（{{ fallbackProjects.join('、') }}），
        其「预计成本/预计利润」已退化为已实现支出口径，不具备预测意义
      </div>
    </el-card>

    <!-- 单项目详情（§6）：8 指标 + 四象限 + 项目风险 -->
    <template v-if="current">
      <el-card shadow="never" class="detail-card">
        <template #header>
          <div class="card-header">
            <span class="card-title">
              {{ current.projectName }}
              <el-tag :type="healthTagType(current.health)" size="small" class="status-tag">
                {{ healthText(current.health) }}
              </el-tag>
            </span>
            <span class="card-hint">回答：还能不能赚钱 / 利润为何变化 / 哪类成本出问题 / 回款付款是否匹配 / 是否有资金缺口</span>
            <!-- §13 单据穿透链：本页入口从成本分类级起步 -->
            <el-button type="primary" link size="small" @click="openProjectDrill">
              单据穿透<el-icon><IconArrowRight /></el-icon>
            </el-button>
          </div>
        </template>

        <!-- §6 顶部 8 指标 -->
        <div class="metric-strip">
          <div class="strip-item">
            <span class="s-label">合同收入</span>
            <span class="s-value">{{ formatWan(current.contractIncome) }} 万</span>
          </div>
          <div class="strip-item">
            <span class="s-label">目标成本</span>
            <span class="s-value">{{ detail.budget != null ? `${formatWan(detail.budget)} 万` : '未建 CBS' }}</span>
          </div>
          <div class="strip-item">
            <span class="s-label">预计成本</span>
            <span class="s-value">{{ formatWan(current.forecastTotalCost) }} 万</span>
          </div>
          <div class="strip-item">
            <span class="s-label">预计利润</span>
            <span class="s-value" :class="Number(current.forecastProfit) < 0 ? 'is-danger' : 'is-success'">
              {{ formatWan(current.forecastProfit) }} 万
            </span>
          </div>
          <div class="strip-item">
            <span class="s-label">利润率</span>
            <span class="s-value">{{ formatRate(current.profitRate) }}</span>
          </div>
          <div class="strip-item">
            <span class="s-label">回款</span>
            <span class="s-value">{{ formatRate(ratio(current.cumulativeReceived, current.contractIncome)) }}</span>
          </div>
          <div class="strip-item">
            <span class="s-label">支付</span>
            <span class="s-value">{{ formatRate(ratio(current.cumulativePaid, current.forecastTotalCost)) }}</span>
          </div>
          <div class="strip-item">
            <span class="s-label">资金缺口</span>
            <span class="s-value" :class="Number(current.fundGapAmount) > 0 ? 'is-danger' : 'is-muted'">
              {{ Number(current.fundGapAmount) > 0 ? `${formatWan(current.fundGapAmount)} 万` : '无' }}
            </span>
          </div>
        </div>
      </el-card>

      <el-row :gutter="16" class="quad-row">
        <el-col :xs="24" :lg="12">
          <el-card shadow="never" class="quad-card" v-loading="detailLoading">
            <template #header>
              <div class="card-header">
                <span>利润变化趋势</span>
                <el-tag v-if="!hasSnapshot" type="info" size="small">暂无该项目快照（每日 03:30 生成）</el-tag>
              </div>
            </template>
            <div ref="profitChartRef" class="chart-body"></div>
          </el-card>
        </el-col>
        <el-col :xs="24" :lg="12">
          <el-card shadow="never" class="quad-card" v-loading="detailLoading">
            <template #header>
              <div class="card-header">
                <span>成本执行率（§7.2 七类）</span>
                <el-tag v-if="!executionRates.length" type="info" size="small">未建 CBS 成本账户</el-tag>
              </div>
            </template>
            <div v-if="executionRates.length" class="exec-list">
              <div v-for="e in executionRates" :key="e.code" class="exec-item">
                <span class="e-name">{{ e.name }}</span>
                <el-progress :percentage="e.percent" :status="e.status" :stroke-width="12"
                  class="e-bar" :show-text="false" />
                <span class="e-rate" :class="e.rateClass">{{ e.rateText }}</span>
                <span class="e-flag">{{ e.flag }}</span>
                <!-- §13 七类行直达穿透链（四类映射供应商级，其余从成本分类级） -->
                <el-button link type="primary" size="small" class="e-drill"
                  @click="openCategoryDrill(e.code, e.name)">穿透</el-button>
              </div>
            </div>
            <el-empty v-else description="无成本账户数据" :image-size="50" />
          </el-card>
        </el-col>
      </el-row>

      <el-row :gutter="16" class="quad-row">
        <el-col :xs="24" :lg="12">
          <el-card shadow="never" class="quad-card" v-loading="detailLoading">
            <template #header><span>回款 / 付款（月度）</span></template>
            <div ref="cashChartRef" class="chart-body"></div>
          </el-card>
        </el-col>
        <el-col :xs="24" :lg="12">
          <el-card shadow="never" class="quad-card" v-loading="detailLoading">
            <template #header>
              <div class="card-header">
                <span>TOP 成本偏差</span>
                <span class="card-hint">按超支额降序，最多 8 条</span>
              </div>
            </template>
            <div v-if="topDeviations.length" class="deviation-list">
              <div v-for="d in topDeviations" :key="d.code" class="deviation-item">
                <el-tag :type="d.over ? 'danger' : 'warning'" size="small" effect="plain">
                  {{ d.over ? '已超支' : '预测超支' }}
                </el-tag>
                <span class="d-name">{{ d.name }}</span>
                <span class="d-detail">{{ d.detail }}</span>
                <span class="d-over">+{{ formatWan(d.amount) }} 万</span>
              </div>
            </div>
            <el-empty v-else description="无超支账户（实际与预测均未超当前预算）" :image-size="50" />
          </el-card>
        </el-col>
      </el-row>

      <el-card shadow="never" class="risk-card" v-loading="riskLoading">
        <template #header>
          <div class="card-header">
            <span>项目风险</span>
            <el-button link type="primary" @click="$router.push('/cockpit/risk-center')">
              进入风险中心<el-icon><IconArrowRight /></el-icon>
            </el-button>
          </div>
        </template>
        <el-empty v-if="!risks.length" description="该项目当前无待处理风险" :image-size="50" />
        <div v-for="r in risks" :key="r.id" class="risk-item">
          <span>{{ r.severity === 'RED' ? '🔴' : r.severity === 'YELLOW' ? '🟡' : '⚪' }}</span>
          <span class="r-title">{{ r.title }}</span>
          <span class="r-impact">{{ formatWan(r.impactAmount) }} 万</span>
          <el-tag size="small" :type="r.handleStatus === 'OPEN' ? 'danger' : 'info'">
            {{ handleStatusText(r.handleStatus) }}
          </el-tag>
        </div>
      </el-card>
    </template>
    <el-empty v-else description="点击上表任意项目查看单项目经营详情（§6）" :image-size="70" />

    <!-- 单据穿透链（§13，P2-4） -->
    <DrillDownBreadcrumb v-model="drillVisible" :entry="drillEntry" />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh, QuestionFilled } from '@element-plus/icons-vue'
import { IconArrowRight } from '@tabler/icons-vue'
import * as echarts from 'echarts'
import { useAppStore } from '@/stores/app'
import DrillDownBreadcrumb, { type DrillEntry } from '@/components/DrillDownBreadcrumb.vue'
import type { DrillContractCategory } from '@/api/cockpit'
import {
  getProjectHealth, getCockpitProfitTrend, getCockpitFilterOptions, getRiskPage,
  type ProjectHealth, type RiskRegister, type CockpitFilterOptions
} from '@/api/cockpit'
import { getProjectCostControl, type DocCategorySummary, type CostAccountSummary } from '@/api/dashboard'
import { formatWan, toWan } from '@/utils/chart-format'
import { pickChartTheme, chartAxisStyle, chartTooltipStyle, chartSeriesLineStyle } from '@/constants/chart-theme'

const appStore = useAppStore()

const loading = ref(false)
const detailLoading = ref(false)
const riskLoading = ref(false)
const projects = ref<ProjectHealth[]>([])
const current = ref<ProjectHealth | null>(null)
const quickFilter = ref('ALL')
const quickFilters = ref<{ code: string; label: string }[]>([{ code: 'ALL', label: '全部项目' }])

// 单项目详情数据
const detail = ref<{
  budget: number | null
  docCategories: DocCategorySummary[]
  accounts: CostAccountSummary[]
  snapshots: any[]
  realized: any
}>({ budget: null, docCategories: [], accounts: [], snapshots: [], realized: null })
const risks = ref<RiskRegister[]>([])
const hasSnapshot = ref(false)

// ==================== 单据穿透链入口（§13，P2-4）====================
const drillVisible = ref(false)
const drillEntry = ref<DrillEntry | null>(null)

/** 七类→合同类别（仅四类直接映射；措施/管理/商务无单一合同源，从成本分类级进入） */
const DOC_TO_CONTRACT: Record<string, DrillContractCategory> = {
  MATERIAL: 'PURCHASE', LABOR: 'LABOR', MACHINE: 'MACHINE', SUBCONTRACT: 'SUBCONTRACT'
}

function openProjectDrill() {
  if (!current.value) return
  drillEntry.value = {
    projectId: current.value.projectId,
    projectName: current.value.projectName
  }
  drillVisible.value = true
}

function openCategoryDrill(code: string, name: string) {
  if (!current.value) return
  const base: DrillEntry = {
    projectId: current.value.projectId,
    projectName: current.value.projectName
  }
  const token = DOC_TO_CONTRACT[code]
  drillEntry.value = token
    ? { ...base, contractCategory: token, categoryLabel: name }
    : base
  drillVisible.value = true
}

const profitChartRef = ref<HTMLElement>()
const cashChartRef = ref<HTMLElement>()
let profitChart: echarts.ECharts | null = null
let cashChart: echarts.ECharts | null = null

const quickFilterLabel = computed(() =>
  quickFilters.value.find(q => q.code === quickFilter.value)?.label || quickFilter.value)

/** 未建 CBS 的项目（costBasis=FALLBACK_TOTAL_EXPENSE）：其「预计」已退化，必须如实告知 */
const fallbackProjects = computed(() =>
  projects.value.filter(p => p.costBasis === 'FALLBACK_TOTAL_EXPENSE').map(p => p.projectName))

function healthText(health: string) {
  return health === 'RED' ? '高风险' : health === 'YELLOW' ? '需关注' : '正常'
}

type TagType = 'success' | 'primary' | 'warning' | 'info' | 'danger'

function healthTagType(health: string): TagType {
  return health === 'RED' ? 'danger' : health === 'YELLOW' ? 'warning' : 'success'
}

function handleStatusText(status: string) {
  return status === 'OPEN' ? '待处理' : status === 'PROCESSING' ? '处理中'
    : status === 'RESOLVED' ? '已解决' : '已忽略'
}

function formatRate(value: number | null | undefined) {
  if (value == null) return '—'
  const n = Number(value)
  if (Number.isNaN(n)) return '—'
  return `${(n * 100).toFixed(1)}%`
}

/** 比率：分母为 0 或空时返回 null（前端显示「—」，不用 0 冒充「0%」） */
function ratio(numerator: unknown, denominator: unknown): number | null {
  const d = Number(denominator || 0)
  if (!d) return null
  return Number(numerator || 0) / d
}

/** 成本执行率（§6「成本执行率」象限）：实际发生 ÷ 当前预算，按七类 */
const executionRates = computed(() => {
  return detail.value.docCategories
    .filter(c => Number(c.current) > 0)
    .map(c => {
      const rate = Number(c.actual || 0) / Number(c.current)
      const percent = Math.min(Math.round(rate * 100), 100)
      // 超 100% 为超支（RED），90-100% 为临界（YELLOW），其余正常
      const over = rate > 1
      const near = rate > 0.9 && rate <= 1
      return {
        code: c.code,
        name: c.name,
        percent,
        // el-progress 的 status 只接受字面量联合，三元结果会被推为 string，故显式断言
        status: (over ? 'exception' : near ? 'warning' : 'success') as 'exception' | 'warning' | 'success',
        rateText: `${(rate * 100).toFixed(0)}%`,
        rateClass: over ? 'is-danger' : near ? 'is-warning' : '',
        flag: over ? '🔴' : near ? '🟡' : ''
      }
    })
    .sort((a, b) => b.percent - a.percent)
})

/** TOP 成本偏差（§6）：实际或预测超当前预算的账户，按超支额降序取 8 条 */
const topDeviations = computed(() => {
  const list: { code: string; name: string; detail: string; amount: number; over: boolean }[] = []
  for (const a of detail.value.accounts) {
    const currentBudget = Number(a.current || 0)
    const actualOver = Number(a.actual || 0) - currentBudget
    const forecastOver = Number(a.forecast || 0) - currentBudget
    if (actualOver > 0) {
      list.push({
        code: a.code, name: a.name, over: true, amount: actualOver,
        detail: `实际 ${formatWan(a.actual)} 万 > 预算 ${formatWan(currentBudget)} 万`
      })
    } else if (forecastOver > 0) {
      list.push({
        code: a.code, name: a.name, over: false, amount: forecastOver,
        detail: `预测 ${formatWan(a.forecast)} 万 > 预算 ${formatWan(currentBudget)} 万`
      })
    }
  }
  return list.sort((x, y) => y.amount - x.amount).slice(0, 8)
})

async function loadList() {
  loading.value = true
  try {
    const res: any = await getProjectHealth({
      quickFilter: quickFilter.value === 'ALL' ? undefined : quickFilter.value
    })
    projects.value = res?.data || []
    // 当前选中项目若已被筛掉，清空详情区（不展示与列表不一致的数据）
    if (current.value && !projects.value.some(p => p.projectId === current.value?.projectId)) {
      current.value = null
    }
  } catch (e: any) {
    projects.value = []
    ElMessage.error('加载项目经营列表失败：' + (e?.message || '接口异常'))
  } finally {
    loading.value = false
  }
}

async function loadFilterOptions() {
  try {
    const res: any = await getCockpitFilterOptions()
    if (res?.data?.quickFilters?.length) {
      quickFilters.value = res.data.quickFilters
    }
  } catch (e: any) {
    // 可选项加载失败不阻断列表（退回内置「全部项目」单项），但显式告知
    ElMessage.warning('快捷筛选可选项加载失败，仅保留「全部项目」：' + (e?.message || '接口异常'))
  }
}

async function selectProject(row: ProjectHealth) {
  current.value = row
  detailLoading.value = true
  riskLoading.value = true
  detail.value = { budget: null, docCategories: [], accounts: [], snapshots: [], realized: null }
  risks.value = []
  try {
    const [costRes, trendRes]: any[] = await Promise.all([
      getProjectCostControl(row.projectId),
      getCockpitProfitTrend({ projectId: row.projectId, months: 6 })
    ])
    const cost = costRes?.data
    detail.value = {
      budget: cost?.totals?.baselineTotal ?? null,
      docCategories: cost?.docCategories || [],
      accounts: cost?.accounts || [],
      snapshots: trendRes?.data?.snapshots || [],
      realized: trendRes?.data?.realized || null
    }
    hasSnapshot.value = detail.value.snapshots.length > 0
    await nextTick()
    renderCharts()
  } catch (e: any) {
    hasSnapshot.value = false
    ElMessage.error('加载单项目详情失败：' + (e?.message || '接口异常'))
  } finally {
    detailLoading.value = false
  }

  try {
    const res: any = await getRiskPage({ page: 1, size: 10, projectId: row.projectId })
    // 台账含已解决/已忽略，此处只呈现待处理与处理中（老板视角「需要我处理的」）
    risks.value = (res?.data?.records || [])
      .filter((r: RiskRegister) => r.handleStatus === 'OPEN' || r.handleStatus === 'PROCESSING')
  } catch (e: any) {
    ElMessage.error('加载项目风险失败：' + (e?.message || '接口异常'))
  } finally {
    riskLoading.value = false
  }
}

type Theme = ReturnType<typeof pickChartTheme>

function renderCharts() {
  const theme = pickChartTheme(appStore.isDark)
  if (profitChartRef.value) {
    if (!profitChart || profitChart.isDisposed()) profitChart = echarts.init(profitChartRef.value)
    if (hasSnapshot.value) {
      profitChart.setOption({
        tooltip: { trigger: 'axis', ...chartTooltipStyle(theme) },
        grid: { left: '3%', right: '4%', bottom: '8%', top: '14%', containLabel: true },
        xAxis: {
          type: 'category',
          data: detail.value.snapshots.map((s: any) => s.snapshotMonth),
          ...chartAxisStyle(theme)
        },
        yAxis: { type: 'value', name: '万元', ...chartAxisStyle(theme) },
        series: [{
          name: '预计利润', type: 'line', smooth: true,
          data: detail.value.snapshots.map((s: any) => toWan(Number(s.forecastProfit) || 0)),
          itemStyle: { color: theme.highlight },
          markLine: { silent: true, data: [{ yAxis: 0, lineStyle: { color: theme.semantic.danger } }] }
        }]
      }, true)
    } else {
      profitChart.setOption(emptyOption(theme, '暂无该项目预计利润快照'), true)
    }
  }
  if (cashChartRef.value) {
    if (!cashChart || cashChart.isDisposed()) cashChart = echarts.init(cashChartRef.value)
    const realized = detail.value.realized
    const months: any[] = realized?.months || []
    if (months.length) {
      cashChart.setOption({
        tooltip: { trigger: 'axis', ...chartTooltipStyle(theme) },
        legend: { bottom: 0, textStyle: { color: theme.text.secondary } },
        grid: { left: '3%', right: '4%', bottom: '14%', top: '14%', containLabel: true },
        xAxis: {
          type: 'category',
          data: months.map((m: any) => `${m.month}月`),
          ...chartAxisStyle(theme)
        },
        yAxis: { type: 'value', name: '万元', ...chartAxisStyle(theme) },
        series: [
          {
            name: '回款', type: 'line', smooth: true,
            data: months.map((m: any) => toWan(Number(m.income) || 0)),
            itemStyle: { color: theme.semantic.success },
            lineStyle: { type: chartSeriesLineStyle(0, theme) }
          },
          {
            name: '付款', type: 'line', smooth: true,
            data: months.map((m: any) => toWan(Number(m.expense) || 0)),
            itemStyle: { color: theme.semantic.warning },
            lineStyle: { type: chartSeriesLineStyle(1, theme) }
          }
        ]
      }, true)
    } else {
      cashChart.setOption(emptyOption(theme, '暂无该项目月度收支数据'), true)
    }
  }
}

function emptyOption(theme: Theme, text: string) {
  return {
    title: {
      text, left: 'center', top: 'middle',
      textStyle: { color: theme.text.secondary, fontSize: 13, fontWeight: 'normal' }
    }
  }
}

function handleResize() {
  profitChart?.resize()
  cashChart?.resize()
}

watch(() => appStore.isDark, () => {
  if (current.value) renderCharts()
})

onMounted(async () => {
  window.addEventListener('resize', handleResize)
  await loadFilterOptions()
  await loadList()
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  profitChart?.dispose()
  cashChart?.dispose()
  profitChart = null
  cashChart = null
})
</script>

<style scoped>
.po-container {
  padding: var(--zw-content-padding);
  display: flex;
  flex-direction: column;
  gap: var(--zw-space-md);
}
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--zw-space-sm);
  flex-wrap: wrap;
}
.card-title { font-weight: var(--zw-font-weight-semibold); }
.card-hint {
  font-size: var(--zw-font-size-xs);
  color: var(--el-text-color-secondary);
  font-weight: normal;
}
.header-actions {
  display: flex;
  align-items: center;
  gap: var(--zw-space-sm);
  flex-wrap: wrap;
}
.col-help {
  margin-left: var(--zw-space-xs);
  cursor: help;
  color: var(--el-text-color-placeholder);
}
.health-dot {
  display: inline-block;
  width: 10px;
  height: 10px;
  border-radius: 50%;
  margin-right: var(--zw-space-xs);
}
.dot-red { background-color: var(--el-color-danger); }
.dot-yellow { background-color: var(--el-color-warning); }
.dot-green { background-color: var(--el-color-success); }
.health-text { font-size: var(--zw-font-size-xs); }
.basis-warn {
  margin-top: var(--zw-space-sm);
  font-size: var(--zw-font-size-xs);
  color: var(--el-color-warning);
  line-height: 1.6;
}
.status-tag { margin-left: var(--zw-space-sm); }
/* §6 顶部 8 指标条 */
.metric-strip {
  display: grid;
  grid-template-columns: repeat(8, minmax(0, 1fr));
  gap: var(--zw-space-sm-md);
}
@media (max-width: 1400px) {
  .metric-strip { grid-template-columns: repeat(4, minmax(0, 1fr)); }
}
@media (max-width: 640px) {
  .metric-strip { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}
.strip-item {
  display: flex;
  flex-direction: column;
  gap: var(--zw-space-xs);
}
.s-label {
  font-size: var(--zw-font-size-xs);
  color: var(--el-text-color-secondary);
}
.s-value {
  font-size: var(--zw-font-size-lg);
  font-weight: var(--zw-font-weight-semibold);
  font-variant-numeric: tabular-nums;
}
.quad-row { width: 100%; }
.quad-card { height: 100%; }
.chart-body { height: 240px; }
/* 成本执行率列表 */
.exec-list {
  display: flex;
  flex-direction: column;
  gap: var(--zw-space-sm);
  max-height: 240px;
  overflow-y: auto;
}
.exec-item {
  display: flex;
  align-items: center;
  gap: var(--zw-space-sm);
}
.e-name {
  flex-shrink: 0;
  width: 48px;
  font-size: var(--zw-font-size-sm);
}
.e-bar { flex: 1; }
.e-rate {
  flex-shrink: 0;
  width: 48px;
  text-align: right;
  font-size: var(--zw-font-size-sm);
  font-variant-numeric: tabular-nums;
}
.e-flag { flex-shrink: 0; width: 20px; }
.e-drill {
  flex-shrink: 0;
  font-size: var(--zw-font-size-xs);
}
/* TOP 成本偏差 */
.deviation-list {
  display: flex;
  flex-direction: column;
  gap: var(--zw-space-xs);
  max-height: 240px;
  overflow-y: auto;
}
.deviation-item {
  display: flex;
  align-items: center;
  gap: var(--zw-space-sm);
  font-size: var(--zw-font-size-sm);
}
.d-name { flex-shrink: 0; max-width: 140px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.d-detail {
  flex: 1;
  min-width: 0;
  font-size: var(--zw-font-size-xs);
  color: var(--el-text-color-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.d-over {
  flex-shrink: 0;
  font-variant-numeric: tabular-nums;
  font-weight: var(--zw-font-weight-semibold);
  color: var(--el-color-danger);
}
/* 项目风险 */
.risk-item {
  display: flex;
  align-items: center;
  gap: var(--zw-space-sm);
  padding: var(--zw-space-sm) 0;
  border-bottom: 1px solid var(--el-border-color-lighter);
}
.risk-item:last-child { border-bottom: none; }
.r-title {
  flex: 1;
  min-width: 0;
  font-size: var(--zw-font-size-sm);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.r-impact {
  font-variant-numeric: tabular-nums;
  font-weight: var(--zw-font-weight-semibold);
}
.is-danger { color: var(--el-color-danger); }
.is-warning { color: var(--el-color-warning); }
.is-success { color: var(--el-color-success); }
.is-muted { color: var(--el-text-color-placeholder); }
</style>
