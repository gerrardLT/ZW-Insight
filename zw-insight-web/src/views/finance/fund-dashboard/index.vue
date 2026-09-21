<template>
  <div class="fund-dashboard-container">
    <!-- ============ 核心三大指标 ============ -->
    <el-row :gutter="16">
      <el-col :span="8">
        <el-card shadow="never" class="metric-card">
          <template #header>
            <div class="metric-header">
              <span>垫资分析</span>
              <el-tooltip content="垫资 = 累计产值 - 累计收款，衡量自有资金占压程度">
                <el-icon class="metric-help"><QuestionFilled /></el-icon>
              </el-tooltip>
            </div>
          </template>
          <div class="metric-main">{{ formatAmount(dashboard.advanceAmount) }}</div>
          <div class="metric-sub">
            <span>垫资率 {{ formatRate(dashboard.advanceRate) }}</span>
            <el-divider direction="vertical" />
            <span>累计产值 {{ formatAmount(dashboard.cumulativeOutput) }}</span>
            <el-divider direction="vertical" />
            <span>累计收款 {{ formatAmount(dashboard.cumulativeReceived) }}</span>
          </div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="never" class="metric-card">
          <template #header>
            <div class="metric-header">
              <span>经营性现金流净额</span>
              <el-tooltip content="现金流入 - 现金流出（收付口径），负值表明主业失血">
                <el-icon class="metric-help"><QuestionFilled /></el-icon>
              </el-tooltip>
            </div>
          </template>
          <div class="metric-main" :class="(dashboard.operatingCashFlow || 0) < 0 ? 'metric-negative' : 'metric-positive'">
            {{ formatAmount(dashboard.operatingCashFlow) }}
          </div>
          <div class="metric-sub">
            <span>流入 {{ formatAmount(dashboard.cashInflow) }}</span>
            <el-divider direction="vertical" />
            <span>流出 {{ formatAmount(dashboard.cashOutflow) }}</span>
          </div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="never" class="metric-card">
          <template #header>
            <div class="metric-header">
              <span>回款分析</span>
              <el-tooltip content="回款率 = 累计收款 / (累计收款 + 应收账款)，钱收回来没有">
                <el-icon class="metric-help"><QuestionFilled /></el-icon>
              </el-tooltip>
            </div>
          </template>
          <div class="metric-main">{{ formatRate(dashboard.collectionRate) }}</div>
          <div class="metric-sub">
            <span>应收账款 {{ formatAmount(dashboard.receivableTotal) }}</span>
            <el-divider direction="vertical" />
            <span>累计收款 {{ formatAmount(dashboard.cumulativeReceived) }}</span>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- ============ 辅助指标 ============ -->
    <el-row :gutter="16" style="margin-top: var(--zw-space-md)">
      <el-col :span="8">
        <el-card shadow="never">
          <template #header><span>保证金占用（四类）</span></template>
          <div class="metric-main">{{ formatAmount(dashboard.bondOccupying) }}</div>
          <div class="metric-sub">
            <span>保函替代率 {{ formatRate(dashboard.guaranteeRatio) }}</span>
            <el-divider direction="vertical" />
            <span>减少现金占压</span>
          </div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="never">
          <template #header><span>工资专户合规预警</span></template>
          <div class="metric-main" :class="(dashboard.wageAccountWarnings || 0) > 0 ? 'metric-negative' : 'metric-positive'">
            {{ dashboard.wageAccountWarnings ?? 0 }} 个
          </div>
          <div class="metric-sub">
            <span>拨付逾期/拨付不足的专户数（条例红线）</span>
          </div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="never">
          <template #header><span>资金缺口滚动预测</span></template>
          <div class="metric-main">
            {{ formatAmount(totalGap) }}
          </div>
          <div class="metric-sub">
            <span>未来各月净缺口合计（付-收）</span>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- ============ 滚动预测明细 ============ -->
    <el-card shadow="never" style="margin-top: var(--zw-space-md)">
      <template #header><span>未来月份资金缺口明细（滚动预测快照）</span></template>
      <el-table :data="dashboard.rollingGaps || []" v-loading="loading" border>
        <el-table-column prop="month" label="月份" width="110" align="center" />
        <el-table-column label="预计收款" width="150" align="right">
          <template #default="{ row }">{{ formatAmount(row.expectedReceipts) }}</template>
        </el-table-column>
        <el-table-column label="预计付款" width="150" align="right">
          <template #default="{ row }">{{ formatAmount(row.expectedPayments) }}</template>
        </el-table-column>
        <el-table-column label="净缺口（付-收）" width="160" align="right">
          <template #default="{ row }">
            <span :class="(row.netGap || 0) > 0 ? 'gap-negative' : 'gap-positive'">{{ formatAmount(row.netGap) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="风险等级" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="riskTag(row.riskLevel)" size="small">{{ riskLabel(row.riskLevel) }}</el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { QuestionFilled } from '@element-plus/icons-vue'
import { getFundDashboard, type FundDashboard } from '@/api/fund-dashboard'

const loading = ref(false)
const dashboard = ref<FundDashboard>({})

const totalGap = computed(() =>
  (dashboard.value.rollingGaps || []).reduce((sum, g) => sum + (g.netGap || 0), 0)
)

function formatAmount(value?: number) {
  return value != null ? `¥${Number(value).toLocaleString('zh-CN', { minimumFractionDigits: 2 })}` : '—'
}
function formatRate(value?: number) {
  return value != null ? `${(Number(value) * 100).toFixed(1)}%` : '—'
}
function riskLabel(level: string) {
  return { LOW: '低', MEDIUM: '中', HIGH: '高' }[level] || level
}
function riskTag(level: string): 'primary' | 'success' | 'warning' | 'info' | 'danger' {
  const map: Record<string, 'primary' | 'success' | 'warning' | 'info' | 'danger'> = { LOW: 'success', MEDIUM: 'warning', HIGH: 'danger' }
  return map[level] || 'info'
}

async function loadDashboard() {
  loading.value = true
  try {
    const res = await getFundDashboard()
    dashboard.value = res.data.data || {}
  } finally {
    loading.value = false
  }
}

onMounted(loadDashboard)
</script>

<style scoped>
.fund-dashboard-container {
  padding: var(--zw-space-md);
}
.metric-card :deep(.el-card__header) {
  padding: var(--zw-space-sm-md) var(--zw-space-md);
}
.metric-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-weight: 600;
}
.metric-help {
  color: var(--el-text-color-secondary);
  cursor: help;
}
.metric-main {
  font-size: var(--zw-font-size-3xl);
  font-weight: 700;
  line-height: 1.4;
}
.metric-sub {
  margin-top: var(--zw-space-sm);
  color: var(--el-text-color-secondary);
  font-size: var(--zw-font-size-sm);
}
.metric-positive {
  color: var(--el-color-success);
}
.metric-negative {
  color: var(--el-color-danger);
}
.gap-negative {
  color: var(--el-color-danger);
  font-weight: 600;
}
.gap-positive {
  color: var(--el-color-success);
}
</style>
