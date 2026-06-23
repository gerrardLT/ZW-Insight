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

const props = withDefaults(defineProps<{
  modelValue?: number
  width?: string
}>(), {
  width: '100%'
})

const emit = defineEmits<{
  (e: 'update:modelValue', value: number | undefined): void
  (e: 'change', value: number | undefined, item: any): void
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

function handleChange(val: number | undefined) {
  emit('update:modelValue', val)
  const item = options.value.find(o => o.id === val)
  emit('change', val, item)
}

onMounted(() => {
  handleSearch('')
})
</script>
