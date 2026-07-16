<template>
  <el-select
    v-model="modelValue"
    placeholder="请选择分包合同"
    filterable
    clearable
    :loading="loading"
    :style="{ width: width }"
    @change="handleChange"
  >
    <el-option
      v-for="item in options"
      :key="item.id"
      :label="item.contractName"
      :value="item.id"
    />
  </el-select>
</template>

<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { getSubcontractPage } from '@/api/subcontract'

const props = withDefaults(defineProps<{
  modelValue?: number
  width?: string
  projectId?: number
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

watch(() => props.projectId, () => {
  loadData()
})

async function loadData() {
  loading.value = true
  try {
    const params: any = { page: 1, size: 100 }
    if (props.projectId) params.projectId = props.projectId
    const res: any = await getSubcontractPage(params)
    options.value = res.data?.records || res.data || []
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
  loadData()
})
</script>
