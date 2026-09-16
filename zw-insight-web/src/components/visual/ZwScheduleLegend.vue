<template>
  <div class="zw-schedule-legend">
    <div class="legend-title">
      <span class="domain-badge">WBS</span>
      <span>工程甘特进度图例与线型编码</span>
    </div>
    <div class="legend-items">
      <!-- 计划基准 -->
      <div class="legend-item">
        <span class="sample-bar baseline-bar"></span>
        <span class="legend-text">计划基准工期</span>
      </div>

      <!-- 实际完成 -->
      <div class="legend-item">
        <span class="sample-bar actual-bar"></span>
        <span class="legend-text">实际完成进度</span>
      </div>

      <!-- 超期延误 -->
      <div class="legend-item">
        <span class="sample-bar delay-bar"></span>
        <span class="legend-text">超期延误报警</span>
      </div>

      <!-- 关键路径 -->
      <div class="legend-item">
        <span class="sample-bar critical-bar"></span>
        <span class="legend-text">关键路径 (CPM)</span>
      </div>

      <!-- 交付里程碑 -->
      <div class="legend-item">
        <span class="sample-diamond milestone-diamond"></span>
        <span class="legend-text">关键工程里程碑</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
</script>

<style scoped>
.zw-schedule-legend {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 16px;
  background: var(--zw-steel-bg);
  border: 1px solid var(--zw-steel-line-strong);
  font-size: 12px;
  color: var(--zw-steel-text);
}

.legend-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 700;
}

.domain-badge {
  /* WBS 属 project 域，徽章回归域色编码（与 business-visual.ts / --zw-domain-* 同源） */
  background: var(--zw-domain-project);
  color: var(--zw-on-primary);
  font-family: var(--zw-font-mono, monospace);
  font-size: 10px;
  font-weight: 800;
  padding: 1px 5px;
  border-radius: 2px;
}

.legend-items {
  display: flex;
  align-items: center;
  gap: 20px;
}

.legend-item {
  display: flex;
  align-items: center;
  gap: 8px;
}

.sample-bar {
  display: inline-block;
  width: 28px;
  height: 8px;
  border-radius: 1px;
}

.baseline-bar {
  background: var(--zw-steel-fill-strong);
  border: 1px dashed var(--zw-steel-line-dashed);
}

.actual-bar {
  background: var(--zw-success);
}

.delay-bar {
  background: var(--zw-danger);
  background-image: repeating-linear-gradient(
    45deg,
    transparent,
    transparent 2px,
    color-mix(in srgb, var(--zw-hazard-black) 30%, transparent) 2px,
    color-mix(in srgb, var(--zw-hazard-black) 30%, transparent) 4px
  );
}

.critical-bar {
  background: var(--zw-brand);
  border: 1px solid var(--zw-steel-text);
}

.sample-diamond {
  display: inline-block;
  width: 10px;
  height: 10px;
  background: var(--zw-brand);
  transform: rotate(45deg);
}

.legend-text {
  font-size: 11px;
  color: var(--zw-steel-text-faint);
}
</style>
