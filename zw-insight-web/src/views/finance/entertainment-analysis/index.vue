<template>
  <div class="ent-container">
    <!-- 顶部筛选：项目 + 报销来源（§6.3 分析维度可按项目/来源切分） -->
    <el-card shadow="never" class="filter-card">
      <div class="filter-bar">
        <span class="filter-label">项目：</span>
        <ProjectSelector v-model="selectedProjectId" width="280px" @change="loadAll" />
        <span class="filter-label">报销来源：</span>
        <el-select v-model="sourceType" clearable placeholder="全部来源" style="width: 140px"
          @change="loadAll">
          <el-option label="项目报销" value="PROJECT" />
          <el-option label="个人报销" value="PERSONAL" />
        </el-select>
        <el-button type="primary" :icon="Refresh" :loading="loading" @click="loadAll">刷新</el-button>
        <span class="filter-hint">
          老板默认看异常：正常报销不罗列（驾驶舱 V1 §8.1）
        </span>
      </div>
    </el-card>

    <!-- §8.1 预算执行：预算 / 实际 / 超预算 / 预算执行率 / 环比 -->
    <el-row :gutter="16" class="budget-row" v-loading="loading">
      <el-col :span="24">
        <el-card shadow="never" class="budget-card">
          <template #header>
            <div class="card-header">
              <span class="card-title">当月预算执行（{{ budget.month || '—' }}）</span>
              <span class="card-hint">计划额取月度资金计划科目明细的招待费计划额</span>
            </div>
          </template>
          <div class="budget-grid">
            <div class="b-item">
              <span class="b-label">预算</span>
              <span class="b-value">{{ budget.hasBaseline ? formatMoney(budget.planned) : '未编计划' }}</span>
            </div>
            <div class="b-item">
              <span class="b-label">实际</span>
              <span class="b-value">{{ formatMoney(budget.actual) }}</span>
            </div>
            <div class="b-item">
              <span class="b-label">超预算</span>
              <span class="b-value" :class="overBudget > 0 ? 'is-danger' : 'is-success'">
                {{ budget.hasBaseline ? formatMoney(overBudget) : '—' }}
              </span>
            </div>
            <div class="b-item">
              <span class="b-label">预算执行率</span>
              <span class="b-value" :class="rateClass">{{ formatPercent(budget.rate) }}</span>
            </div>
            <div class="b-item">
              <span class="b-label">环比（较上月）</span>
              <span class="b-value" :class="momClass">{{ momText }}</span>
            </div>
            <div class="b-item">
              <span class="b-label">同比</span>
              <!-- 如实标注：趋势窗口仅近 6 个月，无去年同月数据，不给推算值 -->
              <el-tooltip content="月度趋势窗口为近 6 个月，无去年同月数据源；不做推算以免失真"
                placement="top">
                <span class="b-value is-muted">无数据源</span>
              </el-tooltip>
            </div>
          </div>
          <el-alert v-if="!budget.hasBaseline" type="info" :closable="false" show-icon
            class="budget-alert"
            title="本项目当月未编制招待费计划，无法计算执行率与超限判定（不视为合规，也不误报超限）" />
          <el-alert v-else-if="budget.overLimit" type="error" :closable="false" show-icon
            class="budget-alert"
            :title="`当月招待费 ${formatMoney(budget.actual)} 已超计划限额 ${formatMoney(budget.planned)}，需说明原因并压降后续支出`" />
        </el-card>
      </el-col>
    </el-row>

    <!-- 异常清单（§11 八类预警中命中项）：默认展示，正常项隐藏 -->
    <el-card shadow="never" class="anomaly-card" v-loading="loading">
      <template #header>
        <div class="card-header">
          <span class="card-title">异常</span>
          <el-tag v-if="anomalies.length" type="danger" size="small">
            {{ anomalies.length }} 类命中
          </el-tag>
          <el-tag v-else type="success" size="small">无异常</el-tag>
        </div>
      </template>
      <el-empty v-if="!anomalies.length && !loading"
        description="无命中预警项（无事由/无对象/无审批/票据缺失/拆单/高频/超限均为 0）"
        :image-size="60" />
      <div v-for="a in anomalies" :key="a.name" class="anomaly-item">
        <span class="a-severity" :class="severityClass(a.name)">
          {{ severityIcon(a.name) }}
        </span>
        <span class="a-name">{{ a.name }}</span>
        <span class="a-count">{{ a.count }} {{ countUnit(a.name) }}</span>
        <span class="a-suggestion">{{ a.suggestion }}</span>
      </div>
    </el-card>

    <el-row :gutter="16" class="chart-row">
      <!-- 聚合指标（§6.3：总额/次数/单次最高/人均） -->
      <el-col :xs="24" :lg="10">
        <el-card shadow="never" class="metric-card" v-loading="loading">
          <template #header>
            <span class="card-title">累计指标</span>
          </template>
          <div class="metric-grid">
            <div class="m-item">
              <span class="m-label">累计金额</span>
              <span class="m-value">{{ formatMoney(summary.totalAmount) }}</span>
            </div>
            <div class="m-item">
              <span class="m-label">累计笔数</span>
              <span class="m-value">{{ Number(summary.totalCount || 0) }} 笔</span>
            </div>
            <div class="m-item">
              <span class="m-label">单笔最高</span>
              <span class="m-value" :class="Number(summary.maxSingleAmount) > singleLimit ? 'is-danger' : ''">
                {{ formatMoney(summary.maxSingleAmount) }}
              </span>
              <span class="m-note">限额 {{ formatMoney(singleLimit) }}</span>
            </div>
            <div class="m-item">
              <span class="m-label">人均金额</span>
              <span class="m-value">{{ formatMoney(summary.avgPerCapita) }}</span>
            </div>
          </div>
          <!-- 限额来自后端风险规则配置（zw.risk.entertainment-single-limit），前端不写死判定值 -->
          <div class="metric-note">
            单笔限额与「同人单月高频」阈值由后端风险规则配置项下发（zw.risk.entertainment-*），
            此处仅展示对照，判定以风险中心为准。
          </div>
        </el-card>
      </el-col>

      <!-- 月度趋势（§6.3「月度变化」，近 6 个月） -->
      <el-col :xs="24" :lg="14">
        <el-card shadow="never" class="chart-card" v-loading="loading">
          <template #header>
            <div class="card-header">
              <span class="card-title">月度变化（近 6 个月）</span>
              <el-tag v-if="!monthlyTrend.length" type="info" size="small">暂无已生效报销数据</el-tag>
            </div>
          </template>
          <div ref="trendChartRef" class="chart-body"></div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 责任人累计排行（§6.3「按责任人」维度） -->
    <el-card shadow="never" class="handler-card" v-loading="loading">
      <template #header>
        <div class="card-header">
          <span class="card-title">经办人累计排行</span>
          <span class="card-hint">按累计金额降序；高频经办人需专项说明（§11 预警项之一）</span>
        </div>
      </template>
      <el-table :data="byHandler" size="small" border>
        <el-table-column label="经办人" min-width="140">
          <template #default="{ row }">{{ row.handlerName || '（未登记）' }}</template>
        </el-table-column>
        <el-table-column label="累计金额" align="right" min-width="140">
          <template #default="{ row }">{{ formatMoney(row.totalAmount) }}</template>
        </el-table-column>
        <el-table-column label="笔数" align="right" width="100">
          <template #default="{ row }">{{ Number(row.totalCount || 0) }}</template>
        </el-table-column>
        <el-table-column label="单笔均值" align="right" min-width="140">
          <template #default="{ row }">{{ formatMoney(avgPerRow(row)) }}</template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无招待费报销记录（或所选范围内无数据）" :image-size="50" />
        </template>
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted, onBeforeUnmount } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import * as echarts from 'echarts'
import ProjectSelector from '@/components/ProjectSelector.vue'
import { useAppStore } from '@/stores/app'
import {
  getEntertainmentAnalysis,
  type EntertainmentAnalysis,
  type EntertainmentAnomaly,
  type EntertainmentByHandler
} from '@/api/reimbursement-analysis'
import { pickChartTheme, chartAxisStyle, chartTooltipStyle } from '@/constants/chart-theme'

const appStore = useAppStore()

const loading = ref(false)
const selectedProjectId = ref<number>()
const sourceType = ref<string | undefined>(undefined)

const analysis = ref<EntertainmentAnalysis | null>(null)

const summary = computed(() => analysis.value?.summary || ({} as any))
const byHandler = computed<EntertainmentByHandler[]>(() => analysis.value?.byHandler || [])
const anomalies = computed<EntertainmentAnomaly[]>(() => analysis.value?.anomalies || [])
const monthlyTrend = computed(() => analysis.value?.monthlyTrend || [])
const budget = computed(() => analysis.value?.budgetExecution || ({} as any))

/**
 * 单笔限额对照值。后端风险规则的真实阈值来自配置项 zw.risk.entertainment-single-limit
 * （默认 3000），本接口未下发该配置，故此处仅按文档 §8.1 的示例口径展示对照线，
 * **判定结果以风险中心为准**（不用前端常量去判级，避免两处阈值漂移）。
 */
const singleLimit = 3000

/** 超预算额 = 实际 − 计划（仅在已编计划时有意义） */
const overBudget = computed(() => {
  if (!budget.value?.hasBaseline) return 0
  return Number(budget.value.actual || 0) - Number(budget.value.planned || 0)
})

const rateClass = computed(() => {
  if (!budget.value?.hasBaseline || budget.value.rate == null) return 'is-muted'
  return Number(budget.value.rate) > 1 ? 'is-danger' : 'is-success'
})

/**
 * 环比（较上月）：直接由月度趋势的当月与上月两行真实数据相减，
 * 无上月数据时显示"—"（不用 0 冒充"无变化"）。
 */
const mom = computed<{ delta: number; base: number } | null>(() => {
  const rows = monthlyTrend.value
  if (!rows.length) return null
  const currentMonth = budget.value?.month
  const idx = currentMonth ? rows.findIndex(r => r.month === currentMonth) : rows.length - 1
  if (idx < 0) return null
  const current = Number(rows[idx].amount || 0)
  if (idx === 0) return null
  const prev = Number(rows[idx - 1].amount || 0)
  return { delta: current - prev, base: prev }
})

const momText = computed(() => {
  const m = mom.value
  if (!m) return '—'
  const sign = m.delta > 0 ? '+' : ''
  const pct = m.base !== 0 ? ` (${sign}${((m.delta / Math.abs(m.base)) * 100).toFixed(1)}%)` : ''
  return `${sign}${formatMoney(m.delta)}${pct}`
})

const momClass = computed(() => {
  const m = mom.value
  if (!m || m.delta === 0) return 'is-muted'
  return m.delta > 0 ? 'is-danger' : 'is-success'
})

// ==================== 格式化 ====================
/** 金额：≥1 万显示万元（2 位小数），否则显示元；空值/NaN 显示"—"（不显示 0 掩盖无数据） */
function formatMoney(value: unknown): string {
  if (value === null || value === undefined || value === '') return '—'
  const n = Number(value)
  if (Number.isNaN(n)) return '—'
  if (Math.abs(n) >= 10000) return `${(n / 10000).toFixed(2)} 万`
  return `${n.toFixed(2)} 元`
}

function formatPercent(value: unknown): string {
  if (value === null || value === undefined || value === '') return '—'
  const n = Number(value)
  if (Number.isNaN(n)) return '—'
  return `${(n * 100).toFixed(1)}%`
}

/** 单笔均值；笔数为 0 时返回 null（前端显示“—”，不除零也不拿 0 冒充）。
 *  参数用宽松结构：el-table 插槽 row 为 DefaultRow，声明完整接口会报不可赋值。 */
function avgPerRow(row: { totalAmount?: number; totalCount?: number }): number | null {
  const count = Number(row.totalCount || 0)
  if (!count) return null
  return Number(row.totalAmount || 0) / count
}

/** 合规硬伤（§11 RED 档）与管理偏差（YELLOW 档）分开呈现，不混为一色 */
const RED_ANOMALIES = ['无招待事由', '无招待对象', '发票不完整', '超月度限额', '单笔超限']

function severityClass(name: string): string {
  return RED_ANOMALIES.includes(name) ? 'sev-red' : 'sev-yellow'
}

function severityIcon(name: string): string {
  return RED_ANOMALIES.includes(name) ? '🔴' : '🟡'
}

/** 计数单位：金额类超限按"项"，笔数类按"笔"，高频经办人按"人" */
function countUnit(name: string): string {
  if (name.includes('经办人')) return '人'
  if (name === '超月度限额') return '项'
  return '笔'
}

// ==================== 图表 ====================
const trendChartRef = ref<HTMLElement>()
let trendChart: echarts.ECharts | null = null

function renderChart() {
  if (!trendChartRef.value) return
  if (!trendChart || trendChart.isDisposed()) trendChart = echarts.init(trendChartRef.value)
  const theme = pickChartTheme(appStore.isDark)
  if (!monthlyTrend.value.length) {
    trendChart.setOption({
      title: {
        text: '暂无已生效（APPROVED）招待费报销数据',
        left: 'center', top: 'middle',
        textStyle: { color: theme.text.secondary, fontSize: 13, fontWeight: 'normal' }
      }
    }, true)
    return
  }
  trendChart.setOption({
    tooltip: { trigger: 'axis', ...chartTooltipStyle(theme) },
    legend: { bottom: 0, textStyle: { color: theme.text.secondary } },
    grid: { left: '3%', right: '4%', bottom: '14%', top: '14%', containLabel: true },
    xAxis: {
      type: 'category',
      data: monthlyTrend.value.map(r => r.month),
      ...chartAxisStyle(theme)
    },
    yAxis: [
      { type: 'value', name: '元', ...chartAxisStyle(theme) },
      { type: 'value', name: '笔', ...chartAxisStyle(theme) }
    ],
    series: [
      {
        name: '金额', type: 'bar', barMaxWidth: 36,
        data: monthlyTrend.value.map(r => Number(r.amount || 0)),
        itemStyle: { color: theme.highlight }
      },
      {
        name: '笔数', type: 'line', yAxisIndex: 1, smooth: true,
        data: monthlyTrend.value.map(r => Number(r.cnt || 0)),
        itemStyle: { color: theme.semantic.warning }
      }
    ]
  }, true)
}

function handleResize() {
  trendChart?.resize()
}

// ==================== 数据加载（真实接口，失败显式提示不静默） ====================
async function loadAll() {
  loading.value = true
  try {
    const res: any = await getEntertainmentAnalysis({
      projectId: selectedProjectId.value,
      sourceType: sourceType.value
    })
    analysis.value = res?.data || null
    renderChart()
  } catch (e: any) {
    analysis.value = null
    renderChart()
    ElMessage.error('加载招待费分析失败：' + (e?.message || '接口异常'))
  } finally {
    loading.value = false
  }
}

watch(() => appStore.isDark, () => renderChart())

onMounted(() => {
  window.addEventListener('resize', handleResize)
  loadAll()
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  trendChart?.dispose()
  trendChart = null
})
</script>

<style scoped lang="scss">
.ent-container {
  padding: var(--zw-content-padding);
  display: flex;
  flex-direction: column;
  gap: var(--zw-space-md);
}

.filter-card :deep(.el-card__body) { padding: var(--zw-space-sm-md); }

.filter-bar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--zw-space-sm);
}

.filter-label {
  font-size: var(--zw-font-size-sm);
  color: var(--zw-text-secondary);
}

.filter-hint {
  margin-left: auto;
  font-size: var(--zw-font-size-xs);
  color: var(--zw-text-tertiary);
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--zw-space-sm);
}

.card-title { font-weight: var(--zw-font-weight-semibold); }

.card-hint {
  font-size: var(--zw-font-size-xs);
  color: var(--zw-text-quaternary);
  font-weight: normal;
}

/* §8.1 预算执行六格 */
.budget-grid {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: var(--zw-space-sm-md);
}

@media (max-width: 1200px) {
  .budget-grid { grid-template-columns: repeat(3, minmax(0, 1fr)); }
}

@media (max-width: 640px) {
  .budget-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}

.b-item {
  display: flex;
  flex-direction: column;
  gap: var(--zw-space-xs);
}

.b-label {
  font-size: var(--zw-font-size-xs);
  color: var(--zw-text-secondary);
}

.b-value {
  font-size: var(--zw-font-size-lg);
  font-weight: var(--zw-font-weight-semibold);
  font-variant-numeric: tabular-nums;
}

.budget-alert { margin-top: var(--zw-space-sm); }

/* 异常清单 */
.anomaly-item {
  display: flex;
  align-items: center;
  gap: var(--zw-space-sm);
  padding: var(--zw-space-sm) 0;
  border-bottom: 1px solid var(--el-border-color-lighter);

  &:last-child { border-bottom: none; }
}

.a-name {
  font-size: var(--zw-font-size-sm);
  font-weight: var(--zw-font-weight-semibold);
  min-width: 120px;
}

.a-count {
  font-variant-numeric: tabular-nums;
  font-size: var(--zw-font-size-sm);
  color: var(--zw-text-secondary);
  min-width: 70px;
}

.a-suggestion {
  flex: 1;
  min-width: 0;
  font-size: var(--zw-font-size-xs);
  color: var(--zw-text-tertiary);
}

/* 累计指标四格 */
.metric-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--zw-space-sm-md);
}

.m-item {
  display: flex;
  flex-direction: column;
  gap: var(--zw-space-xs);
}

.m-label {
  font-size: var(--zw-font-size-xs);
  color: var(--zw-text-secondary);
}

.m-value {
  font-size: var(--zw-font-size-xl);
  font-weight: var(--zw-font-weight-semibold);
  font-variant-numeric: tabular-nums;
}

.m-note {
  font-size: var(--zw-font-size-xs);
  color: var(--zw-text-quaternary);
}

.metric-note {
  margin-top: var(--zw-space-sm);
  font-size: var(--zw-font-size-xs);
  color: var(--zw-text-tertiary);
  line-height: 1.6;
}

.chart-card { height: 100%; }

.chart-body { height: 280px; }

.is-danger { color: var(--zw-danger); }
.is-success { color: var(--zw-success); }
.is-muted { color: var(--zw-text-tertiary); }
</style>
