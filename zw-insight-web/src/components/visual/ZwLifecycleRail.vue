<template>
  <div class="zw-lifecycle-rail">
    <div class="rail-header">
      <div class="domain-tag">
        <span class="domain-badge">PRJ</span>
        <span class="title">项目全生命周期阶段图（当前视图）</span>
      </div>
      <div class="header-tip">
        <span class="tip-dot"></span>
        <span class="tip-text">数据源自工程实体各模块实时状态，无历史契约时不推算伪造历史时间戳</span>
      </div>
    </div>

    <!-- 阶段轨道 -->
    <div class="stage-track">
      <div
        v-for="(stage, idx) in stages"
        :key="stage.key"
        class="stage-node"
        :class="[stage.status, { 'is-active': stage.key === currentStageKey }]"
      >
        <div class="node-step mono">0{{ idx + 1 }}</div>
        <div class="node-content">
          <div class="node-name">{{ stage.name }}</div>
          <div class="node-desc">{{ stage.description }}</div>
          <div class="node-badge" :class="stage.status">
            {{ getStatusText(stage.status) }}
          </div>
        </div>
        <div v-if="idx < stages.length - 1" class="stage-connector"></div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

export interface LifecycleProps {
  currentStageKey?: 'INIT' | 'CONTRACT' | 'PLAN' | 'EXEC' | 'SETTLE' | 'ARCHIVE'
  projectStatus?: string | number
}

const props = withDefaults(defineProps<LifecycleProps>(), {
  currentStageKey: 'EXEC'
})

const stages = computed(() => {
  const list = [
    { key: 'INIT', name: '项目立项', description: '勘察报备与立项审批' },
    { key: 'CONTRACT', name: '合同订立', description: '主合同与支出合同体系' },
    { key: 'PLAN', name: '方案计划', description: 'WBS工作分解与进度基线' },
    { key: 'EXEC', name: '施工履约', description: '机械/材料/劳务现场执行' },
    { key: 'SETTLE', name: '产值结算', description: '产值确认与工程结算' },
    { key: 'ARCHIVE', name: '竣工归档', description: '质保金管理与资料归档' }
  ]

  const keyOrder = ['INIT', 'CONTRACT', 'PLAN', 'EXEC', 'SETTLE', 'ARCHIVE']
  const currentIndex = keyOrder.indexOf(props.currentStageKey)

  return list.map((item, idx) => {
    let status: 'completed' | 'active' | 'pending' = 'pending'
    if (idx < currentIndex) {
      status = 'completed'
    } else if (idx === currentIndex) {
      status = 'active'
    }
    return { ...item, status }
  })
})

function getStatusText(status: string) {
  switch (status) {
    case 'completed': return '已就绪'
    case 'active': return '当前阶段'
    default: return '待流转'
  }
}
</script>

<style scoped>
.zw-lifecycle-rail {
  border: 1px solid var(--zw-steel-line-strong);
  background: var(--zw-steel-bg);
  padding: 20px;
  color: var(--zw-steel-text);
}

.rail-header {
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
  /* V1 生命周期总图属 project 域（与 business-visual.ts / --zw-domain-* 同源） */
  background: var(--zw-domain-project);
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
}

.header-tip {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--zw-steel-text-faint);
}

.tip-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--zw-brand);
}

.stage-track {
  display: grid;
  grid-template-columns: repeat(6, 1fr);
  gap: 12px;
  position: relative;
}

.stage-node {
  position: relative;
  background: var(--zw-steel-fill-faint);
  border: 1px solid var(--zw-steel-line-strong);
  padding: 14px 12px;
  display: flex;
  flex-direction: column;
  box-sizing: border-box;
}

.stage-node.is-active {
  border-color: var(--zw-brand);
  background: color-mix(in srgb, var(--zw-brand) 5%, transparent);
}

.stage-node.completed {
  border-color: var(--zw-success);
}

.node-step {
  font-size: 16px;
  font-weight: 800;
  color: var(--zw-steel-text-faint);
  margin-bottom: 6px;
}

.stage-node.is-active .node-step { color: var(--zw-brand); }
.stage-node.completed .node-step { color: var(--zw-success); }

.node-name {
  font-size: 13px;
  font-weight: 700;
  margin-bottom: 4px;
}

.node-desc {
  font-size: 11px;
  color: var(--zw-steel-text-faint);
  line-height: 1.4;
  margin-bottom: 10px;
  min-height: 30px;
}

.node-badge {
  display: inline-block;
  font-size: 10px;
  font-weight: 700;
  padding: 2px 6px;
  border-radius: 2px;
  align-self: flex-start;
}

.node-badge.completed {
  background: color-mix(in srgb, var(--zw-success) 15%, transparent);
  color: var(--zw-success);
}

.node-badge.active {
  background: color-mix(in srgb, var(--zw-brand) 15%, transparent);
  color: var(--zw-brand);
}

.node-badge.pending {
  background: var(--zw-steel-fill-soft);
  color: var(--zw-steel-text-faint);
}

.mono {
  font-family: var(--zw-font-mono, monospace);
}
</style>
