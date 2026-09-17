<template>
  <view class="detail-page">
    <OfflineBanner />

    <view class="loading-mask" v-if="loading">
      <text>加载中...</text>
    </view>

    <view class="failed-state" v-else-if="loadFailed">
      <text class="failed-tip">变更事件加载失败</text>
      <text class="retry-btn" @click="loadData">重试</text>
    </view>

    <template v-else-if="event">
      <!-- 头部：编号 + 状态 -->
      <view class="head-card">
        <view class="head-row">
          <text class="event-number">{{ event.eventNumber }}</text>
          <text class="event-status" :class="event.status">{{ statusText(event.status) }}</text>
        </view>
        <text class="event-title">{{ event.title }}</text>
        <view class="head-meta">
          <text>{{ sourceText(event.sourceType) }}</text>
          <text v-if="event.category">· {{ categoryText(event.category) }}</text>
          <text>· {{ formatDate(event.createdAt) }}</text>
        </view>
      </view>

      <!-- 流转进度：现场最关心「现在到哪一步、卡在谁那里」 -->
      <view class="form-section">
        <view class="section-title">流转进度</view>
        <view class="steps">
          <view
            v-for="(step, i) in steps"
            :key="step.key"
            class="step"
            :class="{ done: step.done, current: step.current }"
          >
            <view class="step-dot">{{ i + 1 }}</view>
            <view class="step-body">
              <text class="step-name">{{ step.name }}</text>
              <text class="step-time" v-if="step.time">{{ step.time }}</text>
            </view>
          </view>
        </view>
        <view class="reject-box" v-if="event.rejectionReason">
          <text class="reject-label">{{ event.status === 'CANCELLED' ? '作废原因' : '驳回原因' }}</text>
          <text class="reject-text">{{ event.rejectionReason }}</text>
        </view>
      </view>

      <!-- 变更内容 -->
      <view class="form-section">
        <view class="section-title">变更内容</view>
        <view class="kv"><text class="k">所属项目</text><text class="v">{{ event.projectName || projectId }}</text></view>
        <view class="kv" v-if="event.sourceRef"><text class="k">来源单号</text><text class="v">{{ event.sourceRef }}</text></view>
        <view class="desc-block">
          <text class="desc-text">{{ event.description || '暂无描述' }}</text>
        </view>
      </view>

      <!-- 影响评估：审批依据，现场也能看到商务怎么算的 -->
      <view class="form-section" v-if="event.impactAssessment">
        <view class="section-title">影响评估</view>
        <view class="kv">
          <text class="k">成本影响</text>
          <text class="v money" :class="{ negative: Number(event.costDelta) < 0 }">
            {{ Number(event.costDelta) > 0 ? '+' : '' }}{{ formatAmount(event.costDelta) }}
          </text>
        </view>
        <view class="kv">
          <text class="k">工期影响</text>
          <text class="v">{{ event.scheduleDelayDays || 0 }} 天</text>
        </view>
        <view class="kv" v-if="event.assessedAt">
          <text class="k">评估时间</text>
          <text class="v">{{ formatDate(event.assessedAt) }}</text>
        </view>
        <view class="desc-block" v-if="event.impactAssessment.rationale">
          <text class="desc-label">评估理由</text>
          <text class="desc-text">{{ event.impactAssessment.rationale }}</text>
        </view>
      </view>
      <view class="form-section" v-else>
        <view class="section-title">影响评估</view>
        <view class="empty-inline"><text>尚未评估（商务/造价测算后回填）</text></view>
      </view>

      <!-- 受影响的成本账户：批准后据此调整 CBS 当前预算 -->
      <view class="form-section" v-if="affectedAccounts.length">
        <view class="section-title">受影响成本账户</view>
        <view class="acct-row" v-for="a in affectedAccounts" :key="a.accountId">
          <text class="acct-id">账户 #{{ a.accountId }}</text>
          <text class="acct-delta" :class="{ down: a.deltaType === 'DECREASE' }">
            {{ a.deltaType === 'DECREASE' ? '−' : '+' }}{{ formatAmount(a.deltaAmount) }}
          </text>
        </view>
      </view>

      <!-- 现场照片 -->
      <view class="form-section" v-if="docs.length">
        <view class="section-title">佐证材料</view>
        <view class="image-list">
          <image
            v-for="(d, i) in docs"
            :key="i"
            :src="d.url"
            mode="aspectFill"
            class="thumb"
            @click="previewDoc(i)"
          />
        </view>
      </view>

      <!-- 操作区：按状态机给出当前可执行动作，不给无效按钮 -->
      <view class="action-bar" v-if="actions.length">
        <button
          v-for="act in actions"
          :key="act.key"
          class="action-btn"
          :class="act.type"
          :loading="acting === act.key"
          :disabled="!!acting"
          @click="runAction(act.key)"
        >{{ act.label }}</button>
      </view>
    </template>
  </view>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import OfflineBanner from '@/components/OfflineBanner.vue'
import {
  getChangeEventDetail,
  startChangeEventAssessment,
  cancelChangeEvent
} from '@/api/common'
import { rejectIfOffline } from '@/utils/offlineSubmit'
import type { ChangeEventDoc } from '@/api/types'

const eventId = ref<number | null>(null)
const projectId = ref<number | null>(null)
const loading = ref(false)
const loadFailed = ref(false)
const acting = ref('')
const event = ref<any>(null)

onLoad((options: any) => {
  eventId.value = options?.id ? Number(options.id) : null
  projectId.value = options?.projectId ? Number(options.projectId) : null
  if (eventId.value) loadData()
})

async function loadData() {
  if (!eventId.value) return
  loading.value = true
  loadFailed.value = false
  try {
    const res: any = await getChangeEventDetail(eventId.value)
    event.value = res || null
    if (!event.value) loadFailed.value = true
  } catch (e) {
    loadFailed.value = true
  } finally {
    loading.value = false
  }
}

/** 流转进度：把状态机翻译成现场看得懂的四步 */
const steps = computed(() => {
  const e = event.value
  if (!e) return []
  const order = ['DRAFT', 'ASSESSING', 'APPROVING', 'APPROVED']
  const names: Record<string, string> = {
    DRAFT: '现场登记',
    ASSESSING: '影响评估',
    APPROVING: '审批中',
    APPROVED: '已批准'
  }
  const times: Record<string, string> = {
    DRAFT: formatDate(e.createdAt),
    ASSESSING: formatDate(e.assessedAt),
    APPROVING: formatDate(e.assessedAt),
    APPROVED: formatDate(e.approvedAt)
  }
  // 驳回/作废：走到哪一步就停在哪一步，并标红当前态
  const isRejected = e.status === 'REJECTED'
  const isCancelled = e.status === 'CANCELLED'
  let currentIndex = order.indexOf(e.status)
  if (isRejected) currentIndex = 2 // 停在审批环节
  if (isCancelled) currentIndex = Math.max(0, order.indexOf(e.status))

  return order.map((key, i) => ({
    key,
    name: names[key],
    time: times[key] || '',
    done: !isRejected && !isCancelled && i < currentIndex,
    current: i === currentIndex
  }))
})

/** 当前状态下允许的动作（与后端 ChangeEventStatus 流转表一致） */
const actions = computed(() => {
  const s = event.value?.status
  if (!s) return []
  const list: { key: string; label: string; type: string }[] = []
  if (s === 'DRAFT') {
    list.push({ key: 'assess', label: '提交评估', type: 'primary' })
    list.push({ key: 'cancel', label: '作废', type: 'plain' })
  } else if (s === 'ASSESSING') {
    list.push({ key: 'cancel', label: '作废', type: 'plain' })
  } else if (s === 'APPROVING') {
    list.push({ key: 'cancel', label: '作废', type: 'plain' })
  }
  return list
})

const affectedAccounts = computed(() => event.value?.affectedAccounts || [])
// 显式声明元素类型：否则 v-for 索引退化为 string|number，previewDoc(idx: number) 报错
const docs = computed<ChangeEventDoc[]>(() =>
  ((event.value?.supportingDocs || []) as ChangeEventDoc[]).filter((d) => d && d.url)
)

async function runAction(key: string) {
  if (!eventId.value) return
  // 状态流转属强一致操作，离线时明确拒绝而非静默入队（避免留下永久中间态）
  if (rejectIfOffline('变更流转需联网操作，请联网后重试')) return

  acting.value = key
  try {
    if (key === 'assess') {
      await startChangeEventAssessment(eventId.value)
      uni.showToast({ title: '已转入评估', icon: 'success' })
    } else if (key === 'cancel') {
      const confirmed = await new Promise<boolean>((resolve) => {
        uni.showModal({
          title: '作废确认',
          content: '作废后事件不可恢复；已批准的变更须登记反向变更冲销。确认作废？',
          success: (r) => resolve(!!r.confirm),
          fail: () => resolve(false)
        })
      })
      if (!confirmed) return
      await cancelChangeEvent(eventId.value, '移动端现场作废')
      uni.showToast({ title: '已作废', icon: 'success' })
    }
    await loadData()
  } catch (e: any) {
    uni.showToast({ title: e?.message || '操作失败', icon: 'none', duration: 2500 })
  } finally {
    acting.value = ''
  }
}

function previewDoc(idx: number) {
  uni.previewImage({ urls: docs.value.map((d: any) => d.url), current: idx })
}

function statusText(status?: string) {
  const map: Record<string, string> = {
    DRAFT: '草稿', ASSESSING: '评估中', APPROVING: '审批中',
    APPROVED: '已批准', REJECTED: '已驳回', CANCELLED: '已作废'
  }
  return map[status || ''] || status || '-'
}

function sourceText(type?: string) {
  const map: Record<string, string> = {
    FIELD_EVENT: '现场事件', DESIGN_CHANGE: '设计变更',
    OWNER_REQUEST: '业主指令', VARIATION_ORDER: '清单变更', OTHER: '其他'
  }
  return map[type || ''] || type || '-'
}

function categoryText(cat?: string) {
  const map: Record<string, string> = {
    COST_IMPACT: '成本影响', SCOPE_CHANGE: '范围变更',
    SCHEDULE_DELAY: '工期延误', QUALITY_ISSUE: '质量问题', OTHER: '其他'
  }
  return map[cat || ''] || cat || ''
}

function formatAmount(value: number | string | null | undefined): string {
  if (value == null || value === '') return '-'
  const num = typeof value === 'string' ? parseFloat(value) : Number(value)
  if (isNaN(num)) return '-'
  return (num / 10000).toFixed(2) + '万'
}

function formatDate(s?: string) {
  if (!s) return ''
  return String(s).replace('T', ' ').slice(0, 16)
}
</script>

<style scoped>
.detail-page { padding: 20rpx; padding-bottom: 180rpx; }

.loading-mask { padding: 120rpx 0; text-align: center; font-size: 26rpx; color: var(--zw-text-quaternary); }
.failed-state { padding: 120rpx 0; text-align: center; }
.failed-tip { display: block; font-size: 26rpx; color: var(--zw-danger); margin-bottom: 16rpx; }
.retry-btn { display: inline-block; font-size: 26rpx; color: var(--zw-brand); border: 1rpx solid var(--zw-brand); padding: 8rpx 32rpx; border-radius: var(--zw-radius-sm); }

.head-card { background: var(--zw-bg-card); border: 1rpx solid var(--zw-border-light); border-left: 8rpx solid var(--zw-brand); border-radius: var(--zw-radius-lg); padding: 24rpx; margin-bottom: 20rpx; }
.head-row { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12rpx; }
.event-number { font-family: var(--zw-font-mono); font-size: 26rpx; color: var(--zw-brand); }
.event-status { font-size: 24rpx; padding: 4rpx 14rpx; border-radius: var(--zw-radius-sm); background: var(--zw-bg-tag); color: var(--zw-text-secondary); }
.event-status.APPROVED { background: var(--zw-success-light); color: var(--zw-success); }
.event-status.REJECTED { background: var(--zw-danger-light); color: var(--zw-danger); }
.event-status.APPROVING, .event-status.ASSESSING { background: var(--zw-warning-light); color: #a8790a; }
.event-title { display: block; font-size: 32rpx; font-weight: bold; color: var(--zw-text-primary); line-height: 1.4; margin-bottom: 12rpx; }
.head-meta { font-size: 24rpx; color: var(--zw-text-tertiary); }

.form-section { background: var(--zw-bg-card); border: 1rpx solid var(--zw-border-light); border-radius: var(--zw-radius-lg); padding: 0 24rpx 20rpx; margin-bottom: 20rpx; }
.section-title { font-size: 28rpx; font-weight: bold; color: var(--zw-text-primary); padding: 24rpx 0 12rpx; border-bottom: 1rpx solid var(--zw-border-light); margin-bottom: 12rpx; }

.steps { padding: 8rpx 0; }
.step { display: flex; align-items: flex-start; gap: 16rpx; padding: 12rpx 0; opacity: 0.45; }
.step.done, .step.current { opacity: 1; }
.step-dot { width: 40rpx; height: 40rpx; line-height: 40rpx; text-align: center; border-radius: 50%; background: var(--zw-bg-surface-3); color: var(--zw-text-tertiary); font-size: 22rpx; flex-shrink: 0; }
.step.done .step-dot { background: var(--zw-success); color: #fff; }
.step.current .step-dot { background: var(--zw-brand); color: var(--zw-on-primary); }
.step-body { display: flex; flex-direction: column; }
.step-name { font-size: 27rpx; color: var(--zw-text-primary); }
.step-time { font-size: 22rpx; color: var(--zw-text-quaternary); margin-top: 4rpx; }

.reject-box { margin-top: 12rpx; padding: 16rpx; background: var(--zw-danger-light); border-radius: var(--zw-radius-sm); }
.reject-label { display: block; font-size: 22rpx; color: var(--zw-danger); margin-bottom: 6rpx; }
.reject-text { font-size: 26rpx; color: var(--zw-text-primary); line-height: 1.5; }

.kv { display: flex; justify-content: space-between; align-items: center; padding: 14rpx 0; border-bottom: 1rpx solid var(--zw-border-light); }
.kv:last-child { border-bottom: none; }
.k { font-size: 26rpx; color: var(--zw-text-tertiary); }
.v { font-size: 26rpx; color: var(--zw-text-primary); text-align: right; max-width: 60%; }
.v.money { font-family: var(--zw-font-mono); }
.v.money.negative { color: var(--zw-success); }

.desc-block { padding: 16rpx 0; }
.desc-label { display: block; font-size: 22rpx; color: var(--zw-text-tertiary); margin-bottom: 8rpx; }
.desc-text { font-size: 27rpx; color: var(--zw-text-primary); line-height: 1.6; }

.acct-row { display: flex; justify-content: space-between; align-items: center; padding: 14rpx 0; border-bottom: 1rpx solid var(--zw-border-light); }
.acct-row:last-child { border-bottom: none; }
.acct-id { font-size: 26rpx; color: var(--zw-text-secondary); }
.acct-delta { font-family: var(--zw-font-mono); font-size: 26rpx; color: var(--zw-danger); }
.acct-delta.down { color: var(--zw-success); }

.image-list { display: flex; flex-wrap: wrap; gap: 16rpx; padding: 12rpx 0; }
.thumb { width: 200rpx; height: 200rpx; border-radius: var(--zw-radius-sm); }

.empty-inline { padding: 24rpx 0; font-size: 25rpx; color: var(--zw-text-quaternary); }

.action-bar { position: fixed; left: 0; right: 0; bottom: 0; display: flex; gap: 16rpx; padding: 16rpx 24rpx calc(16rpx + env(safe-area-inset-bottom)); background: var(--zw-bg-card); border-top: 1rpx solid var(--zw-border-light); }
.action-btn { flex: 1; height: 88rpx; line-height: 88rpx; font-size: 30rpx; border-radius: var(--zw-radius-sm); border: none; }
.action-btn.primary { background: var(--zw-brand); color: var(--zw-on-primary); }
.action-btn.plain { background: var(--zw-bg-hover); color: var(--zw-text-primary); }
</style>
