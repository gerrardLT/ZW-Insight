<template>
  <div class="fund-center-container">
    <!-- 四卡（§9.1）：账户资金 / 应收未收 / 应付未付 / 90天缺口
         2026-09-24 修正：第三卡原误用「已批未付」顶替文档要求的「应付未付」（口径被换窄，
         漏掉已结算但尚未提交付款申请的义务），现已改为应付未付，已批未付降为 sub 行 -->
    <div v-loading="cardLoading" class="fund-grid">
      <el-card v-for="card in fundCards" :key="card.label" shadow="never" class="fund-card"
        :class="{ 'fund-alert': card.alert }">
        <div class="fund-label">
          <span>{{ card.label }}</span>
          <el-tooltip :content="card.tooltip" placement="top">
            <el-icon class="fund-help"><QuestionFilled /></el-icon>
          </el-tooltip>
        </div>
        <div class="fund-value" :class="card.valueClass">{{ card.value }}</div>
        <div class="fund-sub">{{ card.sub }}</div>
      </el-card>
    </div>

    <el-row :gutter="16">
      <!-- 未来现金流预测：本表为**月度粒度**滚动 6 个月。
           ⚠ 与 §9.2 的差距如实标注：§9.2 要求的是「未来90天资金预测」的 30/60/90 天三档，
           尚未实现（属已批准的 P1-A 期）；不得声称本表已对齐 §9.2。 -->
      <el-col :xs="24" :lg="14">
        <el-card shadow="never" class="panel-card">
          <template #header>
            <div class="card-header">
              <span>未来资金预测（滚动 6 个月）</span>
              <el-button link type="primary" @click="loadForecast">刷新预测</el-button>
            </div>
          </template>
          <el-alert type="info" :closable="false" show-icon class="panel-tip"
            title="付款侧 = 已批未付申请按付款日落月，其中已逾期未付全额计入当月（“其中逾期”列，属构成项不另计）；收款侧 = 应收台账按到期日落月（无台账时回退月度计划）。每日 01:15 自动刷新。" />
          <el-table :data="forecastData" v-loading="forecastLoading" border size="small">
            <el-table-column prop="forecastMonth" label="月份" width="100" align="center" />
            <el-table-column label="预计回款" align="right">
              <template #default="{ row }">{{ formatWan(row.expectedReceipts) }}</template>
            </el-table-column>
            <el-table-column label="预计付款" align="right">
              <template #default="{ row }">{{ formatWan(row.expectedPayments) }}</template>
            </el-table-column>
            <!-- 构成列：已含在「预计付款」内，标“其中”避免误读为需叠加（V2026_63） -->
            <el-table-column label="其中逾期" align="right" width="110">
              <template #default="{ row }">
                <span :class="Number(row.overdueUnpaid) > 0 ? 'gap-negative' : ''">
                  {{ formatWan(row.overdueUnpaid) }}
                </span>
              </template>
            </el-table-column>
            <el-table-column label="资金差额" align="right">
              <template #default="{ row }">
                <span :class="Number(row.netGap) > 0 ? 'gap-negative' : 'gap-positive'">
                  {{ formatWan(row.netGap) }}
                </span>
              </template>
            </el-table-column>
            <el-table-column label="风险" width="80" align="center">
              <template #default="{ row }">
                <el-tag :type="riskTag(row.riskLevel)" size="small">{{ riskLabel(row.riskLevel) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="snapshotDate" label="快照日" width="110" align="center" />
          </el-table>
          <el-empty v-if="!forecastLoading && !forecastData.length"
            description="暂无预测快照（每日 01:15 自动生成，或到资金计划页手动触发）" :image-size="60" />
        </el-card>
      </el-col>

      <!-- 待支付大额支出 TOP（§9.3；V2026_63 含已逾期） -->
      <el-col :xs="24" :lg="10">
        <el-card shadow="never" class="panel-card">
          <template #header>
            <div class="card-header">
              <span>待支付大额支出（含逾期）</span>
              <el-select v-model="topDays" size="small" style="width: 100px" @change="loadTopExpenses">
                <el-option label="30 天" :value="30" />
                <el-option label="60 天" :value="60" />
                <el-option label="90 天" :value="90" />
              </el-select>
            </div>
          </template>
          <el-table :data="topExpenses" v-loading="topLoading" border size="small">
            <el-table-column prop="categoryName" label="支出科目" min-width="140" show-overflow-tooltip />
            <el-table-column label="金额" align="right">
              <template #default="{ row }">
                <span class="amount-strong">{{ formatWan(row.amount) }}</span>
              </template>
            </el-table-column>
            <!-- 构成列：已含在「金额」内；原口径下界为今天会漏掉逾期款，导致本表恒为空 -->
            <el-table-column label="其中逾期" align="right" width="110">
              <template #default="{ row }">
                <span :class="Number(row.overdueAmount) > 0 ? 'gap-negative' : ''">
                  {{ formatWan(row.overdueAmount) }}
                </span>
              </template>
            </el-table-column>
            <el-table-column prop="count" label="笔数" width="70" align="center" />
          </el-table>
          <el-empty v-if="!topLoading && !topExpenses.length"
            description="无已批未付的付款申请（含已逾期）" :image-size="60" />
        </el-card>
      </el-col>
    </el-row>

    <!-- 未来 90 天资金预测（§9.2 严格版式：30/60/90 天三档累计窗口）+ 缺口归因 -->
    <el-row :gutter="16">
      <el-col :xs="24" :lg="12">
        <el-card shadow="never" class="panel-card">
          <template #header>
            <div class="card-header">
              <span>未来 90 天资金预测</span>
              <el-tag type="info" size="small">累计窗口（§9.2）</el-tag>
            </div>
          </template>
          <el-alert type="info" :closable="false" show-icon class="panel-tip"
            title="资金差额 = 预计回款 − 预计付款（§9.2 口径，负数=净流出）；资金缺口 = 预计付款 − 可用资金（§10.4 口径，正数=缺钱）。两者口径不同，不可混用。" />
          <el-table :data="dayForecastRows" v-loading="dayLoading" border size="small">
            <el-table-column prop="label" label="指标" width="130" />
            <el-table-column v-for="d in dayWindows" :key="d" :label="`${d} 天`" align="right">
              <template #default="{ row }">
                <span :class="row.cls(d)">{{ row.val(d) }}</span>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>

      <el-col :xs="24" :lg="12">
        <el-card shadow="never" class="panel-card">
          <template #header>
            <div class="card-header">
              <span>缺口归因：哪个项目 / 主要付款对象</span>
              <el-select v-model="attrDays" size="small" style="width: 100px" @change="loadAttribution">
                <el-option label="30 天" :value="30" />
                <el-option label="60 天" :value="60" />
                <el-option label="90 天" :value="90" />
              </el-select>
            </div>
          </template>
          <!-- §9.2 要求老板能看到“哪个项目导致缺口 + 主要付款对象”，旧实现完全无归因 -->
          <div class="attr-title">按项目（净缺口降序）</div>
          <el-table :data="attribution?.byProject || []" v-loading="attrLoading" border size="small">
            <el-table-column label="项目" min-width="140" show-overflow-tooltip>
              <template #default="{ row }">{{ row.projectName }}</template>
            </el-table-column>
            <el-table-column label="预计付款" align="right">
              <template #default="{ row }">{{ formatWan(row.expectedPayments) }}</template>
            </el-table-column>
            <el-table-column label="预计回款" align="right">
              <template #default="{ row }">{{ formatWan(row.expectedReceipts) }}</template>
            </el-table-column>
            <el-table-column label="净缺口" align="right">
              <template #default="{ row }">
                <span :class="Number(row.netGap) > 0 ? 'gap-negative' : 'gap-positive'">{{ formatWan(row.netGap) }}</span>
              </template>
            </el-table-column>
          </el-table>
          <div class="attr-title">主要付款对象（待付金额降序）</div>
          <el-table :data="attribution?.byPayee || []" v-loading="attrLoading" border size="small">
            <el-table-column label="收款方" min-width="160" show-overflow-tooltip>
              <template #default="{ row }">{{ row.supplierName }}</template>
            </el-table-column>
            <el-table-column label="待付金额" align="right">
              <template #default="{ row }">{{ formatWan(row.amount) }}</template>
            </el-table-column>
            <el-table-column prop="count" label="笔数" width="70" align="center" />
          </el-table>
          <el-empty v-if="!attrLoading && !attribution?.byPayee?.length"
            description="窗口内无待付款单据" :image-size="60" />
        </el-card>
      </el-col>
    </el-row>

    <!-- 应收账龄（§10 回款风险：逾期项目排前） -->
    <el-card shadow="never" class="panel-card">
      <template #header>
        <div class="card-header">
          <span>应收账款风险（账龄）</span>
          <div>
            <el-tag type="info" size="small">未结清 {{ formatWan(aging.totalOpen) }}</el-tag>
            <el-tag :type="Number(aging.totalOverdue) > 0 ? 'danger' : 'success'" size="small" class="tag-gap">
              逾期 {{ formatWan(aging.totalOverdue) }}
            </el-tag>
            <el-button link type="primary" @click="$router.push('/finance/receivable')">
              应收台账<el-icon><IconArrowRight /></el-icon>
            </el-button>
          </div>
        </div>
      </template>
      <el-table :data="aging.projects" v-loading="agingLoading" border size="small">
        <el-table-column label="项目" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">{{ row.projectName || `项目#${row.projectId}` }}</template>
        </el-table-column>
        <el-table-column label="应收" align="right">
          <template #default="{ row }">{{ formatWan(row.openBalance) }}</template>
        </el-table-column>
        <el-table-column label="逾期" align="right">
          <template #default="{ row }">
            <span :class="{ 'gap-negative': Number(row.overdueBalance) > 0 }">{{ formatWan(row.overdueBalance) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="逾期天数" width="100" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.maxOverdueDays > 0" :type="row.maxOverdueDays > 90 ? 'danger' : 'warning'" size="small">
              {{ row.maxOverdueDays }} 天
            </el-tag>
            <span v-else>—</span>
          </template>
        </el-table-column>
        <el-table-column v-for="b in bucketDefs" :key="b.key" :label="b.label" align="right">
          <template #default="{ row }">{{ formatWan(row.buckets?.[b.key]) }}</template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!agingLoading && !aging.projects.length"
        description="暂无未结清应收（结算审批通过后自动生成台账）" :image-size="60" />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { QuestionFilled } from '@element-plus/icons-vue'
import { IconArrowRight } from '@tabler/icons-vue'
import { getCockpitOverview, type CockpitOverview } from '@/api/cockpit'
import {
  getRollingForecastPage, getFutureExpenseTop, generateRollingForecast,
  getForecastByDays, getGapAttribution,
  type FundRollingForecast, type FutureExpenseRow, type DayForecast, type GapAttribution
} from '@/api/fund-plan'
import { getReceivableAging, type ReceivableAging, type AgingBucket } from '@/api/receivable'
import { formatWan } from '@/utils/chart-format'

const bucketDefs: { key: AgingBucket; label: string }[] = [
  { key: 'NOT_DUE', label: '未到期' },
  { key: 'D0_30', label: '0-30天' },
  { key: 'D31_60', label: '31-60天' },
  { key: 'D61_90', label: '61-90天' },
  { key: 'OVER_90', label: '90天+' }
]

const cardLoading = ref(false)
const forecastLoading = ref(false)
const topLoading = ref(false)
const agingLoading = ref(false)

const overview = ref<CockpitOverview | null>(null)
const forecastData = ref<FundRollingForecast[]>([])
const topExpenses = ref<FutureExpenseRow[]>([])
const topDays = ref(30)
const aging = ref<ReceivableAging>({ totalOpen: 0, totalOverdue: 0, projects: [] })

// ==================== §9.2 未来 90 天预测（30/60/90 三档累计窗口）====================
const dayWindows = [30, 60, 90]
const dayForecasts = ref<Record<number, DayForecast | null>>({})
const dayLoading = ref(false)
const attrDays = ref(90)
const attribution = ref<GapAttribution | null>(null)
const attrLoading = ref(false)

/**
 * §9.2 表格为「行=指标、列=30/60/90 天」的转置版式（数值随窗口递增即累计口径）。
 * 同时呈现两套口径：资金差额 netFlow（§9.2）与资金缺口 gap（§10.4），避免用户混用。
 */
const dayForecastRows = computed(() => {
  const get = (d: number) => dayForecasts.value[d] || null
  const num = (d: number, key: keyof DayForecast) => Number(get(d)?.[key] ?? 0)
  return [
    { label: '预计回款', val: (d: number) => formatWan(get(d)?.expectedReceipts), cls: () => '' },
    { label: '预计付款', val: (d: number) => formatWan(get(d)?.expectedPayments), cls: () => '' },
    {
      label: '　其中逾期',
      val: (d: number) => formatWan(get(d)?.overdueUnpaid),
      cls: (d: number) => (num(d, 'overdueUnpaid') > 0 ? 'gap-negative' : '')
    },
    {
      label: '资金差额',
      val: (d: number) => formatWan(get(d)?.netFlow),
      cls: (d: number) => (num(d, 'netFlow') < 0 ? 'gap-negative' : 'gap-positive')
    },
    { label: '可用资金', val: (d: number) => formatWan(get(d)?.availableFund), cls: () => '' },
    {
      label: '资金缺口',
      val: (d: number) => formatWan(get(d)?.gap),
      cls: (d: number) => (num(d, 'gap') > 0 ? 'gap-negative' : 'gap-positive')
    },
    {
      label: '覆盖状态',
      val: (d: number) => {
        const f = get(d)
        if (!f) return '—'
        if (num(d, 'gap') <= 0) return '🟢 无缺口'
        return f.coverable ? '🟡 可覆盖' : '🔴 需筹资'
      },
      cls: (d: number) => (num(d, 'gap') > 0 && !get(d)?.coverable ? 'gap-negative' : '')
    }
  ]
})

async function loadDayForecasts() {
  dayLoading.value = true
  try {
    const results = await Promise.all(dayWindows.map(d => getForecastByDays({ days: d })))
    const map: Record<number, DayForecast | null> = {}
    results.forEach((r: any, i) => {
      map[dayWindows[i]] = r?.data || null
    })
    dayForecasts.value = map
  } catch (e: any) {
    dayForecasts.value = {}
    ElMessage.error('加载 90 天资金预测失败：' + (e?.message || '接口异常'))
  } finally {
    dayLoading.value = false
  }
}

async function loadAttribution() {
  attrLoading.value = true
  try {
    const res: any = await getGapAttribution({ days: attrDays.value, topN: 8 })
    attribution.value = res?.data || null
  } catch (e: any) {
    attribution.value = null
    ElMessage.error('加载缺口归因失败：' + (e?.message || '接口异常'))
  } finally {
    attrLoading.value = false
  }
}

/**
 * 四卡数据源均为真实端点：
 * 账户资金 = 各账户最新余额快照合计；应收未收 = 台账 OPEN 余额；
 * 已批未付 = 付款申请 APPROVED 且 payStatus=UNPAID（按科目聚合求和，窗口 365 天覆盖存量）；
 * 90天缺口 = 公司级滚动预测未来 3 个月正缺口合计。
 */
const fundCards = computed(() => {
  const o = overview.value
  const gap = Number(o?.gap90Days) || 0
  const detail = o?.gap90DaysDetail
  const balance = Number(o?.accountBalance) || 0
  return [
    {
      label: '账户资金', value: formatWan(o?.accountBalance),
      // 余额未登记时必须如实告知：否则「可用资金」会静默地只剩预计回款，用户无从得知
      sub: balance > 0 ? '各账户最新余额快照合计' : '⚠ 未登记余额快照（可用资金仅含预计回款）',
      tooltip: '来源为网银对账单手工登记的余额快照（biz_bank_balance，与资金日报头寸同源），非流水推导；未登记时为 0，不伪造估算值',
      valueClass: '', alert: balance <= 0
    },
    {
      label: '应收未收', value: formatWan(aging.value.totalOpen),
      sub: `其中逾期 ${formatWan(aging.value.totalOverdue)}`,
      tooltip: '应收台账 OPEN 余额合计（结算审批生成，回款 FIFO 核销）',
      valueClass: '', alert: Number(aging.value.totalOverdue) > 0
    },
    {
      // UI §9.1 第三卡为「应付未付」（已确认付款义务），不是「已批未付」；
      // 后者降为 sub 行呈现（两者语义不同，已批未付仅是应付未付的子集）
      label: '应付未付', value: formatWan(o?.payableOutstanding),
      sub: `其中已进入付款流程 ${formatWan(o?.approvedUnpaid)}`,
      tooltip: '应付未付 = Σ支出合同（累计结算 − 累计已付），含尚未提交付款申请的义务；'
        + '「已进入付款流程」= 已审批未付申请的剩余未付额（部分支付只计未付部分）',
      valueClass: '', alert: Number(o?.payableOutstanding) > 0
    },
    {
      label: '90天资金缺口', value: formatWan(gap),
      sub: gap > 0
        ? `🔴 需安排资金（可用资金 ${formatWan(o?.availableFund)}）`
        : `🟢 可用资金 ${formatWan(o?.availableFund)} 可覆盖`,
      tooltip: '资金流转 §10.4：缺口 = 未来 3 个月预计支付 − 可用资金（正数=缺钱）。'
        + '可用资金 = 账户余额快照 + 窗口内预计回款。'
        + (detail
          ? `当前构成：预计支付 ${formatWan(detail.expectedPayments)}、预计回款 ${formatWan(detail.expectedReceipts)}、账户余额 ${formatWan(detail.accountBalance)}`
          : ''),
      valueClass: gap > 0 ? 'value-danger' : 'value-success', alert: gap > 0
    }
  ]
})

/** 风险级别文案 */
function riskLabel(level: string) {
  return { LOW: '低', MEDIUM: '中', HIGH: '高' }[level] || level
}

function riskTag(level: string): 'success' | 'warning' | 'danger' | 'info' {
  const map: Record<string, 'success' | 'warning' | 'danger'> = {
    LOW: 'success', MEDIUM: 'warning', HIGH: 'danger'
  }
  return map[level] || 'info'
}

async function loadCards() {
  cardLoading.value = true
  try {
    const res: any = await getCockpitOverview()
    overview.value = res.data || null
  } catch (e: any) {
    ElMessage.error('加载资金总览失败：' + (e?.message || '接口异常'))
  } finally {
    cardLoading.value = false
  }
}

async function loadForecast() {
  forecastLoading.value = true
  try {
    const res: any = await getRollingForecastPage({ page: 1, size: 6 })
    const records: FundRollingForecast[] = res.data?.records || []
    // 无快照时主动触发生成一次（真实计算，非伪造）；仍为空则展示空态
    if (!records.length) {
      await generateRollingForecast(undefined, 6)
      const retry: any = await getRollingForecastPage({ page: 1, size: 6 })
      forecastData.value = [...(retry.data?.records || [])].reverse()
    } else {
      forecastData.value = [...records].reverse()
    }
  } catch (e: any) {
    forecastData.value = []
    ElMessage.error('加载资金预测失败：' + (e?.message || '接口异常'))
  } finally {
    forecastLoading.value = false
  }
}

async function loadTopExpenses() {
  topLoading.value = true
  try {
    const res: any = await getFutureExpenseTop({ days: topDays.value })
    topExpenses.value = res.data || []
  } catch (e: any) {
    topExpenses.value = []
    ElMessage.error('加载大额支出失败：' + (e?.message || '接口异常'))
  } finally {
    topLoading.value = false
  }
}

// 已批未付不再用「365 天窗口求和」的前端 workaround（会漏掉更早的逾期单据），
// 改由后端 overview.approvedUnpaid 权威给出（按剩余未付额聚合，无窗口限制）。

async function loadAging() {
  agingLoading.value = true
  try {
    const res: any = await getReceivableAging()
    aging.value = res.data || { totalOpen: 0, totalOverdue: 0, projects: [] }
  } catch (e: any) {
    aging.value = { totalOpen: 0, totalOverdue: 0, projects: [] }
    ElMessage.error('加载应收账龄失败：' + (e?.message || '接口异常'))
  } finally {
    agingLoading.value = false
  }
}

onMounted(() => {
  loadCards()
  loadForecast()
  loadTopExpenses()
  loadAging()
  loadDayForecasts()
  loadAttribution()
})
</script>

<style scoped>
.fund-center-container {
  padding: var(--zw-content-padding);
  display: flex;
  flex-direction: column;
  gap: var(--zw-space-md);
}
.fund-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: var(--zw-space-sm-md);
}
@media (max-width: 1100px) {
  .fund-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}
@media (max-width: 640px) {
  .fund-grid { grid-template-columns: 1fr; }
}
.fund-card { border-left: 3px solid var(--el-color-primary); }
.fund-card.fund-alert {
  border-left-color: var(--el-color-danger);
  background-color: var(--el-color-danger-light-9);
}
.fund-label {
  display: flex;
  align-items: center;
  gap: var(--zw-space-xs);
  font-size: var(--zw-font-size-sm);
  color: var(--el-text-color-secondary);
}
.fund-help { cursor: help; }
.fund-value {
  margin-top: var(--zw-space-xs);
  font-size: var(--zw-font-size-xl);
  font-weight: var(--zw-font-weight-semibold);
  font-variant-numeric: tabular-nums;
}
.fund-sub {
  margin-top: var(--zw-space-xs);
  font-size: var(--zw-font-size-xs);
  color: var(--el-text-color-secondary);
}
.value-danger { color: var(--el-color-danger); }
.value-success { color: var(--el-color-success); }
.panel-card { height: 100%; }
.panel-tip { margin-bottom: var(--zw-space-sm-md); }
.attr-title {
  margin: var(--zw-space-sm) 0 var(--zw-space-xs);
  font-size: var(--zw-font-size-sm);
  font-weight: 600;
  color: var(--el-text-color-secondary);
}
.attr-title:first-of-type { margin-top: 0; }
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--zw-space-sm);
  flex-wrap: wrap;
}
.gap-negative {
  color: var(--el-color-danger);
  font-weight: var(--zw-font-weight-semibold);
}
.gap-positive { color: var(--el-color-success); }
.amount-strong {
  font-variant-numeric: tabular-nums;
  font-weight: var(--zw-font-weight-semibold);
}
.tag-gap { margin: 0 var(--zw-space-sm); }
</style>
