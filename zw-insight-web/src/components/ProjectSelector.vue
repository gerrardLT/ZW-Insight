<template>
  <el-select
    v-model="modelValue"
    placeholder="请选择项目"
    filterable
    remote
    :remote-method="handleSearch"
    :loading="loading"
    clearable
    :style="{ width: width }"
    @change="handleChange"
  >
    <el-option v-for="item in options" :key="item.id" :label="item.projectName" :value="item.id" />
  </el-select>
</template>

<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { getProjectList } from '@/api/project'

// id 类型说明：后端雪花 ID 超出 JS Number.MAX_SAFE_INTEGER，Jackson 序列化为 string，
// 故 modelValue/emit 均为 number | string（2026-09-16 实证：项目看板卡片回传 string id 触发类型告警）
const props = withDefaults(defineProps<{
  modelValue?: number | string
  width?: string
  /**
   * 挂载时若未选值，自动选中项目列表首项并触发 change。
   * 仅用于「必须选项目才能渲染」的页面（成本看板/项目看板/成本中心/月度分析/甘特图等），
   * 避免进入页面出现「请先选择项目」的空白引导；列表类页面不要开启（需保持「全部项目」）。
   */
  defaultFirst?: boolean
}>(), {
  width: '100%',
  defaultFirst: false
})

const emit = defineEmits<{
  (e: 'update:modelValue', value: number | string | undefined): void
  (e: 'change', value: number | string | undefined, item: any): void
}>()

const modelValue = ref(props.modelValue)
const loading = ref(false)
const options = ref<any[]>([])

watch(() => props.modelValue, (val) => {
  modelValue.value = val
})

async function handleSearch(query: string) {
  loading.value = true
  try {
    const res: any = await getProjectList({ projectName: query })
    options.value = res.data || []
  } finally {
    loading.value = false
  }
}

function handleChange(val: number | string | undefined) {
  emit('update:modelValue', val)
  // 雪花 ID 经 Jackson 序列化为 string，两端统一按字符串比较，避免 number/string 严格比较失配
  const item = options.value.find(o => String(o.id) === String(val))
  emit('change', val, item)
}

onMounted(async () => {
  await handleSearch('')
  // 必须选项目才能渲染的页面：未选值时默认选中首项并触发 change，避免空白引导
  if (props.defaultFirst && (modelValue.value === undefined || modelValue.value === null || modelValue.value === '')) {
    const first = options.value[0]
    if (first) {
      modelValue.value = first.id
      emit('update:modelValue', first.id)
      emit('change', first.id, first)
    }
  }
})
</script>
