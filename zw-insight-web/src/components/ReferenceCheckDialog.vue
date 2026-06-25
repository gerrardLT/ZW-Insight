<template>
  <el-dialog
    v-model="dialogVisible"
    :title="title"
    width="520px"
    destroy-on-close
    @close="handleClose"
  >
    <!-- 正常删除确认 -->
    <template v-if="!hasReferences && !loading">
      <el-alert type="warning" :closable="false" show-icon>
        确定要删除该数据吗？此操作不可恢复。
      </el-alert>
    </template>

    <!-- 加载中 -->
    <template v-if="loading">
      <div class="loading-wrap">
        <el-icon class="is-loading"><Loading /></el-icon>
        <span>正在检查引用关系...</span>
      </div>
    </template>

    <!-- 有引用，禁止删除 -->
    <template v-if="hasReferences && !loading">
      <el-alert type="error" :closable="false" show-icon style="margin-bottom: 16px">
        无法删除：当前数据被以下单据引用，请先处理引用关系。
      </el-alert>
      <el-table :data="references" border size="small" max-height="300">
        <el-table-column prop="refType" label="引用类型" width="130" />
        <el-table-column prop="refCode" label="单据编号" min-width="160" show-overflow-tooltip />
        <el-table-column prop="refTime" label="引用时间" width="170" />
      </el-table>
    </template>

    <template #footer>
      <el-button @click="dialogVisible = false">取消</el-button>
      <el-button
        type="danger"
        :loading="deleting"
        :disabled="hasReferences || loading"
        @click="handleConfirmDelete"
      >
        确认删除
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Loading } from '@element-plus/icons-vue'

interface ReferenceItem {
  refType: string
  refCode: string
  refTime: string
}

const props = defineProps<{
  visible: boolean
  title?: string
  deleteAction: () => Promise<any>
}>()

const emit = defineEmits<{
  (e: 'update:visible', value: boolean): void
  (e: 'success'): void
}>()

const dialogVisible = ref(false)
const loading = ref(false)
const deleting = ref(false)
const hasReferences = ref(false)
const references = ref<ReferenceItem[]>([])

watch(() => props.visible, (val) => {
  dialogVisible.value = val
  if (val) {
    // 重置状态
    hasReferences.value = false
    references.value = []
    loading.value = false
    deleting.value = false
    // 打开时先尝试执行删除来检查引用
    checkAndDelete()
  }
})

watch(dialogVisible, (val) => {
  emit('update:visible', val)
})

async function checkAndDelete() {
  loading.value = true
  try {
    await props.deleteAction()
    // 删除成功，直接关闭并通知
    ElMessage.success('删除成功')
    dialogVisible.value = false
    emit('success')
  } catch (error: any) {
    // 检查是否为引用校验错误
    const responseData = error.response?.data
    if (error.response?.status === 400 && responseData?.data?.references) {
      // 有引用关系，显示引用列表
      hasReferences.value = true
      references.value = (responseData.data.references as ReferenceItem[]) || []
    } else {
      // 其他错误，关闭弹窗
      dialogVisible.value = false
    }
  } finally {
    loading.value = false
  }
}

async function handleConfirmDelete() {
  // 用户确认删除（仅在无引用时可点击）
  deleting.value = true
  try {
    await props.deleteAction()
    ElMessage.success('删除成功')
    dialogVisible.value = false
    emit('success')
  } catch (error: any) {
    const responseData = error.response?.data
    if (error.response?.status === 400 && responseData?.data?.references) {
      hasReferences.value = true
      references.value = (responseData.data.references as ReferenceItem[]) || []
    }
  } finally {
    deleting.value = false
  }
}

function handleClose() {
  emit('update:visible', false)
}
</script>

<style scoped>
.loading-wrap {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 24px 0;
  color: #909399;
  font-size: 14px;
}
</style>
