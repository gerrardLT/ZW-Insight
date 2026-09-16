<template>
  <div class="zw-cost-five-state">
    <div class="header-plate">
      <div class="domain-tag">
        <span class="domain-badge">CST</span>
        <span class="title">成本五态控制全景</span>
      </div>
      <div class="metrics-summary">
        <div class="summary-item">
          <span class="label">当前预算</span>
          <span class="val mono">{{ narrative.currentBudget.toLocaleString('zh-CN', { minimumFractionDigits: 2 }) }} 元</span>
        </div>
        <div class="summary-item">
          <span class="label">预算结余</span>
          <span class="val mono" :class="narrative.remainingBudget >= 0 ? 'text-green' : 'text-danger'">
            {{ narrative.remainingBudget.toLocaleString('zh-CN', { minimumFractionDigits: 2 }) }} 元
          </span>
        </div>
        <div class="summary-item">
          <span class="label">预计偏差 (EAC)</span>
          <span class="val mono" :class="narrative.varianceAmount >= 0 ? 'text-green' : 'text-danger'">
            {{ narrative.varianceAmount >= 0 ? '+' : '' }}{{ narrative.varianceAmount.toLocaleString('zh-CN', { minimumFractionDigits: 2 }) }} 元
            ({{ narrative.varianceRate.toFixed(1) }}%)
          </span>
        </div>
      </div>
    </div>

    <!-- 五态水平量度轨道 -->
    <div class="five-state-rail">
      <div
        v-for="(node, idx) in narrative.nodes"
        :key="node.key"
        class="rail-node"
        :class="[node.status, { 'is-last': idx === narrative.nodes.length - 1 }]"
      >
        <div class="node-head">
          <span class="node-code">{{ node.code }}</span>
          <span class="node-label">{{ node.label }}</span>
        </div>
        <div class="node-amount mono">{{ node.formattedAmount }} <span class="unit">万元</span></div>
        <div class="node-bar-track">
          <div
            class="node-bar-fill"
            :style="{ width: Math.min(node.ratioToCurrent, 100) + '%' }"
            :class="node.status"
          ></div>
        </div>
        <div class="node-ratio mono">{{ node.ratioToCurrent.toFixed(1) }}%</div>
      </div>
    </div>

    <!-- 趋势状态区：真实性断言，无趋势能力时展示真实蓝图空状态，拒绝伪造折线 -->
    <div class="trend-section">
      <div class="section-title">月度成本动态走势</div>
      <div v-if="narrative.hasTrendCapability" class="trend-chart-container">
        <slot name="trendChart"></slot>
      </div>
      <div v-else class="trend-empty-wrap">
        <ZwBlueprintEmpty
          type="trend"
          size="sm"
          title="暂无月度历史成本数据能力"
          description="后端成本接口当前未开通 monthly_trends 历史按月切片流水；系统恪守真实性，拒绝前端插值伪造曲线。"
        />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { buildCostNarrative, type CostFiveStateRaw } from '@/utils/costNarrative'
import ZwBlueprintEmpty from '@/components/visual/ZwBlueprintEmpty.vue'

const props = defineProps<{
  rawData?: CostFiveStateRaw | null
}>()

const narrative = computed(() => buildCostNarrative(props.rawData))
</script>

<style scoped>
.zw-cost-five-state {
  border: 1px solid var(--zw-steel-line-strong);
  background: var(--zw-steel-bg);
  padding: 20px;
  color: var(--zw-steel-text);
}

.header-plate {
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-bottom: 1px solid var(--zw-steel-line-strong);
  padding-bottom: 14px;
  margin-bottom: 20px;
}

.domain-tag {
  display: flex;
  align-items: center;
  gap: 8px;
}

.domain-badge {
  /* CST 属 cost 域（与 business-visual.ts / --zw-domain-* 同源） */
  background: var(--zw-domain-cost);
  color: var(--zw-on-primary);
  font-family: var(--zw-font-mono, monospace);
  font-size: 11px;
  font-weight: 800;
  padding: 2px 6px;
  border-radius: 2px;
}

.title {
  font-size: 15px;
  font-weight: 700;
  letter-spacing: 0.02em;
}

.metrics-summary {
  display: flex;
  gap: 24px;
}

.summary-item {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
}

.summary-item .label {
  font-size: 11px;
  color: var(--zw-steel-text-faint);
}

.summary-item .val {
  font-size: 14px;
  font-weight: 700;
}

.mono {
  font-family: var(--zw-font-mono, monospace);
}

.text-green { color: var(--zw-success); }
.text-danger { color: var(--zw-danger); }

.five-state-rail {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 12px;
  background: var(--zw-steel-fill-faint);
  padding: 16px;
  border: 1px solid var(--zw-steel-line-strong);
  margin-bottom: 20px;
}

.rail-node {
  display: flex;
  flex-direction: column;
  border-right: 1px dashed var(--zw-steel-line-strong);
  padding-right: 12px;
}

.rail-node.is-last {
  border-right: none;
  padding-right: 0;
}

.node-head {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 6px;
}

.node-code {
  font-family: var(--zw-font-mono, monospace);
  font-size: 10px;
  color: var(--zw-steel-text-faint);
  font-weight: 700;
}

.node-label {
  font-size: 12px;
  color: var(--zw-steel-text-faint);
}

.node-amount {
  font-size: 16px;
  font-weight: 800;
  margin-bottom: 8px;
}

.node-amount .unit {
  font-size: 10px;
  font-weight: normal;
  color: var(--zw-steel-text-faint);
}

.node-bar-track {
  width: 100%;
  height: 6px;
  background: var(--zw-steel-line);
  border-radius: 1px;
  overflow: hidden;
  margin-bottom: 4px;
}

.node-bar-fill {
  height: 100%;
  background: var(--zw-info);
}

.node-bar-fill.normal { background: var(--zw-info); }
.node-bar-fill.warning { background: var(--zw-warning); }
.node-bar-fill.danger { background: var(--zw-danger); }

.node-ratio {
  font-size: 11px;
  color: var(--zw-steel-text-faint);
}

.trend-section {
  border-top: 1px solid var(--zw-steel-line-strong);
  padding-top: 14px;
}

.section-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--zw-steel-text-faint);
  margin-bottom: 8px;
}

.trend-empty-wrap {
  padding: 10px 0;
}
</style>
