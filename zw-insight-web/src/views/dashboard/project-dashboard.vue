<template>
  <div class="project-dashboard-container">
    <!-- 顶部项目选择器 -->
    <el-card shadow="never" class="selector-card">
      <div class="selector-bar">
        <span class="selector-label">选择项目：</span>
        <ProjectSelector
          v-model="selectedProjectId"
          width="320px"
          @change="handleProjectChange"
        />
      </div>
    </el-card>

    <!-- 未选择项目时的引导提示 -->
    <el-empty v-if="!selectedProjectId" description="请先选择一个项目以查看看板数据" />

    <!-- 四宫格看板布局 -->
    <template v-else>
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
              <el-empty v-else-if="!budget.loading && isEmpty(budget.data)" description="暂无数据" :image-size="80" />
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
              <el-empty v-else-if="!progress.loading && isEmpty(progress.data)" description="暂无数据" :image-size="80" />
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
              <el-empty v-else-if="!contract.loading && isEmpty(contract.data)" description="暂无数据" :image-size="80" />
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
              <el-empty v-else-if="!output.loading && isEmpty(output.data)" description="暂无数据" :image-size="80" />
              <div v-show="!output.error && !isEmpty(output.data)" ref="outputChartRef" class="chart-box"></div>
            </div>
          </el-card>
        </el-col>
      </el-row>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, watch, nextTick, onBeforeUnmount } from 'vue'
import * as echarts from 'echarts'
import ProjectSelector from '@/components/ProjectSelector.vue'
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

// 当前选中的项目 ID
const selectedProjectId = ref<number>()

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
  loader: (projectId: number) => Promise<any>,
  projectId: number
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

/** 加载指定项目的全部四个维度数据（各维度并行、独立失败处理） */
function loadDashboard(projectId: number) {
  // 四个维度并行请求，使用 allSettled 语义：单个维度的失败不会中断其他维度
  loadDimension(budget, getProjectBudget, projectId)
  loadDimension(progress, getProjectProgress, projectId)
  loadDimension(contract, getProjectContract, projectId)
  loadDimension(output, getProjectOutput, projectId)
}

/** 切换项目：清除当前数据 → 重新进入加载状态 → 请求新项目数据 */
function handleProjectChange(projectId: number | undefined) {
  selectedProjectId.value = projectId
  resetDimensions()
  if (projectId) {
    loadDashboard(projectId)
  }
}

// ======================== ECharts 图表渲染（task 8.3） ========================
// 四个维度各自持有一个 ECharts 实例，按需 init / setOption / dispose
let budgetChart: echarts.ECharts | null = null
let progressChart: echarts.ECharts | null = null
let contractChart: echarts.ECharts | null = null
let outputChart: echarts.ECharts | null = null

/** 金额保留两位小数 */
function toAmount2(val: number): number {
  return Math.round((val || 0) * 100) / 100
}

/** 金额转万元（保留两位小数） */
function toWan(val: number): number {
  return Math.round(((val || 0) / 10000) * 100) / 100
}

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
  const used = toAmount2(data.usedAmount)
  // 剩余预算不为负：超预算时剩余按 0 处理
  const remaining = toAmount2(Math.max((data.totalBudget || 0) - (data.usedAmount || 0), 0))
  budgetChart.setOption(
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
          itemStyle: { borderRadius: 6, borderColor: '#fff', borderWidth: 2 },
          label: { show: true, formatter: '{b}: {c} 元' },
          data: [
            { name: '已执行金额', value: used, itemStyle: { color: '#f56c6c' } },
            { name: '剩余预算', value: remaining, itemStyle: { color: '#67c23a' } }
          ]
        }
      ]
    },
    true
  )
}

/** 项目进度：环形仪表盘，完成率 0%–100% 整数 */
function renderProgressChart(data: ProgressDTO) {
  progressChart = ensureChart(progressChart, progressChartRef.value)
  if (!progressChart) return
  // 后端 completionRate 为 0~1 的比率（保留4位小数）→ 转百分比整数，显示区间裁剪到 0–100
  const percent = Math.min(Math.max(Math.round((data.completionRate || 0) * 100), 0), 100)
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
            itemStyle: { color: '#409eff' }
          },
          axisLine: { lineStyle: { width: 18 } },
          splitLine: { show: false },
          axisTick: { show: false },
          axisLabel: { show: false },
          data: [{ value: percent }],
          detail: {
            valueAnimation: true,
            formatter: '{value}%',
            fontSize: 28,
            offsetCenter: [0, 0],
            color: '#303133'
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
  contractChart.setOption(
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
          itemStyle: { color: '#409eff' }
        },
        {
          name: '回款金额',
          type: 'bar',
          data: [toWan(data.receivedAmount)],
          itemStyle: { color: '#67c23a' }
        }
      ]
    },
    true
  )
}

/** 月度产值：折线图，X 轴月份，Y 轴产值（万元） */
function renderOutputChart(data: OutputTrendDTO) {
  outputChart = ensureChart(outputChart, outputChartRef.value)
  if (!outputChart) return
  const trend = data.trend || []
  outputChart.setOption(
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
          itemStyle: { color: '#e6a23c' },
          lineStyle: { color: '#e6a23c' }
        }
      ]
    },
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
  padding: 16px;
}
.selector-card {
  margin-bottom: 16px;
}
.selector-bar {
  display: flex;
  align-items: center;
}
.selector-label {
  font-size: 14px;
  color: #606266;
  margin-right: 8px;
}
.panel-row {
  margin-bottom: 16px;
}
.panel-card {
  height: 100%;
}
.panel-title {
  font-weight: 600;
  color: #303133;
}
.panel-body {
  min-height: 320px;
}
.chart-box {
  height: 320px;
  width: 100%;
}
</style>
