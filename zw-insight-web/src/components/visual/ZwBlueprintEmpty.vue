<template>
  <div class="zw-blueprint-empty" :class="[size, { 'bordered': bordered }]">
    <div class="blueprint-canvas">
      <!-- 工业蓝图网格背景 -->
      <div class="grid-layer"></div>
      
      <!-- 矢量蓝图主体插画 -->
      <svg class="blueprint-svg" viewBox="0 0 160 120" fill="none" xmlns="http://www.w3.org/2000/svg">
        <!-- 蓝图底板边框 -->
        <rect x="10" y="10" width="140" height="100" rx="2" stroke="currentColor" stroke-width="1.5" stroke-dasharray="4 2" class="base-frame" />
        <line x1="10" y1="24" x2="150" y2="24" stroke="currentColor" stroke-width="1" class="header-line" />
        
        <!-- 工程标尺刻度 -->
        <line x1="20" y1="10" x2="20" y2="15" stroke="currentColor" stroke-width="1" />
        <line x1="40" y1="10" x2="40" y2="15" stroke="currentColor" stroke-width="1" />
        <line x1="60" y1="10" x2="60" y2="15" stroke="currentColor" stroke-width="1" />
        <line x1="80" y1="10" x2="80" y2="15" stroke="currentColor" stroke-width="1" />
        <line x1="100" y1="10" x2="100" y2="15" stroke="currentColor" stroke-width="1" />
        <line x1="120" y1="10" x2="120" y2="15" stroke="currentColor" stroke-width="1" />
        <line x1="140" y1="10" x2="140" y2="15" stroke="currentColor" stroke-width="1" />

        <!-- 针对不同场景的符号插画 -->
        <g v-if="type === 'trend'" class="symbol-trend">
          <path d="M30 85 L60 65 L95 75 L130 45" stroke="#ff6b00" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" stroke-dasharray="3 3" />
          <circle cx="30" cy="85" r="3" fill="#ff6b00" />
          <circle cx="60" cy="65" r="3" fill="#ff6b00" />
          <circle cx="95" cy="75" r="3" fill="#ff6b00" />
          <circle cx="130" cy="45" r="3" fill="#ff6b00" />
          <line x1="25" y1="95" x2="135" y2="95" stroke="currentColor" stroke-width="1.5" />
        </g>
        <g v-else-if="type === 'cost'" class="symbol-cost">
          <rect x="35" y="45" width="20" height="45" fill="currentColor" opacity="0.15" />
          <rect x="70" y="35" width="20" height="55" fill="#ff6b00" opacity="0.25" />
          <rect x="105" y="55" width="20" height="35" fill="currentColor" opacity="0.15" />
          <line x1="25" y1="90" x2="135" y2="90" stroke="currentColor" stroke-width="1.5" />
        </g>
        <g v-else class="symbol-general">
          <polygon points="80,35 115,95 45,95" stroke="currentColor" stroke-width="1.5" stroke-dasharray="3 2" />
          <line x1="80" y1="52" x2="80" y2="76" stroke="#ff6b00" stroke-width="2.5" stroke-linecap="round" />
          <circle cx="80" cy="85" r="1.5" fill="#ff6b00" />
        </g>

        <!-- 右下角工程测绘十字丝 -->
        <path d="M135 95 L145 95 M140 90 L140 100" stroke="currentColor" stroke-width="1" opacity="0.7" />
      </svg>
    </div>

    <!-- 文本叙事 -->
    <div class="empty-content">
      <div class="empty-title">{{ title || defaultTitle }}</div>
      <div class="empty-description" v-if="description || defaultDesc">{{ description || defaultDesc }}</div>
      <div class="empty-actions" v-if="$slots.action">
        <slot name="action"></slot>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(
  defineProps<{
    type?: 'general' | 'trend' | 'cost' | 'offline'
    title?: string
    description?: string
    size?: 'sm' | 'md' | 'lg'
    bordered?: boolean
  }>(),
  {
    type: 'general',
    size: 'md',
    bordered: false
  }
)

const defaultTitle = computed(() => {
  switch (props.type) {
    case 'trend':
      return '暂无月度历史数据能力'
    case 'cost':
      return '暂无成本控制数据'
    case 'offline':
      return '暂无可用离线数据'
    default:
      return '暂无数据记录'
  }
})

const defaultDesc = computed(() => {
  switch (props.type) {
    case 'trend':
      return '该项目暂未归集月度成本流水，系统拒绝插值伪造数据'
    case 'cost':
      return '项目未关联或未编制 CBS 成本账户'
    case 'offline':
      return '本地无历史缓存，请在网络畅通时进行数据同步'
    default:
      return '当前筛选条件下未检索到相关工程记录'
  }
})
</script>

<style scoped>
.zw-blueprint-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 32px 16px;
  color: var(--zw-steel-text-faint);
  text-align: center;
}

.zw-blueprint-empty.bordered {
  border: 1px dashed var(--zw-steel-line-strong);
  background: var(--zw-steel-fill-faint);
}

.zw-blueprint-empty.sm {
  padding: 16px 8px;
}
.zw-blueprint-empty.sm .blueprint-svg {
  width: 110px;
  height: 82px;
}

.zw-blueprint-empty.md .blueprint-svg {
  width: 160px;
  height: 120px;
}

.zw-blueprint-empty.lg {
  padding: 48px 24px;
}
.zw-blueprint-empty.lg .blueprint-svg {
  width: 220px;
  height: 165px;
}

.blueprint-canvas {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--zw-steel-text-faint);
  opacity: 0.85;
}

.empty-content {
  margin-top: 14px;
  max-width: 380px;
}

.empty-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--zw-steel-text);
  letter-spacing: 0.02em;
}

.empty-description {
  margin-top: 6px;
  font-size: 12px;
  color: var(--zw-steel-text-faint);
  line-height: 1.5;
}

.empty-actions {
  margin-top: 16px;
}
</style>
