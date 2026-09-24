<template>
  <div class="ma-container">
    <!-- 筛选与操作：项目 + 月份 + 生成/导出 -->
    <el-card shadow="never" class="filter-card">
      <div class="filter-bar">
        <span class="filter-label">项目：</span>
        <ProjectSelector v-model="selectedProjectId" width="280px" @change="onProjectChange" />
        <span class="filter-label">月份：</span>
        <el-date-picker v-model="month" type="month" value-format="YYYY-MM" format="YYYY 年 MM 月"
          placeholder="选择月份" style="width: 150px" :clearable="false" @change="loadData" />
        <el-button type="primary" :icon="Refresh" :loading="loading" @click="loadData">查询</el-button>
        <el-button :loading="generating" :disabled="!selectedProjectId" @click="onGenerate">
          生成/重算本月
        </el-button>
        <el-button :disabled="!rows.length" @click="exportCsv">导出 CSV</el-button>
        <span v-if="generatedAt" class="filter-hint">数据生成于 {{ generatedAt }}</span>
      </div>
    </el-card>

    <el-empty v-if="!selectedProjectId" description="请先选择项目以查看月度经营分析表" :image-size="80" />

    <template v-else>
      <!-- 口径说明（后端下发，必须展示；把「为空」的原因讲清楚，避免被误读为 0） -->
      <el-alert v-for="(note, i) in notes" :key="i" :title="note" type="info" :closable="false"
        show-icon class="note-alert" />

      <el-card shadow="never" class="matrix-card" v-loading="loading">
        <template #header>
          <div class="card-header">
            <span class="card-title">月度经营分析表 · {{ month || '—' }}</span>
            <span class="card-hint">
              资金流转 §9：10 类费用 × 6 列；定时任务每月 1 日 04:00 生成上月，同月重跑为覆盖更新
            </span>
          </div>
        </template>

        <el-table :data="rows" size="small" border show-summary :summary-method="summaryMethod"
          class="matrix-table">
          <el-table-column prop="categoryName" label="类别" width="110" fixed="left">
            <template #default="{ row }">
              <span>{{ row.categoryName }}</span>
              <el-tag v-if="row.categoryCode === 'UNCLASSIFIED'" type="warning" size="small"
                effect="plain" class="cat-tag">需规范子类命名</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="预算" align="right" min-width="120">
            <template #default="{ row }">{{ formatMoney(row.budgetAmount) }}</template>
          </el-table-column>
          <el-table-column align="right" min-width="130">
            <template #header>
              <span>本月发生</span>
              <el-tooltip placement="top"
                content="= 本月末累计发生 − 上月末累计发生（两个时点快照之差）。首次生成无上月行时为空，不用 0 冒充「本月无发生」。">
                <el-icon class="col-help"><QuestionFilled /></el-icon>
              </el-tooltip>
            </template>
            <template #default="{ row }">
              <span :class="{ 'is-muted': row.currentMonthOccurred == null }">
                {{ formatMoney(row.currentMonthOccurred) }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="累计发生" align="right" min-width="120">
            <template #default="{ row }">{{ formatMoney(row.cumulativeOccurred) }}</template>
          </el-table-column>
          <el-table-column align="right" min-width="130">
            <template #header>
              <span>累计支付</span>
              <el-tooltip placement="top"
                content="审批口径：合同 cumulative_paid 由付款申请审批通过时回写（非银行现金口径）。仅直接费四类（人工/材料/机械/分包）有此数据源，间接费为空而非 0。">
                <el-icon class="col-help"><QuestionFilled /></el-icon>
              </el-tooltip>
            </template>
            <template #default="{ row }">
              <span :class="{ 'is-muted': row.cumulativePaid == null }">
                {{ formatMoney(row.cumulativePaid) }}
              </span>
            </template>
          </el-table-column>
          <el-table-column align="right" min-width="130">
            <template #header>
              <span>应付未付</span>
              <el-tooltip placement="top" content="= 累计发生 − 累计支付；累计支付无数据源时同为空。">
                <el-icon class="col-help"><QuestionFilled /></el-icon>
              </el-tooltip>
            </template>
            <template #default="{ row }">
              <span :class="payableClass(row.payableOutstanding)">
                {{ formatMoney(row.payableOutstanding) }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="预计最终" align="right" min-width="120">
            <template #default="{ row }">
              <span :class="forecastClass(row)">{{ formatMoney(row.forecastFinal) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="账户数" align="right" width="90">
            <template #default="{ row }">
              <el-tooltip v-if="!row.accountCount" placement="top"
                content="该类未建 CBS 成本账户，金额为 0 是真实结果（非数据缺失掩盖）；请到「预算 → 成本账户」建立后重算">
                <span class="is-muted">0</span>
              </el-tooltip>
              <span v-else>{{ row.accountCount }}</span>
            </template>
          </el-table-column>
          <template #empty>
            <el-empty :image-size="60"
              description="该月尚未生成分析表，点击「生成/重算本月」按真实 CBS 账户与合同数据生成" />
          </template>
        </el-table>
      </el-card>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, QuestionFilled } from '@element-plus/icons-vue'
import ProjectSelector from '@/components/ProjectSelector.vue'
import {
  getMonthlyAnalysis, generateMonthlyAnalysis,
  type MonthlyAnalysisRow
} from '@/api/monthly-analysis'

const loading = ref(false)
const generating = ref(false)
const selectedProjectId = ref<number>()
const month = ref<string>(currentMonth())

const rows = ref<MonthlyAnalysisRow[]>([])
const notes = ref<string[]>([])
const generatedAt = ref<string | null>(null)
const totals = ref<Record<string, any>>({})

function currentMonth(): string {
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`
}

/** 金额：null/undefined → 「—」（口径缺失，与 0 严格区分）；≥1 万显示万元 */
function formatMoney(value: unknown): string {
  if (value === null || value === undefined || value === '') return '—'
  const n = Number(value)
  if (Number.isNaN(n)) return '—'
  if (Math.abs(n) >= 10000) return `${(n / 10000).toFixed(2)} 万`
  return n.toFixed(2)
}

/** 应付未付 > 0 表示还有钱没付（正常但需关注），标红提示资金压力 */
function payableClass(value: number | null): string {
  if (value == null) return 'is-muted'
  return Number(value) > 0 ? 'is-warning' : ''
}

/** 预计最终 > 预算 → 预计超支，标红（§7.1 口径）。
 *  参数用宽松结构：el-table 插槽 row 为 DefaultRow，声明完整接口会报不可赋值。 */
function forecastClass(row: { forecastFinal?: number | null; budgetAmount?: number | null; accountCount?: number }): string {
  const forecast = Number(row.forecastFinal || 0)
  const budget = Number(row.budgetAmount || 0)
  if (!row.accountCount) return 'is-muted'
  return budget > 0 && forecast > budget ? 'is-danger' : ''
}

/** 合计行：直接取后端 totals（口径由后端保证，前端不再自行相加造成两套算法） */
function summaryMethod({ columns }: { columns: any[] }) {
  const t = totals.value || {}
  return columns.map((col, index) => {
    if (index === 0) return '合计'
    switch (index) {
      case 1: return formatMoney(t.budgetAmount)
      case 2: return t.currentMonthOccurred == null ? '—' : `${formatMoney(t.currentMonthOccurred)}${t.currentMonthComplete ? '' : ' *'}`
      case 3: return formatMoney(t.cumulativeOccurred)
      case 4: return formatMoney(t.cumulativePaid)
      case 5: return formatMoney(t.payableOutstanding)
      case 6: return formatMoney(t.forecastFinal)
      default: return ''
    }
  })
}

async function loadData() {
  if (!selectedProjectId.value) {
    rows.value = []
    notes.value = []
    totals.value = {}
    generatedAt.value = null
    return
  }
  loading.value = true
  try {
    const res: any = await getMonthlyAnalysis({
      projectId: selectedProjectId.value,
      month: month.value || undefined
    })
    const data = res?.data
    rows.value = data?.rows || []
    notes.value = data?.notes || []
    totals.value = data?.totals || {}
    generatedAt.value = data?.generatedAt || null
    // 后端可能回落到已生成的最新月份，以其返回的 month 为准（避免界面显示与实际数据不符）
    if (data?.month) {
      month.value = data.month
    }
  } catch (e: any) {
    rows.value = []
    notes.value = []
    totals.value = {}
    ElMessage.error('加载月度经营分析表失败：' + (e?.message || '接口异常'))
  } finally {
    loading.value = false
  }
}

function onProjectChange() {
  loadData()
}

/**
 * 手工生成/重算：同月重跑为覆盖更新（按唯一键 upsert），
 * 会覆盖既有数值，故先确认再执行（不静默改数据）。
 */
async function onGenerate() {
  if (!selectedProjectId.value || !month.value) return
  try {
    await ElMessageBox.confirm(
      `将按当前 CBS 成本账户与合同数据重新生成 ${month.value} 的经营分析表；同月已有数据会被覆盖更新（不新增重复行）。是否继续？`,
      '生成/重算确认', { type: 'warning', confirmButtonText: '生成', cancelButtonText: '取消' })
  } catch {
    return
  }
  generating.value = true
  try {
    const res: any = await generateMonthlyAnalysis(selectedProjectId.value, month.value)
    const report = res?.data || {}
    const nullCats: string[] = report.occurredNullCategories || []
    ElMessage.success(
      `生成完成：${report.rows ?? 0} 行（新增 ${report.inserted ?? 0} / 更新 ${report.updated ?? 0}）`
      + (nullCats.length ? `；其中 ${nullCats.length} 类无 CBS 账户` : ''))
    await loadData()
  } catch (e: any) {
    ElMessage.error('生成失败：' + (e?.message || '接口异常'))
  } finally {
    generating.value = false
  }
}

/**
 * 导出 CSV（UTF-8 BOM，Excel 可直接打开）。
 * 空值导出为空单元格而非 0 —— 与页面「—」保持同一口径，
 * 避免导出后把「无数据源」误读成「金额为 0」。
 */
function exportCsv() {
  if (!rows.value.length) return
  const header = ['类别', '预算', '本月发生', '累计发生', '累计支付', '应付未付', '预计最终', '账户数', '本月发生口径', '累计支付口径']
  const cell = (v: unknown) => (v === null || v === undefined ? '' : String(v))
  const lines = rows.value.map(r => [
    r.categoryName,
    cell(r.budgetAmount), cell(r.currentMonthOccurred), cell(r.cumulativeOccurred),
    cell(r.cumulativePaid), cell(r.payableOutstanding), cell(r.forecastFinal),
    cell(r.accountCount), cell(r.occurredBasis), cell(r.paidBasis)
  ])
  const t = totals.value || {}
  lines.push([
    '合计', cell(t.budgetAmount), cell(t.currentMonthOccurred), cell(t.cumulativeOccurred),
    cell(t.cumulativePaid), cell(t.payableOutstanding), cell(t.forecastFinal),
    '', '累计支付与应付未付合计仅覆盖直接费四类', ''
  ])
  const csv = '\uFEFF' + [header, ...lines].map(l => l.join(',')).join('\r\n')
  const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `月度经营分析表_${month.value}.csv`
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  URL.revokeObjectURL(url)
}

onMounted(() => {
  loadData()
})
</script>

<style scoped lang="scss">
.ma-container {
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

.note-alert { margin-bottom: var(--zw-space-xs); }

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
  color: var(--zw-text-quaternary);
  font-weight: normal;
}

.matrix-table :deep(.el-table__footer) { font-weight: var(--zw-font-weight-semibold); }

.col-help {
  margin-left: var(--zw-space-xs);
  cursor: help;
  color: var(--el-text-color-placeholder);
}

.cat-tag { margin-left: var(--zw-space-xs); }

.is-muted { color: var(--zw-text-quaternary); }
.is-danger { color: var(--zw-danger); }
.is-warning { color: var(--zw-warning); }
</style>
