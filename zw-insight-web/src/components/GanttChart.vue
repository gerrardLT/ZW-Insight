<template>
  <div class="gantt-chart-wrapper">
    <!-- 工具栏：时间轴缩放 -->
    <div class="gantt-toolbar">
      <el-radio-group v-model="currentScale" size="small" @change="handleScaleChange">
        <el-radio-button value="day">日</el-radio-button>
        <el-radio-button value="week">周</el-radio-button>
        <el-radio-button value="month">月</el-radio-button>
      </el-radio-group>
      <el-switch
        v-model="showCriticalPath"
        active-text="关键路径"
        inactive-text=""
        style="margin-left: 16px"
        @change="handleCriticalPathToggle"
      />
    </div>
    <!-- 甘特图容器 -->
    <div ref="ganttContainer" class="gantt-container"></div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { gantt } from 'dhtmlx-gantt'
import 'dhtmlx-gantt/codebase/dhtmlxgantt.css'
import { getSchedulePlanTree, updateSchedulePlanDates } from '@/api/site'

// ======================== Props ========================
interface GanttChartProps {
  projectId: number
  editable?: boolean
}

const props = withDefaults(defineProps<GanttChartProps>(), {
  editable: false
})

const emit = defineEmits<{
  (e: 'task-updated', id: number): void
}>()

// ======================== 响应式状态 ========================
const ganttContainer = ref<HTMLElement | null>(null)
const currentScale = ref<'day' | 'week' | 'month'>('day')
const showCriticalPath = ref(true)

// ======================== 状态颜色映射 ========================
const STATUS_CLASS: Record<string, string> = {
  NOT_STARTED: 'gantt-task-not-started',
  IN_PROGRESS: 'gantt-task-in-progress',
  COMPLETED: 'gantt-task-completed',
  DELAYED: 'gantt-task-delayed'
}

// ======================== 数据格式转换 ========================

/** 解析 predecessors 字符串 → dhtmlx-gantt links 数组 */
function parsePredecessors(tasks: any[]): any[] {
  const links: any[] = []
  let linkId = 1

  // 依赖类型映射: FS=0, SS=1, FF=2, SF=3
  const typeMap: Record<string, string> = { FS: '0', SS: '1', FF: '2', SF: '3' }

  for (const task of tasks) {
    if (!task.predecessors) continue
    // predecessors 格式: "2FS,3SS" 或 "2FS"
    const parts = task.predecessors.split(',').map((s: string) => s.trim())
    for (const part of parts) {
      // 匹配数字 + 可选类型标识
      const match = part.match(/^(\d+)(FS|SS|FF|SF)?$/)
      if (match) {
        const sourceId = parseInt(match[1])
        const linkType = typeMap[match[2] || 'FS'] || '0'
        links.push({
          id: linkId++,
          source: sourceId,
          target: task.id,
          type: linkType
        })
      }
    }
  }

  return links
}

/** 后端数据 → dhtmlx-gantt 数据格式 */
function transformData(backendData: any[]): { data: any[]; links: any[] } {
  const tasks = backendData.map((item: any) => ({
    id: item.id,
    text: item.taskName,
    start_date: item.planStartDate,
    end_date: item.planEndDate,
    progress: item.progress || 0,
    parent: item.parentId || 0,
    open: true,
    // 自定义字段用于状态颜色
    status: item.status || 'NOT_STARTED',
    actual_start: item.actualStartDate || ''
  }))

  const links = parsePredecessors(backendData)

  return { data: tasks, links }
}

// ======================== 时间轴缩放配置 ========================
function applyScale(scale: 'day' | 'week' | 'month') {
  switch (scale) {
    case 'day':
      gantt.config.scale_unit = 'day'
      gantt.config.date_scale = '%m月%d日'
      gantt.config.step = 1
      gantt.config.min_column_width = 40
      gantt.config.subscales = [{ unit: 'month', step: 1, date: '%Y年%m月' }]
      break
    case 'week':
      gantt.config.scale_unit = 'week'
      gantt.config.date_scale = '第%W周'
      gantt.config.step = 1
      gantt.config.min_column_width = 60
      gantt.config.subscales = [{ unit: 'month', step: 1, date: '%Y年%m月' }]
      break
    case 'month':
      gantt.config.scale_unit = 'month'
      gantt.config.date_scale = '%Y年%m月'
      gantt.config.step = 1
      gantt.config.min_column_width = 80
      gantt.config.subscales = [{ unit: 'year', step: 1, date: '%Y年' }]
      break
  }
  gantt.render()
}

function handleScaleChange(val: string | number | boolean | undefined) {
  applyScale(val as 'day' | 'week' | 'month')
}

// ======================== 关键路径 ========================
function handleCriticalPathToggle(val: string | number | boolean) {
  gantt.config.highlight_critical_path = !!val
  gantt.render()
}

// ======================== 日期格式化工具 ========================
function formatDate(date: Date): string {
  const y = date.getFullYear()
  const m = String(date.getMonth() + 1).padStart(2, '0')
  const d = String(date.getDate()).padStart(2, '0')
  return `${y}-${m}-${d}`
}

// ======================== 初始化甘特图 ========================
function initGantt() {
  if (!ganttContainer.value) return

  // 启用关键路径插件
  gantt.plugins({
    critical_path: true
  })

  // 基础配置
  gantt.config.date_format = '%Y-%m-%d'
  gantt.config.highlight_critical_path = showCriticalPath.value
  gantt.config.show_progress = true
  gantt.config.fit_tasks = true
  gantt.config.auto_scheduling = false
  gantt.config.readonly = !props.editable

  // 拖拽配置
  gantt.config.drag_move = props.editable
  gantt.config.drag_resize = props.editable
  gantt.config.drag_progress = props.editable
  gantt.config.drag_links = false // 不允许拖拽创建连线

  // 列配置
  gantt.config.columns = [
    { name: 'text', label: '任务名称', tree: true, width: 200 },
    { name: 'start_date', label: '开始日期', align: 'center', width: 90 },
    { name: 'end_date', label: '结束日期', align: 'center', width: 90 },
    { name: 'progress', label: '进度', align: 'center', width: 60, template: (task: any) => Math.round(task.progress * 100) + '%' }
  ]

  // 状态颜色模板
  gantt.templates.task_class = function (_start: Date, _end: Date, task: any) {
    return STATUS_CLASS[task.status] || ''
  }

  // 关键路径连线样式
  gantt.templates.link_class = function (link: any) {
    if (gantt.isCriticalLink(link)) {
      return 'critical_link'
    }
    return ''
  }

  // 应用初始时间轴
  applyScale(currentScale.value)

  // 初始化
  gantt.init(ganttContainer.value)

  // 拖拽结束事件 → 保存至后端
  if (props.editable) {
    gantt.attachEvent('onAfterTaskDrag', async function (id: string | number) {
      const task = gantt.getTask(id)
      const startDate = task.start_date ? formatDate(task.start_date) : ''
      const endDate = task.end_date ? formatDate(task.end_date) : ''

      try {
        await updateSchedulePlanDates(Number(id), {
          planStartDate: startDate,
          planEndDate: endDate
        })
        ElMessage.success('进度已更新')
        emit('task-updated', Number(id))
      } catch {
        ElMessage.error('保存失败，请重试')
        // 重新加载数据恢复原状
        loadData()
      }
      return true
    })
  }

  // 加载数据
  loadData()
}

// ======================== 加载数据 ========================
async function loadData() {
  try {
    const res: any = await getSchedulePlanTree(props.projectId)
    const backendData = res.data || []
    const ganttData = transformData(backendData)
    gantt.clearAll()
    gantt.parse(ganttData)
  } catch {
    ElMessage.error('加载进度数据失败')
  }
}

/** 外部调用刷新 */
function refresh() {
  loadData()
}

defineExpose({ refresh })

// ======================== 生命周期 ========================
onMounted(() => {
  initGantt()
})

onBeforeUnmount(() => {
  gantt.clearAll()
})

// 监听 projectId 变化重新加载
watch(() => props.projectId, () => {
  if (props.projectId) {
    loadData()
  }
})
</script>

<style scoped>
.gantt-chart-wrapper {
  width: 100%;
  display: flex;
  flex-direction: column;
}

.gantt-toolbar {
  display: flex;
  align-items: center;
  margin-bottom: 12px;
  padding: 8px 0;
}

.gantt-container {
  width: 100%;
  height: 500px;
}
</style>

<style>
/* 状态颜色 - 全局样式（dhtmlx-gantt 内部结构无法用 scoped） */
.gantt-task-not-started .gantt_task_progress,
.gantt-task-not-started .gantt_task_content {
  background-color: #909399 !important;
}
.gantt-task-not-started .gantt_task_line {
  background-color: #909399 !important;
  border-color: #7d8087 !important;
}

.gantt-task-in-progress .gantt_task_progress,
.gantt-task-in-progress .gantt_task_content {
  background-color: #409EFF !important;
}
.gantt-task-in-progress .gantt_task_line {
  background-color: #409EFF !important;
  border-color: #3a8ee6 !important;
}

.gantt-task-completed .gantt_task_progress,
.gantt-task-completed .gantt_task_content {
  background-color: #67C23A !important;
}
.gantt-task-completed .gantt_task_line {
  background-color: #67C23A !important;
  border-color: #5daf34 !important;
}

.gantt-task-delayed .gantt_task_progress,
.gantt-task-delayed .gantt_task_content {
  background-color: #F56C6C !important;
}
.gantt-task-delayed .gantt_task_line {
  background-color: #F56C6C !important;
  border-color: #e65d5d !important;
}

/* 关键路径高亮 */
.critical_task .gantt_task_line {
  border: 2px solid #E6A23C !important;
  box-shadow: 0 0 4px rgba(230, 162, 60, 0.5);
}

.critical_link .gantt_line_wrapper div {
  background-color: #E6A23C !important;
}

.critical_link .gantt_link_arrow {
  border-color: #E6A23C !important;
}
</style>
