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
}>(), {
  width: '100%'
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
  const item = options.value.find(o => o.id === val)
  emit('change', val, item)
}

onMounted(() => {
  handleSearch('')
})
</script>
