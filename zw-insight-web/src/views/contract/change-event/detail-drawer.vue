<template>
  <el-drawer
    v-model="drawerVisible"
    title="变更事件详情"
    direction="rtl"
    size="620px"
  >
    <div v-loading="loading" class="detail-container">
      <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" />

      <template v-else-if="eventData">
        <!-- 头部：编号 + 状态 + 标题 -->
        <div class="detail-header">
          <div class="header-row">
            <span class="event-number">{{ eventData.eventNumber }}</span>
            <el-tag :type="(CHANGE_EVENT_STATUS_TAG_TYPES[eventData.status || ''] as any) || 'info'" size="large">
              {{ CHANGE_EVENT_STATUS_LABELS[eventData.status || ''] || eventData.status }}
            </el-tag>
          </div>
          <h3 class="title">{{ eventData.title }}</h3>
          <div class="meta-row">
            <el-tag size="small" effect="plain">
              {{ CHANGE_EVENT_SOURCE_LABELS[eventData.sourceType] || eventData.sourceType }}
            </el-tag>
            <el-tag
              v-if="eventData.category"
              size="small"
              :type="(CHANGE_EVENT_CATEGORY_TAG_TYPES[eventData.category] as any) || 'info'"
            >
              {{ CHANGE_EVENT_CATEGORY_LABELS[eventData.category] || eventData.category }}
            </el-tag>
            <span v-if="eventData.sourceRef" class="meta-source">来源单号：{{ eventData.sourceRef }}</span>
          </div>
        </div>

        <!-- 流转进度：把状态机翻译成看得懂的步骤 -->
        <el-card shadow="never" class="info-card">
          <template #header><span class="card-title">流转进度</span></template>
          <el-steps :active="activeStep" align-center finish-status="success" :process-status="processStatus">
            <el-step title="现场登记" :description="formatDateTime(eventData.createdAt)" />
            <el-step title="影响评估" :description="formatDateTime(eventData.assessedAt)" />
            <el-step title="审批" :description="formatDateTime(eventData.approvedAt)" />
            <el-step title="成本传导" description="自动调整 CBS" />
          </el-steps>
          <el-alert
            v-if="eventData.rejectionReason"
            class="reject-alert"
            :title="eventData.status === 'CANCELLED' ? '作废原因' : '驳回原因'"
            :description="eventData.rejectionReason"
            :type="eventData.status === 'REJECTED' ? 'error' : 'info'"
            show-icon
            :closable="false"
          />
        </el-card>

        <!-- 变更内容 -->
        <el-card shadow="never" class="info-card">
          <template #header><span class="card-title">变更内容</span></template>
          <p class="description">{{ eventData.description || '暂无描述' }}</p>
        </el-card>

        <!-- 影响评估 -->
        <el-card shadow="never" class="info-card">
          <template #header><span class="card-title">影响评估</span></template>
          <template v-if="eventData.impactAssessment">
            <el-descriptions :column="2" border size="small">
              <el-descriptions-item label="成本影响">
                <span :class="Number(eventData.costDelta) < 0 ? 'is-success' : 'is-danger'">
                  {{ Number(eventData.costDelta) > 0 ? '+' : '' }}{{ toWan(eventData.costDelta) }}
                </span>
              </el-descriptions-item>
              <el-descriptions-item label="工期影响">
                {{ eventData.scheduleDelayDays || 0 }} 天
              </el-descriptions-item>
              <el-descriptions-item label="评估理由" :span="2">
                {{ eventData.impactAssessment.rationale || '-' }}
              </el-descriptions-item>
              <el-descriptions-item
                v-if="eventData.impactAssessment.assessmentNotes"
                label="备注"
                :span="2"
              >
                {{ eventData.impactAssessment.assessmentNotes }}
              </el-descriptions-item>
            </el-descriptions>
          </template>
          <el-empty v-else description="尚未评估（商务/造价测算后回填）" :image-size="60" />
        </el-card>

        <!-- 受影响的成本账户：批准后据此调整 CBS 当前预算 -->
        <el-card v-if="eventData.affectedAccounts?.length" shadow="never" class="info-card">
          <template #header><span class="card-title">受影响成本账户</span></template>
          <el-table :data="eventData.affectedAccounts" border size="small">
            <el-table-column prop="accountId" label="账户ID" width="140" />
            <el-table-column prop="deltaType" label="方向" width="100" align="center">
              <template #default="{ row }">
                <el-tag size="small" :type="row.deltaType === 'INCREASE' ? 'danger' : 'success'">
                  {{ row.deltaType === 'INCREASE' ? '增加' : '减少' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="金额" align="right">
              <template #default="{ row }">
                <span :class="row.deltaType === 'DECREASE' ? 'is-success' : 'is-danger'">
                  {{ row.deltaType === 'DECREASE' ? '−' : '+' }}{{ toWan(row.deltaAmount) }}
                </span>
              </template>
            </el-table-column>
          </el-table>
        </el-card>

        <!-- 影响的 WBS 节点 -->
        <el-card v-if="eventData.affectedWbsIds?.length" shadow="never" class="info-card">
          <template #header><span class="card-title">影响的 WBS 节点</span></template>
          <div class="wbs-tags">
            <el-tag v-for="id in eventData.affectedWbsIds" :key="id" type="info" size="small">
              节点 #{{ id }}
            </el-tag>
          </div>
        </el-card>

        <!-- 佐证材料 -->
        <el-card v-if="eventData.supportingDocs?.length" shadow="never" class="info-card">
          <template #header><span class="card-title">佐证材料</span></template>
          <div class="docs-list">
            <div v-for="(doc, i) in eventData.supportingDocs" :key="i" class="doc-item">
              <el-icon><Document /></el-icon>
              <span class="doc-name">{{ doc.name || doc.type || `附件${i + 1}` }}</span>
              <el-button v-if="doc.url" type="primary" link size="small" @click="openDoc(doc)">
                查看
              </el-button>
            </div>
          </div>
        </el-card>

        <!-- 审计信息 -->
        <el-card shadow="never" class="info-card">
          <template #header><span class="card-title">审计信息</span></template>
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="登记时间">{{ formatDateTime(eventData.createdAt) }}</el-descriptions-item>
            <el-descriptions-item label="评估时间">{{ formatDateTime(eventData.assessedAt) || '-' }}</el-descriptions-item>
            <el-descriptions-item label="批准时间">{{ formatDateTime(eventData.approvedAt) || '-' }}</el-descriptions-item>
            <el-descriptions-item label="流程实例">{{ eventData.workflowInstanceId || '未接入 BPMN' }}</el-descriptions-item>
          </el-descriptions>
        </el-card>

        <!-- 操作区：按状态机给出当前可执行动作，不给无效按钮 -->
        <div class="action-bar">
          <el-button v-if="isEditable" type="primary" @click="handleEdit">编辑</el-button>
          <el-button
            v-if="eventData.status === 'DRAFT'"
            type="warning"
            @click="handleStartAssessment"
          >转入评估</el-button>
          <el-button
            v-if="eventData.status === 'ASSESSING'"
            type="primary"
            @click="handleEditAssessment"
          >填写影响评估</el-button>
          <el-button
            v-if="eventData.status === 'APPROVING'"
            type="success"
            @click="handleApprove"
          >批准</el-button>
          <el-button
            v-if="eventData.status === 'APPROVING'"
            type="danger"
            @click="handleReject"
          >驳回</el-button>
          <el-button
            v-if="eventData.status === 'APPROVING'"
            type="warning"
            plain
            @click="handleReAssess"
          >退回重评</el-button>
          <el-button
            v-if="isCancellable"
            type="info"
            plain
            @click="handleCancel"
          >作废</el-button>
          <el-button
            v-if="isDeletable"
            type="danger"
            plain
            @click="handleDelete"
          >删除</el-button>
        </div>
      </template>
    </div>
  </el-drawer>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Document } from '@element-plus/icons-vue'
import {
  getChangeEvent, approveChangeEvent, rejectChangeEvent, cancelChangeEvent,
  startChangeEventAssessment, reAssessChangeEvent, deleteChangeEvent,
  CHANGE_EVENT_STATUS_LABELS, CHANGE_EVENT_STATUS_TAG_TYPES,
  CHANGE_EVENT_SOURCE_LABELS,
  CHANGE_EVENT_CATEGORY_LABELS, CHANGE_EVENT_CATEGORY_TAG_TYPES,
  type BizChangeEvent
} from '@/api/change-event'
import { toWan } from '@/utils/chart-format'

const props = defineProps<{
  visible: boolean
  eventId?: number
}>()

const emit = defineEmits<{
  (e: 'update:visible', value: boolean): void
  (e: 'changed'): void
  (e: 'edit', event: BizChangeEvent): void
}>()

const loading = ref(false)
const error = ref<string | null>(null)
const eventData = ref<BizChangeEvent | null>(null)

const drawerVisible = computed({
  get: () => props.visible,
  set: (val) => emit('update:visible', val)
})

/** 可编辑：与后端 ChangeEventStatus.isEditable() 一致 */
const isEditable = computed(() => {
  const s = eventData.value?.status
  return s === 'DRAFT' || s === 'ASSESSING'
})

/** 可作废：状态机 APPROVING 及之前 */
const isCancellable = computed(() => {
  const s = eventData.value?.status
  return s === 'DRAFT' || s === 'ASSESSING' || s === 'APPROVING'
})

/** 可删除：仅草稿/已作废（进入审批链的必须留痕） */
const isDeletable = computed(() => {
  const s = eventData.value?.status
  return s === 'DRAFT' || s === 'CANCELLED'
})

/** 步骤条当前步：驳回停在审批步并标红 */
const activeStep = computed(() => {
  const s = eventData.value?.status
  switch (s) {
    case 'DRAFT': return 0
    case 'ASSESSING': return 1
    case 'APPROVING': return 2
    case 'APPROVED': return 4
    case 'REJECTED': return 2
    case 'CANCELLED': return 0
    default: return 0
  }
})

const processStatus = computed(() => {
  const s = eventData.value?.status
  if (s === 'REJECTED') return 'error'
  if (s === 'CANCELLED') return 'wait'
  return 'process'
})

watch(() => [props.visible, props.eventId], ([visible, id]) => {
  if (visible && id) {
    loadEventData(Number(id))
  } else if (!visible) {
    eventData.value = null
    error.value = null
  }
}, { immediate: true })

async function loadEventData(id: number) {
  loading.value = true
  error.value = null
  try {
    const res: any = await getChangeEvent(id)
    eventData.value = res.data
  } catch (e: any) {
    error.value = e?.message || '加载失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

async function refresh() {
  if (props.eventId) await loadEventData(props.eventId)
  emit('changed')
}

function formatDateTime(s?: string) {
  if (!s) return ''
  return String(s).replace('T', ' ').slice(0, 16)
}

function handleEdit() {
  if (!eventData.value) return
  emit('edit', eventData.value)
  drawerVisible.value = false
}

/** 填写/修改影响评估：复用编辑弹窗（评估字段在其中） */
function handleEditAssessment() {
  handleEdit()
}

async function handleStartAssessment() {
  try {
    await startChangeEventAssessment(eventData.value!.id!)
    ElMessage.success('已转入评估中')
    await refresh()
  } catch (e: any) {
    ElMessage.error(e?.message || '操作失败')
  }
}

async function handleApprove() {
  let comment: string
  try {
    const r = await ElMessageBox.prompt(
      `批准后将自动调整受影响成本账户的「当前预算」。可填写审批意见：`,
      '批准确认',
      { confirmButtonText: '批准', cancelButtonText: '取消', inputPlaceholder: '审批意见（可空）' }
    )
    comment = r.value || ''
  } catch {
    return
  }
  try {
    await approveChangeEvent(eventData.value!.id!, comment || undefined)
    ElMessage.success('已批准，成本传导已提交（异步生效）')
    await refresh()
  } catch (e: any) {
    ElMessage.error(e?.message || '批准失败')
  }
}

async function handleReject() {
  let reason: string
  try {
    const r = await ElMessageBox.prompt('请填写驳回原因（必填）：', '驳回', {
      confirmButtonText: '驳回',
      cancelButtonText: '取消',
      inputValidator: (v: string) => (v && v.trim() ? true : '驳回原因不能为空')
    })
    reason = r.value
  } catch {
    return
  }
  try {
    await rejectChangeEvent(eventData.value!.id!, reason)
    ElMessage.success('已驳回')
    await refresh()
  } catch (e: any) {
    ElMessage.error(e?.message || '驳回失败')
  }
}

async function handleReAssess() {
  let reason: string
  try {
    const r = await ElMessageBox.prompt(
      '退回后事件回到「评估中」，保留事件连续性（不作废重登）。请填写退回原因：',
      '退回重新评估',
      { confirmButtonText: '退回', cancelButtonText: '取消', inputPlaceholder: '原因（可空）' }
    )
    reason = r.value || ''
  } catch {
    return
  }
  try {
    await reAssessChangeEvent(eventData.value!.id!, reason || undefined)
    ElMessage.success('已退回评估中')
    await refresh()
  } catch (e: any) {
    ElMessage.error(e?.message || '操作失败')
  }
}

async function handleCancel() {
  let reason: string
  try {
    const r = await ElMessageBox.prompt(
      '作废后事件不可恢复。已批准的变更须登记反向变更冲销，不能作废抹除历史。请填写作废原因：',
      '作废确认',
      { confirmButtonText: '作废', cancelButtonText: '取消', inputPlaceholder: '原因（可空）' }
    )
    reason = r.value || ''
  } catch {
    return
  }
  try {
    await cancelChangeEvent(eventData.value!.id!, reason || undefined)
    ElMessage.success('已作废')
    await refresh()
  } catch (e: any) {
    ElMessage.error(e?.message || '作废失败')
  }
}

async function handleDelete() {
  try {
    await ElMessageBox.confirm('确认删除该变更事件？仅草稿/已作废可删除。', '删除确认', { type: 'warning' })
  } catch {
    return
  }
  try {
    await deleteChangeEvent(eventData.value!.id!)
    ElMessage.success('已删除')
    drawerVisible.value = false
    emit('changed')
  } catch (e: any) {
    ElMessage.error(e?.message || '删除失败')
  }
}

function openDoc(doc: any) {
  if (doc?.url) window.open(doc.url, '_blank')
}
</script>

<style scoped lang="scss">
.detail-container {
  padding: 0 var(--zw-space-sm) var(--zw-space-lg);
}

.detail-header {
  margin-bottom: var(--zw-space-md);
  padding-bottom: var(--zw-space-sm-md);
  border-bottom: 1px solid var(--zw-border-light);

  .header-row {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: var(--zw-space-sm);

    .event-number {
      font-family: var(--zw-font-mono);
      font-size: var(--zw-font-size-md);
      font-weight: var(--zw-font-weight-semibold);
      color: var(--zw-brand);
    }
  }

  .title {
    font-size: var(--zw-font-size-lg);
    font-weight: var(--zw-font-weight-semibold);
    color: var(--zw-text-primary);
    margin: 0 0 var(--zw-space-sm) 0;
    line-height: 1.4;
  }

  .meta-row {
    display: flex;
    align-items: center;
    gap: var(--zw-space-sm);
    flex-wrap: wrap;

    .meta-source {
      font-size: var(--zw-font-size-xs);
      color: var(--zw-text-tertiary);
      font-family: var(--zw-font-mono);
    }
  }
}

.info-card {
  margin-bottom: var(--zw-space-sm-md);

  .card-title {
    font-size: var(--zw-font-size-base);
    font-weight: var(--zw-font-weight-semibold);
    color: var(--zw-text-primary);
  }
}

.reject-alert {
  margin-top: var(--zw-space-sm-md);
}

.description {
  font-size: var(--zw-font-size-base);
  color: var(--zw-text-secondary);
  line-height: 1.7;
  margin: 0;
  white-space: pre-wrap;
}

.wbs-tags {
  display: flex;
  flex-wrap: wrap;
  gap: var(--zw-space-xs);
}

.docs-list {
  .doc-item {
    display: flex;
    align-items: center;
    gap: var(--zw-space-sm);
    padding: var(--zw-space-sm) 0;
    border-bottom: 1px solid var(--zw-border-light);

    &:last-child { border-bottom: none; }

    .doc-name {
      flex: 1;
      color: var(--zw-text-secondary);
      font-size: var(--zw-font-size-sm);
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
  }
}

.action-bar {
  position: sticky;
  bottom: 0;
  background-color: var(--zw-bg-card);
  padding: var(--zw-space-sm-md) 0;
  border-top: 1px solid var(--zw-border-light);
  display: flex;
  gap: var(--zw-space-sm);
  flex-wrap: wrap;
  justify-content: flex-end;
}

.is-danger { color: var(--zw-danger); font-weight: var(--zw-font-weight-medium); }
.is-success { color: var(--zw-success); font-weight: var(--zw-font-weight-medium); }
</style>
