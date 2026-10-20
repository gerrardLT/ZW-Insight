<template>
  <el-dialog
    :model-value="visible"
    :title="title"
    width="480px"
    destroy-on-close
    @update:model-value="emit('update:visible', $event)"
  >
    <p class="submit-tip">{{ message }}</p>
    <el-form label-width="80px">
      <el-form-item label="抄送人">
        <UserMultiSelector v-model="ccUserIds" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="emit('update:visible', false)">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="emit('confirm')">确认提交</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
/**
 * ApprovalSubmitDialog — 提交审批确认弹窗（含抄送人选择）。
 * 抄送人仅在调用方提交时传入 ccUserIds（后端 variables.ccUserIds → biz_workflow_cc），
 * 为空时行为与旧提交一致；不改变审批流程定义与审批人解析。
 */
import { computed } from 'vue'
import UserMultiSelector from '@/components/UserMultiSelector.vue'

const props = withDefaults(defineProps<{
  visible: boolean
  message: string
  title?: string
  submitting?: boolean
  ccUserIds?: string[]
}>(), {
  title: '提交审批',
  submitting: false,
  ccUserIds: () => []
})

const emit = defineEmits<{
  (e: 'update:visible', value: boolean): void
  (e: 'update:ccUserIds', value: string[]): void
  (e: 'confirm'): void
}>()

const ccUserIds = computed({
  get: () => props.ccUserIds,
  set: (value: string[]) => emit('update:ccUserIds', value)
})
</script>

<style scoped>
.submit-tip {
  margin: 0 0 var(--zw-space-md);
  color: var(--el-text-color-regular);
  line-height: 1.6;
}
</style>
