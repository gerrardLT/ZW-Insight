<template>
  <el-select
    v-model="selected"
    multiple
    filterable
    remote
    :remote-method="handleSearch"
    :loading="loading"
    :placeholder="placeholder"
    :style="{ width }"
    clearable
    @change="handleChange"
  >
    <el-option v-for="u in options" :key="u.id" :label="labelOf(u)" :value="u.id" />
  </el-select>
</template>

<script setup lang="ts">
/**
 * UserMultiSelector — 通用选人组件（抄送人等场景）。
 * 数据源为 GET /v1/system/user/candidates（本租户启用用户的轻量视图），
 * 不暴露手机号/邮箱等敏感字段。
 * 雪花 ID 超出 JS 安全整数范围（Jackson 全局序列化为 String），
 * 因此 ID 全程按字符串承载，禁止 Number() 转换（否则精度丢失会抄错人）。
 */
import { ref, onMounted, watch } from 'vue'
import { getUserCandidates } from '@/api/system'

const props = withDefaults(defineProps<{
  modelValue?: Array<number | string>
  placeholder?: string
  width?: string
}>(), {
  modelValue: () => [],
  placeholder: '选择抄送人（可选）',
  width: '100%'
})

const emit = defineEmits<{
  (e: 'update:modelValue', value: string[]): void
}>()

const selected = ref<Array<number | string>>([...(props.modelValue || [])])
const loading = ref(false)
const options = ref<any[]>([])

watch(() => props.modelValue, (val) => {
  selected.value = [...(val || [])]
})

function labelOf(user: any) {
  return user.realName ? `${user.realName}（${user.username}）` : user.username
}

async function handleSearch(keyword: string) {
  loading.value = true
  try {
    const res: any = await getUserCandidates({ keyword, limit: 50 })
    options.value = res.data || []
  } finally {
    loading.value = false
  }
}

function handleChange(value: Array<number | string>) {
  // 雪花 ID 以字符串原样传递（后端 List<Long> 由 Jackson 转换），仅去重不做数值转换
  const ids = Array.from(new Set((value || []).map(v => String(v))))
  emit('update:modelValue', ids)
}

onMounted(() => handleSearch(''))
</script>
