<template>
  <el-select
    v-model="modelValue"
    :placeholder="placeholder"
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
import { useProjectContext } from '@/composables/useProjectContext'

// id 类型说明：后端雪花 ID 超出 JS Number.MAX_SAFE_INTEGER，Jackson 序列化为 string，
// 故 modelValue/emit 均为 number | string（2026-09-16 实证：项目看板卡片回传 string id 触发类型告警）
const props = withDefaults(defineProps<{
  modelValue?: number | string
  width?: string
  placeholder?: string
  /** 挂载时是否回落全局「当前项目」上下文；顶栏切换器自身须传 false，避免自我回写 */
  autoDefault?: boolean
}>(), {
  width: '100%',
  placeholder: '请选择项目',
  autoDefault: true
})

const emit = defineEmits<{
  (e: 'update:modelValue', value: number | string | undefined): void
  (e: 'change', value: number | string | undefined, item: any): void
}>()

const { currentProjectId } = useProjectContext()

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
  const item = options.value.find(o => String(o.id) === String(val))
  emit('change', val, item)
}

onMounted(() => {
  handleSearch('')
  // 全局「当前项目」回落：仅当调用方未传值时采用一次。
  // 只读上下文、不回写全局，故与顶栏切换器之间不会形成回环。
  if (props.autoDefault && (modelValue.value === undefined || modelValue.value === null || modelValue.value === '')) {
    const ctxId = currentProjectId.value
    if (ctxId != null) {
      modelValue.value = ctxId
      emit('update:modelValue', ctxId)
    }
  }
})
</script>
