<template>
  <div class="fund-center-container">
    <!-- 四卡（§9.1）：账户资金 / 应收未收 / 已批未付 / 90天缺口 -->
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
      <!-- 未来现金流预测（§9.2，月度粒度为后端真实预测口径） -->
      <el-col :xs="24" :lg="14">
        <el-card shadow="never" class="panel-card">
          <template #header>
            <div class="card-header">
              <span>未来资金预测（滚动 6 个月）</span>
              <el-button link type="primary" @click="loadForecast">刷新预测</el-button>
            </div>
          </template>
          <el-alert type="info" :closable="false" show-icon class="panel-tip"
            title="付款侧 = 已批未付申请按付款日落月；收款侧 = 应收台账按到期日落月（无台账时回退月度计划）。每日 01:15 自动刷新。" />
          <el-table :data="forecastData" v-loading="forecastLoading" border size="small">
            <el-table-column prop="forecastMonth" label="月份" width="100" align="center" />
            <el-table-column label="预计回款" align="right">
              <template #default="{ row }">{{ formatWan(row.expectedReceipts) }}</template>
            </el-table-column>
            <el-table-column label="预计付款" align="right">
              <template #default="{ row }">{{ formatWan(row.expectedPayments) }}</template>
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

      <!-- 未来大额支出 TOP（§9.3） -->
      <el-col :xs="24" :lg="10">
        <el-card shadow="never" class="panel-card">
          <template #header>
            <div class="card-header">
              <span>未来 30 天大额支出</span>
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
            <el-table-column prop="count" label="笔数" width="70" align="center" />
          </el-table>
          <el-empty v-if="!topLoading && !topExpenses.length"
            description="该窗口内无已批未付的付款申请" :image-size="60" />
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
  type FundRollingForecast, type FutureExpenseRow
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

/**
 * 四卡数据源均为真实端点：
 * 账户资金 = 各账户最新余额快照合计；应收未收 = 台账 OPEN 余额；
 * 已批未付 = 付款申请 APPROVED 且 payStatus=UNPAID（按科目聚合求和，窗口 365 天覆盖存量）；
 * 90天缺口 = 公司级滚动预测未来 3 个月正缺口合计。
 */
const fundCards = computed(() => {
  const o = overview.value
  const unpaidTotal = unpaidApproved.value
  const gap = Number(o?.gap90Days) || 0
  return [
    {
      label: '账户资金', value: formatWan(o?.accountBalance), sub: '各账户最新余额快照合计',
      tooltip: '来源为网银对账单手工登记的余额快照（与资金日报头寸同源），非流水推导',
      valueClass: '', alert: false
    },
    {
      label: '应收未收', value: formatWan(aging.value.totalOpen),
      sub: `其中逾期 ${formatWan(aging.value.totalOverdue)}`,
      tooltip: '应收台账 OPEN 余额合计（结算审批生成，回款 FIFO 核销）',
      valueClass: '', alert: Number(aging.value.totalOverdue) > 0
    },
    {
      label: '已批未付', value: formatWan(unpaidTotal), sub: '审批通过但银行未支付',
      tooltip: '付款申请 status=APPROVED 且 pay_status≠PAID 合计；支付态由银行流水勾稽或手工标记产生',
      valueClass: '', alert: false
    },
    {
      label: '90天资金缺口', value: formatWan(gap),
      sub: gap > 0 ? '🔴 需安排资金' : '🟢 无缺口',
      tooltip: '未来 3 个月滚动预测的正净缺口合计（盈余月不抵消缺口月）',
      valueClass: gap > 0 ? 'value-danger' : 'value-success', alert: gap > 0
    }
  ]
})

/** 已批未付合计：用未来大额支出聚合（同源 APPROVED+UNPAID 口径）求和，窗口取 365 天覆盖存量 */
const unpaidApproved = ref(0)

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

/** 已批未付合计（365 天窗口，覆盖存量未付申请） */
async function loadUnpaidTotal() {
  try {
    const res: any = await getFutureExpenseTop({ days: 365 })
    const rows: FutureExpenseRow[] = res.data || []
    unpaidApproved.value = rows.reduce((sum, r) => sum + (Number(r.amount) || 0), 0)
  } catch (e: any) {
    unpaidApproved.value = 0
    ElMessage.error('加载已批未付合计失败：' + (e?.message || '接口异常'))
  }
}

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
  loadUnpaidTotal()
  loadAging()
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
