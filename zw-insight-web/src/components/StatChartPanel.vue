<template>
  <el-card shadow="never" class="stat-chart-panel card-corner-marked">
    <template #header>
      <div class="panel-header">
        <div class="panel-title-wrap">
          <div class="panel-eyebrow">METRICS // MEASUREMENT</div>
          <span class="panel-title">{{ title }}</span>
        </div>
        <div class="header-actions">
          <div v-if="hazardRibbon" class="hazard-badge">
            <span class="hazard-stripe"></span>
            <span class="hazard-text">{{ hazardRibbon }}</span>
          </div>
          <el-button link size="small" :loading="loading" @click="load">
            <el-icon><Refresh /></el-icon>刷新
          </el-button>
        </div>
      </div>
    </template>
    <div class="panel-body">
      <!-- 失败态：显示后端错误消息（空数据的业务提示同样由此透传），提供重试 -->
      <div v-if="errorMsg" class="panel-state" data-testid="stat-panel-error">
        <ZwEmptyState type="error" :description="errorMsg">
          <template #action>
            <el-button type="primary" size="small" @click="load">重试</el-button>
          </template>
        </ZwEmptyState>
      </div>
      <!-- 空态：接口成功但无可绘制数据（bespoke 插画：空白图纸+圆规——「等待绘制」隐喻，经 #image slot 注入） -->
      <div v-else-if="isEmpty" class="panel-state" data-testid="stat-panel-empty">
        <ZwEmptyState type="data" :description="emptyText">
          <template #image>
            <img :src="emptyImg" class="zw-empty-img" alt="空白图纸与圆规插图" />
          </template>
        </ZwEmptyState>
      </div>
      <!-- 图表容器常驻 DOM（v-show），避免 echarts 重复 init -->
      <div
        v-show="!errorMsg && !isEmpty"
        ref="chartRef"
        v-loading="loading"
        class="chart-box"
        :style="{ height: height }"
      ></div>
      <!-- 首次加载骨架屏：覆盖图表区域，带蓝图机械网格与激光扫描动效 -->
      <div
        v-if="loading && !hasLoaded"
        class="blueprint-skeleton-overlay"
        data-testid="stat-panel-skeleton"
        :style="{ height: height }"
      >
        <div class="blueprint-grid"></div>
        <div class="blueprint-axis-y">
          <span>100%</span>
          <span>75%</span>
          <span>50%</span>
          <span>25%</span>
          <span>0%</span>
        </div>
        <div class="blueprint-scanner"></div>
        <div class="blueprint-rows">
          <div class="skeleton-bar" style="height: 60%; width: 12%;"></div>
          <div class="skeleton-bar" style="height: 85%; width: 12%;"></div>
          <div class="skeleton-bar" style="height: 45%; width: 12%;"></div>
          <div class="skeleton-bar" style="height: 70%; width: 12%;"></div>
          <div class="skeleton-bar" style="height: 90%; width: 12%;"></div>
        </div>
        <div class="blueprint-caption">CALIBRATING INDUSTRIAL METRICS // 正在校准图表数据</div>
      </div>
    </div>
  </el-card>
</template>

<script setup lang="ts">
/**
 * 通用统计图表卡片（P0 差距收口 T7）
 *
 * 统一承接统计面板的加载/失败/空态：
 * - fetchData 抛错（含后端 BusinessException 的"暂无数据"提示）→ 失败态 + 重试，不静默
 * - buildOption 返回 null → 空态 el-empty
 * - 成功 → echarts setOption（notMerge=true 防残留序列）
 */
import { ref, computed, watch, nextTick, onMounted, onBeforeUnmount } from 'vue'
import * as echarts from 'echarts'
import { Refresh } from '@/components/icons/registry'
import ZwEmptyState from '@/components/ZwEmptyState.vue'
import { useAppStore } from '@/stores/app'
import emptyLight from '@/assets/empty-blueprint.png'
import emptyDark from '@/assets/empty-blueprint-dark.png'
import { pickChartTheme, applyChartTheme } from '@/constants/chart-theme'

const appStore = useAppStore()
const emptyImg = computed(() => (appStore.isDark ? emptyDark : emptyLight))

const props = withDefaults(defineProps<{
  /** 面板标题 */
  title: string
  /** 图表高度 */
  height?: string
  /** 空态文案 */
  emptyText?: string
  /** 危险预警条文案（例如超支预警/风险告警） */
  hazardRibbon?: string
  /** 数据加载函数，返回业务数据（res.data 解包由调用方完成），失败抛 Error */
  fetchData: () => Promise<any>
  /** 由数据构建 echarts option；返回 null 表示空数据，展示空态 */
  buildOption: (data: any) => any | null
}>(), {
  height: '300px',
  emptyText: '暂无统计数据'
})

const loading = ref(false)
const errorMsg = ref('')
const isEmpty = ref(false)
/** 是否完成过一次加载：首次加载展示骨架屏，后续刷新用 v-loading 遮罩防闪烁 */
const hasLoaded = ref(false)
const chartRef = ref<HTMLElement>()
let chart: echarts.ECharts | null = null
/** 最近一次成功加载的业务数据：暗色/亮色切换时不重复请求，直接重绘 */
let lastData: any = null

/** 由数据构建并应用当前主题的 option；返回 null 表示空数据 */
function buildThemedOption(data: any) {
  const option = props.buildOption(data)
  if (!option) return null
  return applyChartTheme(option, pickChartTheme(appStore.isDark))
}

async function load() {
  loading.value = true
  errorMsg.value = ''
  isEmpty.value = false
  try {
    const data = await props.fetchData()
    const option = buildThemedOption(data)
    if (!option) {
      isEmpty.value = true
      lastData = null
      return
    }
    lastData = data
    await nextTick()
    if (!chartRef.value) return
    if (!chart || chart.isDisposed()) {
      chart = echarts.init(chartRef.value)
    }
    chart.setOption(option, true)
  } catch (e: any) {
    // 不静默：错误消息直接展示（后端空数据的业务提示也走此通道）
    errorMsg.value = e?.message || '加载统计数据失败'
    lastData = null
  } finally {
    loading.value = false
    hasLoaded.value = true
  }
}

// 主题切换即时重绘（echarts 为 canvas 绘制，颜色在 option 创建时固化，需重建）
watch(() => appStore.isDark, () => {
  if (!lastData || errorMsg.value || isEmpty.value || !chart || chart.isDisposed()) return
  const option = buildThemedOption(lastData)
  if (option) chart.setOption(option, true)
})

function handleResize() {
  chart?.resize()
}

onMounted(() => {
  load()
  window.addEventListener('resize', handleResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  chart?.dispose()
  chart = null
})

defineExpose({ reload: load })
</script>

<style scoped>
.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

/* 工业危险预警带 */
.hazard-badge {
  display: inline-flex;
  align-items: center;
  height: 24px;
  padding: 0 8px;
  background: var(--zw-warning-light);
  border: 1px solid var(--zw-warning);
  border-radius: var(--zw-radius-xs);
  gap: 6px;
}

.hazard-stripe {
  width: 8px;
  height: 14px;
  background: repeating-linear-gradient(
    45deg,
    var(--zw-hazard-black) 0 4px,
    var(--zw-hazard-yellow) 4px 8px
  );
}

.hazard-text {
  font-size: 11px;
  font-weight: 700;
  color: var(--zw-text-primary);
  letter-spacing: 0.5px;
}

.panel-title-wrap {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.panel-eyebrow {
  font-family: var(--zw-font-display);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 1px;
  text-transform: uppercase;
  color: var(--zw-text-tertiary);
}

.panel-title {
  font-weight: var(--zw-font-weight-semibold, 600);
  color: var(--zw-text-primary);
}

.panel-state {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 220px;
}

.panel-body {
  position: relative;
}

/* 蓝图机械网格骨架屏 */
.blueprint-skeleton-overlay {
  position: absolute;
  inset: 0;
  z-index: 2;
  background: var(--zw-bg-card);
  overflow: hidden;
  border: 1px dashed var(--zw-border);
  box-sizing: border-box;
}

/* 蓝图网格底纹 */
.blueprint-grid {
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(to right, color-mix(in srgb, var(--zw-brand) 5%, transparent) 1px, transparent 1px),
    linear-gradient(to bottom, color-mix(in srgb, var(--zw-brand) 5%, transparent) 1px, transparent 1px);
  background-size: 20px 20px;
}

/* 纵轴标尺 */
.blueprint-axis-y {
  position: absolute;
  left: 8px;
  top: 16px;
  bottom: 32px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  font-family: var(--zw-font-mono);
  font-size: 10px;
  color: var(--zw-text-quaternary);
  user-select: none;
}

/* 扫描激光束 */
.blueprint-scanner {
  position: absolute;
  top: 0;
  bottom: 0;
  width: 40px;
  background: linear-gradient(90deg, transparent, color-mix(in srgb, var(--zw-brand) 15%, transparent), transparent);
  animation: laser-sweep 2s infinite linear;
}

@keyframes laser-sweep {
  0% { left: -40px; }
  100% { left: 100%; }
}

.blueprint-rows {
  position: absolute;
  left: 60px;
  right: 24px;
  bottom: 40px;
  top: 30px;
  display: flex;
  align-items: flex-end;
  justify-content: space-around;
}

.skeleton-bar {
  background: var(--zw-bg-hover);
  border: 1px solid var(--zw-border-light);
  border-radius: var(--zw-radius-xs);
  transition: all 0.3s;
}

.blueprint-caption {
  position: absolute;
  bottom: 10px;
  left: 0;
  right: 0;
  text-align: center;
  font-family: var(--zw-font-mono);
  font-size: 11px;
  font-weight: 600;
  color: var(--zw-brand);
  letter-spacing: 1px;
}
</style>
