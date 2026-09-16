<template>
  <div class="zw-credential-panel">
    <!-- 凭证顶部铭牌 -->
    <div class="panel-header">
      <div class="header-left">
        <div class="domain-tag">
          <span class="domain-badge">APR</span>
          <span class="title">工程业务审批凭证</span>
        </div>
        <div class="biz-id mono">单据流水: {{ businessType || 'FLOW' }} // {{ businessId || taskId }}</div>
      </div>
      <!-- 流程状态印章（明确注明“流程状态”，非法律电子签章） -->
      <div class="stamp-seal" :class="stampClass">
        <div class="seal-inner">
          <div class="seal-title">ZW·流程状态</div>
          <div class="seal-status">{{ statusText }}</div>
          <div class="seal-meta mono">{{ nodeName || 'PROCESS' }}</div>
        </div>
      </div>
    </div>

    <!-- 凭证主体字段 -->
    <div class="credential-body">
      <div class="section-title">业务单据摘要</div>
      <div class="fields-grid">
        <div class="field-item">
          <span class="field-label">业务类型</span>
          <span class="field-value mono">{{ businessType || '-' }}</span>
        </div>
        <div class="field-item">
          <span class="field-label">业务编号</span>
          <span class="field-value mono">{{ businessId || '-' }}</span>
        </div>
        <div class="field-item">
          <span class="field-label">当前节点</span>
          <span class="field-value font-bold">{{ nodeName || '流转中' }}</span>
        </div>
        <div class="field-item">
          <span class="field-label">任务处理人</span>
          <span class="field-value">{{ assigneeName || '候选组待认领' }}</span>
        </div>
        <div class="field-item full-width" v-if="comment">
          <span class="field-label">审批意见</span>
          <span class="field-value comment-box">{{ comment }}</span>
        </div>
      </div>
    </div>

    <!-- 审批记录流转痕迹 -->
    <div class="records-timeline" v-if="historyRecords && historyRecords.length > 0">
      <div class="section-title">流转记录时间线</div>
      <div class="timeline-list">
        <div v-for="(rec, idx) in historyRecords" :key="idx" class="timeline-item">
          <div class="timeline-dot"></div>
          <div class="timeline-content">
            <div class="timeline-head">
              <span class="rec-node font-bold">{{ rec.nodeName || '审批节点' }}</span>
              <span class="rec-user">{{ rec.userName || rec.userId }}</span>
              <span class="rec-time mono">{{ rec.time }}</span>
            </div>
            <div class="rec-comment" v-if="rec.comment">{{ rec.comment }}</div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  businessType?: string
  businessId?: string | number
  taskId?: string
  nodeName?: string
  assigneeName?: string
  status?: string | number
  comment?: string
  historyRecords?: Array<{
    nodeName?: string
    userName?: string
    userId?: string | number
    time?: string
    comment?: string
  }>
}>()

const statusText = computed(() => {
  const s = String(props.status || '')
  if (s === '1' || s.toUpperCase() === 'APPROVED' || s.toUpperCase() === 'COMPLETED') return '已通过'
  if (s === '2' || s.toUpperCase() === 'REJECTED') return '已退回'
  if (s === '3' || s.toUpperCase() === 'TERMINATED') return '已终止'
  return '审批中'
})

const stampClass = computed(() => {
  const t = statusText.value
  if (t === '已通过') return 'seal-success'
  if (t === '已退回' || t === '已终止') return 'seal-danger'
  return 'seal-processing'
})
</script>

<style scoped>
.zw-credential-panel {
  border: 1px solid var(--zw-steel-line-strong);
  background: var(--zw-steel-bg);
  padding: 24px;
  color: var(--zw-steel-text);
  position: relative;
}

.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  border-bottom: 1px solid var(--zw-steel-line-strong);
  padding-bottom: 18px;
  margin-bottom: 20px;
}

.domain-tag {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}

.domain-badge {
  /* APR 属 workflow 域（与 business-visual.ts / --zw-domain-* 同源） */
  background: var(--zw-domain-workflow);
  color: var(--zw-steel-text);
  font-family: var(--zw-font-mono, monospace);
  font-size: 11px;
  font-weight: 800;
  padding: 2px 6px;
  border-radius: 2px;
}

.title {
  font-size: 16px;
  font-weight: 700;
}

.biz-id {
  font-size: 12px;
  color: var(--zw-steel-text-faint);
}

/* 工业印章 */
.stamp-seal {
  width: 90px;
  height: 90px;
  border: 2px dashed var(--zw-brand);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 4px;
  transform: rotate(-12deg);
  opacity: 0.88;
}

.seal-inner {
  width: 100%;
  height: 100%;
  border: 1px solid currentColor;
  border-radius: 50%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
}

.seal-title {
  font-size: 8px;
  font-weight: 600;
  letter-spacing: 0.5px;
}

.seal-status {
  font-size: 15px;
  font-weight: 900;
  margin: 1px 0;
}

.seal-meta {
  font-size: 8px;
  text-transform: uppercase;
}

.seal-success {
  border-color: var(--zw-success);
  color: var(--zw-success);
}

.seal-danger {
  border-color: var(--zw-danger);
  color: var(--zw-danger);
}

.seal-processing {
  border-color: var(--zw-domain-workflow);
  color: var(--zw-domain-workflow);
}

.section-title {
  font-size: 13px;
  font-weight: 700;
  color: var(--zw-steel-text-faint);
  margin-bottom: 12px;
}

.fields-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px 24px;
  background: var(--zw-steel-fill-faint);
  padding: 16px;
  border: 1px solid var(--zw-steel-line-strong);
  margin-bottom: 20px;
}

.field-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.field-item.full-width {
  grid-column: span 2;
}

.field-label {
  font-size: 11px;
  color: var(--zw-steel-text-faint);
}

.field-value {
  font-size: 13px;
}

.comment-box {
  background: var(--zw-steel-fill-soft);
  padding: 8px 12px;
  border-radius: 2px;
  line-height: 1.4;
}

.records-timeline {
  border-top: 1px solid var(--zw-steel-line-strong);
  padding-top: 16px;
}

.timeline-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.timeline-item {
  display: flex;
  gap: 12px;
  position: relative;
}

.timeline-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--zw-domain-workflow);
  margin-top: 4px;
}

.timeline-content {
  flex: 1;
}

.timeline-head {
  display: flex;
  gap: 12px;
  align-items: center;
  font-size: 12px;
  margin-bottom: 4px;
}

.rec-time {
  color: var(--zw-steel-text-faint);
  font-size: 11px;
}

.rec-comment {
  font-size: 12px;
  color: var(--zw-steel-text-faint);
}

.mono {
  font-family: var(--zw-font-mono, monospace);
}

.font-bold {
  font-weight: 700;
}
</style>
