<template>
  <div class="project-dashboard-container">
    <!-- ============ 项目墙（未选项目）：卡片网格即看板入口，替代原空白空态 ============
         2026-09-16 方案 A 两级钻取：L1 项目墙（找项目/比项目）→ L2 单项目看板 -->
    <template v-if="!selectedProjectId">
      <el-card shadow="never" class="wall-toolbar">
        <div class="wall-toolbar-row">
          <div class="wall-heading">
            <span class="wall-title">项目看板</span>
            <span class="wall-count">共 {{ wallTotal }} 个项目</span>
          </div>
          <el-input
            v-model="keyword"
            class="wall-search"
            placeholder="搜索项目名称"
            clearable
            :prefix-icon="Search"
          />
        </div>
        <div class="wall-filters">
          <button
            v-for="f in WALL_STATUS"
            :key="f.value"
            type="button"
            class="wall-chip"
            :class="{ active: statusFilter === f.value }"
            @click="switchStatus(f.value)"
          >
            {{ f.label }}
          </button>
        </div>
      </el-card>

      <div v-loading="wallLoading" class="wall-grid-wrap">
        <div v-if="!wallLoading && !wallProjects.length" class="wall-empty">
          <img :src="emptyImg" class="zw-empty-img" alt="" />
          <p class="wall-empty-text">
            {{ keyword || statusFilter ? '没有匹配的项目，试试调整筛选条件' : '暂无项目' }}
          </p>
        </div>
        <div v-else class="wall-grid">
          <div
            v-for="row in wallProjects"
            :key="row.id"
            class="project-card"
            role="button"
            tabindex="0"
            @click="selectProject(row)"
            @keydown.enter="selectProject(row)"
            @keydown.space.prevent="selectProject(row)"
          >
            <div class="pc-head">
              <span class="pc-name" :title="row.projectName">{{ row.projectName }}</span>
              <el-tag :type="statusType(row.status)" size="small" effect="plain">{{ statusLabel(row.status) }}</el-tag>
            </div>
            <div class="pc-code">{{ row.projectCode }}</div>
            <div class="pc-owner" :title="row.ownerCompanyName">业主：{{ row.ownerCompanyName || '—' }}</div>
            <div class="pc-metrics">
              <div class="pc-metric">
                <span class="pc-metric-label">合同额</span>
                <span class="pc-metric-value">{{ formatWan(row.contractAmount) }} 万</span>
              </div>
              <div class="pc-metric">
                <span class="pc-metric-label">累计产值</span>
                <span class="pc-metric-value">{{ formatWan(row.cumulativeOutput) }} 万</span>
              </div>
            </div>
            <div class="pc-progress">
              <span class="pc-progress-label">产值达成率</span>
              <el-progress
                :percentage="achieveRate(row)"
                :stroke-width="6"
                :show-text="false"
                class="pc-progress-bar"
              />
              <span class="pc-progress-num">{{ achieveRate(row) }}%</span>
            </div>
            <div class="pc-enter">查看看板 →</div>
          </div>
        </div>
      </div>
    </template>

    <!-- ============ 单项目看板（已选项目）：横幅 + KPI 摘要带 + 四宫格 ============ -->
    <template v-else>
      <el-card shadow="never" class="banner-card">
        <div class="banner-row">
          <el-button link class="banner-back" @click="backToWall">
            <el-icon><ArrowLeft /></el-icon>返回项目列表
          </el-button>
          <div class="banner-info">
            <span class="banner-name">{{ selectedProject?.projectName || '项目看板' }}</span>
            <el-tag
              v-if="selectedProject"
              :type="statusType(selectedProject.status)"
              size="small"
              effect="dark"
            >
              {{ statusLabel(selectedProject.status) }}
            </el-tag>
            <span class="banner-meta">
              {{ selectedProject?.projectCode || '' }}<template v-if="selectedProject?.ownerCompanyName"> · {{ selectedProject.ownerCompanyName }}</template>
            </span>
          </div>
          <div class="banner-switch">
            <span class="banner-switch-label">快速切换</span>
            <ProjectSelector v-model="selectedProjectId" width="240px" @change="handleProjectChange" />
          </div>
        </div>
      </el-card>

      <!-- KPI 摘要带：由四维数据派生（零额外请求），一眼读出项目健康度；
           回款率<50% / 预算使用率>90% 时副指标转红提示风险 -->
      <div class="kpi-band">
        <div class="kpi-card">
          <div class="kpi-label">合同总额</div>
          <div class="kpi-value">{{ kpi.contract ? fmtWan(kpi.contract.total) : '—' }}</div>
          <div class="kpi-sub">施工合同口径</div>
        </div>
        <div class="kpi-card">
          <div class="kpi-label">累计回款</div>
          <div class="kpi-value">{{ kpi.receipt ? fmtWan(kpi.receipt.amount) : '—' }}</div>
          <div class="kpi-sub" :class="{ danger: kpi.receipt && kpi.receipt.rate < 50 }">
            回款率 {{ kpi.receipt ? kpi.receipt.rate + '%' : '—' }}
          </div>
        </div>
        <div class="kpi-card">
          <div class="kpi-label">累计产值</div>
          <div class="kpi-value">{{ kpi.output ? fmtWan(kpi.output.total) : '—' }}</div>
          <div class="kpi-sub">本月 {{ kpi.output ? fmtWan(kpi.output.month) : '—' }}</div>
        </div>
        <div class="kpi-card">
          <div class="kpi-label">预算使用</div>
          <div class="kpi-value">{{ kpi.budget ? fmtWan(kpi.budget.used) : '—' }}</div>
          <div class="kpi-sub" :class="{ danger: kpi.budget && kpi.budget.rate > 90 }">
            使用率 {{ kpi.budget ? kpi.budget.rate + '%' : '—' }}
          </div>
        </div>
        <div class="kpi-card">
          <div class="kpi-label">进度完成</div>
          <div class="kpi-value">{{ kpi.progress ? kpi.progress.rate + '%' : '—' }}</div>
          <div class="kpi-sub">{{ kpi.progress ? `已完成 ${kpi.progress.done}/${kpi.progress.total} 项` : '—' }}</div>
        </div>
      </div>

      <!-- 四宫格看板布局 -->
      <el-row :gutter="16" class="panel-row">
        <!-- 预算执行 -->
        <el-col :span="12">
          <el-card shadow="never" class="panel-card">
            <template #header>
              <span class="panel-title">预算执行</span>
            </template>
            <div v-loading="budget.loading" class="panel-body">
              <el-alert
                v-if="budget.error"
                :title="budget.error"
                type="error"
                show-icon
                :closable="false"
              />
              <el-empty v-else-if="!budget.loading && isEmpty(budget.data)" description="暂无数据">
                <template #image>
                  <img :src="emptyImg" class="zw-empty-img" alt="" />
                </template>
              </el-empty>
              <!-- 图表占位容器（task 8.3 渲染 ECharts） -->
              <div v-show="!budget.error && !isEmpty(budget.data)" ref="budgetChartRef" class="chart-box"></div>
            </div>
          </el-card>
        </el-col>

        <!-- 项目进度 -->
        <el-col :span="12">
          <el-card shadow="never" class="panel-card">
            <template #header>
              <span class="panel-title">项目进度</span>
            </template>
            <div v-loading="progress.loading" class="panel-body">
              <el-alert
                v-if="progress.error"
                :title="progress.error"
                type="error"
                show-icon
                :closable="false"
              />
              <el-empty v-else-if="!progress.loading && isEmpty(progress.data)" description="暂无数据">
                <template #image>
                  <img :src="emptyImg" class="zw-empty-img" alt="" />
                </template>
              </el-empty>
              <div v-show="!progress.error && !isEmpty(progress.data)" ref="progressChartRef" class="chart-box"></div>
            </div>
          </el-card>
        </el-col>
      </el-row>

      <el-row :gutter="16" class="panel-row">
        <!-- 合同回款 -->
        <el-col :span="12">
          <el-card shadow="never" class="panel-card">
            <template #header>
              <span class="panel-title">合同回款</span>
            </template>
            <div v-loading="contract.loading" class="panel-body">
              <el-alert
                v-if="contract.error"
                :title="contract.error"
                type="error"
                show-icon
                :closable="false"
              />
              <el-empty v-else-if="!contract.loading && isEmpty(contract.data)" description="暂无数据">
                <template #image>
                  <img :src="emptyImg" class="zw-empty-img" alt="" />
                </template>
              </el-empty>
              <div v-show="!contract.error && !isEmpty(contract.data)" ref="contractChartRef" class="chart-box"></div>
            </div>
          </el-card>
        </el-col>

        <!-- 月度产值 -->
        <el-col :span="12">
          <el-card shadow="never" class="panel-card">
            <template #header>
              <span class="panel-title">月度产值</span>
            </template>
            <div v-loading="output.loading" class="panel-body">
              <el-alert
                v-if="output.error"
                :title="output.error"
                type="error"
                show-icon
                :closable="false"
              />
              <el-empty v-else-if="!output.loading && isEmpty(output.data)" description="暂无数据">
                <template #image>
                  <img :src="emptyImg" class="zw-empty-img" alt="" />
                </template>
              </el-empty>
              <div v-show="!output.error && !isEmpty(output.data)" ref="outputChartRef" class="chart-box"></div>
            </div>
          </el-card>
        </el-col>
      </el-row>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, reactive, watch, nextTick, onMounted, onBeforeUnmount } from 'vue'
import * as echarts from 'echarts'
import { ArrowLeft } from '@element-plus/icons-vue'
import ProjectSelector from '@/components/ProjectSelector.vue'
import { Search } from '@/components/icons/registry'
import { getProjectPage, getProjectDetail } from '@/api/project'
import type { Project, ProjectPageQuery } from '@/types/project'
import {
  getProjectBudget,
  getProjectProgress,
  getProjectContract,
  getProjectOutput,
  type BudgetExecutionDTO,
  type ProgressDTO,
  type ContractReceiptDTO,
  type OutputTrendDTO
} from '@/api/dashboard'
import { toAmount2, toWan, clampPercent, nonNegativeRemaining, formatWan } from '@/utils/chart-format'
import { useAppStore } from '@/stores/app'
import emptyLight from '@/assets/empty-blueprint.png'
import emptyDark from '@/assets/empty-blueprint-dark.png'
import { pickChartTheme, applyChartTheme } from '@/constants/chart-theme'

const appStore = useAppStore()
const emptyImg = computed(() => (appStore.isDark ? emptyDark : emptyLight))

// 当前选中的项目 ID：后端雪花 ID 超出 JS 安全整数，真实序列化为 string，
// 故此处为 number | string（与 ProjectSelector/卡片回传原生类型一致，不做强转）
const selectedProjectId = ref<number | string | undefined>()

// ======================== 项目墙（L1）：卡片网格即看板入口 ========================

// 项目状态映射：与 views/project/index.vue statusMap 同源镜像（8 态）
const PROJECT_STATUS: Record<string, { label: string; type: string }> = {
  DRAFT: { label: '草稿', type: 'info' },
  FILED: { label: '已报备', type: 'primary' },
  TENDERING: { label: '招标中', type: 'warning' },
  WON: { label: '已中标', type: 'success' },
  CONSTRUCTION: { label: '施工中', type: '' },
  COMPLETED: { label: '已竣工', type: 'success' },
  CLOSING: { label: '结项审批中', type: 'warning' },
  CLOSED: { label: '已关闭', type: 'danger' }
}
function statusLabel(status: string) {
  return PROJECT_STATUS[status]?.label || status
}
function statusType(status: string) {
  return (PROJECT_STATUS[status]?.type || 'info') as any
}

// 状态筛选 chips（高频业务态优先；全部 = 空）
const WALL_STATUS = [
  { value: '', label: '全部' },
  { value: 'CONSTRUCTION', label: '施工中' },
  { value: 'COMPLETED', label: '已竣工' },
  { value: 'TENDERING', label: '招标中' },
  { value: 'WON', label: '已中标' },
  { value: 'FILED', label: '已报备' },
  { value: 'CLOSED', label: '已关闭' }
]

const wallLoading = ref(false)
const wallProjects = ref<Project[]>([])
const wallTotal = ref(0)
const keyword = ref('')
const statusFilter = ref('')

// 已选项目信息（横幅展示）：卡片路径直接命中缓存，下拉切到缓存外项目时补拉详情
// 缓存键用 String(id) 归一化：防 string/number 混用导致 miss（雪花 id 真实为 string）
const selectedProject = ref<Project | null>(null)
const projectCache = new Map<string, Project>()

/** 产值达成率 = 累计产值 / 合同额（0-100 整数；无合同额按 0） */
function achieveRate(row: Project): number {
  const contract = Number(row.contractAmount) || 0
  if (contract <= 0) return 0
  return clampPercent((Number(row.cumulativeOutput) || 0) / contract)
}

/** 加载项目墙（真实接口：ProjectController#page；搜索/状态筛选联动） */
async function loadWall() {
  wallLoading.value = true
  try {
    const params: ProjectPageQuery = { page: 1, size: 100 }
    if (keyword.value.trim()) params.projectName = keyword.value.trim()
    if (statusFilter.value) params.status = statusFilter.value
    const res: any = await getProjectPage(params)
    wallProjects.value = res.data?.records || []
    wallTotal.value = res.data?.total || 0
    wallProjects.value.forEach((p) => projectCache.set(String(p.id), p))
  } finally {
    wallLoading.value = false
  }
}

/** 状态筛选切换：立即重拉（无防抖，点击低频） */
function switchStatus(value: string) {
  if (statusFilter.value === value) return
  statusFilter.value = value
  loadWall()
}

// 关键词搜索防抖 300ms（与表格页搜索体验一致）
let wallSearchTimer: ReturnType<typeof setTimeout> | null = null
watch(keyword, () => {
  if (wallSearchTimer) clearTimeout(wallSearchTimer)
  wallSearchTimer = setTimeout(loadWall, 300)
})

/** 缓存外项目补拉详情（仅下拉快速切换路径；卡片路径必命中缓存） */
async function resolveProject(id: number | string): Promise<Project | null> {
  const key = String(id)
  if (projectCache.has(key)) return projectCache.get(key) || null
  try {
    const res: any = await getProjectDetail(id)
    const p = (res?.data || null) as Project | null
    if (p) projectCache.set(key, p)
    return p
  } catch {
    return null
  }
}

/** 卡片点击：选中项目直达看板（零额外请求，缓存即横幅数据源；id 原生透传不强转） */
function selectProject(row: Project) {
  projectCache.set(String(row.id), row)
  handleProjectChange(row.id)
}

/** 返回项目墙：清空选择与四维数据 */
function backToWall() {
  handleProjectChange(undefined)
}

// 图表占位容器引用（task 8.3 将基于这些 ref 初始化 ECharts 实例）
const budgetChartRef = ref<HTMLElement>()
const progressChartRef = ref<HTMLElement>()
const contractChartRef = ref<HTMLElement>()
const outputChartRef = ref<HTMLElement>()

// 单维度状态结构：各维度独立维护 loading / error / data，互不影响
interface DimensionState<T> {
  loading: boolean
  error: string
  data: T | null
}

const budget = reactive<DimensionState<BudgetExecutionDTO>>({ loading: false, error: '', data: null })
const progress = reactive<DimensionState<ProgressDTO>>({ loading: false, error: '', data: null })
const contract = reactive<DimensionState<ContractReceiptDTO>>({ loading: false, error: '', data: null })
const output = reactive<DimensionState<OutputTrendDTO>>({ loading: false, error: '', data: null })

/** 判断某维度数据是否为空（用于展示「暂无数据」空状态） */
function isEmpty(data: any): boolean {
  if (data === null || data === undefined) return true
  if (Array.isArray(data)) return data.length === 0
  if (typeof data === 'object') return Object.keys(data).length === 0
  return false
}

/** 清除所有维度的当前数据与错误状态 */
function resetDimensions() {
  ;[budget, progress, contract, output].forEach((d) => {
    d.data = null
    d.error = ''
  })
}

/**
 * 单维度加载封装：独立 try/catch，任一维度失败不影响其他维度。
 * @param state  目标维度状态
 * @param loader 实际请求函数（返回 R<T> 包装对象）
 */
async function loadDimension<T>(
  state: DimensionState<T>,
  loader: (projectId: number | string) => Promise<any>,
  projectId: number | string
) {
  state.loading = true
  state.error = ''
  state.data = null
  try {
    const res: any = await loader(projectId)
    state.data = (res?.data ?? null) as T | null
  } catch (e: any) {
    state.error = e?.message || '数据加载失败，请稍后重试'
  } finally {
    state.loading = false
  }
}

/** 加载指定项目的全部四个维度数据（各维度并行、独立失败处理；id 原生 string/number 均可拼 URL） */
function loadDashboard(projectId: number | string) {
  // 四个维度并行请求，使用 allSettled 语义：单个维度的失败不会中断其他维度
  loadDimension(budget, getProjectBudget, projectId)
  loadDimension(progress, getProjectProgress, projectId)
  loadDimension(contract, getProjectContract, projectId)
  loadDimension(output, getProjectOutput, projectId)
}

/** 切换项目：清除当前数据 → 重新进入加载状态 → 请求新项目数据 */
function handleProjectChange(projectId: number | string | undefined) {
  selectedProjectId.value = projectId
  resetDimensions()
  selectedProject.value = projectId != null ? projectCache.get(String(projectId)) || null : null
  if (projectId != null && projectId !== '') {
    // 下拉切到缓存外项目（如被筛选掉）时补拉详情，横幅不空挂；返回后仍选中该项目才回填
    if (!projectCache.has(String(projectId))) {
      resolveProject(projectId).then((p) => {
        if (selectedProjectId.value === projectId && p) selectedProject.value = p
      })
    }
    loadDashboard(projectId)
  }
}

// KPI 摘要带：由四维 state 派生（零额外请求）；维度加载中/失败时对应卡显示 —
const kpi = computed(() => {
  const c = contract.data
  const b = budget.data
  const o = output.data
  const p = progress.data
  return {
    contract: c && !isEmpty(c) ? { total: toWan(c.contractTotal) } : null,
    receipt: c && !isEmpty(c) ? { amount: toWan(c.receivedAmount), rate: clampPercent(c.receiptRate) } : null,
    output: o && !isEmpty(o) ? { total: toWan(o.totalOutput), month: toWan(o.monthOutput) } : null,
    budget: b && !isEmpty(b) ? { used: toWan(b.usedAmount), rate: clampPercent(b.usageRate) } : null,
    progress: p && !isEmpty(p) ? { rate: clampPercent(p.completionRate), done: p.completedTasks, total: p.totalTasks } : null
  }
})

/** KPI 大数字展示：万元千分位（1 位小数） */
function fmtWan(v: number): string {
  return v.toLocaleString('zh-CN', { maximumFractionDigits: 1 }) + ' 万'
}

onMounted(() => {
  loadWall()
})

// ======================== ECharts 图表渲染（task 8.3） ========================
// 四个维度各自持有一个 ECharts 实例，按需 init / setOption / dispose
let budgetChart: echarts.ECharts | null = null
let progressChart: echarts.ECharts | null = null
let contractChart: echarts.ECharts | null = null
let outputChart: echarts.ECharts | null = null

/**
 * 获取或初始化某个容器对应的 ECharts 实例。
 * 容器使用 v-show 控制，DOM 始终存在但可能尺寸为 0，故在 render 时机由 nextTick 保证布局完成。
 */
function ensureChart(
  current: echarts.ECharts | null,
  el: HTMLElement | undefined
): echarts.ECharts | null {
  if (!el) return current
  if (current && !current.isDisposed()) return current
  return echarts.init(el)
}

/** 预算执行：饼图（已执行金额 vs 剩余预算），金额精度两位小数 */
function renderBudgetChart(data: BudgetExecutionDTO) {
  budgetChart = ensureChart(budgetChart, budgetChartRef.value)
  if (!budgetChart) return
  const theme = pickChartTheme(appStore.isDark)
  const used = toAmount2(data.usedAmount)
  // 剩余预算不为负：超预算时剩余按 0 处理（nonNegativeRemaining 语义，chart-format 单一事实源）
  const remaining = toAmount2(nonNegativeRemaining(data.totalBudget, data.usedAmount))
  budgetChart.setOption(
    applyChartTheme(
      {
        tooltip: {
          trigger: 'item',
          valueFormatter: (v: number) => `${Number(v).toFixed(2)} 元`
        },
        legend: { bottom: 0 },
        series: [
          {
            name: '预算执行',
            type: 'pie',
            radius: ['40%', '70%'],
            avoidLabelOverlap: false,
            itemStyle: { borderRadius: 2, borderColor: theme.surface.card, borderWidth: 2 },
            label: { show: true, formatter: '{b}: {c} 元', color: theme.text.secondary },
            data: [
              { name: '已执行金额', value: used, itemStyle: { color: theme.semantic.danger } },
              { name: '剩余预算', value: remaining, itemStyle: { color: theme.semantic.success } }
            ]
          }
        ]
      },
      theme
    ),
    true
  )
}

/** 项目进度：环形仪表盘，完成率 0%–100% 整数 */
function renderProgressChart(data: ProgressDTO) {
  progressChart = ensureChart(progressChart, progressChartRef.value)
  if (!progressChart) return
  const theme = pickChartTheme(appStore.isDark)
  // 后端 completionRate 为 0~1 的比率（保留4位小数）→ 转百分比整数，显示区间裁剪到 0–100
  const percent = clampPercent(data.completionRate)
  progressChart.setOption(
    {
      series: [
        {
          type: 'gauge',
          startAngle: 90,
          endAngle: -270,
          min: 0,
          max: 100,
          radius: '80%',
          pointer: { show: false },
          progress: {
            show: true,
            overlap: false,
            roundCap: true,
            clip: false,
            itemStyle: { color: theme.highlight }
          },
          axisLine: { lineStyle: { width: 18, color: [[1, theme.axis.splitLine]] } },
          splitLine: { show: false },
          axisTick: { show: false },
          axisLabel: { show: false },
          data: [{ value: percent }],
          detail: {
            valueAnimation: true,
            formatter: '{value}%',
            fontSize: 28,
            offsetCenter: [0, 0],
            color: theme.text.primary
          }
        }
      ]
    },
    true
  )
}

/** 合同回款：双柱并列（合同金额 vs 回款金额），Y 轴单位万元 */
function renderContractChart(data: ContractReceiptDTO) {
  contractChart = ensureChart(contractChart, contractChartRef.value)
  if (!contractChart) return
  const theme = pickChartTheme(appStore.isDark)
  contractChart.setOption(
    applyChartTheme(
      {
        tooltip: {
          trigger: 'axis',
          axisPointer: { type: 'shadow' },
          valueFormatter: (v: number) => `${Number(v).toFixed(2)} 万元`
        },
        legend: { bottom: 0, data: ['合同金额', '回款金额'] },
        grid: { left: '3%', right: '4%', bottom: '12%', containLabel: true },
        xAxis: { type: 'category', data: ['合同 / 回款'] },
        yAxis: { type: 'value', name: '万元', axisLabel: { formatter: '{value}' } },
        series: [
          {
            name: '合同金额',
            type: 'bar',
            barGap: '20%',
            data: [toWan(data.contractTotal)],
            itemStyle: { color: theme.seriesGray[0] }
          },
          {
            name: '回款金额',
            type: 'bar',
            data: [toWan(data.receivedAmount)],
            itemStyle: { color: theme.highlight }
          }
        ]
      },
      theme
    ),
    true
  )
}

/** 月度产值：折线图，X 轴月份，Y 轴产值（万元） */
function renderOutputChart(data: OutputTrendDTO) {
  outputChart = ensureChart(outputChart, outputChartRef.value)
  if (!outputChart) return
  const theme = pickChartTheme(appStore.isDark)
  const trend = data.trend || []
  outputChart.setOption(
    applyChartTheme(
      {
        tooltip: {
          trigger: 'axis',
          valueFormatter: (v: number) => `${Number(v).toFixed(2)} 万元`
        },
        grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
        xAxis: { type: 'category', boundaryGap: false, data: trend.map((t) => t.month) },
        yAxis: { type: 'value', name: '万元', axisLabel: { formatter: '{value}' } },
        series: [
          {
            name: '月度产值',
            type: 'line',
            smooth: true,
            areaStyle: { opacity: 0.15 },
            data: trend.map((t) => toWan(t.amount)),
            itemStyle: { color: theme.highlight },
            lineStyle: { color: theme.highlight }
          }
        ]
      },
      theme
    ),
    true
  )
}

/** 释放某个维度的图表实例（数据被清空 / 报错 / 卸载时调用） */
function disposeChart(chart: echarts.ECharts | null): null {
  if (chart && !chart.isDisposed()) chart.dispose()
  return null
}

// 数据驱动渲染：各维度数据可用且无错误时渲染图表；否则释放对应实例。
// 使用 nextTick 确保 v-show 容器在重新选择项目后已完成布局再 init/resize。
watch(
  () => budget.data,
  (val) => {
    if (val && !budget.error && !isEmpty(val)) {
      nextTick(() => renderBudgetChart(val))
    } else {
      budgetChart = disposeChart(budgetChart)
    }
  }
)

watch(
  () => progress.data,
  (val) => {
    if (val && !progress.error && !isEmpty(val)) {
      nextTick(() => renderProgressChart(val))
    } else {
      progressChart = disposeChart(progressChart)
    }
  }
)

watch(
  () => contract.data,
  (val) => {
    if (val && !contract.error && !isEmpty(val)) {
      nextTick(() => renderContractChart(val))
    } else {
      contractChart = disposeChart(contractChart)
    }
  }
)

watch(
  () => output.data,
  (val) => {
    if (val && !output.error && !isEmpty(val)) {
      nextTick(() => renderOutputChart(val))
    } else {
      outputChart = disposeChart(outputChart)
    }
  }
)

// 主题切换即时重绘：以当前已加载的各维度数据重建 option，不重复请求接口
watch(
  () => appStore.isDark,
  () => {
    if (budget.data && !budget.error && !isEmpty(budget.data)) nextTick(() => renderBudgetChart(budget.data!))
    if (progress.data && !progress.error && !isEmpty(progress.data)) nextTick(() => renderProgressChart(progress.data!))
    if (contract.data && !contract.error && !isEmpty(contract.data)) nextTick(() => renderContractChart(contract.data!))
    if (output.data && !output.error && !isEmpty(output.data)) nextTick(() => renderOutputChart(output.data!))
  }
)

// 窗口尺寸变化时，防抖（300ms）内完成四个图表自适应重绘
let resizeTimer: ReturnType<typeof setTimeout> | null = null
function handleResize() {
  if (resizeTimer) clearTimeout(resizeTimer)
  resizeTimer = setTimeout(() => {
    budgetChart?.resize()
    progressChart?.resize()
    contractChart?.resize()
    outputChart?.resize()
  }, 300)
}
window.addEventListener('resize', handleResize)

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  if (resizeTimer) clearTimeout(resizeTimer)
  budgetChart = disposeChart(budgetChart)
  progressChart = disposeChart(progressChart)
  contractChart = disposeChart(contractChart)
  outputChart = disposeChart(outputChart)
})

// 暴露图表容器引用，便于在同文件内扩展 ECharts 渲染逻辑
defineExpose({ budgetChartRef, progressChartRef, contractChartRef, outputChartRef })
</script>

<style scoped>
.project-dashboard-container {
  padding: var(--zw-space-md);
}

/* ===== 项目墙（L1）===== */
.wall-toolbar {
  margin-bottom: var(--zw-space-md);
}
.wall-toolbar-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--zw-space-md);
}
.wall-heading {
  display: flex;
  align-items: baseline;
  gap: var(--zw-space-sm);
}
.wall-title {
  font-size: var(--zw-font-size-md);
  font-weight: 600;
  color: var(--zw-text-primary);
}
.wall-count {
  font-size: var(--zw-font-size-sm);
  color: var(--zw-text-tertiary);
}
.wall-search {
  width: 240px;
}
.wall-filters {
  display: flex;
  flex-wrap: wrap;
  gap: var(--zw-space-xs);
  margin-top: var(--zw-space-sm);
}
.wall-chip {
  border: 1px solid var(--zw-border-light);
  background: transparent;
  color: var(--zw-text-secondary);
  font-size: var(--zw-font-size-sm);
  padding: var(--zw-space-2xs) var(--zw-space-sm);
  border-radius: var(--zw-radius-xs);
  cursor: pointer;
  transition: border-color 0.15s, color 0.15s;
}
.wall-chip:hover {
  color: var(--zw-brand);
  border-color: var(--zw-brand);
}
.wall-chip.active {
  background: var(--zw-brand);
  border-color: var(--zw-brand);
  color: var(--zw-on-primary);
}
.wall-grid-wrap {
  min-height: 240px;
}
.wall-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: var(--zw-space-md);
}
.wall-empty {
  text-align: center;
  padding: var(--zw-space-xl) 0;
}
.wall-empty-text {
  color: var(--zw-text-tertiary);
  font-size: var(--zw-font-size-sm);
  margin-top: var(--zw-space-sm);
}
.project-card {
  background: var(--zw-bg-card);
  border: 1px solid var(--zw-border-light);
  border-radius: var(--zw-radius-xs);
  padding: var(--zw-space-md);
  cursor: pointer;
  display: flex;
  flex-direction: column;
  gap: var(--zw-space-2xs);
  transition: border-color 0.15s;
}
.project-card:hover,
.project-card:focus-visible {
  border-color: var(--zw-brand);
  outline: none;
}
.pc-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--zw-space-sm);
}
.pc-name {
  font-size: var(--zw-font-size-base);
  font-weight: 600;
  color: var(--zw-text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.pc-code {
  font-family: var(--zw-font-display);
  font-size: var(--zw-font-size-xs);
  color: var(--zw-text-quaternary);
  letter-spacing: 0.5px;
}
.pc-owner {
  font-size: var(--zw-font-size-sm);
  color: var(--zw-text-tertiary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.pc-metrics {
  display: flex;
  gap: var(--zw-space-lg);
  margin-top: var(--zw-space-2xs);
}
.pc-metric-label {
  display: block;
  font-size: var(--zw-font-size-xs);
  color: var(--zw-text-quaternary);
  margin-bottom: var(--zw-space-2xs);
}
.pc-metric-value {
  font-size: var(--zw-font-size-md);
  font-weight: 600;
  color: var(--zw-text-primary);
}
.pc-progress {
  display: flex;
  align-items: center;
  gap: var(--zw-space-sm);
  margin-top: var(--zw-space-2xs);
}
.pc-progress-label {
  font-size: var(--zw-font-size-xs);
  color: var(--zw-text-quaternary);
  white-space: nowrap;
}
.pc-progress-bar {
  flex: 1;
}
.pc-progress-num {
  font-size: var(--zw-font-size-xs);
  color: var(--zw-text-secondary);
  min-width: 34px;
  text-align: right;
  font-variant-numeric: tabular-nums;
}
.pc-enter {
  margin-top: var(--zw-space-2xs);
  font-size: var(--zw-font-size-xs);
  color: var(--zw-brand);
  text-align: right;
}

/* ===== 单项目看板（L2）：横幅 + KPI 带 ===== */
.banner-card {
  margin-bottom: var(--zw-space-md);
}
.banner-row {
  display: flex;
  align-items: center;
  gap: var(--zw-space-md);
}
.banner-back {
  color: var(--zw-text-secondary);
}
.banner-info {
  display: flex;
  align-items: center;
  gap: var(--zw-space-sm);
  flex: 1;
  min-width: 0;
}
.banner-name {
  font-size: var(--zw-font-size-md);
  font-weight: 600;
  color: var(--zw-text-primary);
  white-space: nowrap;
}
.banner-meta {
  font-size: var(--zw-font-size-sm);
  color: var(--zw-text-tertiary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.banner-switch {
  display: flex;
  align-items: center;
  gap: var(--zw-space-sm);
}
.banner-switch-label {
  font-size: var(--zw-font-size-sm);
  color: var(--zw-text-tertiary);
  white-space: nowrap;
}
.kpi-band {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: var(--zw-space-md);
  margin-bottom: var(--zw-space-md);
}
.kpi-card {
  background: var(--zw-bg-card);
  border: 1px solid var(--zw-border-light);
  border-radius: var(--zw-radius-xs);
  padding: var(--zw-space-md);
}
.kpi-label {
  font-size: var(--zw-font-size-xs);
  color: var(--zw-text-quaternary);
  letter-spacing: 0.5px;
}
.kpi-value {
  font-size: var(--zw-font-size-2xl);
  font-weight: 600;
  color: var(--zw-text-primary);
  margin: var(--zw-space-2xs) 0;
  line-height: 1.2;
  font-variant-numeric: tabular-nums;
}
.kpi-sub {
  font-size: var(--zw-font-size-sm);
  color: var(--zw-text-tertiary);
}
.kpi-sub.danger {
  color: var(--zw-danger);
}
.panel-row {
  margin-bottom: var(--zw-space-md);
}
.panel-card {
  height: 100%;
}
.panel-title {
  font-weight: 600;
  color: var(--zw-text-primary);
}
.panel-body {
  min-height: 320px;
}
.chart-box {
  height: 320px;
  width: 100%;
}
</style>
