<template>
  <div class="finance-workbench">
    <div class="page-header">
      <div>
        <h2>财务待办</h2>
        <p class="page-hint">待审批、已批未付、逾期未付与应收逾期，按当前财务数据范围汇总</p>
      </div>
      <el-button :loading="loading" @click="load">刷新</el-button>
    </div>

    <WorkbenchSummaryCards :cards="cards" :loading="loading" @select="handleCard" />

    <div v-if="error" class="workbench-error" data-testid="finance-workbench-error">
      <span>财务待办加载失败：{{ error }}</span>
      <el-button link type="primary" @click="load">重试</el-button>
    </div>

    <el-row :gutter="16">
      <el-col :xs="24" :lg="12">
        <el-card shadow="never" class="panel-card">
          <template #header>
            <div class="panel-header">
              <span>待支付 TOP5</span>
              <el-button link type="primary" @click="go('/finance/hub/payable-group?tab=payment-apply&payStatus=NOT_PAID')">
                去付款申请
              </el-button>
            </div>
          </template>
          <el-table v-if="loading || topPayments.length" :data="topPayments" v-loading="loading" border size="small">
            <el-table-column prop="projectName" label="项目" min-width="150" show-overflow-tooltip />
            <el-table-column prop="supplierName" label="收款单位" min-width="140" show-overflow-tooltip />
            <el-table-column label="金额(万)" width="110" align="right">
              <template #default="{ row }">{{ formatWan(row.amount) }}</template>
            </el-table-column>
            <el-table-column prop="paymentDate" label="付款日期" width="110" />
            <el-table-column label="逾期" width="80" align="center">
              <template #default="{ row }">
                <el-tag v-if="Number(row.overdueDays) > 0" type="danger" size="small">{{ row.overdueDays }} 天</el-tag>
                <span v-else>—</span>
              </template>
            </el-table-column>
          </el-table>
          <ZwEmptyState
            v-if="!loading && !topPayments.length"
            type="data"
            description="无已批未付的付款申请"
          />
        </el-card>
      </el-col>

      <el-col :xs="24" :lg="12">
        <el-card shadow="never" class="panel-card">
          <template #header>
            <div class="panel-header">
              <span>逾期应收 TOP5</span>
              <el-button link type="primary" @click="go('/finance/hub/receivable-group?tab=receivable')">
                去应收台账
              </el-button>
            </div>
          </template>
          <el-table v-if="loading || topReceivables.length" :data="topReceivables" v-loading="loading" border size="small">
            <el-table-column prop="projectName" label="项目" min-width="150" show-overflow-tooltip />
            <el-table-column prop="counterpartyName" label="建设单位" min-width="140" show-overflow-tooltip />
            <el-table-column label="余额(万)" width="110" align="right">
              <template #default="{ row }">{{ formatWan(row.amount) }}</template>
            </el-table-column>
            <el-table-column prop="dueDate" label="到期日" width="110" />
            <el-table-column label="逾期" width="80" align="center">
              <template #default="{ row }">
                <el-tag v-if="Number(row.overdueDays) > 0" type="danger" size="small">{{ row.overdueDays }} 天</el-tag>
                <span v-else>—</span>
              </template>
            </el-table-column>
          </el-table>
          <ZwEmptyState
            v-if="!loading && !topReceivables.length"
            type="data"
            description="无逾期应收"
          />
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import WorkbenchSummaryCards from '@/components/WorkbenchSummaryCards.vue'
import ZwEmptyState from '@/components/ZwEmptyState.vue'
import { getFinanceWorkbench } from '@/api/finance'
import { formatWan } from '@/utils/chart-format'

const router = useRouter()
const loading = ref(false)
const error = ref('')
const data = ref<any>(null)

const topPayments = computed<any[]>(() => data.value?.topOverduePayments || [])
const topReceivables = computed<any[]>(() => data.value?.topOverdueReceivables || [])

const cards = computed(() => {
  const payable = data.value?.payable || {}
  const receivable = data.value?.receivable || {}
  return [
    {
      key: 'pending',
      label: '待审批付款',
      value: Number(payable.pendingApprovalCount || 0),
      sub: `${formatWan(payable.pendingApprovalAmount)} 万 · 未认领发票 ${Number(payable.unclaimedInvoiceCount || 0)} 笔`
    },
    {
      key: 'unpaid',
      label: '已批未付',
      value: Number(payable.approvedUnpaidCount || 0),
      sub: `${formatWan(payable.approvedUnpaidAmount)} 万`,
      alert: Number(payable.approvedUnpaidCount || 0) > 0
    },
    {
      key: 'overdue',
      label: '逾期未付',
      value: Number(payable.overdueCount || 0),
      sub: `${formatWan(payable.overdueAmount)} 万`,
      alert: Number(payable.overdueCount || 0) > 0
    },
    {
      key: 'receivable',
      label: '应收逾期',
      value: Number(receivable.overdueCount || 0),
      sub: `${formatWan(receivable.overdueAmount)} 万`,
      alert: Number(receivable.overdueCount || 0) > 0
    }
  ]
})

async function load() {
  loading.value = true
  error.value = ''
  try {
    const res: any = await getFinanceWorkbench()
    if (!res?.data) {
      throw new Error('接口返回为空')
    }
    data.value = res.data
  } catch (e: any) {
    error.value = e?.message || '接口异常'
  } finally {
    loading.value = false
  }
}

function handleCard(card: { key: string }) {
  if (card.key === 'pending') {
    go('/finance/hub/payable-group?tab=payment-apply&status=SUBMITTED')
  } else if (card.key === 'unpaid' || card.key === 'overdue') {
    go('/finance/hub/payable-group?tab=payment-apply&payStatus=NOT_PAID')
  } else if (card.key === 'receivable') {
    go('/finance/hub/receivable-group?tab=receivable')
  }
}

function go(path: string) {
  router.push(path)
}

onMounted(load)
</script>

<style scoped>
.finance-workbench {
  padding: var(--zw-space-md);
}

.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--zw-space-md);
  margin-bottom: var(--zw-space-md);
}

.page-header h2 {
  margin: 0;
  font-size: var(--zw-font-size-xl);
}

.page-hint {
  margin: var(--zw-space-xs) 0 0;
  color: var(--zw-text-secondary);
  font-size: var(--zw-font-size-sm);
}

.workbench-error {
  display: flex;
  align-items: center;
  gap: var(--zw-space-sm);
  margin-bottom: var(--zw-space-sm);
  padding: var(--zw-space-xs) var(--zw-space-sm);
  border: 1px solid var(--zw-danger);
  border-radius: var(--zw-radius-sm);
  color: var(--zw-danger);
  font-size: var(--zw-font-size-sm);
  background: var(--zw-danger-light);
}

.panel-card {
  margin-bottom: var(--zw-space-md);
}

.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
</style>
