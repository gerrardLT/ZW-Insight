<template>
  <div class="cockpit-container">
    <!-- 顶部筛选 + 数据时间（§18 顶部 5% / §14 全局筛选器）
         已实现：所属公司 / 项目 / 年度 / 刷新 + 6 个快捷筛选，可选项均取自
         /cockpit/filter-options 真实数据（公司=项目表 distinct owner_company）。
         ⚠ §14 另要求「区域」「项目经理」：biz_project 无 region / project_manager 列，
         全仓亦无项目经理字段 → 无数据源，故置灰并在 tooltip 说明原因，不做选了不生效的假下拉。 -->
    <div class="cockpit-header">
      <div class="header-title">
        <h2>工程经营驾驶舱</h2>
        <span class="header-hint">30 秒看懂经营 → 3 分钟定位异常 → 10 分钟追到单据</span>
      </div>
      <div class="header-actions">
        <el-select v-model="companyFilter" clearable placeholder="全部公司" style="width: 150px"
          @change="onCompanyChange">
          <el-option v-for="c in filterOptions.companies" :key="c.companyId"
            :label="c.companyName" :value="c.companyId" />
        </el-select>
        <el-select v-model="projectFilter" clearable filterable placeholder="全部项目"
          style="width: 190px" @change="loadAll">
          <el-option v-for="p in scopedProjectOptions" :key="p.projectId"
            :label="p.projectName" :value="p.projectId" />
        </el-select>
        <el-select v-model="yearFilter" style="width: 110px" @change="loadAll">
          <el-option v-for="y in yearOptions" :key="y" :label="`${y}年度`" :value="y" />
        </el-select>
        <el-button :loading="loading" @click="loadAll">
          <el-icon><Refresh /></el-icon>刷新
        </el-button>
        <span class="update-time">数据更新 {{ updateTime }}</span>
        <el-tooltip v-for="d in filterOptions.unsupportedDimensions" :key="d.code"
          :content="`${dimensionLabel(d.code)}：无数据源 —— ${d.reason}`" placement="bottom">
          <el-select :model-value="undefined" disabled :placeholder="dimensionLabel(d.code)"
            style="width: 118px" />
        </el-tooltip>
      </div>
    </div>

    <!-- 快捷筛选（§14 老板常用 6 项）：作用于项目健康度；非法值后端报 400，故只传接口下发的 code -->
    <div class="quick-filter-bar">
      <span class="quick-filter-label">快捷筛选</span>
      <el-radio-group v-model="quickFilter" size="small" @change="loadHealth">
        <el-radio-button v-for="q in filterOptions.quickFilters" :key="q.code" :label="q.code">
          {{ q.label }}
        </el-radio-button>
      </el-radio-group>
      <span v-if="overview?.scope?.filtered" class="scope-note">
        当前统计 {{ overview.scope.projectCount }} 个项目
      </span>
      <!-- 口径提示（不得静默）：有筛选时资金缺口读项目级快照，账户余额无法拆分故未计入 -->
      <el-tag v-if="overview?.gapBasis === 'PROJECT_SNAPSHOT_WITHOUT_ACCOUNT_BALANCE'"
        type="warning" size="small">
        资金缺口为项目级口径：未含公司账户余额，数值偏保守
      </el-tag>
    </div>

    <!-- 核心指标卡 8 张（§4，15%）：经营结果 4 + 资金状态 4
         四要素结构：① 当前值 ② 与上期变化 ③ 目标/口径 ④ 风险状态。
         无真实依据的要素一律显示“—”，不用 0 或“正常”充数（环比无基期时后端下发 null）。 -->
    <div v-loading="loading" class="metric-grid">
      <el-card v-for="card in metricCards" :key="card.label" shadow="never" class="metric-card"
        :class="{ 'metric-alert': card.alert, 'metric-clickable': !!card.drillField }"
        @click="openCardDrill(card)">
        <div class="metric-label">
          <span>{{ card.label }}</span>
          <el-tooltip v-if="card.tooltip" :content="card.tooltip" placement="top">
            <el-icon class="metric-help"><QuestionFilled /></el-icon>
          </el-tooltip>
          <!-- §16.1 可下钻标识：告知老板“点数字能看构成” -->
          <el-tooltip v-if="card.drillField" content="点击看各项目构成" placement="top">
            <el-icon class="metric-drill"><IconArrowRight /></el-icon>
          </el-tooltip>
        </div>
        <div class="metric-value" :class="card.valueClass">{{ card.value }}</div>
        <div class="metric-change" :class="card.changeClass">{{ card.change }}</div>
        <div class="metric-target">{{ card.target }}</div>
        <div class="metric-status" :class="card.statusClass">{{ card.status }}</div>
      </el-card>
    </div>

    <!-- 四象限图表（§18，各 20%） -->
    <el-row :gutter="16" class="chart-row">
      <el-col :xs="24" :lg="12">
        <el-card shadow="never" class="chart-card">
          <template #header>
            <div class="card-header">
              <span>预计利润趋势</span>
              <div class="header-tags">
                <!-- §5.1 要求点击月份展开归因（成本类别分解差额） -->
                <el-tag v-if="hasSnapshot" type="info" size="small">点击月份看归因</el-tag>
                <el-tag v-if="!hasSnapshot" type="info" size="small">
                  暂无快照（每日 03:30 生成，可先触发风险扫描）
                </el-tag>
              </div>
            </div>
          </template>
          <div ref="profitChartRef" class="chart-body"></div>
        </el-card>
      </el-col>
      <el-col :xs="24" :lg="12">
        <el-card shadow="never" class="chart-card">
          <template #header>
            <div class="card-header">
              <span>{{ cashMode === 'month' ? '现金流预测（滚动 6 个月）' : `未来 ${cashMode} 天资金预测` }}</span>
              <div class="header-tags">
                <!-- §9.2 要求 30/60/90 天三档；月度视图保留为趋势补充 -->
                <el-tag v-if="cashMode !== 'month' && dayForecast"
                  :type="Number(dayForecast.gap) > 0 ? (dayForecast.coverable ? 'warning' : 'danger') : 'success'"
                  size="small">
                  缺口 {{ formatWan(dayForecast.gap) }}·{{ Number(dayForecast.gap) > 0 ? (dayForecast.coverable ? '可覆盖' : '需筹资') : '无缺口' }}
                </el-tag>
                <el-tag v-if="cashMode === 'month' && !rollingData.length" type="info" size="small">暂无预测快照</el-tag>
                <el-radio-group v-model="cashMode" size="small" @change="onCashModeChange">
                  <el-radio-button label="month">月度</el-radio-button>
                  <el-radio-button :label="30">30天</el-radio-button>
                  <el-radio-button :label="60">60天</el-radio-button>
                  <el-radio-button :label="90">90天</el-radio-button>
                </el-radio-group>
              </div>
            </div>
          </template>
          <div ref="cashChartRef" class="chart-body"></div>
          <!-- 逾期堆积必须显式告知（V2026_63）：它已计入当月预计付款，
               但老板需知道其中多少是拖欠；图表不另加系列以免与净缺口配色混淆 -->
          <div v-if="cashMode === 'month' && currentMonthOverdue > 0" class="overdue-note"
            style="margin-top: var(--zw-space-xs); font-size: var(--zw-font-size-xs); color: var(--el-color-danger)">
            当月预计付款中含已逾期未付 <strong>{{ formatWan(currentMonthOverdue) }}</strong> 万元（构成项，不另计）
          </div>
          <!-- 天窗口下如实告知口径（§9.2 资金差额 vs §10.4 资金缺口不同） -->
          <div v-if="cashMode !== 'month' && dayForecast" class="overdue-note"
            style="margin-top: var(--zw-space-xs); font-size: var(--zw-font-size-xs); color: var(--el-text-color-secondary)">
            资金差额（回款−付款）<strong>{{ formatWan(dayForecast.netFlow) }}</strong> 万；
            资金缺口（付款−可用资金）<strong>{{ formatWan(dayForecast.gap) }}</strong> 万；
            其中已逾期 {{ formatWan(dayForecast.overdueUnpaid) }} 万
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16" class="chart-row">
      <el-col :xs="24" :lg="12">
        <el-card shadow="never" class="chart-card">
          <template #header>
            <div class="card-header">
              <span>项目经营健康度（异常优先）</span>
              <el-tag v-if="quickFilter !== 'ALL'" type="warning" size="small">
                已筛选：{{ quickFilterLabel }}（{{ healthData.length }} 项）
              </el-tag>
            </div>
          </template>
          <div class="health-list">
            <el-empty v-if="!healthData.length"
              :description="quickFilter === 'ALL' ? '暂无在算项目' : `无符合「${quickFilterLabel}」的项目`"
              :image-size="60" />
            <div v-for="item in healthData" :key="item.projectId" class="health-item"
              @click="goProjectCost(item.projectId)">
              <span class="health-dot" :class="`dot-${item.health.toLowerCase()}`"></span>
              <div class="health-main">
                <div class="health-name">{{ item.projectName }}</div>
                <div class="health-metrics">
                  <span>预计利润 <b :class="profitClass(item.forecastProfit)">{{ formatWan(item.forecastProfit) }}</b></span>
                  <span>利润率 <b>{{ formatRate(item.profitRate) }}</b></span>
                  <!-- §5.2“利润变化”：null = 无当月快照/无上期基期，显示“—”不当 0 -->
                  <span>利润变化
                    <b :class="deltaClass(item.profitDelta)">{{ formatDelta(item.profitDelta) }}</b>
                  </span>
                  <!-- §5.2“资金缺口”：仅统计 FUND_GAP 类活跃风险的影响额 -->
                  <span v-if="Number(item.fundGapAmount) > 0" class="health-risk">
                    资金缺口 <b>{{ formatWan(item.fundGapAmount) }}</b>
                  </span>
                  <span v-if="item.redCount || item.yellowCount" class="health-risk">
                    🔴{{ item.redCount }} 🟡{{ item.yellowCount }}
                  </span>
                </div>
                <div v-if="item.costBasis === 'FALLBACK_TOTAL_EXPENSE'" class="health-basis-warn">
                  成本口径：未建 CBS 成本账户，"预计"退化为已实现支出
                </div>
                <div v-if="item.topRisks?.length" class="health-top-risk">{{ item.topRisks[0] }}</div>
              </div>
              <el-icon class="health-arrow"><IconArrowRight /></el-icon>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="24" :lg="12">
        <el-card shadow="never" class="chart-card">
          <template #header>
            <div class="card-header">
              <span>成本结构（完工预测）</span>
              <el-tag v-if="!costStructure.length" type="info" size="small">暂无成本账户数据</el-tag>
            </div>
          </template>
          <div ref="costChartRef" class="chart-body"></div>
        </el-card>
      </el-col>
    </el-row>

    <!-- TOP 风险 / 待办（§11，20%） -->
    <el-card shadow="never" class="risk-card">
      <template #header>
        <div class="card-header">
          <span>TOP 风险 · 待处理事项</span>
          <div>
            <el-tag type="danger" size="small">🔴 严重 {{ riskSummary.redCount }}</el-tag>
            <el-tag type="warning" size="small" class="tag-gap">🟡 关注 {{ riskSummary.yellowCount }}</el-tag>
            <el-button link type="primary" @click="$router.push('/cockpit/risk-center')">
              进入风险中心<el-icon><IconArrowRight /></el-icon>
            </el-button>
          </div>
        </div>
      </template>
      <el-empty v-if="!topRisks.length" description="当前无待处理风险" :image-size="60" />
      <div v-for="risk in topRisks" :key="risk.id" class="risk-item" @click="$router.push('/cockpit/risk-center')">
        <span class="risk-severity" :class="`sev-${risk.severity.toLowerCase()}`">
          {{ risk.severity === 'RED' ? '🔴' : risk.severity === 'YELLOW' ? '🟡' : '⚪' }}
        </span>
        <span class="risk-title">{{ risk.title }}</span>
        <span class="risk-impact">{{ formatWan(risk.impactAmount) }}</span>
        <el-button link type="primary">查看 →</el-button>
      </div>
    </el-card>

    <!-- 利润变化归因（§5.1 点击月份展开）：本期 vs 上期快照按成本类别分解差额。
         无上期基线时如实告知“无法归因”，不拿本期数充当变化量。 -->
    <el-dialog v-model="attributionVisible" :title="`利润变化归因 · ${attributionMonth}`"
      width="620px" append-to-body>
      <div v-loading="attributionLoading" class="attribution-body">
        <el-alert v-if="attribution && !attribution.hasBaseline" type="info" :closable="false"
          title="该月无上期快照，无法给出归因"
          description="归因需两个月的快照对比（首月为基期）。可待下月快照生成后再查看，或改选其他月份。" />
        <template v-else-if="attribution">
          <div class="attr-summary">
            <span>预计利润 <b>{{ formatWan(attribution.forecastProfit) }}</b></span>
            <span>较上期
              <b :class="deltaClass(attribution.profitDelta)">{{ formatDelta(attribution.profitDelta) }}</b>
            </span>
          </div>
          <el-table :data="attribution.items" size="small" border>
            <el-table-column label="成本类别" min-width="110">
              <template #default="{ row }">{{ categoryLabel(row.category) }}</template>
            </el-table-column>
            <el-table-column label="本期预测" align="right" min-width="110">
              <template #default="{ row }">{{ formatWan(row.forecast) }}</template>
            </el-table-column>
            <el-table-column label="上期预测" align="right" min-width="110">
              <template #default="{ row }">{{ formatWan(row.prevForecast) }}</template>
            </el-table-column>
            <el-table-column label="对利润的影响" align="right" min-width="130">
              <template #default="{ row }">
                <b :class="deltaClass(row.delta)">{{ formatDelta(row.delta) }}</b>
              </template>
            </el-table-column>
          </el-table>
          <div class="attr-note">口径：成本上升对利润的影响为负值；类别取自 CBS 成本账户完工预测。</div>
        </template>
        <el-empty v-else-if="!attributionLoading" description="未获取到归因数据" :image-size="60" />
      </div>
    </el-dialog>

    <!-- 数字卡下钻（§16.1 点击数字 → 各项目构成）：逐项目真实值，非前端摊分。
         构成合计与卡片值不一致时必须显式提示（两套聚合口径差异），不让老板自己对不上账。 -->
    <el-dialog v-model="drillVisible" :title="drillTitle" width="660px" append-to-body>
      <div v-loading="drillLoading" class="drill-body">
        <el-table :data="drillRows" size="small" border>
          <el-table-column prop="projectName" label="项目" min-width="180" />
          <el-table-column label="数值" align="right" min-width="130">
            <template #default="{ row }">
              {{ drillIsRate ? formatRate(row.value) : `${formatWan(row.value)} 万` }}
            </template>
          </el-table-column>
          <el-table-column v-if="!drillIsRate" label="占比" align="right" width="100">
            <template #default="{ row }">{{ shareOf(row.value) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="80" align="center">
            <template #default="{ row }">
              <el-button link type="primary" size="small" @click="goProjectCost(row.projectId)">
                详情
              </el-button>
            </template>
          </el-table-column>
          <template #empty>
            <el-empty description="筛选范围内无项目" :image-size="50" />
          </template>
        </el-table>
        <div v-if="!drillIsRate && drillTotal != null" class="drill-total">
          构成合计 <b>{{ formatWan(drillTotal) }} 万</b>
          <span v-if="drillMismatch != null" class="drill-warn">
            与卡片值相差 {{ formatWan(drillMismatch) }} 万（两套聚合的项目集合或口径不同，需核对）
          </span>
        </div>
        <div v-if="drillIsRate" class="drill-total">
          利润率为比值指标，<b>不可逐项目相加</b>；公司级值以卡片为准。
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Refresh, QuestionFilled } from '@element-plus/icons-vue'
import { IconArrowRight } from '@tabler/icons-vue'
import * as echarts from 'echarts'
import { useAppStore } from '@/stores/app'
import {
  getCockpitOverview,
  getCockpitFilterOptions,
  getCockpitProfitTrend,
  getProfitAttribution,
  getProjectHealth,
  getRiskSummary,
  getRiskPage,
  scanRisks,
  type CockpitOverview,
  type CockpitFilterOptions,
  type ProfitAttribution,
  type ProjectHealth,
  type RiskRegister,
  type RiskSummary
} from '@/api/cockpit'
import { getRollingForecastPage, getForecastByDays, type FundRollingForecast, type DayForecast } from '@/api/fund-plan'
import { formatWan, toWan } from '@/utils/chart-format'
import { pickChartTheme, chartAxisStyle, chartTooltipStyle, chartSeriesLineStyle } from '@/constants/chart-theme'

const router = useRouter()
const appStore = useAppStore()

const loading = ref(false)
const updateTime = ref('—')
const yearFilter = ref(new Date().getFullYear())
const yearOptions = computed(() => {
  const current = new Date().getFullYear()
  return [current, current - 1, current - 2]
})

// ==================== 全局筛选（UI §14）====================
// 可选项全部取自后端 /filter-options 真实数据，前端不写死公司/项目/快捷筛选清单
const companyFilter = ref<number | undefined>(undefined)
const projectFilter = ref<number | undefined>(undefined)
const quickFilter = ref('ALL')
const filterOptions = ref<CockpitFilterOptions>({
  companies: [], projects: [], quickFilters: [], unsupportedDimensions: []
})

/** 项目下拉随所属公司联动（未选公司时列出全部项目） */
const scopedProjectOptions = computed(() =>
  companyFilter.value == null
    ? filterOptions.value.projects
    : filterOptions.value.projects.filter(p => p.ownerCompanyId === companyFilter.value))

const quickFilterLabel = computed(() =>
  filterOptions.value.quickFilters.find(q => q.code === quickFilter.value)?.label || quickFilter.value)

/** 无数据源维度的中文名（置灰下拉用， tooltip 内附后端下发的具体原因） */
function dimensionLabel(code: string) {
  return code === 'REGION' ? '区域' : code === 'PROJECT_MANAGER' ? '项目经理' : code
}

/** 切换公司时清空已选项目（否则可能带着其他公司的 projectId 去筛） */
function onCompanyChange() {
  projectFilter.value = undefined
  loadAll()
}

async function loadFilterOptions() {
  try {
    const res: any = await getCockpitFilterOptions()
    if (res?.data) {
      filterOptions.value = res.data
    }
  } catch (e: any) {
    // 筛选器可选项加载失败不阻断首屏，但必须显式告知（否则用户以为“没有公司可选”）
    ElMessage.error('加载筛选器可选项失败：' + (e?.message || '接口异常'))
  }
}

/** 当前筛选参数（undefined 不传，后端视为不限） */
function scopeParams() {
  return {
    ownerCompanyId: companyFilter.value,
    projectId: projectFilter.value
  }
}

const overview = ref<CockpitOverview | null>(null)
const healthData = ref<ProjectHealth[]>([])
const rollingData = ref<FundRollingForecast[]>([])
// 当月逾期未付（rollingData 已按月份升序，首元素即当月）；
// 构成项：已含在当月 expectedPayments/netGap 内，仅用于告知，不可叠加
const currentMonthOverdue = computed(() => Number(rollingData.value[0]?.overdueUnpaid || 0))
const topRisks = ref<RiskRegister[]>([])
const costStructure = ref<{ category: string; amount: number }[]>([])
const hasSnapshot = ref(false)
const riskSummary = ref<RiskSummary>({
  redCount: 0, redImpact: 0, yellowCount: 0, yellowImpact: 0,
  infoCount: 0, infoImpact: 0, activeTotal: 0
})

const profitChartRef = ref<HTMLElement>()
const cashChartRef = ref<HTMLElement>()
const costChartRef = ref<HTMLElement>()
let profitChart: echarts.ECharts | null = null
let cashChart: echarts.ECharts | null = null
let costChart: echarts.ECharts | null = null

// 现金流预测视图：月度滚动（趋势）/ 30、60、90 天窗口（§9.2 累计口径）
const cashMode = ref<'month' | 30 | 60 | 90>('month')
const dayForecast = ref<DayForecast | null>(null)

async function loadDayForecast() {
  if (cashMode.value === 'month') return
  try {
    const res: any = await getForecastByDays({ days: cashMode.value })
    dayForecast.value = res?.data || null
  } catch (e: any) {
    dayForecast.value = null
    ElMessage.error('加载天窗口资金预测失败：' + (e?.message || '接口异常'))
  }
}

async function onCashModeChange() {
  await loadDayForecast()
  renderCharts()
}

// ==================== 指标卡四要素（§4：当前值 / 与上期变化 / 目标·口径 / 风险状态）====================
/**
 * 带符号的增减额（万元）。null/undefined → “—”：
 * 后端在无上月快照时下发 null，前端必须显示“—”而不是 0（不把“无法判断”伪装成“无变化”）。
 */
function formatDelta(value: number | string | null | undefined): string {
  if (value == null) return '—'
  const n = Number(value)
  if (Number.isNaN(n)) return '—'
  return `${n > 0 ? '+' : ''}${formatWan(n)} 万`
}

function deltaClass(value: number | string | null | undefined): string {
  if (value == null) return ''
  const n = Number(value)
  if (Number.isNaN(n) || n === 0) return ''
  return n < 0 ? 'value-danger' : 'value-success'
}

/**
 * 环比文案：“较上期 ↑ 12.3 万 (+5.2%)”。
 * 百分以上期值（= 当前值 − delta）为基数；基数为 0 时不给百分比（避免除零得出 ∞）。
 */
function changeText(delta: number | null | undefined, current: number | null | undefined): string {
  if (delta == null) return '较上期 —'
  const d = Number(delta)
  if (Number.isNaN(d)) return '较上期 —'
  const arrow = d > 0 ? '↑' : d < 0 ? '↓' : '→'
  const base = Number(current || 0) - d
  const pct = base !== 0 ? ` (${d > 0 ? '+' : ''}${((d / Math.abs(base)) * 100).toFixed(1)}%)` : ''
  return `较上期 ${arrow} ${formatWan(Math.abs(d))} 万${pct}`
}

/**
 * 数字卡下钻映射（§16.1「点击数字 → 各项目构成」）。
 * 字段名与后端 /cockpit/project-health 行字段一致（逐项目真实值，非前端摊分）。
 * 90 天资金缺口无逐项目构成：账户余额属公司级资金池，硬摊会失真，故只给说明。
 */
const CARD_DRILL: Record<string, { field?: string; isRate?: boolean; note?: string }> = {
  '合同收入': { field: 'contractIncome' },
  '预计总成本': { field: 'forecastTotalCost' },
  '预计利润': { field: 'forecastProfit' },
  '预计利润率': { field: 'profitRate', isRate: true },
  '累计回款': { field: 'cumulativeReceived' },
  '累计支付': { field: 'cumulativePaid' },
  '应收未收': { field: 'receivableOutstanding' },
  '90天资金缺口': {
    note: '资金缺口为公司级现金流口径（账户余额属公司资金池，不可按项目拆分），无逐项目构成'
  }
}

const metricCards = computed(() => {
  const o = overview.value
  if (!o) return []
  const changes = o.changes
  const noBaseline = !changes || changes.basis === 'NO_BASELINE'
  const profitNegative = Number(o.forecastProfit) < 0
  const gapPositive = Number(o.gap90Days) > 0
  const targetRate = Number(o.targets?.forecastProfitRate ?? 0)
  const rateBelowTarget = Number(o.forecastProfitRate) < targetRate
  const receivableOpen = Number(o.receivableOutstanding) > 0
  const cards = [
    {
      label: '合同收入', value: formatWan(o.contractIncome) + ' 万',
      change: noBaseline ? '较上期 —' : changeText(changes?.contractIncome, o.contractIncome),
      changeClass: deltaClass(noBaseline ? null : changes?.contractIncome),
      target: '口径 生效/已结算施工合同',
      status: '—', statusClass: '',
      tooltip: '生效/已结算施工合同金额合计；无施工合同时回退项目合同额',
      alert: false
    },
    {
      label: '预计总成本', value: formatWan(o.forecastTotalCost) + ' 万',
      change: noBaseline ? '较上期 —' : changeText(changes?.forecastTotalCost, o.forecastTotalCost),
      // 成本上升为不利：颜色与利润相反（不用 deltaClass 的“涨=绿”）
      changeClass: noBaseline || changes?.forecastTotalCost == null ? ''
        : Number(changes.forecastTotalCost) > 0 ? 'value-danger' : 'value-success',
      target: '口径 CBS 完工预测（EAC）',
      status: '—', statusClass: '',
      tooltip: '成本账户完工预测合计；无成本账户时退化为已实现支出',
      alert: false
    },
    {
      label: '预计利润', value: formatWan(o.forecastProfit) + ' 万',
      valueClass: profitNegative ? 'value-danger' : '',
      change: noBaseline ? '较上期 —' : changeText(changes?.forecastProfit, o.forecastProfit),
      changeClass: deltaClass(noBaseline ? null : changes?.forecastProfit),
      target: '口径 收入 − 预计总成本',
      status: profitNegative ? '🔴 预计亏损' : '🟢 盈利',
      statusClass: profitNegative ? 'sub-danger' : 'sub-success',
      tooltip: '预计利润 = 合同收入 − 预计最终总成本（非已实现收支差）',
      alert: profitNegative
    },
    {
      label: '预计利润率', value: formatRate(o.forecastProfitRate),
      // 后端未给利润率环比（宁缺勿滥，不用推算值冒充）
      change: '较上期 —', changeClass: '',
      target: `目标 ≥ ${(targetRate * 100).toFixed(1)}%`,
      status: rateBelowTarget ? '🟡 低于目标' : '🟢 达标',
      statusClass: rateBelowTarget ? 'sub-warn' : 'sub-success',
      tooltip: `预计利润 ÷ 合同收入；目标值由后端配置 cockpit.target-profit-rate 下发（${o.targets?.basis || 'CONFIG'}）`,
      alert: false
    },
    {
      label: '累计回款', value: formatWan(o.cumulativeReceived) + ' 万',
      // 回款的“变化”用本月新增回款（真实单据口径），而非快照差值
      change: `本月新增 ${formatWan(changes?.receivedThisMonth)} 万`,
      changeClass: Number(changes?.receivedThisMonth) > 0 ? 'value-success' : '',
      target: '口径 项目收入账合计',
      status: '—', statusClass: '',
      tooltip: '回款登记审批通过回写的项目总收入；本月新增为 receive_date 落本月的 APPROVED 单据',
      alert: false
    },
    {
      label: '累计支付', value: formatWan(o.cumulativePaid) + ' 万',
      // 后端明确不给累计支付环比（updated_at 会被任意修改污染，无可靠“本月审批”时间字段）
      change: '较上期 —', changeClass: '',
      target: `本月现金需求 ${formatWan(o.currentMonthCashNeed)} 万`,
      status: '口径 审批（非现金）', statusClass: '',
      tooltip: 'total_expense 为审批口径：付款申请审批通过即回写；现金支付状态见付款申请「支付状态」',
      alert: false
    },
    {
      label: '应收未收', value: formatWan(o.receivableOutstanding) + ' 万',
      change: '较上期 —', changeClass: '',
      target: '口径 台账 OPEN 余额',
      status: receivableOpen ? '🟡 待收回' : '🟢 已结清',
      statusClass: receivableOpen ? 'sub-warn' : 'sub-success',
      tooltip: '应收台账未结清余额（结算审批生成，回款 FIFO 核销）；逐单逾期天数见风险中心',
      alert: false
    },
    {
      label: '90天资金缺口', value: formatWan(o.gap90Days) + ' 万',
      valueClass: gapPositive ? 'value-danger' : 'value-success',
      change: '较上期 —', changeClass: '',
      target: `可用资金 ${formatWan(o.availableFund)} 万`,
      status: gapPositive ? '🔴 需安排资金' : '🟢 资金可覆盖',
      statusClass: gapPositive ? 'sub-danger' : 'sub-success',
      tooltip: '资金流转 §10.4：缺口 = 未来 3 个月预计支付 − 可用资金（正数=缺钱）。'
        + '可用资金 = 账户余额快照 + 窗口内预计回款'
        + (o.gapBasis === 'PROJECT_SNAPSHOT_WITHOUT_ACCOUNT_BALANCE'
          ? '；当前为公司/项目筛选口径，账户余额无法拆分故未计入，数值偏保守' : ''),
      alert: gapPositive
    }
  ]
  // 统一补 valueClass 默认值：对象字面量部分缺字段时 TS 推为联合类型，
  // 模板统一访问 card.valueClass 会报“属性不存在”；同时附加 §16.1 下钻元信息
  return cards.map(c => {
    const drill = CARD_DRILL[c.label] || {}
    return {
      valueClass: '',
      drillField: drill.field,
      drillIsRate: !!drill.isRate,
      drillNote: drill.note,
      ...c
    }
  })
})

function formatRate(value?: number) {
  if (value == null) return '—'
  return `${(Number(value) * 100).toFixed(1)}%`
}

function profitClass(value?: number) {
  return Number(value) < 0 ? 'value-danger' : ''
}

function goProjectCost(projectId: number) {
  router.push({ path: '/project-cost-control', query: { projectId: String(projectId) } })
}

// ==================== 图表 ====================
type Theme = ReturnType<typeof pickChartTheme>

function buildProfitOption(theme: Theme) {
  const months = profitTrend.value.snapshots.map(s => s.snapshotMonth)
  return {
    tooltip: { trigger: 'axis', ...chartTooltipStyle(theme) },
    grid: { left: '3%', right: '4%', bottom: '8%', top: '18%', containLabel: true },
    xAxis: { type: 'category', data: months, ...chartAxisStyle(theme) },
    yAxis: { type: 'value', name: '万元', ...chartAxisStyle(theme) },
    series: [
      {
        name: '预计利润', type: 'line', smooth: true, connectNulls: false,
        data: profitTrend.value.snapshots.map(s => toWan(Number(s.forecastProfit) || 0)),
        itemStyle: { color: theme.highlight },
        markLine: {
          silent: true,
          data: [{ yAxis: 0, lineStyle: { color: theme.semantic.danger } }]
        }
      }
    ]
  }
}

function buildCashOption(theme: Theme) {
  // 天窗口模式（§9.2）：三项对比柱状；缺口与覆盖状态由卡头 tag + 下方口径行呈现
  if (cashMode.value !== 'month' && dayForecast.value) {
    const d = dayForecast.value
    return {
      tooltip: { trigger: 'axis', ...chartTooltipStyle(theme) },
      grid: { left: '3%', right: '4%', bottom: '8%', top: '18%', containLabel: true },
      xAxis: {
        type: 'category',
        data: ['预计回款', '预计付款', '可用资金'],
        ...chartAxisStyle(theme)
      },
      yAxis: { type: 'value', name: '万元', ...chartAxisStyle(theme) },
      series: [{
        name: `未来 ${cashMode.value} 天`, type: 'bar', barMaxWidth: 56,
        data: [
          { value: toWan(Number(d.expectedReceipts) || 0), itemStyle: { color: theme.semantic.success } },
          { value: toWan(Number(d.expectedPayments) || 0), itemStyle: { color: theme.semantic.warning } },
          { value: toWan(Number(d.availableFund) || 0), itemStyle: { color: theme.highlight } }
        ],
        label: { show: true, position: 'top', formatter: '{c} 万', color: theme.text.secondary }
      }]
    }
  }
  const months = rollingData.value.map(r => r.forecastMonth)
  return {
    tooltip: { trigger: 'axis', ...chartTooltipStyle(theme) },
    legend: { bottom: 0, textStyle: { color: theme.text.secondary } },
    grid: { left: '3%', right: '4%', bottom: '14%', top: '14%', containLabel: true },
    xAxis: { type: 'category', data: months, ...chartAxisStyle(theme) },
    yAxis: { type: 'value', name: '万元', ...chartAxisStyle(theme) },
    series: [
      {
        name: '预计回款', type: 'line', smooth: true,
        data: rollingData.value.map(r => toWan(Number(r.expectedReceipts) || 0)),
        itemStyle: { color: theme.semantic.success },
        lineStyle: { type: chartSeriesLineStyle(0, theme) }
      },
      {
        name: '预计付款', type: 'line', smooth: true,
        data: rollingData.value.map(r => toWan(Number(r.expectedPayments) || 0)),
        itemStyle: { color: theme.semantic.warning },
        lineStyle: { type: chartSeriesLineStyle(1, theme) }
      },
      {
        name: '净缺口', type: 'bar', barMaxWidth: 28,
        data: rollingData.value.map(r => toWan(Number(r.netGap) || 0)),
        itemStyle: { color: theme.semantic.danger }
      }
    ]
  }
}

function buildCostOption(theme: Theme) {
  return {
    tooltip: { trigger: 'axis', ...chartTooltipStyle(theme) },
    grid: { left: '3%', right: '10%', bottom: '3%', top: '8%', containLabel: true },
    xAxis: { type: 'value', name: '万元', ...chartAxisStyle(theme) },
    yAxis: {
      type: 'category', inverse: true, ...chartAxisStyle(theme),
      data: costStructure.value.map(c => categoryLabel(c.category))
    },
    series: [{
      name: '完工预测', type: 'bar', barMaxWidth: 22,
      data: costStructure.value.map(c => toWan(c.amount)),
      itemStyle: { color: theme.highlight },
      label: { show: true, position: 'right', formatter: '{c} 万', color: theme.text.secondary }
    }]
  }
}

/** 成本类别编码 → 中文（与后端 CBS costCategory 值域一致） */
function categoryLabel(category: string) {
  const map: Record<string, string> = {
    MATERIAL: '材料', LABOR: '人工', MACHINE: '机械', SUBCONTRACT: '分包',
    INDIRECT: '间接费', OTHER: '其他'
  }
  return map[category] || category
}

function emptyOption(theme: Theme, text: string) {
  return {
    title: {
      text, left: 'center', top: 'middle',
      textStyle: { color: theme.text.secondary, fontSize: 13, fontWeight: 'normal' }
    }
  }
}

// ==================== 数据加载（真实接口，失败显式提示不静默） ====================
const profitTrend = ref<{ snapshots: any[]; realized: any }>({ snapshots: [], realized: null })

async function loadOverview() {
  try {
    const res: any = await getCockpitOverview(scopeParams())
    overview.value = res.data || null
  } catch (e: any) {
    ElMessage.error('加载经营总览失败：' + (e?.message || '接口异常'))
  }
}

// ==================== 利润归因（§5.1 点击月份展开）====================
const attributionVisible = ref(false)
const attributionLoading = ref(false)
const attributionMonth = ref('')
const attribution = ref<ProfitAttribution | null>(null)

/**
 * 打开指定月份的利润变化归因（本期 vs 上期快照按成本类别分解差额）。
 * 带上当前项目筛选（未选项目时为公司级归因）。
 */
async function openAttribution(month: string) {
  if (!month) return
  attributionMonth.value = month
  attributionVisible.value = true
  attributionLoading.value = true
  attribution.value = null
  try {
    const res: any = await getProfitAttribution({ month, projectId: projectFilter.value })
    attribution.value = res?.data || null
  } catch (e: any) {
    // 归因失败如实告知（弹窗内显示空态），不伪造成本分解数据
    ElMessage.error('加载利润归因失败：' + (e?.message || '接口异常'))
  } finally {
    attributionLoading.value = false
  }
}

// ==================== 数字卡下钻（§16.1 点击数字 → 项目构成）====================
const drillVisible = ref(false)
const drillLoading = ref(false)
const drillTitle = ref('')
const drillIsRate = ref(false)
const drillField = ref('')
const drillRows = ref<{ projectId: number; projectName: string; value: number | null }[]>([])

/** 当前卡片的显示值（用于与构成合计对账） */
const drillCardValue = computed<number | null>(() => {
  const o: any = overview.value
  if (!o || !drillField.value) return null
  const v = o[drillField.value]
  return v == null ? null : Number(v)
})

/** 构成合计（率类指标不可相加，故返回 null） */
const drillTotal = computed<number | null>(() => {
  if (drillIsRate.value) return null
  return drillRows.value.reduce((sum, r) => sum + Number(r.value || 0), 0)
})

/**
 * 构成合计与卡片值的差额（>1 元才报）。
 * 不一致意味着两套聚合口径存在缺口（例如预计利润取 listProjectForecasts、
 * 回款/支付取项目表回写字段，项目集合可能不同），必须显式提示而非静默展示。
 */
const drillMismatch = computed<number | null>(() => {
  if (drillTotal.value == null || drillCardValue.value == null) return null
  const diff = drillTotal.value - drillCardValue.value
  return Math.abs(diff) > 1 ? diff : null
})

/** 占比：合计为 0 或负时不给百分比（避免除零与负分母得出误导性占比） */
function shareOf(value: number | null): string {
  const total = drillTotal.value
  if (value == null || total == null || total <= 0) return '—'
  return `${((Number(value) / total) * 100).toFixed(1)}%`
}

async function openCardDrill(card: any) {
  if (!card?.drillField) {
    // 无逐项目构成的指标（如公司级资金缺口）：如实告知原因，不硬摊
    ElMessage.info(card?.drillNote || '该指标无逐项目构成')
    return
  }
  drillTitle.value = `${card.label} · 各项目构成`
  drillIsRate.value = !!card.drillIsRate
  drillField.value = card.drillField
  drillVisible.value = true
  drillLoading.value = true
  drillRows.value = []
  try {
    // 构成取筛选范围内全部项目（不带快捷筛选，否则合计与卡片数字对不上）
    const res: any = await getProjectHealth({ ...scopeParams() })
    const rows: any[] = res?.data || []
    drillRows.value = rows
      .map(r => ({
        projectId: Number(r.projectId),
        projectName: String(r.projectName ?? '—'),
        value: r[card.drillField] == null ? null : Number(r[card.drillField])
      }))
      .sort((a, b) => Number(b.value ?? Number.MIN_SAFE_INTEGER) - Number(a.value ?? Number.MIN_SAFE_INTEGER))
  } catch (e: any) {
    ElMessage.error('加载项目构成失败：' + (e?.message || '接口异常'))
  } finally {
    drillLoading.value = false
  }
}

async function loadProfitTrend() {
  try {
    const res: any = await getCockpitProfitTrend({ months: 6, year: yearFilter.value })
    profitTrend.value = res.data || { snapshots: [], realized: null }
    hasSnapshot.value = (profitTrend.value.snapshots || []).length > 0
    // 成本结构取最新公司级快照的类别分解（真实快照数据，无快照则不渲染）
    const latest = [...(profitTrend.value.snapshots || [])].reverse()
      .find((s: any) => s.categoryBreakdown)
    costStructure.value = latest ? parseBreakdown(latest.categoryBreakdown) : []
  } catch (e: any) {
    hasSnapshot.value = false
    costStructure.value = []
    ElMessage.error('加载利润趋势失败：' + (e?.message || '接口异常'))
  }
}

function parseBreakdown(json: string): { category: string; amount: number }[] {
  try {
    const obj = JSON.parse(json)
    return Object.entries(obj)
      .map(([category, amount]) => ({ category, amount: Number(amount) || 0 }))
      .filter(c => c.amount > 0)
      .sort((a, b) => b.amount - a.amount)
  } catch {
    // 解析失败如实返回空（不伪造结构），由卡片提示"暂无成本账户数据"
    return []
  }
}

async function loadRolling() {
  try {
    const res: any = await getRollingForecastPage({ page: 1, size: 6 })
    const records: FundRollingForecast[] = res.data?.records || []
    // 后端按月倒序返回，图表需按月升序
    rollingData.value = [...records].reverse()
  } catch (e: any) {
    rollingData.value = []
    ElMessage.error('加载现金流预测失败：' + (e?.message || '接口异常'))
  }
}

async function loadHealth() {
  try {
    const res: any = await getProjectHealth({
      // ALL 等同于不筛选，不传参（后端对非法值报 400，只传接口下发的 code）
      quickFilter: quickFilter.value === 'ALL' ? undefined : quickFilter.value,
      ...scopeParams()
    })
    healthData.value = res.data || []
  } catch (e: any) {
    healthData.value = []
    ElMessage.error('加载项目健康度失败：' + (e?.message || '接口异常'))
  }
}

async function loadRisks() {
  try {
    const [summaryRes, pageRes]: any[] = await Promise.all([
      getRiskSummary(),
      getRiskPage({ page: 1, size: 5, handleStatus: 'OPEN' })
    ])
    riskSummary.value = summaryRes.data || riskSummary.value
    topRisks.value = pageRes.data?.records || []
    // 无待处理风险时补查 PROCESSING，保证"待老板处理"区不空白（仍是真实数据）
    if (!topRisks.value.length) {
      const processingRes: any = await getRiskPage({ page: 1, size: 5, handleStatus: 'PROCESSING' })
      topRisks.value = processingRes.data?.records || []
    }
  } catch (e: any) {
    ElMessage.error('加载风险数据失败：' + (e?.message || '接口异常'))
  }
}

function renderCharts() {
  const theme = pickChartTheme(appStore.isDark)
  if (profitChartRef.value) {
    if (!profitChart || profitChart.isDisposed()) profitChart = echarts.init(profitChartRef.value)
    profitChart.setOption(
      hasSnapshot.value ? buildProfitOption(theme) : emptyOption(theme, '暂无预计利润快照'), true)
    // §5.1：点击月份弹出归因。先 off 再 on，避免多次 renderCharts 重复绑定造成弹窗多次触发
    profitChart.off('click')
    profitChart.on('click', (params: any) => {
      if (params?.name) {
        openAttribution(String(params.name))
      }
    })
  }
  if (cashChartRef.value) {
    if (!cashChart || cashChart.isDisposed()) cashChart = echarts.init(cashChartRef.value)
    // 数据源随视图切换：月度看滚动快照，天窗口看 forecastByDays
    const hasCashData = cashMode.value === 'month'
      ? rollingData.value.length > 0
      : !!dayForecast.value
    cashChart.setOption(
      hasCashData
        ? buildCashOption(theme)
        : emptyOption(theme, cashMode.value === 'month'
          ? '暂无滚动预测快照'
          : '暂无天窗口预测数据（需存在已批未付单据或应收台账）'),
      true)
  }
  if (costChartRef.value) {
    if (!costChart || costChart.isDisposed()) costChart = echarts.init(costChartRef.value)
    costChart.setOption(
      costStructure.value.length ? buildCostOption(theme) : emptyOption(theme, '暂无成本账户数据'), true)
  }
}

async function loadAll() {
  loading.value = true
  try {
    await Promise.all([loadOverview(), loadProfitTrend(), loadRolling(), loadHealth(), loadRisks()])
    updateTime.value = new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
    renderCharts()
  } finally {
    loading.value = false
  }
}

/**
 * 首屏若风险台账为空（新环境/未跑定时任务），触发一次真实扫描。
 * 不伪造风险数据，扫描结果由 7 条规则基于真实单据产生。
 */
async function ensureRisksScanned() {
  if (riskSummary.value.activeTotal > 0) return
  try {
    const res: any = await scanRisks()
    if (res.data?.failedRules?.length) {
      ElMessage.warning(`风险扫描完成，但 ${res.data.failedRules.length} 条规则执行失败：${res.data.failedRules.join('、')}`)
    }
    await loadRisks()
    await loadHealth()
  } catch (e: any) {
    // 扫描失败不阻断首屏（风险区显示空态），但必须显式告知，不静默
    ElMessage.warning('风险扫描未执行：' + (e?.message || '接口异常'))
  }
}

function handleResize() {
  profitChart?.resize()
  cashChart?.resize()
  costChart?.resize()
}

watch(() => appStore.isDark, () => renderCharts())

onMounted(async () => {
  window.addEventListener('resize', handleResize)
  // 筛选器可选项先加载（公司/项目/快捷筛选/无数据源维度均由后端下发）
  await loadFilterOptions()
  await loadAll()
  await ensureRisksScanned()
  renderCharts()
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  profitChart?.dispose()
  cashChart?.dispose()
  costChart?.dispose()
  profitChart = null
  cashChart = null
  costChart = null
})
</script>

<style scoped>
.cockpit-container {
  padding: var(--zw-content-padding);
  display: flex;
  flex-direction: column;
  gap: var(--zw-space-md);
}
.cockpit-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: var(--zw-space-sm);
}
.header-title h2 {
  margin: 0;
  font-size: var(--zw-font-size-xl);
  font-weight: var(--zw-font-weight-semibold);
}
.header-hint {
  font-size: var(--zw-font-size-xs);
  color: var(--el-text-color-secondary);
}
.header-actions {
  display: flex;
  align-items: center;
  gap: var(--zw-space-sm);
}
.update-time {
  font-size: var(--zw-font-size-xs);
  color: var(--el-text-color-secondary);
}

/* 快捷筛选条（§14）+ 筛选范围/口径提示 */
.quick-filter-bar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--zw-space-sm);
}
.quick-filter-label {
  font-size: var(--zw-font-size-sm);
  color: var(--el-text-color-secondary);
}
.scope-note {
  font-size: var(--zw-font-size-xs);
  color: var(--el-text-color-regular);
}

/* 8 卡网格（§4：经营结果 4 + 资金状态 4） */
.metric-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: var(--zw-space-sm-md);
}
@media (max-width: 1200px) {
  .metric-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}
@media (max-width: 640px) {
  .metric-grid { grid-template-columns: 1fr; }
}
.metric-card {
  border-left: 3px solid var(--el-color-primary);
}
.metric-card.metric-alert {
  border-left-color: var(--el-color-danger);
  background-color: var(--el-color-danger-light-9);
}
.metric-label {
  display: flex;
  align-items: center;
  gap: var(--zw-space-xs);
  font-size: var(--zw-font-size-sm);
  color: var(--el-text-color-secondary);
}
.metric-help { cursor: help; }
/* §16.1 可下钻卡片：鼠标手型 + 右侧箭头提示 */
.metric-card.metric-clickable { cursor: pointer; }
.metric-drill {
  margin-left: auto;
  color: var(--el-text-color-placeholder);
}
.metric-value {
  margin-top: var(--zw-space-xs);
  font-size: var(--zw-font-size-xl);
  font-weight: var(--zw-font-weight-semibold);
  font-variant-numeric: tabular-nums;
}
/* 四要素之二：与上期变化（null 显示“—”，颜色随涨跌） */
.metric-change {
  margin-top: var(--zw-space-xs);
  font-size: var(--zw-font-size-xs);
  color: var(--el-text-color-secondary);
  font-variant-numeric: tabular-nums;
}
/* 四要素之三：目标/口径 */
.metric-target {
  margin-top: var(--zw-space-xs);
  font-size: var(--zw-font-size-xs);
  color: var(--el-text-color-secondary);
}
/* 四要素之四：风险状态 */
.metric-status {
  margin-top: var(--zw-space-xs);
  font-size: var(--zw-font-size-xs);
  color: var(--el-text-color-regular);
}
.metric-sub {
  margin-top: var(--zw-space-xs);
  font-size: var(--zw-font-size-xs);
  color: var(--el-text-color-secondary);
}
.value-danger { color: var(--el-color-danger); }
.value-success { color: var(--el-color-success); }
.sub-danger { color: var(--el-color-danger); font-weight: var(--zw-font-weight-semibold); }
.sub-warn { color: var(--el-color-warning); }
.sub-success { color: var(--el-color-success); }

.chart-row { width: 100%; }
.chart-card { height: 100%; }
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--zw-space-sm);
}
.header-tags {
  display: flex;
  align-items: center;
  gap: var(--zw-space-sm);
  flex-wrap: wrap;
}
.chart-body { height: 260px; }

/* 项目健康度列表（§5.2：异常项目排前） */
.health-list {
  max-height: 260px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: var(--zw-space-sm);
}
.health-item {
  display: flex;
  align-items: flex-start;
  gap: var(--zw-space-sm);
  padding: var(--zw-space-sm);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: var(--zw-radius-sm);
  cursor: pointer;
  transition: background-color var(--zw-duration-base);
}
.health-item:hover { background-color: var(--el-fill-color-light); }
.health-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  margin-top: var(--zw-space-xs);
  flex-shrink: 0;
}
.dot-red { background-color: var(--el-color-danger); }
.dot-yellow { background-color: var(--el-color-warning); }
.dot-green { background-color: var(--el-color-success); }
.health-main { flex: 1; min-width: 0; }
.health-name { font-weight: var(--zw-font-weight-semibold); }
.health-metrics {
  display: flex;
  flex-wrap: wrap;
  gap: var(--zw-space-sm-md);
  margin-top: var(--zw-space-xs);
  font-size: var(--zw-font-size-xs);
  color: var(--el-text-color-secondary);
}
.health-risk { color: var(--el-color-danger); }
.health-basis-warn {
  margin-top: var(--zw-space-xs);
  font-size: var(--zw-font-size-xs);
  color: var(--el-color-warning);
}
.health-top-risk {
  margin-top: var(--zw-space-xs);
  font-size: var(--zw-font-size-xs);
  color: var(--el-text-color-regular);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.health-arrow { color: var(--el-text-color-placeholder); }

/* TOP 风险（§11：老板待处理事项） */
.risk-card :deep(.el-card__body) { padding-top: var(--zw-space-sm); }
.risk-item {
  display: flex;
  align-items: center;
  gap: var(--zw-space-sm);
  padding: var(--zw-space-sm) 0;
  border-bottom: 1px solid var(--el-border-color-lighter);
  cursor: pointer;
}
.risk-item:last-child { border-bottom: none; }
.risk-title {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: var(--zw-font-size-sm);
}
.risk-impact {
  font-variant-numeric: tabular-nums;
  font-weight: var(--zw-font-weight-semibold);
  color: var(--el-color-danger);
}
.tag-gap { margin: 0 var(--zw-space-sm); }

/* 利润归因弹窗（§5.1） */
.attribution-body { min-height: 120px; }
.attr-summary {
  display: flex;
  gap: var(--zw-space-md);
  margin-bottom: var(--zw-space-sm);
  font-size: var(--zw-font-size-sm);
}
.attr-summary b { font-variant-numeric: tabular-nums; }
.attr-note {
  margin-top: var(--zw-space-sm);
  font-size: var(--zw-font-size-xs);
  color: var(--el-text-color-secondary);
}

/* 数字卡下钻弹窗（§16.1） */
.drill-body { min-height: 120px; }
.drill-total {
  margin-top: var(--zw-space-sm);
  font-size: var(--zw-font-size-sm);
  font-variant-numeric: tabular-nums;
}
.drill-warn {
  margin-left: var(--zw-space-sm);
  font-size: var(--zw-font-size-xs);
  color: var(--el-color-warning);
}
</style>
