<template>
  <el-select
    v-model="modelValue"
    placeholder="请选择供应商"
    filterable
    remote
    :remote-method="handleSearch"
    :loading="loading"
    clearable
    :style="{ width: width }"
    @change="handleChange"
  >
    <el-option v-for="item in options" :key="item.id" :label="item.supplierName" :value="item.id" />
  </el-select>
</template>

<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import request from '@/utils/request'

const props = withDefaults(defineProps<{
  modelValue?: number
  width?: string
  supplierType?: string
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
    const params: any = { supplierName: query }
    if (props.supplierType) params.supplierType = props.supplierType
    const res: any = await request.get('/v1/basedata/supplier/list', { params })
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
