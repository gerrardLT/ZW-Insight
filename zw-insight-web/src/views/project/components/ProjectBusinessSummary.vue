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
  </section>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { getProjectOverview, getProjectCostControl } from '@/api/dashboard'
import type { ProjectDashboardDTO, ProjectCostControlDTO } from '@/api/dashboard'

const props = defineProps<{ projectId: string }>()
const loading = ref(false)
const data = ref<{ overview: ProjectDashboardDTO | null; cost: ProjectCostControlDTO | null }>({ overview: null, cost: null })
const errors = ref({ overview: '', cost: '' })
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
async function load() {
  const current = ++generation
  const id = props.projectId
  data.value = { overview: null, cost: null }
  errors.value = { overview: '', cost: '' }
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
button { cursor: pointer; }
</style>
