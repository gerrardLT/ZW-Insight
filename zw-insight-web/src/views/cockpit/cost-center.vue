<template>
  <div class="cc-container">
    <!-- 项目选择（成本中心为单项目视角；公司级成本结构见驾驶舱首页「成本结构」卡） -->
    <el-card shadow="never" class="filter-card">
      <div class="filter-bar">
        <span class="filter-label">项目：</span>
        <ProjectSelector v-model="selectedProjectId" width="300px" @change="loadAll" />
        <el-button :loading="loading" :icon="Refresh" :disabled="!selectedProjectId" @click="loadAll">
          刷新
        </el-button>
        <span class="filter-hint">
          老板视角：钱花到哪里去了、哪里已超预算（§7）；商务费用可继续下钻到招待费（§8）
        </span>
      </div>
    </el-card>

    <el-empty v-if="!selectedProjectId" description="请先选择项目" :image-size="80" />

    <template v-else>
      <!-- §7.1 总览四要素 -->
      <el-card shadow="never" class="overview-card" v-loading="loading">
        <template #header><span class="card-title">成本总览（§7.1）</span></template>
        <div class="ov-grid">
          <div class="ov-item">
            <span class="o-label">目标成本</span>
            <span class="o-value">{{ formatMoney(totals.baselineTotal) }}</span>
            <span class="o-note">原始批准预算（Σ CBS baseline）</span>
          </div>
          <div class="ov-item">
            <span class="o-label">已发生</span>
            <span class="o-value">{{ formatMoney(totals.actualTotal) }}</span>
            <span class="o-note">使用率 {{ formatPercent(totals.usageRate) }}</span>
          </div>
          <div class="ov-item">
            <span class="o-label">预计最终成本</span>
            <span class="o-value">{{ formatMoney(totals.forecastTotal) }}</span>
            <span class="o-note">CBS 完工预测（EAC）</span>
          </div>
          <div class="ov-item">
            <span class="o-label">预计超支</span>
            <span class="o-value" :class="overrun > 0 ? 'is-danger' : 'is-success'">
              {{ totals.forecastOverrun == null ? '—' : `${overrun > 0 ? '+' : ''}${formatMoney(overrun)}` }}
            </span>
            <span class="o-note">{{ overrun > 0 ? '🔴 超出目标成本' : '🟢 未超目标成本' }}</span>
          </div>
        </div>
        <el-alert v-if="!hasCbs" type="warning" :closable="false" show-icon class="ov-alert"
          title="本项目未建 CBS 成本账户，以下全部为 0（不是数据缺失掩盖）；请到「预算 → 成本账户」建立 CBS 后重看" />
      </el-card>

      <el-row :gutter="16" class="struct-row">
        <!-- §7.2 成本结构（七类） -->
        <el-col :xs="24" :lg="14">
          <el-card shadow="never" class="struct-card" v-loading="loading">
            <template #header>
              <div class="card-header">
                <span class="card-title">成本结构（§7.2 七类）</span>
                <span class="card-hint">条形长度=预计最终占该类预算比；🔴 超支&gt;10%、🟡 超支</span>
              </div>
            </template>
            <div v-if="docCategories.length" class="struct-list">
              <div v-for="c in docCategories" :key="c.code" class="struct-item"
                :class="{ 'is-clickable': isBusiness(c.code) }"
                @click="isBusiness(c.code) && scrollToBusiness()">
                <span class="st-name">{{ c.name }}</span>
                <el-progress :percentage="barPercent(c)" :status="barStatus(c)" :stroke-width="14"
                  class="st-bar" :show-text="false" />
                <span class="st-amount">{{ formatMoney(c.forecast) }}</span>
                <span class="st-rate" :class="rateClass(c)">{{ rateText(c) }}</span>
                <span class="st-flag">{{ riskFlag(c.riskLevel) }}</span>
                <el-tooltip v-if="c.code === 'OTHER' && c.accountCount" placement="top"
                  content="子类名不在关键词表的账户，如实单列（不静默并入其他类）；请在 CBS 中规范子类命名">
                  <el-icon class="st-help"><QuestionFilled /></el-icon>
                </el-tooltip>
              </div>
            </div>
            <el-empty v-else description="无成本账户数据" :image-size="50" />
          </el-card>
        </el-col>

        <!-- §8.1 招待费分析（老板默认看异常） -->
        <el-col :xs="24" :lg="10">
          <el-card shadow="never" class="ent-card" v-loading="entLoading">
            <template #header>
              <div class="card-header">
                <span class="card-title">招待费（§8.1）</span>
                <el-button link type="primary" @click="$router.push('/finance/entertainment-analysis')">
                  完整分析<el-icon><IconArrowRight /></el-icon>
                </el-button>
              </div>
            </template>
            <div class="ent-budget">
              <span>预算 <b>{{ ent.budgetText }}</b></span>
              <span>实际 <b>{{ formatMoney(entActual) }}</b></span>
              <span>超预算 <b :class="entOver > 0 ? 'is-danger' : 'is-success'">{{ ent.budgetText === '未编计划' ? '—' : formatMoney(entOver) }}</b></span>
              <span>执行率 <b :class="entRateClass">{{ entRateText }}</b></span>
            </div>
            <el-divider class="ent-divider" />
            <div class="ent-anomaly-title">
              异常
              <el-tag v-if="!entAnomalies.length" type="success" size="small">无</el-tag>
            </div>
            <div v-for="a in entAnomalies" :key="a.name" class="ent-anomaly">
              <span>{{ redAnomalies.includes(a.name) ? '🔴' : '🟡' }}</span>
              <span class="a-name">{{ a.name }}</span>
              <span class="a-count">{{ a.count }} {{ a.name.includes('经办人') ? '人' : '笔' }}</span>
            </div>
            <el-empty v-if="!entAnomalies.length && !entLoading" description="无命中预警项（正常费用隐藏）"
              :image-size="40" />
          </el-card>
        </el-col>
      </el-row>

      <!-- §8 商务费用下钻 -->
      <el-card ref="businessRef" shadow="never" class="business-card" v-loading="loading">
        <template #header>
          <div class="card-header">
            <span class="card-title">商务及管理费用下钻（§8）</span>
            <span class="card-hint">按 CBS 子类聚合；无账户的子类不显示（不伪造 0 行）</span>
          </div>
        </template>
        <div v-if="businessItems.length" class="business-list">
          <div v-for="b in businessItems" :key="b.name" class="business-item"
            :class="{ 'is-clickable': b.name.includes('招待') }"
            @click="b.name.includes('招待') && $router.push('/finance/entertainment-analysis')">
            <span class="b-name">{{ b.name }}</span>
            <el-progress :percentage="b.percent" :status="b.over ? 'exception' : 'success'"
              :stroke-width="12" class="b-bar" :show-text="false" />
            <span class="b-amount">{{ formatMoney(b.actual) }}</span>
            <span class="b-rate" :class="b.over ? 'is-danger' : ''">{{ b.rateText }}</span>
            <span class="b-flag">{{ b.over ? '🔴' : b.near ? '🟡' : '' }}</span>
          </div>
        </div>
        <el-empty v-else
          description="无商务及管理费用类 CBS 账户（招待费/差旅费/车辆费/会议费/办公费等）"
          :image-size="50" />
      </el-card>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh, QuestionFilled } from '@element-plus/icons-vue'
import { IconArrowRight } from '@tabler/icons-vue'
import ProjectSelector from '@/components/ProjectSelector.vue'
import {
  getProjectCostControl,
  type ProjectCostTotals, type DocCategorySummary, type CostAccountSummary
} from '@/api/dashboard'
import { getEntertainmentAnalysis } from '@/api/reimbursement-analysis'

const loading = ref(false)
const entLoading = ref(false)
const selectedProjectId = ref<number>()
const businessRef = ref()

const totals = ref<ProjectCostTotals>({})
const docCategories = ref<DocCategorySummary[]>([])
const accounts = ref<CostAccountSummary[]>([])

// 招待费（§8.1）：来自真实报销明细聚合，无数据时如实显示未编计划/无异常
const entActual = ref<number>(0)
const entAnomalies = ref<{ name: string; count: number; suggestion: string }[]>([])
const ent = ref<{ budgetText: string; planned: number | null; rate: number | null; overLimit: boolean }>({
  budgetText: '未编计划', planned: null, rate: null, overLimit: false
})

/** 合规硬伤（§11 RED 档），与招待费分析页保持同一分档 */
const redAnomalies = ['无招待事由', '无招待对象', '发票不完整', '超月度限额', '单笔超限']

const hasCbs = computed(() => docCategories.value.some(c => Number(c.accountCount) > 0))
const overrun = computed(() => Number(totals.value.forecastOverrun || 0))
const entOver = computed(() => entActual.value - Number(ent.value.planned || 0))
const entRateText = computed(() =>
  ent.value.rate == null ? '—' : `${(Number(ent.value.rate) * 100).toFixed(0)}%`)
const entRateClass = computed(() => {
  if (ent.value.rate == null) return 'is-muted'
  return Number(ent.value.rate) > 1 ? 'is-danger' : 'is-success'
})

/** §8 商务及管理费用：按 CBS 子类聚合（招待/差旅/车辆/会议/办公/管理费等） */
const BUSINESS_KEYWORDS = ['招待', '差旅', '车辆', '会议', '办公', '管理', '审计', '咨询', '商务']

const businessItems = computed(() => {
  const groups = new Map<string, { actual: number; current: number }>()
  for (const a of accounts.value) {
    const sub = a.costSubcategory || a.name
    if (!BUSINESS_KEYWORDS.some(k => sub.includes(k))) continue
    const g = groups.get(sub) || { actual: 0, current: 0 }
    g.actual += Number(a.actual || 0)
    g.current += Number(a.current || 0)
    groups.set(sub, g)
  }
  return Array.from(groups.entries())
    .map(([name, g]) => {
      const rate = g.current > 0 ? g.actual / g.current : null
      return {
        name,
        actual: g.actual,
        percent: rate == null ? 0 : Math.min(Math.round(rate * 100), 100),
        over: rate != null && rate > 1,
        near: rate != null && rate > 0.9 && rate <= 1,
        rateText: rate == null ? '—' : `${(rate * 100).toFixed(0)}%`
      }
    })
    .sort((x, y) => y.actual - x.actual)
})

function isBusiness(code: string) {
  return code === 'BUSINESS'
}

function scrollToBusiness() {
  businessRef.value?.$el?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

/** 金额：≥1 万显示万元；空值显示「—」（不显示 0 掩盖无数据） */
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
  return `${n.toFixed(1)}%`
}

/** 条形长度：预计最终 ÷ 该类预算（超 100% 截断到 100，超出部分由 🔴 与偏差率表达） */
function barPercent(c: DocCategorySummary): number {
  const current = Number(c.current || 0)
  if (!current) return 0
  return Math.min(Math.round((Number(c.forecast || 0) / current) * 100), 100)
}

type ProgressStatus = 'success' | 'exception' | 'warning'

function barStatus(c: DocCategorySummary): ProgressStatus | undefined {
  if (c.riskLevel === 'RED') return 'exception'
  if (c.riskLevel === 'YELLOW') return 'warning'
  if (c.riskLevel === 'GREEN') return 'success'
  return undefined
}

/** 偏差率 = (预计最终 − 预算) ÷ 预算；无预算基准时显示「—」（后端已给 null） */
function rateText(c: DocCategorySummary): string {
  const current = Number(c.current || 0)
  if (!current) return '—'
  const rate = ((Number(c.forecast || 0) - current) / current) * 100
  return `${rate > 0 ? '+' : ''}${rate.toFixed(1)}%`
}

function rateClass(c: DocCategorySummary): string {
  const current = Number(c.current || 0)
  if (!current) return 'is-muted'
  return Number(c.forecast || 0) > current ? 'is-danger' : 'is-success'
}

function riskFlag(level: string): string {
  return level === 'RED' ? '🔴' : level === 'YELLOW' ? '🟡' : level === 'GREEN' ? '' : '⚪'
}

async function loadCost() {
  if (!selectedProjectId.value) return
  loading.value = true
  try {
    const res: any = await getProjectCostControl(selectedProjectId.value)
    totals.value = res?.data?.totals || {}
    docCategories.value = res?.data?.docCategories || []
    accounts.value = res?.data?.accounts || []
  } catch (e: any) {
    totals.value = {}
    docCategories.value = []
    accounts.value = []
    ElMessage.error('加载成本中心失败：' + (e?.message || '接口异常'))
  } finally {
    loading.value = false
  }
}

async function loadEntertainment() {
  if (!selectedProjectId.value) return
  entLoading.value = true
  try {
    const res: any = await getEntertainmentAnalysis({ projectId: selectedProjectId.value })
    const data = res?.data
    entActual.value = Number(data?.budgetExecution?.actual || 0)
    entAnomalies.value = data?.anomalies || []
    const be = data?.budgetExecution
    ent.value = {
      budgetText: be?.hasBaseline ? formatMoney(be.planned) : '未编计划',
      planned: be?.hasBaseline ? Number(be.planned || 0) : null,
      rate: be?.rate ?? null,
      overLimit: !!be?.overLimit
    }
  } catch (e: any) {
    entActual.value = 0
    entAnomalies.value = []
    ent.value = { budgetText: '未编计划', planned: null, rate: null, overLimit: false }
    ElMessage.error('加载招待费分析失败：' + (e?.message || '接口异常'))
  } finally {
    entLoading.value = false
  }
}

async function loadAll() {
  if (!selectedProjectId.value) {
    totals.value = {}
    docCategories.value = []
    accounts.value = []
    return
  }
  await Promise.all([loadCost(), loadEntertainment()])
}

onMounted(() => {
  loadAll()
})
</script>

<style scoped>
.cc-container {
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
  color: var(--el-text-color-secondary);
}
.filter-hint {
  margin-left: auto;
  font-size: var(--zw-font-size-xs);
  color: var(--el-text-color-tertiary);
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
/* §7.1 总览四要素 */
.ov-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: var(--zw-space-md);
}
@media (max-width: 900px) {
  .ov-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}
.ov-item {
  display: flex;
  flex-direction: column;
  gap: var(--zw-space-xs);
}
.o-label {
  font-size: var(--zw-font-size-xs);
  color: var(--el-text-color-secondary);
}
.o-value {
  font-size: var(--zw-font-size-xl);
  font-weight: var(--zw-font-weight-semibold);
  font-variant-numeric: tabular-nums;
}
.o-note {
  font-size: var(--zw-font-size-xs);
  color: var(--el-text-color-tertiary);
}
.ov-alert { margin-top: var(--zw-space-sm); }
.struct-row { width: 100%; }
.struct-card, .ent-card { height: 100%; }
/* §7.2 七类结构 */
.struct-list {
  display: flex;
  flex-direction: column;
  gap: var(--zw-space-sm);
}
.struct-item {
  display: flex;
  align-items: center;
  gap: var(--zw-space-sm);
}
.struct-item.is-clickable { cursor: pointer; }
.struct-item.is-clickable:hover { background-color: var(--el-fill-color-light); }
.st-name {
  flex-shrink: 0;
  width: 52px;
  font-size: var(--zw-font-size-sm);
}
.st-bar { flex: 1; }
.st-amount {
  flex-shrink: 0;
  width: 92px;
  text-align: right;
  font-size: var(--zw-font-size-sm);
  font-variant-numeric: tabular-nums;
}
.st-rate {
  flex-shrink: 0;
  width: 62px;
  text-align: right;
  font-size: var(--zw-font-size-sm);
  font-variant-numeric: tabular-nums;
}
.st-flag { flex-shrink: 0; width: 20px; }
.st-help {
  flex-shrink: 0;
  cursor: help;
  color: var(--el-text-color-placeholder);
}
/* §8.1 招待费 */
.ent-budget {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--zw-space-sm);
  font-size: var(--zw-font-size-sm);
  color: var(--el-text-color-secondary);
}
.ent-budget b {
  font-variant-numeric: tabular-nums;
  color: var(--el-text-color-primary);
}
.ent-divider { margin: var(--zw-space-sm) 0; }
.ent-anomaly-title {
  display: flex;
  align-items: center;
  gap: var(--zw-space-sm);
  font-size: var(--zw-font-size-sm);
  font-weight: var(--zw-font-weight-semibold);
  margin-bottom: var(--zw-space-xs);
}
.ent-anomaly {
  display: flex;
  align-items: center;
  gap: var(--zw-space-sm);
  padding: var(--zw-space-xs) 0;
  font-size: var(--zw-font-size-sm);
}
.a-name { min-width: 110px; }
.a-count {
  font-variant-numeric: tabular-nums;
  color: var(--el-text-color-secondary);
}
/* §8 商务费用下钻 */
.business-list {
  display: flex;
  flex-direction: column;
  gap: var(--zw-space-sm);
}
.business-item {
  display: flex;
  align-items: center;
  gap: var(--zw-space-sm);
}
.business-item.is-clickable { cursor: pointer; }
.business-item.is-clickable:hover { background-color: var(--el-fill-color-light); }
.b-name {
  flex-shrink: 0;
  width: 110px;
  font-size: var(--zw-font-size-sm);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.b-bar { flex: 1; }
.b-amount {
  flex-shrink: 0;
  width: 92px;
  text-align: right;
  font-size: var(--zw-font-size-sm);
  font-variant-numeric: tabular-nums;
}
.b-rate {
  flex-shrink: 0;
  width: 52px;
  text-align: right;
  font-size: var(--zw-font-size-sm);
  font-variant-numeric: tabular-nums;
}
.b-flag { flex-shrink: 0; width: 20px; }
.is-danger { color: var(--el-color-danger); }
.is-success { color: var(--el-color-success); }
.is-muted { color: var(--el-text-color-placeholder); }
</style>
