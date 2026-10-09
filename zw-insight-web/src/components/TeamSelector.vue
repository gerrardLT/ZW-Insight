<template>
  <el-select
    v-model="modelValue"
    placeholder="请选择班组"
    filterable
    clearable
    :loading="loading"
    :style="{ width: width }"
    @change="handleChange"
  >
    <el-option
      v-for="item in options"
      :key="item.id"
      :label="item.teamName"
      :value="item.id"
    />
  </el-select>
</template>

<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { getLaborTeamPage } from '@/api/labor'

const props = withDefaults(defineProps<{
  modelValue?: number | string
  projectId?: number | string
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

watch(() => props.projectId, () => {
  loadData()
})

async function loadData() {
  loading.value = true
  try {
    const res: any = await getLaborTeamPage({ page: 1, size: 100, projectId: props.projectId })
    options.value = res.data?.records || res.data || []
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
  loadData()
})
</script>
