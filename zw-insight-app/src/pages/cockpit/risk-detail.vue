<template>
  <view class="detail-page">
    <view v-if="loading" class="state-block"><text>加载中...</text></view>
    <view v-else-if="loadFailed" class="state-block">
      <text class="failed-tip">风险详情加载失败</text>
      <text class="retry-btn" @click="loadDetail">重试</text>
    </view>

    <template v-else-if="risk">
      <!-- 头部：级别 + 类型 + 处理状态 -->
      <view class="head-card" :class="`head-${risk.severity.toLowerCase()}`">
        <view class="head-row">
          <text class="head-sev">{{ severityIcon(risk.severity) }} {{ severityText(risk.severity) }}</text>
          <text class="head-type">{{ riskTypeText(risk.riskType) }}</text>
          <text class="head-status">{{ statusText(risk.handleStatus) }}</text>
        </view>
        <text class="head-title">{{ risk.title }}</text>
      </view>

      <!-- 六要素（驾驶舱 §11：每条风险必须回答的六个问题） -->
      <view class="section-card">
        <view class="field">
          <text class="f-label">影响多少钱</text>
          <text class="f-value impact">{{ toWan(risk.impactAmount) }}</text>
        </view>
        <view class="field">
          <text class="f-label">项目</text>
          <text class="f-value">{{ risk.projectName || (risk.projectId ? `项目#${risk.projectId}` : '公司整体') }}</text>
        </view>
        <view class="field">
          <text class="f-label">谁负责</text>
          <text class="f-value">{{ risk.ownerName || '未指定（项目未配置项目经理）' }}</text>
        </view>
        <view class="field column">
          <text class="f-label">下一步动作</text>
          <text class="f-value">{{ risk.nextAction || '—' }}</text>
        </view>
        <view class="field column">
          <text class="f-label">为什么发生</text>
          <view v-if="reasonRows.length" class="reason-box">
            <view v-for="r in reasonRows" :key="r.key" class="reason-row">
              <text class="r-key">{{ r.key }}</text>
              <text class="r-value">{{ r.value }}</text>
            </view>
          </view>
          <text v-else class="f-value">规则未提供结构化归因</text>
        </view>
        <view class="field column">
          <text class="f-label">判定依据</text>
          <text class="f-value mono">{{ risk.ruleParams || '—' }}</text>
        </view>
        <view class="field">
          <text class="f-label">最近扫描确认</text>
          <text class="f-value">{{ risk.lastScanAt || '—' }}</text>
        </view>
        <view v-if="risk.handleNote" class="field column">
          <text class="f-label">处理备注</text>
          <text class="f-value">{{ risk.handleNote }}</text>
        </view>
      </view>

      <!-- 单据穿透（§13：任何数字可追溯到业务数据） -->
      <view v-if="drillTarget" class="section-card">
        <view class="drill-row" @click="goDrill">
          <text class="f-label">单据穿透</text>
          <text class="drill-link">{{ drillTarget.label }} ›</text>
        </view>
      </view>

      <!-- 处理动作 -->
      <view class="action-bar">
        <view v-if="risk.handleStatus === 'OPEN'" class="action-btn primary" @click="doHandle('PROCESSING')">
          <text>认领处理</text>
        </view>
        <view v-if="risk.handleStatus !== 'RESOLVED'" class="action-btn success" @click="doHandle('RESOLVED')">
          <text>标记已解决</text>
        </view>
        <view v-if="risk.handleStatus !== 'IGNORED'" class="action-btn plain" @click="doIgnore">
          <text>忽略</text>
        </view>
        <view v-if="risk.handleStatus === 'RESOLVED'" class="action-btn plain" @click="doHandle('OPEN')">
          <text>重开</text>
        </view>
      </view>
    </template>
  </view>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { getRiskDetail, handleRisk } from '@/api/common'
import { toWan } from '@/utils/format'

const RISK_TYPE_LABELS: Record<string, string> = {
  PROFIT_LOSS: '利润风险',
  BUDGET_OVER: '超预算',
  FUND_GAP: '资金缺口',
  RECEIVABLE_OVERDUE: '应收逾期',
  RETENTION_OVERDUE: '质保金逾期',
  WAGE_COMPLIANCE: '工资专户合规',
  ENTERTAINMENT_ANOMALY: '招待费异常'
}

/** 归因字段中文（与 PC 端 risk-center.vue 同源，保持两端一致） */
const REASON_LABELS: Record<string, string> = {
  contractIncome: '合同收入', forecastTotalCost: '预计总成本', profitRate: '利润率',
  costBasis: '成本口径', category: '成本类别', actual: '实际发生', budget: '预算',
  rate: '执行率', month: '月份', expectedPayments: '预计付款', expectedReceipts: '预计收款',
  netGap: '净缺口', forecastRiskLevel: '预测风险级', totalAmount: '累计金额',
  totalCount: '笔数', maxSingleAmount: '单次最高', noReasonCount: '无事由笔数',
  noHostCount: '无对象笔数', noPreApprovalCount: '无事前审批笔数', noInvoiceCount: '发票不完整笔数',
  sameDayMultiCount: '同人同日多笔', frequentHandlerCount: '高频经办人数',
  receivableId: '应收ID', dueDate: '到期日', balance: '未结余额', overdueDays: '逾期天数',
  retentionId: '质保金ID', expireDate: '到期日', retentionAmount: '质保金额',
  returnedAmount: '已退金额', accountId: '专户ID', accountNo: '账号',
  complianceFlag: '合规状态', wageBudget: '工资预算', totalReceived: '已到账', totalPaid: '已发放'
}

const riskId = ref<number>(0)
const risk = ref<any>(null)
const loading = ref(false)
const loadFailed = ref(false)

function riskTypeText(type: string) {
  return RISK_TYPE_LABELS[type] || type
}

function severityText(severity: string) {
  return { RED: '严重', YELLOW: '关注', INFO: '提醒' }[severity] || severity
}

function severityIcon(severity: string) {
  return { RED: '🔴', YELLOW: '🟡', INFO: '⚪' }[severity] || '⚪'
}

function statusText(status: string) {
  return { OPEN: '待处理', PROCESSING: '处理中', RESOLVED: '已解决', IGNORED: '已忽略' }[status] || status
}

/** 结构化归因解析（失败时如实展示原文，不静默丢弃） */
const reasonRows = computed(() => {
  const raw = risk.value?.reasonDetail
  if (!raw) return [] as { key: string; value: string }[]
  try {
    const obj = JSON.parse(raw)
    if (Array.isArray(obj)) {
      return obj.map((item: any, idx: number) => ({ key: `明细 ${idx + 1}`, value: JSON.stringify(item) }))
    }
    return Object.entries(obj).map(([key, value]) => ({
      key: REASON_LABELS[key] || key,
      value: String(value)
    }))
  } catch {
    return [{ key: '原始归因', value: String(raw) }]
  }
})

/**
 * 穿透目标：按 bizRefType 映射到移动端已有页面。
 * 未登记的类型不提供入口（不伪造跳转到不存在的页面）。
 */
const drillTarget = computed(() => {
  const r = risk.value
  if (!r) return null
  switch (r.bizRefType) {
    case 'PROJECT':
    case 'COST_ACCOUNT':
      return r.projectId
        ? { label: '项目成本控制看板', url: `/pages/project/cost-control/index?projectId=${r.projectId}` }
        : null
    default:
      return null
  }
})

function goDrill() {
  if (drillTarget.value) uni.navigateTo({ url: drillTarget.value.url })
}

async function loadDetail() {
  if (!riskId.value) {
    loadFailed.value = true
    return
  }
  loading.value = true
  loadFailed.value = false
  try {
    const res: any = await getRiskDetail(riskId.value)
    risk.value = res.data || null
    if (!risk.value) loadFailed.value = true
  } catch {
    // request 拦截器已 toast 具体错误
    loadFailed.value = true
  } finally {
    loading.value = false
  }
}

async function doHandle(action: string, note?: string) {
  const label = statusText(action)
  uni.showModal({
    title: '确认操作',
    content: note ? `确定忽略该风险吗？` : `确定将该风险置为「${label}」吗？`,
    editable: action === 'IGNORED',
    placeholderText: action === 'IGNORED' ? '请填写忽略原因（留痕可审计）' : '',
    success: async (res: any) => {
      if (!res.confirm) return
      const finalNote = action === 'IGNORED' ? (res.content || '').trim() : note
      if (action === 'IGNORED' && !finalNote) {
        uni.showToast({ title: '忽略原因不能为空', icon: 'none' })
        return
      }
      try {
        await handleRisk(riskId.value, action, finalNote)
        uni.showToast({ title: `已置为${label}`, icon: 'success' })
        await loadDetail()
      } catch {
        // 拦截器已提示
      }
    }
  })
}

function doIgnore() {
  doHandle('IGNORED')
}

onLoad((options: any) => {
  const id = Number(options?.id)
  if (!id || !Number.isFinite(id)) {
    uni.showToast({ title: '缺少风险ID参数', icon: 'none' })
    loadFailed.value = true
    return
  }
  riskId.value = id
  loadDetail()
})
</script>

<style scoped>
.detail-page { padding: 20rpx; padding-bottom: 160rpx; }

.state-block {
  display: flex; flex-direction: column; align-items: center; gap: 16rpx;
  padding: 80rpx 0; font-size: 26rpx; color: var(--zw-text-quaternary);
}
.failed-tip { color: var(--zw-danger); }
.retry-btn { color: var(--zw-brand); }

.head-card {
  padding: 24rpx; margin-bottom: 20rpx;
  background: var(--zw-bg-card); border: 1rpx solid var(--zw-border-light);
  border-radius: var(--zw-radius-lg); border-left: 8rpx solid var(--zw-text-quaternary);
}
.head-red { border-left-color: var(--zw-danger); }
.head-yellow { border-left-color: var(--zw-warning); }
.head-info { border-left-color: var(--zw-text-quaternary); }
.head-row { display: flex; align-items: center; gap: 12rpx; margin-bottom: 12rpx; }
.head-sev { font-size: 26rpx; font-weight: 600; color: var(--zw-text-primary); }
.head-type { font-size: 22rpx; color: var(--zw-text-tertiary); }
.head-status { margin-left: auto; font-size: 22rpx; color: var(--zw-text-quaternary); }
.head-title { font-size: 30rpx; line-height: 1.5; color: var(--zw-text-primary); }

.section-card {
  background: var(--zw-bg-card); border: 1rpx solid var(--zw-border-light);
  border-radius: var(--zw-radius-lg); padding: 8rpx 24rpx; margin-bottom: 20rpx;
}
.field {
  display: flex; align-items: center; gap: 16rpx;
  padding: 20rpx 0; border-bottom: 1rpx solid var(--zw-border-light);
}
.field:last-child { border-bottom: none; }
.field.column { flex-direction: column; align-items: flex-start; gap: 10rpx; }
.f-label { font-size: 26rpx; color: var(--zw-text-tertiary); min-width: 180rpx; }
.field.column .f-label { min-width: 0; }
.f-value { flex: 1; font-size: 26rpx; color: var(--zw-text-primary); line-height: 1.5; }
.f-value.impact { color: var(--zw-danger); font-weight: 600; font-size: 30rpx; }
.f-value.mono { font-size: 22rpx; color: var(--zw-text-tertiary); word-break: break-all; }

.reason-box { width: 100%; display: flex; flex-direction: column; gap: 8rpx; }
.reason-row { display: flex; gap: 16rpx; font-size: 24rpx; }
.r-key { color: var(--zw-text-tertiary); min-width: 180rpx; }
.r-value { flex: 1; color: var(--zw-text-primary); word-break: break-all; }

.drill-row { display: flex; align-items: center; padding: 20rpx 0; }
.drill-link { margin-left: auto; font-size: 26rpx; color: var(--zw-brand); }

.action-bar {
  position: fixed; left: 0; right: 0; bottom: 0;
  display: flex; gap: 16rpx; padding: 20rpx;
  background: var(--zw-bg-card); border-top: 1rpx solid var(--zw-border-light);
}
.action-btn {
  flex: 1; text-align: center; padding: 22rpx 0;
  border-radius: var(--zw-radius-md); font-size: 28rpx;
}
.action-btn.primary { background: var(--zw-brand); color: #fff; }
.action-btn.success { background: var(--zw-success); color: #fff; }
.action-btn.plain {
  background: var(--zw-bg-hover); color: var(--zw-text-secondary);
  border: 1rpx solid var(--zw-border-light);
}
</style>
