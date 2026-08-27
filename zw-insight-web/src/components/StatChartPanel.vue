<template>
  <el-card shadow="never" class="stat-chart-panel">
    <template #header>
      <div class="panel-header">
        <span class="panel-title">{{ title }}</span>
        <el-button link size="small" :loading="loading" @click="load">
          <el-icon><Refresh /></el-icon>刷新
        </el-button>
      </div>
    </template>
    <div class="panel-body">
      <!-- 失败态：显示后端错误消息（空数据的业务提示同样由此透传），提供重试 -->
      <div v-if="errorMsg" class="panel-state" data-testid="stat-panel-error">
        <el-empty :description="errorMsg">
          <template #image>
            <HelmetIcon class="zw-empty-icon" />
          </template>
          <el-button type="primary" size="small" @click="load">重试</el-button>
        </el-empty>
      </div>
      <!-- 空态：接口成功但无可绘制数据 -->
      <div v-else-if="isEmpty" class="panel-state" data-testid="stat-panel-empty">
        <el-empty :description="emptyText">
          <template #image>
            <BlueprintCornerIcon class="zw-empty-icon" />
          </template>
        </el-empty>
      </div>
      <!-- 图表容器常驻 DOM（v-show），避免 echarts 重复 init -->
      <div
        v-show="!errorMsg && !isEmpty"
        ref="chartRef"
        v-loading="loading"
        class="chart-box"
        :style="{ height: height }"
      ></div>
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
import { ref, watch, nextTick, onMounted, onBeforeUnmount } from 'vue'
import * as echarts from 'echarts'
import { Refresh } from '@/components/icons/registry'
import { BlueprintCornerIcon, HelmetIcon } from '@/components/icons/zw'
import { useAppStore } from '@/stores/app'
import { pickChartTheme, applyChartTheme } from '@/constants/chart-theme'

const appStore = useAppStore()

const props = withDefaults(defineProps<{
  /** 面板标题 */
  title: string
  /** 图表高度 */
  height?: string
  /** 空态文案 */
  emptyText?: string
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

.panel-title {
  font-weight: var(--zw-font-weight-semibold, 600);
  color: var(--zw-text-primary, #1d2129);
}

.panel-state {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 220px;
}
</style>
