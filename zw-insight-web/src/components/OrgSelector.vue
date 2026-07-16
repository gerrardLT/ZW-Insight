<template>
  <el-tree-select
    v-model="modelValue"
    :data="options"
    :props="treeProps"
    node-key="id"
    placeholder="请选择部门"
    check-strictly
    filterable
    clearable
    :loading="loading"
    :style="{ width: width }"
    @change="handleChange"
  />
</template>

<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { getOrgTree } from '@/api/system'

const props = withDefaults(defineProps<{
  modelValue?: number
  width?: string
}>(), {
  width: '100%'
})

const emit = defineEmits<{
  (e: 'update:modelValue', value: number | undefined): void
  (e: 'change', value: number | undefined): void
}>()

const modelValue = ref(props.modelValue)
const loading = ref(false)
const options = ref<any[]>([])
const treeProps = { label: 'orgName', children: 'children' }

watch(() => props.modelValue, (val) => {
  modelValue.value = val
})

async function loadData() {
  loading.value = true
  try {
    const res: any = await getOrgTree()
    options.value = res.data || []
  } finally {
    loading.value = false
  }
}

function handleChange(val: number | undefined) {
  emit('update:modelValue', val)
  emit('change', val)
}

onMounted(() => {
  loadData()
})
</script>
