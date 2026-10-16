<template>
  <section aria-label="项目经营摘要" :aria-busy="loading">
    <h2>经营摘要</h2>
    <p>金额单位：元。回款取施工合同累计回款；CBS 实际成本不等于审批支出或银行现金支付。</p>
    <p v-if="loading" role="status">正在加载经营摘要…</p>
    <template v-else>
      <div v-for="panel in panels" :key="panel.key" class="summary-panel">
        <h3>{{ panel.label }}</h3>
        <div v-if="errors[panel.key]" role="alert">
          {{ errors[panel.key] }}
          <button type="button" @click="load">重试经营摘要</button>
        </div>
        <p v-else-if="!data[panel.key]" role="status">暂无{{ panel.label }}数据</p>
        <p v-else-if="panel.key === 'cost' && !hasAccounts" role="status">尚未编制 CBS 成本账户，成本指标不可用。</p>
        <dl v-else class="summary-grid">
          <div v-for="metric in panel.metrics" :key="metric.label">
            <dt>{{ metric.label }}</dt>
            <dd>{{ amount(metric.value()) }}</dd>
          </div>
        </dl>
      </div>
    </template>
    <p>缺失金额显示“—”，不按零处理；此处不推算现金余额或预计利润。</p>
    <template v-if="ratios">
      <div class="summary-panel">
        <h3>回款与毛利（口径带分子分母，前端不自算）</h3>
        <dl class="summary-grid">
          <div v-for="m in ratioRows" :key="m.label">
            <dt>{{ m.label }}</dt>
            <dd>
              <template v-if="m.metric?.available && m.metric.value != null">{{ m.text }}</template>
              <template v-else>
                <span class="unavailable">不可算</span>
                <small v-if="m.metric?.unavailableReason === 'CBS_UNAVAILABLE'">未建 CBS 成本账户</small>
                <small v-else-if="m.metric?.unavailableReason === 'DENOMINATOR_ZERO'">分母为零</small>
              </template>
            </dd>
          </div>
        </dl>
        <div v-if="ratios.laborCostCompare" class="labor-compare">
          人工对照（只读）：工资单+点工已批 {{ amount(ratios.laborCostCompare.wageLikeTotal) }} vs 劳务累计结算
          {{ amount(ratios.laborCostCompare.laborSettlementTotal) }}；工资性支出未入 CBS 权责成本（会计政策待定），不写成本账户。
        </div>
      </div>
    </template>
  </section>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { getProjectOverview, getProjectCostControl, getProjectReceiptRatios } from '@/api/dashboard'
import type { ProjectDashboardDTO, ProjectCostControlDTO, ReceiptRatiosDTO, ReceiptRatioMetric } from '@/api/dashboard'

const props = defineProps<{ projectId: string }>()
const loading = ref(false)
const data = ref<{ overview: ProjectDashboardDTO | null; cost: ProjectCostControlDTO | null }>({ overview: null, cost: null })
const errors = ref({ overview: '', cost: '' })
const ratios = ref<ReceiptRatiosDTO | null>(null)
const hasAccounts = computed(() => Number(data.value.cost?.accountCount) > 0)
let generation = 0
const panels = [
  { key: 'overview' as const, label: '合同与产值', metrics: [
    { label: '施工合同总额', value: () => data.value.overview?.contract?.contractTotal },
    { label: '累计开票', value: () => data.value.overview?.contract?.invoicedAmount },
    { label: '累计回款', value: () => data.value.overview?.contract?.receivedAmount },
    { label: '累计上报产值', value: () => data.value.overview?.output?.totalOutput },
  ] },
  { key: 'cost' as const, label: 'CBS 成本', metrics: [
    { label: '当前预算', value: () => data.value.cost?.totals?.currentTotal },
    { label: '已承诺成本', value: () => data.value.cost?.totals?.commitmentTotal },
    { label: '实际成本', value: () => data.value.cost?.totals?.actualTotal },
    { label: '预测完工成本（EAC）', value: () => data.value.cost?.totals?.forecastTotal },
  ] },
]
function amount(value: unknown) {
  return typeof value === 'number' && Number.isFinite(value)
    ? value.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 }) : '—'
}

// C5 回款率四口径 + 双毛利（value 已由后端算好，前端仅展示）
const ratioRows = computed(() => {
  const r = ratios.value
  if (!r) return []
  const pct = (m: ReceiptRatioMetric) =>
    m.available && m.value != null ? `${(Number(m.value) * 100).toFixed(2)}%` : ''
  const amt = (m?: ReceiptRatioMetric) =>
    m && typeof m.numerator === 'number' ? amount(m.numerator) : '—'
  return [
    { label: '回款率 /合同额', metric: r.collectionRateByContract, text: pct(r.collectionRateByContract) },
    { label: '回款率 /累计产值', metric: r.collectionRateByOutput, text: pct(r.collectionRateByOutput) },
    { label: '回款率 /累计开票', metric: r.collectionRateByInvoice, text: pct(r.collectionRateByInvoice) },
    { label: '回款率 /累计结算', metric: r.collectionRateBySettlement, text: pct(r.collectionRateBySettlement) },
    { label: '产值毛利（产值−CBS实际）', metric: r.outputGrossMargin, text: amt(r.outputGrossMargin) },
    { label: '结算毛利（结算−CBS实际）', metric: r.settlementGrossMargin, text: amt(r.settlementGrossMargin) },
  ]
})
async function load() {
  const current = ++generation
  const id = props.projectId
  data.value = { overview: null, cost: null }
  errors.value = { overview: '', cost: '' }
  ratios.value = null
  loading.value = false
  if (!/^\d+$/.test(id)) return
  loading.value = true
  await Promise.all((['overview', 'cost'] as const).map(async (key) => {
    try {
      const response: any = await (key === 'overview' ? getProjectOverview(id) : getProjectCostControl(id))
      if (current !== generation) return
      if (response.code !== 200) throw new Error('请求失败')
      if (key === 'overview') data.value.overview = response.data
      else {
        if (response.data && String(response.data.projectId) !== id) throw new Error('项目范围不匹配')
        data.value.cost = response.data
      }
    } catch {
      if (current === generation) errors.value[key] = `${key === 'overview' ? '合同与产值' : 'CBS 成本'}加载失败，请重试。`
    }
  }))
  // C5 回款与毛利（独立失败不影响既有两组）
  try {
    const response: any = await getProjectReceiptRatios(id)
    if (current === generation && response.code === 200) ratios.value = response.data
  } catch {
    if (current === generation) ratios.value = null
  }
  if (current === generation) loading.value = false
}
// All state and loaders exist before the immediate watcher runs.
watch(() => props.projectId, load, { immediate: true })
</script>

<style scoped>
section { margin-bottom: var(--zw-space-lg); }
h2 { font-size: var(--zw-font-size-lg); }
h3 { font-size: var(--zw-font-size-md); }
p { color: var(--el-text-color-secondary); }
.summary-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(160px, 1fr)); gap: var(--zw-space-md); }
dd { margin: var(--zw-space-xs) 0; font-variant-numeric: tabular-nums; font-size: var(--zw-font-size-xl); }
.summary-panel { margin-top: var(--zw-space-md); }
.unavailable { color: var(--el-text-color-secondary); font-size: var(--zw-font-size-lg); }
.labor-compare { color: var(--el-text-color-secondary); font-size: var(--zw-font-size-sm); margin-top: var(--zw-space-sm); }
button { cursor: pointer; }
</style>
