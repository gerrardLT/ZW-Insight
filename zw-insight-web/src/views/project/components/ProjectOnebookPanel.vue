<template>
  <section aria-label="项目一本账" :aria-busy="loading">
    <p class="onebook-note">
      金额单位：元。六条线实时聚合自各单据表，不落库；缺失数据显示「—」，
      预计利润在未建 CBS 成本账户时不可算（不退化冒充）。
    </p>
    <p v-if="loading" role="status">正在加载一本账…</p>
    <div v-else-if="loadError" role="alert">
      {{ loadError }}
      <button type="button" class="onebook-retry" @click="load">重试</button>
    </div>
    <template v-else-if="book">
      <!-- ① 收入线 -->
      <h3 class="onebook-title">① 收入</h3>
      <dl class="onebook-grid">
        <div><dt>合同额</dt><dd>{{ money(book.income.contractAmount) }}</dd></div>
        <div><dt>累计签证</dt><dd>{{ money(book.income.visaAmount) }}</dd></div>
        <div><dt>累计产值</dt><dd>{{ money(book.income.outputAmount) }}</dd></div>
        <div><dt>累计结算</dt><dd>{{ money(book.income.settlementAmount) }}</dd></div>
        <div><dt>累计开票</dt><dd>{{ money(book.income.invoicedAmount) }}</dd></div>
        <div><dt>累计回款</dt><dd>{{ money(book.income.receivedAmount) }}</dd></div>
        <div class="onebook-strong"><dt>未收（合同+签证−回款）</dt><dd>{{ money(book.income.unreceived) }}</dd></div>
        <div><dt>应收台账未结</dt><dd>{{ money(book.income.receivableOpen) }}</dd></div>
      </dl>

      <!-- ② 支出线 -->
      <h3 class="onebook-title">② 支出</h3>
      <table class="onebook-table">
        <thead>
          <tr><th>类别</th><th>合同额</th><th>累计结算</th><th>已付</th><th>应付未付</th><th>已收票</th></tr>
        </thead>
        <tbody>
          <tr v-for="c in book.expense.categories" :key="c.category">
            <td><span class="cat-dot" :style="{ background: catColor(c.category) }" />{{ categoryLabel(c.category) }}</td>
            <td class="num">{{ money(c.contractAmount) }}</td>
            <td class="num">{{ money(c.settled) }}</td>
            <td class="num">{{ money(c.paid) }}</td>
            <td class="num">{{ money(c.outstanding) }}</td>
            <td class="num">{{ money(c.invoiceReceived) }}</td>
          </tr>
          <tr v-if="!book.expense.categories.length"><td colspan="6" class="empty">暂无支出合同</td></tr>
        </tbody>
      </table>
      <dl class="onebook-grid">
        <div class="onebook-strong"><dt>应付未付合计</dt><dd>{{ money(book.expense.totalOutstanding) }}</dd></div>
        <div><dt>在途付款（审批中）</dt><dd>{{ money(book.expense.applying) }}</dd></div>
        <div><dt>已批报销</dt><dd>{{ money(book.expense.reimbursementApproved) }}</dd></div>
        <div><dt>备用金未还</dt><dd>{{ money(book.expense.reserveOutstanding) }}</dd></div>
        <div><dt>工资未付</dt><dd>{{ money(book.expense.wageUnpaid) }}</dd></div>
        <div><dt>其他支出</dt><dd>{{ money(book.expense.otherPayment) }}</dd></div>
      </dl>

      <!-- ③ 成本线 -->
      <h3 class="onebook-title">③ 成本（CBS 三算）</h3>
      <dl class="onebook-grid">
        <div><dt>目标成本（原始）</dt><dd>{{ money(book.cost.baseline) }}</dd></div>
        <div><dt>当前预算（含变更）</dt><dd>{{ money(book.cost.currentBudget) }}</dd></div>
        <div><dt>已承诺</dt><dd>{{ money(book.cost.commitment) }}</dd></div>
        <div><dt>实际成本</dt><dd>{{ money(book.cost.actual) }}</dd></div>
        <div><dt>完工预测 EAC</dt><dd>{{ money(book.cost.forecast) }}</dd></div>
      </dl>

      <!-- ④ 利润线（三口径） -->
      <h3 class="onebook-title">④ 利润（三口径并列）</h3>
      <dl class="onebook-grid">
        <div>
          <dt>预计利润（合同+签证 − EAC）</dt>
          <dd>
            <template v-if="book.profit.forecastProfit != null">{{ money(book.profit.forecastProfit) }}</template>
            <template v-else><span class="onebook-na">不可算（未建 CBS 成本账户）</span></template>
          </dd>
        </div>
        <div><dt>已实现利润（收入−支出，审批口径）</dt><dd>{{ money(book.profit.realizedProfit) }}</dd></div>
        <div class="onebook-strong"><dt>落袋利润（银行实收−实付）</dt><dd>{{ money(book.profit.pocketProfit) }}</dd></div>
      </dl>

      <!-- ⑤ 资金线 -->
      <h3 class="onebook-title">⑤ 资金</h3>
      <dl class="onebook-grid">
        <div><dt>银行实收</dt><dd>{{ money(book.fund.cashIn) }}</dd></div>
        <div><dt>银行实付</dt><dd>{{ money(book.fund.cashOut) }}</dd></div>
        <div class="onebook-strong"><dt>累计垫资（实付−实收）</dt><dd>{{ money(book.fund.advance) }}</dd></div>
        <div><dt>应付台账未结</dt><dd>{{ money(book.fund.payableOpen) }}</dd></div>
        <div><dt>质保金未退</dt><dd>{{ money(book.fund.retentionOutstanding) }}</dd></div>
        <div><dt>保证金占压</dt><dd>{{ money(book.fund.bondOccupied) }}</dd></div>
        <div><dt>持有票据</dt><dd>{{ money(book.fund.billHeld) }}</dd></div>
      </dl>

      <!-- ⑥ 税务线 -->
      <h3 class="onebook-title">⑥ 税务</h3>
      <dl class="onebook-grid">
        <div><dt>销项税额</dt><dd>{{ money(book.tax.saleTax) }}</dd></div>
        <div><dt>进项税额</dt><dd>{{ money(book.tax.inputTax) }}</dd></div>
        <div class="onebook-strong"><dt>应纳增值税（销项−进项）</dt><dd>{{ money(book.tax.vatPayable) }}</dd></div>
      </dl>
    </template>
  </section>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { getProjectOnebook, type ProjectOnebook } from '@/api/project'
import { CONTRACT_CATEGORY } from '@/constants/biz-labels'

const props = defineProps<{ projectId: string }>()
const loading = ref(false)
const loadError = ref('')
const book = ref<ProjectOnebook | null>(null)
let generation = 0

function money(v: unknown): string {
  const n = Number(v)
  return Number.isFinite(n) ? n.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 }) : '—'
}
const CAT_COLORS: Record<string, string> = {
  SUBCONTRACT: 'var(--el-color-primary)', LABOR: 'var(--el-color-success)',
  MATERIAL: 'var(--el-color-warning)', MACHINE: '#9c27b0', OTHER: 'var(--el-text-color-secondary)'
}
function catColor(key: string): string { return CAT_COLORS[key] ?? 'var(--el-text-color-secondary)' }
function categoryLabel(key: string): string {
  return CONTRACT_CATEGORY[key] ?? key
}

async function load() {
  const current = ++generation
  const id = props.projectId
  if (!/^\d+$/.test(id)) return
  loading.value = true
  loadError.value = ''
  try {
    const response: any = await getProjectOnebook(id)
    if (current !== generation) return
    if (response.code !== 200) throw new Error('请求失败')
    book.value = response.data
  } catch {
    if (current === generation) loadError.value = '一本账加载失败，请重试。'
  } finally {
    if (current === generation) loading.value = false
  }
}

watch(() => props.projectId, load, { immediate: true })
</script>

<style scoped>
.onebook-note { color: var(--el-text-color-secondary); font-size: var(--zw-font-size-sm); }
.onebook-retry { cursor: pointer; color: var(--el-color-primary); background: none; border: none; padding: 0; }
.onebook-title { margin: var(--zw-space-md) 0 var(--zw-space-xs); font-size: var(--zw-font-size-md); border-left: 3px solid var(--el-color-primary); padding-left: var(--zw-space-xs); }
.onebook-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); gap: var(--zw-space-sm); }
.onebook-grid dt { font-size: var(--zw-font-size-xs); color: var(--el-text-color-secondary); }
.onebook-grid dd { margin: var(--zw-space-2xs) 0 0; font-variant-numeric: tabular-nums; font-size: var(--zw-font-size-md); }
.onebook-strong dt { color: var(--el-text-color-primary); font-weight: 600; }
.onebook-strong dd { font-weight: 600; }
.onebook-na { color: var(--el-text-color-secondary); font-size: var(--zw-font-size-sm); }
.onebook-table { width: 100%; border-collapse: collapse; margin-bottom: var(--zw-space-sm); }
.onebook-table th, .onebook-table td { border: 1px solid var(--el-border-color-lighter); padding: var(--zw-space-2xs) var(--zw-space-sm); font-size: var(--zw-font-size-sm); }
.onebook-table th { background: var(--el-fill-color-light); text-align: right; }
.onebook-table td:first-child { text-align: left; }
.onebook-table td.num { text-align: right; font-variant-numeric: tabular-nums; }
.onebook-table td.empty { text-align: center; color: var(--el-text-color-secondary); }
.cat-dot { display:inline-block; width:8px; height:8px; border-radius:50%; margin-right: var(--zw-space-2xs); vertical-align:middle; }
</style>
